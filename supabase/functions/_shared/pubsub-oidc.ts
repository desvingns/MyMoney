import { createRemoteJWKSet, jwtVerify, type JWTPayload } from "npm:jose@6.0.11";

const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
const DEFAULT_AUDIENCE = "https://shwzjlkhlpgbmzgnxhxi.supabase.co/functions/v1/google-play-rtdn";
const DEFAULT_SERVICE_ACCOUNT = "mymoney-pubsub-push@my-money-502807.iam.gserviceaccount.com";

function configured(name: string, fallback: string): string {
  return Deno.env.get(name)?.trim() || fallback;
}

export async function verifyPubSubOidc(req: Request): Promise<boolean> {
  const authorization = req.headers.get("authorization")?.trim() ?? "";
  if (!authorization.toLowerCase().startsWith("bearer ")) return false;

  const token = authorization.slice(7).trim();
  if (!token) return false;

  try {
    const result = await jwtVerify(token, GOOGLE_JWKS, {
      algorithms: ["RS256"],
      audience: configured("RTDN_PUSH_AUDIENCE", DEFAULT_AUDIENCE),
      issuer: ["accounts.google.com", "https://accounts.google.com"],
    });
    const payload: JWTPayload & { email?: unknown; email_verified?: unknown } = result.payload;
    return payload.email === configured(
      "RTDN_PUSH_SERVICE_ACCOUNT_EMAIL",
      DEFAULT_SERVICE_ACCOUNT,
    ) && payload.email_verified === true;
  } catch {
    return false;
  }
}
