import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  try {
    const accessToken = bearerToken(req.headers.get("authorization"));
    if (!accessToken) return json({ error: "missing_authentication" }, 401);

    const supabaseUrl = requireEnv("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || envDictionaryValue("SUPABASE_SECRET_KEYS");
    if (!serviceRoleKey) throw new Error("Missing service role key");
    const admin = createClient(supabaseUrl, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
      global: { headers: { "X-Client-Info": "quata-auth-global-logout" } },
    });

    const { data: userData, error: userError } = await admin.auth.getUser(accessToken);
    if (userError || !userData.user) return json({ error: "invalid_access_token" }, 401);

    const { data: retirement, error: retirementError } = await admin.rpc(
      "quata_retire_all_device_endpoints",
      { p_auth_user_id: userData.user.id },
    );
    if (retirementError) throw retirementError;

    const { error: signOutError } = await admin.auth.admin.signOut(accessToken, "global");
    if (signOutError) throw signOutError;

    return json({ result: true, retirement });
  } catch (error) {
    console.error(error);
    return json({ error: "internal_error" }, 500);
  }
});

function bearerToken(value: string | null): string | null {
  const match = value?.match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function requireEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

function envDictionaryValue(name: string): string | null {
  const raw = Deno.env.get(name);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    for (const value of Object.values(parsed)) {
      if (typeof value === "string" && value.trim()) return value;
    }
  } catch {
    return null;
  }
  return null;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
