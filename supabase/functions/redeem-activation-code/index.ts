import { jsonResponse, readJson } from "../_shared/http.ts";
import { authenticatedUser } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);

  const auth = await authenticatedUser(req);
  if (!auth) return jsonResponse({ error: "unauthorized" }, 401);

  const body = await readJson(req);
  const code = typeof body?.code === "string" ? body.code.trim() : "";
  if (!code || code.length > 128) return jsonResponse({ error: "invalid_request" }, 400);

  const { data, error } = await auth.client.rpc("redeem_activation_code", { p_code: code });
  if (error) {
    const knownError =
      error.code === "P0002" ||
      error.code === "P0001" ||
      error.code === "22023";
    return jsonResponse({ error: knownError ? error.message : "redemption_failed" }, knownError ? 400 : 500);
  }

  return jsonResponse({ entitlement: Array.isArray(data) ? data[0] : data });
});
