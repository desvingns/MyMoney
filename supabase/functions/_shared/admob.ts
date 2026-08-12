const ADMOB_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
const KEY_CACHE_TTL_MS = 23 * 60 * 60 * 1000;
const P256_COMPONENT_BYTES = 32;

type CachedKeys = {
  expiresAt: number;
  keys: Map<number, CryptoKey>;
};

let cachedKeys: CachedKeys | null = null;

function base64Bytes(value: string): Uint8Array {
  const normalized = value.replaceAll("-", "+").replaceAll("_", "/").padEnd(
    Math.ceil(value.length / 4) * 4,
    "=",
  );
  const binary = atob(normalized);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function derIntegerToFixedWidth(value: Uint8Array): Uint8Array {
  let first = 0;
  while (first < value.length - 1 && value[first] === 0) first++;
  const trimmed = value.slice(first);
  if (trimmed.length > P256_COMPONENT_BYTES) throw new Error("invalid ECDSA component");
  const result = new Uint8Array(P256_COMPONENT_BYTES);
  result.set(trimmed, P256_COMPONENT_BYTES - trimmed.length);
  return result;
}

function derToP1363(signature: Uint8Array): Uint8Array {
  if (signature.length < 8 || signature[0] !== 0x30) throw new Error("invalid ECDSA signature");
  let offset = 1;
  const sequenceLength = signature[offset++];
  if ((sequenceLength & 0x80) !== 0 || sequenceLength !== signature.length - offset) {
    throw new Error("invalid ECDSA sequence");
  }
  if (signature[offset++] !== 0x02) throw new Error("invalid ECDSA r component");
  const rLength = signature[offset++];
  const r = signature.slice(offset, offset + rLength);
  offset += rLength;
  if (signature[offset++] !== 0x02) throw new Error("invalid ECDSA s component");
  const sLength = signature[offset++];
  const s = signature.slice(offset, offset + sLength);
  if (offset + sLength !== signature.length) throw new Error("invalid ECDSA signature length");

  const result = new Uint8Array(P256_COMPONENT_BYTES * 2);
  result.set(derIntegerToFixedWidth(r), 0);
  result.set(derIntegerToFixedWidth(s), P256_COMPONENT_BYTES);
  return result;
}

async function fetchKeys(forceRefresh = false): Promise<Map<number, CryptoKey>> {
  if (!forceRefresh && cachedKeys && cachedKeys.expiresAt > Date.now()) return cachedKeys.keys;

  const response = await fetch(ADMOB_KEYS_URL, { headers: { accept: "application/json" } });
  if (!response.ok) throw new Error(`AdMob key server returned ${response.status}`);
  const payload = await response.json() as { keys?: Array<{ keyId?: number; base64?: string }> };
  const keys = new Map<number, CryptoKey>();
  for (const entry of payload.keys ?? []) {
    if (!Number.isSafeInteger(entry.keyId) || !entry.base64) continue;
    const key = await crypto.subtle.importKey(
      "spki",
      base64Bytes(entry.base64),
      { name: "ECDSA", namedCurve: "P-256" },
      false,
      ["verify"],
    );
    keys.set(entry.keyId, key);
  }
  if (keys.size === 0) throw new Error("AdMob key server returned no usable keys");
  cachedKeys = { expiresAt: Date.now() + KEY_CACHE_TTL_MS, keys };
  return keys;
}

async function verifyWithKey(key: CryptoKey, content: Uint8Array, derSignature: Uint8Array): Promise<boolean> {
  const algorithm = { hash: "SHA-256", name: "ECDSA" } as const;
  try {
    if (await crypto.subtle.verify(algorithm, key, derToP1363(derSignature), content)) return true;
  } catch {
  }
  try {
    return await crypto.subtle.verify(algorithm, key, derSignature, content);
  } catch {
    return false;
  }
}

export async function verifyAdMobSignature(
  signedContent: string,
  keyId: number,
  signature: string,
): Promise<boolean> {
  const signatureBytes = base64Bytes(signature);
  let keys = await fetchKeys();
  let key = keys.get(keyId);
  if (!key) {
    keys = await fetchKeys(true);
    key = keys.get(keyId);
  }
  if (!key) return false;
  return verifyWithKey(key, new TextEncoder().encode(signedContent), signatureBytes);
}

export async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function signedQueryParts(rawQuery: string): {
  keyId: number;
  signature: string;
  signedContent: string;
} | null {
  const signatureMarker = "&signature=";
  const keyIdMarker = "&key_id=";
  const signatureAt = rawQuery.lastIndexOf(signatureMarker);
  const keyIdAt = rawQuery.lastIndexOf(keyIdMarker);
  if (signatureAt <= 0 || keyIdAt <= signatureAt) return null;

  const signature = rawQuery.slice(signatureAt + signatureMarker.length, keyIdAt);
  const keyIdText = rawQuery.slice(keyIdAt + keyIdMarker.length);
  if (!signature || !/^\d+$/.test(keyIdText)) return null;

  const keyId = Number(keyIdText);
  return Number.isSafeInteger(keyId)
    ? { keyId, signature, signedContent: rawQuery.slice(0, signatureAt) }
    : null;
}
