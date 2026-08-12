import { jsonResponse } from "../_shared/http.ts";
import { verifyAdMobSignature, sha256Hex, signedQueryParts } from "../_shared/admob.ts";
import { verifyRewardToken } from "../_shared/reward-token.ts";
import { createAdminClient } from "../_shared/supabase.ts";

function positiveInteger(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

Deno.serve(async (req) => {
  if (req.method !== "GET") return jsonResponse({ error: "method_not_allowed" }, 405);

  const url = new URL(req.url);
  const rawQuery = url.search.slice(1);
  const signed = signedQueryParts(rawQuery);
  if (!signed) return jsonResponse({ error: "invalid_callback_format" }, 400);

  try {
    if (!(await verifyAdMobSignature(signed.signedContent, signed.keyId, signed.signature))) {
      return jsonResponse({ error: "invalid_signature" }, 400);
    }

    const transactionId = url.searchParams.get("transaction_id")?.trim() ?? "";
    const rewardItem = url.searchParams.get("reward_item")?.trim() ?? "";
    const rewardAmount = positiveInteger(url.searchParams.get("reward_amount"));
    const timestamp = positiveInteger(url.searchParams.get("timestamp"));
    const customData = url.searchParams.get("custom_data")?.trim() ?? "";
    const claimedUserId = url.searchParams.get("user_id")?.trim() ?? "";
    const expectedAdUnit = Deno.env.get("ADMOB_REWARDED_AD_UNIT_ID")?.trim();

    if (
      !transactionId ||
      !rewardItem ||
      rewardAmount === null ||
      timestamp === null
    ) {
      return jsonResponse({ error: "invalid_callback_parameters" }, 400);
    }

    if (!customData) return jsonResponse({ ok: true, verification: true });

    const userId = await verifyRewardToken(customData);
    if (
      !userId ||
      (claimedUserId && claimedUserId !== userId) ||
      (expectedAdUnit && url.searchParams.get("ad_unit") !== expectedAdUnit)
    ) {
      return jsonResponse({ error: "invalid_callback_parameters" }, 400);
    }

    const admin = createAdminClient();
    const rewardedAt = new Date(timestamp);
    if (Number.isNaN(rewardedAt.getTime())) return jsonResponse({ error: "invalid_timestamp" }, 400);

    const { data, error } = await admin
      .from("ad_rewards")
      .insert({
        custom_data: customData,
        payload_hash: await sha256Hex(signed.signedContent),
        reward_amount: rewardAmount,
        reward_item: rewardItem,
        rewarded_at: rewardedAt.toISOString(),
        transaction_id: transactionId,
        user_id: userId,
        verified_at: new Date().toISOString(),
      })
      .select("id")
      .single();

    if (error?.code === "23505") {
      return jsonResponse({ ok: true, duplicate: true });
    }
    if (error || !data) {
      console.error("admob-ssv database insert failed", error);
      return jsonResponse({ error: "temporary_database_failure" }, 500);
    }

    const { error: eventError } = await admin.from("provider_events").upsert({
      event_key: transactionId,
      event_type: "reward_verified",
      payload: {
        ad_network: url.searchParams.get("ad_network"),
        ad_unit: url.searchParams.get("ad_unit"),
        reward_amount: rewardAmount,
        reward_item: rewardItem,
        transaction_id: transactionId,
        user_id: userId,
      },
      processed_at: new Date().toISOString(),
      provider: "admob_ssv",
      status: "processed",
    }, { onConflict: "provider,event_key", ignoreDuplicates: true });
    if (eventError) console.error("admob-ssv audit event insert failed", eventError);

    return jsonResponse({ ok: true, duplicate: false, reward_id: data.id });
  } catch (error) {
    console.error("admob-ssv failed", error);
    return jsonResponse({ error: "temporary_verification_failure" }, 500);
  }
});
