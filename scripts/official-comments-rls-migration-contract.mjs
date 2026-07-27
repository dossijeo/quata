#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const forward = await readFile(resolve("supabase/migrations/20260727120001_official_post_comments_actor_guard.sql"), "utf8");
const rollback = await readFile(resolve("supabase/rollbacks/20260727120001_official_post_comments_actor_guard.rollback.sql"), "utf8");
const sql = forward.replace(/--.*$/gm, "").replace(/\s+/g, " ").trim().toLowerCase();
const need = (fragment, name) => {
  if (!sql.includes(fragment)) throw new Error(`official_comments_rls_contract_missing:${name}`);
};

need("alter function public.quata_guard_official_post_comments() security invoker", "trigger_security_invoker");
need("alter table public.official_post_comments enable row level security", "rls_enabled");
need("for select to anon, authenticated using (true)", "legacy_public_read");
need("for insert to authenticated with check (auth.uid() is not null and profile_id = public.quata_current_profile_id())", "actor_insert");
need("for update to authenticated using (public.quata_official_comment_mutation_allowed(profile_id)) with check (public.quata_official_comment_mutation_allowed(profile_id))", "actor_or_admin_update");
need("for delete to authenticated using (public.quata_official_comment_mutation_allowed(profile_id))", "actor_or_admin_delete");
need("new.profile_id is distinct from old.profile_id", "profile_immutable");
need("grant select, insert, update, delete on public.official_post_comments to authenticated", "legacy_android_web_mutations");
if (sql.includes("force row level security")) throw new Error("official_comments_rls_contract_invalid:force_rls");

for (const fragment of [
  "official_post_comments_rollback_refused:catalog_drift",
  "aec234b12010b22a2313a924b9528d8e",
  "alter table public.official_post_comments disable row level security",
  "drop function if exists public.quata_official_comment_mutation_allowed(uuid)",
  "security definer",
  "grant select, insert, update, delete on public.official_post_comments to authenticated",
]) {
  if (!rollback.toLowerCase().includes(fragment)) throw new Error(`official_comments_rollback_contract_missing:${fragment}`);
}
console.log("Official comments RLS migration contract passed.");
