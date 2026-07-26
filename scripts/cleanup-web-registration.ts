// Operator-only Deno runner. Never bundle or expose this script to Web clients.
import { createClient } from "npm:@supabase/supabase-js@2";
import { cleanupRegistration } from "../supabase/functions/quata-web-register/cleanup-contract.mjs";

const requestId = Deno.args[0];
const url = Deno.env.get("SUPABASE_URL");
const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
if (!requestId || !url || !key) throw Error("usage_or_configuration_missing");
const admin = createClient(url, key, { auth: { persistSession: false } });
const { data: row, error } = await admin.from("web_registration_requests")
  .select("id,profile_id,auth_user_id,status").eq("id", requestId)
  .eq("status", "cleanup_required").single();
if (error || !row) throw Error("cleanup_record_not_found");

await cleanupRegistration({
  id: row.id, profileId: row.profile_id, authUserId: row.auth_user_id,
}, {
  revokeWebSessions: async (profileId: string, authId: string) => {
    const result = await admin.from("web_client_sessions").delete()
      .eq("profile_id", profileId).eq("auth_user_id", authId); if (result.error) throw result.error;
  },
  deleteProfile: async (profileId: string, authId: string) => {
    const result = await admin.from("community_profiles").delete()
      .eq("id", profileId).eq("auth_user_id", authId); if (result.error) throw result.error;
  },
  deleteAuthUser: async (authId: string) => {
    const result = await admin.auth.admin.deleteUser(authId); if (result.error) throw result.error;
  },
  markCleaned: async (id: string) => {
    const result = await admin.from("web_registration_requests").delete().eq("id", id);
    if (result.error) throw result.error;
  },
  markCleanupRequired: async (id: string, code: string) => {
    await admin.from("web_registration_requests").update({ last_error_code: code }).eq("id", id);
  },
  alert: async (kind: string) => console.log(JSON.stringify({ event: kind, request_id: requestId })),
});
