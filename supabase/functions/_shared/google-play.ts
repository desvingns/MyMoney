const TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
const API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3";
const PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";

type ServiceAccount = {
  client_email: string;
  private_key: string;
  private_key_id?: string;
};

type TokenResponse = {
  access_token?: string;
  expires_in?: number;
};

let cachedAccessToken: { value: string; expiresAt: number } | null = null;

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function encodeJson(value: unknown): string {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

function serviceAccount(): ServiceAccount {
  const raw = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")?.trim();
  if (!raw) throw new Error("missing environment variable: GOOGLE_PLAY_SERVICE_ACCOUNT_JSON");

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON is not valid JSON");
  }

  if (parsed === null || typeof parsed !== "object") {
    throw new Error("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON must be an object");
  }

  const value = parsed as Record<string, unknown>;
  const clientEmail = typeof value.client_email === "string" ? value.client_email.trim() : "";
  const privateKey = typeof value.private_key === "string"
    ? value.private_key.replaceAll("\\n", "\n").trim()
    : "";
  const privateKeyId = typeof value.private_key_id === "string" ? value.private_key_id.trim() : undefined;

  if (!clientEmail || !privateKey) {
    throw new Error("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON lacks client_email or private_key");
  }

  return { client_email: clientEmail, private_key: privateKey, private_key_id: privateKeyId };
}

function pemToDer(pem: string): ArrayBuffer {
  const encoded = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replaceAll(/\s/g, "");
  const binary = atob(encoded);
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  return bytes.buffer;
}

async function signAssertion(account: ServiceAccount): Promise<string> {
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(account.private_key),
    { hash: "SHA-256", name: "RSASSA-PKCS1-v1_5" },
    false,
    ["sign"],
  );
  const issuedAt = Math.floor(Date.now() / 1000);
  const unsigned = `${encodeJson({ alg: "RS256", typ: "JWT", ...(account.private_key_id ? { kid: account.private_key_id } : {}) })}.${encodeJson({
    aud: TOKEN_ENDPOINT,
    exp: issuedAt + 3600,
    iat: issuedAt,
    iss: account.client_email,
    scope: PLAY_SCOPE,
  })}`;
  const signature = new Uint8Array(await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  ));
  return `${unsigned}.${base64Url(signature)}`;
}

async function accessToken(): Promise<string> {
  if (cachedAccessToken && cachedAccessToken.expiresAt > Date.now() + 60_000) {
    return cachedAccessToken.value;
  }

  const account = serviceAccount();
  const response = await fetch(TOKEN_ENDPOINT, {
    body: new URLSearchParams({
      assertion: await signAssertion(account),
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    }),
    headers: { "content-type": "application/x-www-form-urlencoded" },
    method: "POST",
  });
  const payload = await response.json() as TokenResponse;
  if (!response.ok || typeof payload.access_token !== "string") {
    throw new Error(`Google OAuth token request failed with HTTP ${response.status}`);
  }

  const expiresIn = typeof payload.expires_in === "number" && payload.expires_in > 0
    ? payload.expires_in
    : 3600;
  cachedAccessToken = {
    expiresAt: Date.now() + expiresIn * 1000,
    value: payload.access_token,
  };
  return payload.access_token;
}

async function playGet(path: string): Promise<Record<string, unknown>> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { authorization: `Bearer ${await accessToken()}` },
  });
  const payload = await response.json().catch(() => null) as unknown;
  if (!response.ok || payload === null || typeof payload !== "object") {
    throw new Error(`Google Play API request failed with HTTP ${response.status}`);
  }
  return payload as Record<string, unknown>;
}

export function getGooglePlayPackageName(): string {
  return Deno.env.get("GOOGLE_PLAY_PACKAGE_NAME")?.trim() || "com.kshavrin.mymoney";
}

export async function getSubscriptionPurchase(
  packageName: string,
  purchaseToken: string,
): Promise<Record<string, unknown>> {
  return playGet(
    `/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`,
  );
}

export async function getProductPurchase(
  packageName: string,
  purchaseToken: string,
): Promise<Record<string, unknown>> {
  return playGet(
    `/applications/${encodeURIComponent(packageName)}/purchases/productsv2/tokens/${encodeURIComponent(purchaseToken)}`,
  );
}
