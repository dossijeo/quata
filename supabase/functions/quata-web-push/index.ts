import { createClient } from "npm:@supabase/supabase-js@2";

type WebPushRequest = {
  action?: "subscribe" | "unsubscribe" | "logout";
  subscription?: {
    endpoint?: string;
    expirationTime?: number | null;
    keys?: {
      p256dh?: string;
      auth?: string;
    };
  };
};

type WebSession = {
  id: string;
  profile_id: string;
  auth_user_id: string;
  revoked_at: string | null;
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type, x-quata-web-session",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

Deno.serve(async (req) => {
  try {
    if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
    if (req.method === "GET") {
      return jsonResponse({ public_key: requireEnv("WEB_PUSH_VAPID_PUBLIC_KEY") });
    }
    if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);

    const supabaseUrl = requireEnv("SUPABASE_URL");
    const serviceRoleKey =
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ||
      envDictionaryValue("SUPABASE_SECRET_KEYS");
    if (!serviceRoleKey) throw new Error("Missing service role key");

    const accessToken = bearerToken(req.headers.get("authorization"));
    const webSessionToken = req.headers.get("x-quata-web-session")?.trim() || "";
    if (!accessToken || !webSessionToken) {
      return jsonResponse({ error: "missing_web_authentication" }, 401);
    }

    const admin = createClient(supabaseUrl, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
      global: { headers: { "X-Client-Info": "quata-web-push" } },
    });
    const { data: userData, error: userError } = await admin.auth.getUser(accessToken);
    if (userError || !userData.user) {
      return jsonResponse({ error: "invalid_access_token" }, 401);
    }

    const session = await findWebSession(admin, webSessionToken, userData.user.id);
    if (!session) return jsonResponse({ error: "invalid_web_session" }, 401);

    let payload: WebPushRequest;
    try {
      payload = await req.json();
    } catch {
      return jsonResponse({ error: "invalid_json" }, 400);
    }

    const action = payload.action || "subscribe";
    if (action === "subscribe") {
      return subscribe(admin, req, session, payload.subscription);
    }
    if (action === "unsubscribe") {
      return unsubscribe(admin, session, payload.subscription?.endpoint);
    }
    if (action === "logout") {
      return logout(admin, session);
    }
    return jsonResponse({ error: "invalid_action" }, 400);
  } catch (error) {
    console.error(error);
    return jsonResponse(
      { error: "internal_error", detail: error instanceof Error ? error.message : "Unexpected error" },
      500,
    );
  }
});

async function findWebSession(
  admin: ReturnType<typeof createClient>,
  rawToken: string,
  authUserId: string,
): Promise<WebSession | null> {
  const tokenHash = await sha256(rawToken);
  const { data, error } = await admin
    .from("web_client_sessions")
    .select("id,profile_id,auth_user_id,revoked_at")
    .eq("token_hash", tokenHash)
    .eq("auth_user_id", authUserId)
    .is("revoked_at", null)
    .maybeSingle();
  if (error) throw error;
  if (!data) return null;
  const { error: touchError } = await admin
    .from("web_client_sessions")
    .update({ last_seen_at: new Date().toISOString() })
    .eq("id", data.id);
  if (touchError) throw touchError;
  return data as WebSession;
}

async function subscribe(
  admin: ReturnType<typeof createClient>,
  req: Request,
  session: WebSession,
  subscription: WebPushRequest["subscription"],
) {
  const endpoint = subscription?.endpoint?.trim() || "";
  const p256dh = subscription?.keys?.p256dh?.trim() || "";
  const authSecret = subscription?.keys?.auth?.trim() || "";
  if (!endpoint.startsWith("https://") || p256dh.length < 40 || authSecret.length < 16) {
    return jsonResponse({ error: "invalid_push_subscription" }, 400);
  }
  const now = new Date().toISOString();
  const { data, error } = await admin
    .from("web_push_subscriptions")
    .upsert(
      {
        web_session_id: session.id,
        profile_id: session.profile_id,
        auth_user_id: session.auth_user_id,
        endpoint,
        p256dh,
        auth_secret: authSecret,
        expiration_time: subscription?.expirationTime ?? null,
        user_agent: req.headers.get("user-agent"),
        updated_at: now,
        last_seen_at: now,
        disabled_at: null,
        last_error_text: null,
      },
      { onConflict: "endpoint" },
    )
    .select("id")
    .single();
  if (error) throw error;
  return jsonResponse({ result: true, subscription_id: data.id });
}

async function unsubscribe(
  admin: ReturnType<typeof createClient>,
  session: WebSession,
  rawEndpoint?: string,
) {
  let query = admin
    .from("web_push_subscriptions")
    .update({
      disabled_at: new Date().toISOString(),
      last_error_text: "Disabled by web client",
      updated_at: new Date().toISOString(),
    })
    .eq("web_session_id", session.id)
    .is("disabled_at", null);
  const endpoint = rawEndpoint?.trim();
  if (endpoint) query = query.eq("endpoint", endpoint);
  const { error, count } = await query.select("id", { count: "exact", head: true });
  if (error) throw error;
  return jsonResponse({ result: true, disabled: count ?? 0 });
}

async function logout(admin: ReturnType<typeof createClient>, session: WebSession) {
  const now = new Date().toISOString();
  const { error: subscriptionError } = await admin
    .from("web_push_subscriptions")
    .update({
      disabled_at: now,
      last_error_text: "Disabled on web logout",
      updated_at: now,
    })
    .eq("web_session_id", session.id)
    .is("disabled_at", null);
  if (subscriptionError) throw subscriptionError;
  const { error: sessionError } = await admin
    .from("web_client_sessions")
    .update({ revoked_at: now, updated_at: now })
    .eq("id", session.id);
  if (sessionError) throw sessionError;
  return jsonResponse({ result: true });
}

function bearerToken(value: string | null): string | null {
  const match = value?.match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const hash = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(hash))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

function requireEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

function envDictionaryValue(name: string, preferredKey = "default"): string | null {
  const raw = Deno.env.get(name);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const preferred = parsed[preferredKey];
    if (typeof preferred === "string" && preferred.trim()) return preferred;
    for (const value of Object.values(parsed)) {
      if (typeof value === "string" && value.trim()) return value;
    }
  } catch {
    return null;
  }
  return null;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
