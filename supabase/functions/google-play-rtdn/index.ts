import { jsonResponse, readJson } from "../_shared/http.ts";
import { verifyPubSubOidc } from "../_shared/pubsub-oidc.ts";
import {
  getGooglePlayPackageName,
  getProductPurchase,
  getSubscriptionPurchase,
} from "../_shared/google-play.ts";
import { createAdminClient } from "../_shared/supabase.ts";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const DEFAULT_PLUS_PRODUCTS = new Set(["plus_monthly", "plus_yearly"]);
const GRANTABLE_SUBSCRIPTION_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED",
]);
const REVOKED_SUBSCRIPTION_STATES = new Set([
  "SUBSCRIPTION_STATE_ON_HOLD",
  "SUBSCRIPTION_STATE_PAUSED",
  "SUBSCRIPTION_STATE_EXPIRED",
  "SUBSCRIPTION_STATE_REVOKED",
  "SUBSCRIPTION_STATE_PENDING",
]);

type PubSubMessage = {
  data?: unknown;
  messageId?: unknown;
  message_id?: unknown;
};

type ProviderEvent = {
  id: string;
  status: "received" | "processed" | "failed";
};

type Reconciliation = {
  entitlementChangedForUserId: string | null;
  result: Record<string, unknown>;
};

function decodeBase64Json(data: string): Record<string, unknown> | null {
  try {
    const normalized = data.replaceAll("-", "+").replaceAll("_", "/").padEnd(
      Math.ceil(data.length / 4) * 4,
      "=",
    );
    const binary = atob(normalized);
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const value: unknown = JSON.parse(new TextDecoder().decode(bytes));
    return value !== null && typeof value === "object" && !Array.isArray(value)
      ? value as Record<string, unknown>
      : null;
  } catch {
    return null;
  }
}

function objectValue(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function stringValue(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function notificationType(notification: Record<string, unknown>): string {
  const subscription = objectValue(notification.subscriptionNotification);
  if (subscription) return `subscription_${String(subscription.notificationType ?? "unknown")}`;
  const product = objectValue(notification.oneTimeProductNotification);
  if (product) return `one_time_product_${String(product.notificationType ?? "unknown")}`;
  if (notification.voidedPurchaseNotification) return "voided_purchase";
  if (notification.pendingRefundReviewNotification) return "pending_refund_review";
  if (notification.testNotification) return "test";
  return "unknown";
}

function purchaseToken(notification: Record<string, unknown>): string | null {
  for (const key of ["subscriptionNotification", "oneTimeProductNotification", "voidedPurchaseNotification"]) {
    const value = objectValue(notification[key]);
    const token = stringValue(value?.purchaseToken);
    if (token) return token;
  }
  return null;
}

function productIds(purchase: Record<string, unknown>): string[] {
  const ids = new Set<string>();
  const lineItems = Array.isArray(purchase.lineItems) ? purchase.lineItems : [];
  for (const item of lineItems) {
    const lineItem = objectValue(item);
    const productId = stringValue(lineItem?.productId);
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

function productAllowlisted(ids: string[]): boolean {
  const configured = (Deno.env.get("GOOGLE_PLAY_PLUS_PRODUCT_IDS") ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
  const allowed = configured.length > 0 ? new Set(configured) : DEFAULT_PLUS_PRODUCTS;
  return ids.some((id) => allowed.has(id));
}

function mappedUserId(existing: Record<string, unknown> | null): string | null {
  const userId = stringValue(existing?.user_id);
  return userId && UUID_PATTERN.test(userId) ? userId : null;
}

async function findExistingEntitlement(admin: ReturnType<typeof createAdminClient>, token: string) {
  const { data, error } = await admin
    .from("entitlements")
    .select("id,user_id,starts_at,expires_at,revoked_at")
    .eq("provider", "google_play")
    .eq("provider_reference", token)
    .limit(1)
    .maybeSingle();
  if (error) throw new Error("failed to load existing Google Play entitlement");
  return data as Record<string, unknown> | null;
}

async function updateProviderEvent(
  admin: ReturnType<typeof createAdminClient>,
  eventId: string,
  status: "processed" | "failed",
  eventType: string,
  payload: Record<string, unknown>,
  errorMessage: string | null = null,
) {
  const { error } = await admin.from("provider_events").update({
    error_message: errorMessage,
    event_type: eventType,
    payload,
    processed_at: status === "processed" ? new Date().toISOString() : null,
    status,
  }).eq("id", eventId);
  if (error) throw new Error("failed to update Google Play provider event");
}

async function reconcileSubscription(
  admin: ReturnType<typeof createAdminClient>,
  token: string,
  purchase: Record<string, unknown>,
  eventType: string,
): Promise<Reconciliation> {
  const existing = await findExistingEntitlement(admin, token);
  const state = stringValue(purchase.subscriptionState) ?? "UNKNOWN";
  const ids = productIds(purchase);
  const userId = mappedUserId(existing);
  const metadata = {
    google_play_state: state,
    latest_order_id: stringValue(purchase.latestOrderId),
    product_ids: ids,
    source_event_type: eventType,
  };

  if (!userId) {
    return {
      entitlementChangedForUserId: null,
      result: { action: "awaiting_purchase_binding", product_ids: ids, state },
    };
  }
  if (existing && stringValue(existing.user_id) !== userId) {
    throw new Error("Google Play entitlement user mapping conflict");
  }
  if (GRANTABLE_SUBSCRIPTION_STATES.has(state)) {
    if (!productAllowlisted(ids)) {
      return {
        entitlementChangedForUserId: null,
        result: { action: "ignored_product", product_ids: ids, state },
      };
    }
    const startsAt = stringValue(purchase.startTime) ?? new Date().toISOString();
    const expiresAt = expiryTime(purchase);
    const values = {
      entitlement: "plus",
      expires_at: expiresAt,
      metadata,
      provider: "google_play",
      provider_reference: token,
      revoked_at: null,
      starts_at: startsAt,
      user_id: userId,
    };
    const query = existing
      ? admin.from("entitlements").update(values).eq("id", existing.id)
      : admin.from("entitlements").insert(values);
    const { error } = await query;
    if (error) throw new Error("failed to grant Google Play entitlement");
    return {
      entitlementChangedForUserId: userId,
      result: { action: "entitlement_granted", expires_at: expiresAt, product_ids: ids, state },
    };
  }
  if (REVOKED_SUBSCRIPTION_STATES.has(state) && existing) {
    const { error } = await admin.from("entitlements").update({
      metadata,
      revoked_at: new Date().toISOString(),
    }).eq("id", existing.id);
    if (error) throw new Error("failed to revoke Google Play entitlement");
    return {
      entitlementChangedForUserId: userId,
      result: { action: "entitlement_revoked", product_ids: ids, state },
    };
  }
  return {
    entitlementChangedForUserId: null,
    result: { action: "state_recorded", product_ids: ids, state },
  };
}

async function recomputeWorkspaceBillingState(admin: ReturnType<typeof createAdminClient>) {
  const { error } = await admin.rpc("recompute_workspace_billing_state_from_rtdn");
  if (error) throw new Error("failed to recompute workspace billing state");
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);
  if (!(await verifyPubSubOidc(req))) return jsonResponse({ error: "invalid_pubsub_identity" }, 401);

  const body = await readJson(req);
  const message = objectValue(body?.message) as PubSubMessage | null;
  const messageId = stringValue(message?.messageId ?? message?.message_id);
  const data = stringValue(message?.data);
  if (!message || !messageId || !data) return jsonResponse({ error: "invalid_pubsub_message" }, 400);

  const notification = decodeBase64Json(data);
  if (!notification) return jsonResponse({ error: "invalid_notification_data" }, 400);

  const packageName = stringValue(notification.packageName);
  if (packageName !== getGooglePlayPackageName()) return jsonResponse({ ok: true, ignored: "package_mismatch" });

  const eventType = notificationType(notification);
  const admin = createAdminClient();
  let providerEvent: ProviderEvent | null = null;
  const eventPayload: Record<string, unknown> = {
    event_time_millis: stringValue(notification.eventTimeMillis),
    message_id: messageId,
    notification,
  };

  const { data: inserted, error: insertError } = await admin.from("provider_events").insert({
    event_key: messageId,
    event_type: eventType,
    payload: eventPayload,
    provider: "google_play_rtdn",
    status: "received",
  }).select("id,status").single();
  if (insertError?.code === "23505") {
    const { data: existing, error: existingError } = await admin.from("provider_events")
      .select("id,status")
      .eq("provider", "google_play_rtdn")
      .eq("event_key", messageId)
      .single();
    if (existingError || !existing) return jsonResponse({ error: "temporary_database_failure" }, 500);
    if (existing.status === "processed") return jsonResponse({ ok: true, duplicate: true });
    providerEvent = existing as ProviderEvent;
  } else if (insertError || !inserted) {
    console.error("google-play-rtdn event insert failed", insertError);
    return jsonResponse({ error: "temporary_database_failure" }, 500);
  } else {
    providerEvent = inserted as ProviderEvent;
  }

  try {
    const token = purchaseToken(notification);
    let result: Record<string, unknown> = { action: "event_recorded" };
    if (token && objectValue(notification.subscriptionNotification)) {
      const purchase = await getSubscriptionPurchase(packageName, token);
      const reconciliation = await reconcileSubscription(admin, token, purchase, eventType);
      result = reconciliation.result;
      if (reconciliation.entitlementChangedForUserId) {
        await recomputeWorkspaceBillingState(admin);
      }
      eventPayload.play_purchase = {
        latest_order_id: stringValue(purchase.latestOrderId),
        product_ids: productIds(purchase),
        state: stringValue(purchase.subscriptionState),
      };
    } else if (token && objectValue(notification.oneTimeProductNotification)) {
      const purchase = await getProductPurchase(packageName, token);
      eventPayload.play_purchase = {
        product_ids: productIds(purchase),
        purchase_state: stringValue(purchase.purchaseState),
      };
      result = { action: "one_time_purchase_recorded", product_ids: productIds(purchase) };
    } else if (objectValue(notification.testNotification)) {
      result = { action: "test_notification_recorded" };
    }

    await updateProviderEvent(admin, providerEvent.id, "processed", eventType, {
      ...eventPayload,
      result,
    });
    return jsonResponse({ ok: true, duplicate: false, result });
  } catch (error) {
    const messageText = error instanceof Error ? error.message : "unknown error";
    console.error("google-play-rtdn failed", error);
    try {
      await updateProviderEvent(admin, providerEvent.id, "failed", eventType, eventPayload, messageText);
    } catch (updateError) {
      console.error("google-play-rtdn failure audit update failed", updateError);
    }
    return jsonResponse({ error: "temporary_verification_failure" }, 500);
  }
});
