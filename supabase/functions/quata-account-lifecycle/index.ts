import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type LifecycleRequest = { action?: "deactivate" | "delete"; password?: string };
type StorageObject = { bucket?: string | null; path?: string | null };
type DeletionAssets = {
  urls?: string[] | null;
  storage_objects?: StorageObject[] | null;
};

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || envDictionaryValue("SUPABASE_SECRET_KEYS");
    if (!supabaseUrl || !serviceRoleKey) return json({ error: "server_not_configured" }, 500);

    const token = bearerToken(request.headers.get("authorization"));
    if (!token) return json({ error: "authentication_required" }, 401);

    const admin = createClient(supabaseUrl, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
      global: { headers: { "X-Client-Info": "quata-account-lifecycle" } },
    });
    const { data: userData, error: userError } = await admin.auth.getUser(token);
    if (userError || !userData.user) return json({ error: "invalid_session" }, 401);

    const payload = await readPayload(request);
    if (payload.action !== "deactivate" && payload.action !== "delete") {
      return json({ error: "invalid_action" }, 400);
    }

    const authUserId = userData.user.id;
    const { data: profile, error: profileError } = await admin
      .from("community_profiles")
      .select("id,account_status")
      .eq("auth_user_id", authUserId)
      .eq("account_status", "active")
      .maybeSingle();
    if (profileError) throw profileError;
    if (!profile?.id) {
      if (payload.action === "delete") {
        const { data: pending, error: pendingError } = await admin
          .from("account_deletion_requests")
          .select("auth_user_id,database_deleted_at")
          .eq("auth_user_id", authUserId)
          .maybeSingle();
        if (pendingError) throw pendingError;
        if (pending?.database_deleted_at) {
          const { error: authDeletionError } = await admin.auth.admin.deleteUser(authUserId, false);
          if (authDeletionError) throw authDeletionError;
          return json({ ok: true, action: "delete" });
        }
      }
      return json({ error: "active_profile_not_found" }, 404);
    }

    const password = payload.password?.trim() || "";
    if (!password) return json({ error: "password_required" }, 400);
    const { data: credentialProfile, error: credentialError } = await admin
      .from("community_profiles")
      .select("pass_hash,pass_plain")
      .eq("id", profile.id)
      .single();
    if (credentialError) throw credentialError;
    if (!await validatesPassword(credentialProfile, password)) {
      return json({ error: "invalid_password" }, 403);
    }

    if (payload.action === "deactivate") {
      const { error } = await admin.rpc("quata_account_deactivate", {
        p_profile_id: profile.id,
        p_auth_user_id: authUserId,
      });
      if (error) throw error;

      // Unlinking the profile already blocks every protected database action.
      // Banning and global sign-out additionally invalidate future Auth sessions.
      const { error: banError } = await admin.auth.admin.updateUserById(authUserId, {
        ban_duration: "876000h",
      });
      if (banError) console.error("Could not ban deactivated auth user", banError);
      await admin.auth.admin.signOut(token, "global").catch((error) => {
        console.error("Could not globally revoke deactivated session", error);
      });
      return json({ ok: true, action: "deactivate" });
    }

    const { error: requestError } = await admin.from("account_deletion_requests").upsert({
      auth_user_id: authUserId,
      profile_id: profile.id,
      started_at: new Date().toISOString(),
      assets_removed_at: null,
      database_deleted_at: null,
    });
    if (requestError) throw requestError;

    const { data: rawAssets, error: assetsError } = await admin.rpc("quata_account_collect_deletion_assets", {
      p_profile_id: profile.id,
      p_auth_user_id: authUserId,
    });
    if (assetsError) throw assetsError;
    const assets = (rawAssets || {}) as DeletionAssets;

    await removeAccountStorage(admin, profile.id, assets);
    await removeWordPressVideos(assets.urls || []);
    const { error: assetsRemovedError } = await admin.from("account_deletion_requests").update({
      assets_removed_at: new Date().toISOString(),
    }).eq("auth_user_id", authUserId);
    if (assetsRemovedError) throw assetsRemovedError;

    const { error: deletionError } = await admin.rpc("quata_account_delete_data", {
      p_profile_id: profile.id,
      p_auth_user_id: authUserId,
    });
    if (deletionError) throw deletionError;
    const { error: databaseDeletedError } = await admin.from("account_deletion_requests").update({
      database_deleted_at: new Date().toISOString(),
    }).eq("auth_user_id", authUserId);
    if (databaseDeletedError) throw databaseDeletedError;

    const { error: authDeletionError } = await admin.auth.admin.deleteUser(authUserId, false);
    if (authDeletionError) throw authDeletionError;
    return json({ ok: true, action: "delete" });
  } catch (error) {
    console.error(error);
    return json({ error: "account_operation_failed" }, 500);
  }
});

async function readPayload(request: Request): Promise<LifecycleRequest> {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

function bearerToken(header: string | null): string | null {
  const match = header?.match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

async function validatesPassword(
  profile: { pass_hash?: string | null; pass_plain?: string | null },
  password: string,
): Promise<boolean> {
  const bytes = new TextEncoder().encode(password);
  const hash = await crypto.subtle.digest("SHA-256", bytes);
  const sha256 = Array.from(new Uint8Array(hash))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
  return profile.pass_hash?.trim().toLowerCase() === sha256 || profile.pass_plain === password;
}

async function removeAccountStorage(
  admin: ReturnType<typeof createClient>,
  profileId: string,
  assets: DeletionAssets,
) {
  const pathsByBucket = new Map<string, Set<string>>();
  const add = (bucket?: string | null, path?: string | null) => {
    const normalizedBucket = bucket?.trim();
    const normalizedPath = path?.replace(/^\/+/, "").trim();
    if (!normalizedBucket || !normalizedPath) return;
    const paths = pathsByBucket.get(normalizedBucket) || new Set<string>();
    paths.add(normalizedPath);
    pathsByBucket.set(normalizedBucket, paths);
  };

  for (const item of assets.storage_objects || []) add(item.bucket, item.path);
  for (const url of assets.urls || []) {
    const storage = parseStoragePublicUrl(url);
    if (storage) add(storage.bucket, storage.path);
  }

  for (const prefix of [profileId, `avatars/${profileId}`]) {
    for (const path of await listFilesRecursively(admin, "community-posts", prefix)) add("community-posts", path);
  }
  for (const path of await listFilesRecursively(admin, "chat-attachments", profileId)) add("chat-attachments", path);

  for (const [bucket, paths] of pathsByBucket) {
    const allPaths = [...paths];
    for (let index = 0; index < allPaths.length; index += 100) {
      const { error } = await admin.storage.from(bucket).remove(allPaths.slice(index, index + 100));
      if (error) throw error;
    }
  }
}

async function listFilesRecursively(
  admin: ReturnType<typeof createClient>,
  bucket: string,
  prefix: string,
): Promise<string[]> {
  const files: string[] = [];
  const pending = [prefix.replace(/^\/+|\/+$/g, "")];
  while (pending.length > 0) {
    const folder = pending.pop()!;
    for (let offset = 0; ; offset += 1000) {
      const { data, error } = await admin.storage.from(bucket).list(folder, {
        limit: 1000,
        offset,
        sortBy: { column: "name", order: "asc" },
      });
      if (error) throw error;
      const entries = data || [];
      for (const entry of entries) {
        const path = folder ? `${folder}/${entry.name}` : entry.name;
        if (entry.id || entry.metadata) files.push(path);
        else pending.push(path);
      }
      if (entries.length < 1000) break;
    }
  }
  return files;
}

function parseStoragePublicUrl(raw: string): { bucket: string; path: string } | null {
  try {
    const url = new URL(raw);
    const match = url.pathname.match(/\/storage\/v1\/object\/(?:public|sign)\/([^/]+)\/(.+)$/);
    if (!match) return null;
    return { bucket: decodeURIComponent(match[1]), path: decodeURIComponent(match[2]) };
  } catch {
    return null;
  }
}

async function removeWordPressVideos(urls: string[]) {
  const candidates = [...new Set(urls)].filter((raw) => {
    try {
      const url = new URL(raw);
      return /(^|\.)egquata\.com$/i.test(url.hostname) && /\.(mp4|mov|m4v|webm)(?:$|\?)/i.test(raw);
    } catch {
      return false;
    }
  });
  for (const mediaUrl of candidates) {
    const form = new URLSearchParams({ action: "quqos_delete_post_video", url: mediaUrl });
    const response = await fetch("https://egquata.com/wp-admin/admin-ajax.php", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: form,
    });
    if (!response.ok) throw new Error(`WordPress media deletion failed (${response.status})`);
    const body = await response.json().catch(() => null) as { success?: boolean } | null;
    if (body?.success === false) throw new Error("WordPress rejected media deletion");
  }
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

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
