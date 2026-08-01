import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Vary": "Origin",
};
const languages = new Set(["ES", "EN", "FR"]);
const maxBodyBytes = 48_000;
const maxText = 20_000;

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const declared = Number(request.headers.get("content-length") ?? "0");
  if (declared > maxBodyBytes) return json({ error: "request_too_large" }, 413);
  const authorization = request.headers.get("authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return json({ error: "session_required" }, 401);
  const raw = await request.text();
  if (new TextEncoder().encode(raw).byteLength > maxBodyBytes) return json({ error: "request_too_large" }, 413);
  let payload: Record<string, unknown>;
  try { payload = JSON.parse(raw); } catch { return json({ error: "invalid_json" }, 400); }
  const source = String(payload.source ?? "").toUpperCase();
  const target = String(payload.target ?? "").toUpperCase();
  const text = String(payload.text ?? "");
  const tagHandling = payload.tagHandling === "html" ? "html" : null;
  if (!languages.has(source) || !languages.has(target) || source === target) return json({ error: "invalid_language" }, 400);
  if (!text.trim() || text.length > maxText) return json({ error: "invalid_text" }, 400);

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const deepLKey = Deno.env.get("DEEPL_API_KEY") ?? "";
  if (!supabaseUrl || !anonKey || !deepLKey) return json({ error: "translation_not_configured" }, 503);
  const client = createClient(supabaseUrl, anonKey, { global: { headers: { Authorization: authorization } } });
  const { data: auth, error: authError } = await client.auth.getUser(authorization.slice(7));
  if (authError || !auth.user) return json({ error: "invalid_session" }, 401);
  const { data: profile, error: profileError } = await client.from("community_profiles")
    .select("id,is_official,is_admin").or(`id.eq.${auth.user.id},auth_user_id.eq.${auth.user.id}`).limit(1).maybeSingle();
  if (profileError) return json({ error: "profile_check_failed" }, 503);
  if (!profile || (profile.is_official !== true && profile.is_admin !== true)) return json({ error: "official_role_required" }, 403);

  const form = new URLSearchParams({ text, source_lang: source, target_lang: target === "EN" ? "EN-US" : target });
  if (tagHandling) form.set("tag_handling", tagHandling);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 20_000);
  try {
    const response = await fetch("https://api-free.deepl.com/v2/translate", {
      method: "POST", signal: controller.signal,
      headers: { Authorization: `DeepL-Auth-Key ${deepLKey}`, "Content-Type": "application/x-www-form-urlencoded" },
      body: form,
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) return json({ error: "translation_upstream_failed", status: response.status }, 502);
    const translated = body?.translations?.[0];
    if (typeof translated?.text !== "string") return json({ error: "translation_response_invalid" }, 502);
    return json({ text: translated.text, detectedSourceLanguage: translated.detected_source_language ?? source }, 200);
  } catch (error) {
    return json({ error: error?.name === "AbortError" ? "translation_timeout" : "translation_network_failed" }, 504);
  } finally { clearTimeout(timeout); }
});

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}
