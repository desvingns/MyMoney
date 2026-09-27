import {
    type SupporterVerificationDependencies,
    supporterVerificationHandler,
    verifiedSupporterPurchase,
} from "./supporter-verification.ts";

function assert(value: unknown, message = "assertion failed"): asserts value {
    if (!value) throw new Error(message);
}
const valid = {
    productLineItem: [{
        productId: "coffee_small",
        productOfferDetails: { consumptionState: "CONSUMED" },
    }],
    purchaseStateContext: { purchaseState: "PURCHASED" },
    purchaseCompletionTime: "2026-09-27T10:00:00Z",
};

Deno.test("only purchased coffee products with Google timestamps qualify, including consumed receipts", () => {
    assert(
        verifiedSupporterPurchase(valid)?.purchasedAt ===
            "2026-09-27T10:00:00.000Z",
    );
    for (const state of ["PENDING", "CANCELLED", undefined]) {
        assert(
            verifiedSupporterPurchase({
                ...valid,
                purchaseStateContext: { purchaseState: state },
            }) === null,
        );
    }
    assert(
        verifiedSupporterPurchase({
            ...valid,
            productLineItem: [{ productId: "plus_monthly" }],
        }) === null,
    );
    assert(
        verifiedSupporterPurchase({
            ...valid,
            purchaseCompletionTime: "invalid",
        }) === null,
    );
    assert(
        verifiedSupporterPurchase({ ...valid, productLineItem: [] }) === null,
    );
});

function fixture(overrides: Partial<SupporterVerificationDependencies> = {}) {
    const writes: unknown[][] = [];
    let lookups = 0;
    const deps: SupporterVerificationDependencies = {
        authenticate: () => Promise.resolve("authenticated-user"),
        existing: () => Promise.resolve(null),
        legacyTokens: () => Promise.resolve([]),
        purchase: () => {
            lookups++;
            return Promise.resolve(valid);
        },
        record: (...values) => {
            writes.push(values);
            return Promise.resolve(true);
        },
        ...overrides,
    };
    return {
        handler: supporterVerificationHandler(deps),
        writes,
        lookups: () => lookups,
    };
}
function request(body: unknown) {
    return new Request("https://example.invalid/verify", {
        method: "POST",
        body: JSON.stringify(body),
    });
}

Deno.test("owner, product and timestamp come from authentication and Play, never client fields", async () => {
    const f = fixture();
    assert(
        (await f.handler(
            request({
                purchase_token: "receipt",
                user_id: "victim",
                product_id: "forged",
                purchased_at: "forged",
            }),
        )).status === 200,
    );
    assert(
        JSON.stringify(f.writes) ===
            JSON.stringify([["authenticated-user", "receipt", {
                productId: "coffee_small",
                purchasedAt: "2026-09-27T10:00:00.000Z",
            }]]),
    );
});

Deno.test("unauthenticated, malformed, pending, foreign and failed verification never persist a purchase", async () => {
    for (
        const [overrides, status] of [
            [{ authenticate: () => Promise.resolve(null) }, 401],
            [{
                purchase: () =>
                    Promise.resolve({
                        ...valid,
                        purchaseStateContext: { purchaseState: "PENDING" },
                    }),
            }, 422],
            [{
                existing: () =>
                    Promise.resolve({ userId: "another-user", verified: true }),
            }, 409],
            [{
                purchase: () =>
                    Promise.reject(new Error("provider unavailable")),
            }, 503],
        ] as [Partial<SupporterVerificationDependencies>, number][]
    ) {
        const f = fixture(overrides);
        assert(
            (await f.handler(request({ purchase_token: "receipt" }))).status ===
                status,
        );
        assert(f.writes.length === 0);
    }
    const f = fixture();
    assert((await f.handler(request({ purchase_token: "" }))).status === 400);
    assert(f.writes.length === 0);
});

Deno.test("previously verified owner retries are idempotent without replaying provider calls", async () => {
    const f = fixture({
        existing: () =>
            Promise.resolve({ userId: "authenticated-user", verified: true }),
    });
    assert(
        (await f.handler(request({ purchase_token: "receipt" }))).status ===
            200,
    );
    assert(f.writes.length === 0 && f.lookups() === 0);
});

Deno.test("legacy refresh uses server-owned tokens and re-verifies before persistence", async () => {
    const f = fixture({
        legacyTokens: () => Promise.resolve(["legacy-receipt"]),
    });
    assert((await f.handler(request({ refresh: true }))).status === 200);
    assert(f.writes[0][1] === "legacy-receipt" && f.lookups() === 1);
});

Deno.test("concurrent owner conflict remains a conflict even after a valid Play lookup", async () => {
    const f = fixture({ record: () => Promise.resolve(false) });
    assert(
        (await f.handler(request({ purchase_token: "receipt" }))).status ===
            409,
    );
});

Deno.test("account changes cannot bind an old owner's queued receipt to a new authenticated account", async () => {
    const f = fixture();
    assert(
        (await f.handler(
            request({
                purchase_token: "receipt",
                expected_user_id: "previous-user",
            }),
        )).status === 403,
    );
    assert(f.lookups() === 0 && f.writes.length === 0);
});
