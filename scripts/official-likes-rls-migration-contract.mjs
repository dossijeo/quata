#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const migration = resolve("supabase/migrations/20260726171002_official_post_likes_actor_guard.sql");
const sql = (await readFile(migration, "utf8"))
  .replace(/--.*$/gm, "")
  .replace(/\s+/g, " ")
  .trim()
  .toLowerCase();

function requireContract(fragment, diagnostic) {
  if (!sql.includes(fragment)) throw new Error(`official_likes_rls_contract_missing:${diagnostic}`);
}

requireContract(
  "alter function public.quata_guard_official_post_likes() security invoker",
  "trigger_security_invoker",
);
requireContract(
  "revoke all on function public.quata_guard_official_post_likes() from public, anon, authenticated, service_role",
  "trigger_public_execute_revoked",
);
requireContract(
  "grant execute on function public.quata_guard_official_post_likes() to postgres",
  "trigger_owner_only_execute",
);
requireContract(
  "alter table public.official_post_likes enable row level security",
  "rls_enabled",
);
requireContract(
  "for select to anon, authenticated using (true)",
  "public_read_preserved",
);
requireContract(
  "for insert to authenticated with check ( auth.uid() is not null and profile_id = public.quata_current_profile_id() )",
  "insert_bound_to_actor",
);
requireContract(
  "for delete to authenticated using ( public.quata_official_like_delete_allowed(profile_id) )",
  "delete_bound_to_fail_loud_actor_guard",
);
requireContract(
  "if p_profile_id = v_actor or public.quata_current_profile_is_admin() then return true",
  "delete_owner_or_admin",
);
requireContract(
  "grant select, insert, delete on public.official_post_likes to authenticated",
  "android_authenticated_contract",
);

if (sql.includes("alter table public.official_post_likes force row level security")) {
  throw new Error("official_likes_rls_contract_invalid:force_rls_would_change_service_maintenance");
}

console.log("Official likes RLS migration contract passed.");
