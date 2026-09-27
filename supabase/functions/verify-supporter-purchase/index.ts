import {
    getGooglePlayPackageName,
    getProductPurchase,
    GooglePlayApiError,
} from "../_shared/google-play.ts";
import { authenticatedUser, createAdminClient } from "../_shared/supabase.ts";
import { supporterVerificationHandler } from "../_shared/supporter-verification.ts";

Deno.serve(supporterVerificationHandler({
    authenticate: async (req) =>
        (await authenticatedUser(req))?.user.id ?? null,
    existing: async (token) => {
        const { data, error } = await createAdminClient().from(
            "supporter_purchases",
        )
            .select("user_id,verified_at").eq("purchase_token", token)
            .maybeSingle();
        if (error) throw new Error("purchase lookup failed");
        return data
            ? { userId: data.user_id, verified: data.verified_at !== null }
            : null;
    },
    legacyTokens: async (userId) => {
        const { data, error } = await createAdminClient().from(
            "supporter_purchases",
        )
            .select("purchase_token").eq("user_id", userId).is(
                "verified_at",
                null,
            ).limit(100);
        if (error) throw new Error("legacy purchase lookup failed");
        return (data ?? []).map((row) => row.purchase_token);
    },
    purchase: async (token) => {
        try {
            return await getProductPurchase(getGooglePlayPackageName(), token);
        } catch (error) {
            if (
                error instanceof GooglePlayApiError &&
                [400, 404, 410].includes(error.status)
            ) return {};
            throw error;
        }
    },
    record: async (userId, token, purchase) => {
        const { data, error } = await createAdminClient().rpc(
            "record_verified_supporter_purchase",
            {
                p_user_id: userId,
                p_purchase_token: token,
                p_product_id: purchase.productId,
                p_purchased_at: purchase.purchasedAt,
            },
        );
        if (error) throw new Error("verified purchase persistence failed");
        return data === true;
    },
}));
