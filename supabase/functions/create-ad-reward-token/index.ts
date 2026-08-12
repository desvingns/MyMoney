import { jsonResponse } from "../_shared/http.ts";
import { authenticatedUser } from "../_shared/supabase.ts";
import { createRewardToken } from "../_shared/reward-token.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);

  const auth = await authenticatedUser(req);
  if (!auth) return jsonResponse({ error: "unauthorized" }, 401);

  try {
    const { token, expiresAt } = await createRewardToken(auth.user.id);
    return jsonResponse({ custom_data: token, expires_at: expiresAt });
  } catch (error) {
    console.error("create-ad-reward-token failed", error);
    return jsonResponse({ error: "server_configuration_error" }, 500);
  }
});
