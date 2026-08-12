import { createClient, type SupabaseClient, type User } from "npm:@supabase/supabase-js@2.49.8";

function requiredEnv(...names: string[]): string {
  for (const name of names) {
    const value = Deno.env.get(name)?.trim();
    if (value) return value;
  }
  throw new Error(`missing environment variable: ${names.join(" or ")}`);
}

function projectUrl(): string {
  return requiredEnv("SUPABASE_URL");
}

function namedKey(variable: string): string | null {
  const raw = Deno.env.get(variable)?.trim();
  if (!raw) return null;

  try {
    const values = JSON.parse(raw) as Record<string, unknown>;
    const defaultKey = values.default;
    return typeof defaultKey === "string" && defaultKey.trim() ? defaultKey.trim() : null;
  } catch {
    return null;
  }
}

function publicKey(): string {
  return namedKey("SUPABASE_PUBLISHABLE_KEYS") ??
    requiredEnv("SUPABASE_ANON_KEY", "SUPABASE_PUBLISHABLE_KEY");
}

function serviceKey(): string {
  return namedKey("SUPABASE_SECRET_KEYS") ??
    requiredEnv("SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_SECRET_KEY");
}

export function createUserClient(accessToken: string): SupabaseClient {
  return createClient(projectUrl(), publicKey(), {
    auth: {
      autoRefreshToken: false,
      detectSessionInUrl: false,
      persistSession: false,
    },
    global: {
      headers: { Authorization: `Bearer ${accessToken}` },
    },
  });
}

export function createAdminClient(): SupabaseClient {
  return createClient(projectUrl(), serviceKey(), {
    auth: {
      autoRefreshToken: false,
      detectSessionInUrl: false,
      persistSession: false,
    },
  });
}

export function bearerToken(req: Request): string | null {
  const value = req.headers.get("authorization")?.trim();
  if (!value?.toLowerCase().startsWith("bearer ")) return null;
  return value.slice(7).trim() || null;
}

export async function authenticatedUser(
  req: Request,
): Promise<{ accessToken: string; client: SupabaseClient; user: User } | null> {
  const accessToken = bearerToken(req);
  if (!accessToken) return null;

  const client = createUserClient(accessToken);
  const { data, error } = await client.auth.getUser(accessToken);
  if (error || !data.user) return null;

  return { accessToken, client, user: data.user };
}
