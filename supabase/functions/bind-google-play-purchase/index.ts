import { jsonResponse, readJson } from "../_shared/http.ts";
import {
  getGooglePlayPackageName,
  getSubscriptionPurchase,
} from "../_shared/google-play.ts";
import { authenticatedUser, createAdminClient } from "../_shared/supabase.ts";

const DEFAULT_PLUS_PRODUCTS = new Set(["plus_monthly", "plus_yearly"]);
const GRANTABLE_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED",
]);

function stringValue(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function objectValue(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function productIds(purchase: Record<string, unknown>): string[] {
  const ids = new Set<string>();
  const lineItems = Array.isArray(purchase.lineItems) ? purchase.lineItems : [];
  for (const item of lineItems) {
    const productId = stringValue(objectValue(item)?.productId);
    if (productId) ids.add(productId);
  }
  const productId = stringValue(purchase.productId);
  if (productId) ids.add(productId);
  return [...ids];
}

function expiryTime(purchase: Record<string, unknown>): string | null {
  const values: string[] = [];
  const lineItems = Array.isArray(purchase.lineItems) ? purchase.lineItems : [];
  for (const item of lineItems) {
    const value = stringValue(objectValue(item)?.expiryTime);
    if (value) values.push(value);
  }
  const topLevel = stringValue(purchase.expiryTime);
  if (topLevel) values.push(topLevel);
  return values.sort().at(-1) ?? null;
}

function allowedProductIds(): Set<string> {
  const configured = (Deno.env.get("GOOGLE_PLAY_PLUS_PRODUCT_IDS") ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
  return new Set(configured.length > 0 ? configured : DEFAULT_PLUS_PRODUCTS);
}

function isPlusPurchase(ids: string[]): boolean {
  const allowed = allowedProductIds();
  return ids.some((id) => allowed.has(id));
}

function isoDate(value: string | null): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);

  const auth = await authenticatedUser(req);
  if (!auth) return jsonResponse({ error: "unauthorized" }, 401);

  const body = await readJson(req);
  const purchaseToken = stringValue(body?.purchase_token ?? body?.purchaseToken);
  if (!purchaseToken || purchaseToken.length > 512) {
    return jsonResponse({ error: "purchase_token_required" }, 400);
  }

  try {
    const packageName = getGooglePlayPackageName();
    const purchase = await getSubscriptionPurchase(packageName, purchaseToken);
    const state = stringValue(purchase.subscriptionState) ?? "UNKNOWN";
    const ids = productIds(purchase);
    const expiresAt = isoDate(expiryTime(purchase));
    const startsAt = isoDate(stringValue(purchase.startTime)) ?? new Date().toISOString();

    if (!GRANTABLE_STATES.has(state)) {
      return jsonResponse({ error: "purchase_not_active", state }, 409);
    }
    if (!isPlusPurchase(ids)) {
      return jsonResponse({ error: "purchase_is_not_plus", product_ids: ids }, 409);
    }
    if (!expiresAt || new Date(expiresAt).getTime() <= Date.now()) {
      return jsonResponse({ error: "purchase_expired", state }, 409);
    }
    if (new Date(startsAt).getTime() >= new Date(expiresAt).getTime()) {
      return jsonResponse({ error: "invalid_purchase_period" }, 409);
    }

    const admin = createAdminClient();
    const { data: existing, error: existingError } = await admin
      .from("entitlements")
      .select("id,user_id")
      .eq("provider", "google_play")
      .eq("provider_reference", purchaseToken)
      .limit(1)
      .maybeSingle();
    if (existingError) throw new Error("failed to load Google Play entitlement");
    if (existing && existing.user_id !== auth.user.id) {
      return jsonResponse({ error: "purchase_already_bound" }, 409);
    }

    const values = {
      entitlement: "plus",
      expires_at: expiresAt,
      metadata: {
        google_play_state: state,
        latest_order_id: stringValue(purchase.latestOrderId),
        product_ids: ids,
        source: "bind-google-play-purchase",
      },
      provider: "google_play",
      provider_reference: purchaseToken,
      revoked_at: null,
      starts_at: startsAt,
      user_id: auth.user.id,
    };

    const mutation = existing
      ? admin.from("entitlements").update(values).eq("id", existing.id)
      : admin.from("entitlements").insert(values);
    const { error: mutationError } = await mutation;
    if (mutationError) {
      console.error("bind-google-play-purchase database mutation failed", mutationError);
      return jsonResponse({ error: "temporary_database_failure" }, 500);
    }

    return jsonResponse({
      entitlement: "plus",
      expires_at: expiresAt,
      product_ids: ids,
      state,
      updated: Boolean(existing),
    });
  } catch (error) {
    console.error("bind-google-play-purchase failed", error);
    return jsonResponse({ error: "temporary_verification_failure" }, 500);
  }
});
