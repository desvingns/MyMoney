import { jsonResponse, readJson } from "./http.ts";

export type VerifiedSupporterPurchase = {
    productId: string;
    purchasedAt: string;
};
export type ExistingSupporterPurchase = { userId: string; verified: boolean };
export type SupporterVerificationDependencies = {
    authenticate: (req: Request) => Promise<string | null>;
    existing: (token: string) => Promise<ExistingSupporterPurchase | null>;
    legacyTokens: (userId: string) => Promise<string[]>;
    purchase: (token: string) => Promise<Record<string, unknown>>;
    record: (
        userId: string,
        token: string,
        purchase: VerifiedSupporterPurchase,
    ) => Promise<boolean>;
};

export function verifiedSupporterPurchase(
    value: Record<string, unknown>,
): VerifiedSupporterPurchase | null {
    const state = value.purchaseStateContext as
        | Record<string, unknown>
        | undefined;
    const items = value.productLineItem;
    if (
        state?.purchaseState !== "PURCHASED" || !Array.isArray(items) ||
        items.length !== 1
    ) return null;
    const productId = items[0]?.productId;
    if (productId !== "coffee_small" && productId !== "coffee_large") {
        return null;
    }
    const time = value.purchaseCompletionTime;
    if (typeof time !== "string" || !Number.isFinite(Date.parse(time))) {
        return null;
    }
    return { productId, purchasedAt: new Date(time).toISOString() };
}

export function supporterVerificationHandler(
    deps: SupporterVerificationDependencies,
) {
    async function verify(userId: string, token: string): Promise<number> {
        const existing = await deps.existing(token);
        if (existing && existing.userId !== userId) return 409;
        if (existing?.verified) return 200;
        const purchase = verifiedSupporterPurchase(await deps.purchase(token));
        if (!purchase) return 422;
        return await deps.record(userId, token, purchase) ? 200 : 409;
    }

    return async (req: Request): Promise<Response> => {
        if (req.method !== "POST") {
            return jsonResponse({ error: "method_not_allowed" }, 405);
        }
        try {
            const userId = await deps.authenticate(req);
            if (!userId) return jsonResponse({ error: "unauthorized" }, 401);
            const body = await readJson(req);
            if (
                body?.expected_user_id !== undefined &&
                body.expected_user_id !== userId
            ) {
                return jsonResponse(
                    { error: "authenticated_user_changed" },
                    403,
                );
            }
            if (body?.refresh === true) {
                const tokens = await deps.legacyTokens(userId);
                for (const token of tokens) await verify(userId, token);
                return jsonResponse({ refreshed: true });
            }
            const token = body?.purchase_token;
            if (
                typeof token !== "string" || !token.trim() ||
                token.length > 4096
            ) {
                return jsonResponse({ error: "invalid_purchase_token" }, 400);
            }
            const status = await verify(userId, token);
            return jsonResponse(
                status === 200 ? { verified: true } : {
                    error: status === 409
                        ? "purchase_already_bound"
                        : "invalid_supporter_purchase",
                },
                status,
            );
        } catch {
            return jsonResponse(
                { error: "purchase_verification_unavailable" },
                503,
            );
        }
    };
}
