const TOKEN_VERSION = "v1";
const DEFAULT_TTL_SECONDS = 600;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function tokenSecret(): string {
  const value = Deno.env.get("AD_REWARD_TOKEN_SECRET")?.trim();
  if (!value) throw new Error("missing environment variable: AD_REWARD_TOKEN_SECRET");
  return value;
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function base64UrlBytes(value: string): Uint8Array {
  const normalized = value.replaceAll("-", "+").replaceAll("_", "/").padEnd(
    Math.ceil(value.length / 4) * 4,
    "=",
  );
  const binary = atob(normalized);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

async function hmac(value: string, operation: "sign" | "verify", signature?: Uint8Array): Promise<boolean | Uint8Array> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(tokenSecret()),
    { hash: "SHA-256", name: "HMAC" },
    false,
    [operation],
  );
  if (operation === "sign") {
    return new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value)));
  }
  return crypto.subtle.verify("HMAC", key, signature!, new TextEncoder().encode(value));
}

function ttlSeconds(): number {
  const configured = Number(Deno.env.get("AD_REWARD_TOKEN_TTL_SECONDS"));
  return Number.isInteger(configured) && configured >= 60 && configured <= 3600
    ? configured
    : DEFAULT_TTL_SECONDS;
}

export async function createRewardToken(userId: string): Promise<{ token: string; expiresAt: number }> {
  const expiresAt = Math.floor(Date.now() / 1000) + ttlSeconds();
  const nonce = crypto.randomUUID();
  const unsigned = `${TOKEN_VERSION}.${userId}.${expiresAt}.${nonce}`;
  const signature = await hmac(unsigned, "sign") as Uint8Array;
  return { token: `${unsigned}.${base64Url(signature)}`, expiresAt };
}

export async function verifyRewardToken(token: string): Promise<string | null> {
  const parts = token.split(".");
  if (parts.length !== 5 || parts[0] !== TOKEN_VERSION) return null;

  const [, userId, expiresAtText, nonce, signatureText] = parts;
  if (!UUID_PATTERN.test(userId) || !nonce || !/^\d+$/.test(expiresAtText)) return null;

  const expiresAt = Number(expiresAtText);
  if (!Number.isSafeInteger(expiresAt) || expiresAt < Math.floor(Date.now() / 1000)) return null;

  try {
    const valid = await hmac(
      `${TOKEN_VERSION}.${userId}.${expiresAt}.${nonce}`,
      "verify",
      base64UrlBytes(signatureText),
    ) as boolean;
    return valid ? userId : null;
  } catch {
    return null;
  }
}
