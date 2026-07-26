import { readFile } from "node:fs/promises";

const paths = {
  actor: "supabase/templates/community_profile_follows_actor_guard.sql.template",
  actorRollback:
    "supabase/templates/community_profile_follows_actor_guard.rollback.sql.template",
  counters:
    "supabase/templates/community_profile_follow_counter_reconciliation.sql.template",
  countersRollback:
    "supabase/templates/community_profile_follow_counter_reconciliation.rollback.sql.template",
};

const normalize = (value) =>
  value
    .replace(/--.*$/gm, "")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();

const sql = Object.fromEntries(
  await Promise.all(
    Object.entries(paths).map(async ([key, path]) => [
      key,
      normalize(await readFile(path, "utf8")),
    ]),
  ),
);

const requireFragment = (source, fragment, label) => {
  if (!source.includes(fragment)) {
    throw new Error(`follows_integrity_contract_missing:${label}`);
  }
};

for (const [key, source] of Object.entries(sql)) {
  requireFragment(source, "begin;", `${key}_transaction_begin`);
  requireFragment(source, "commit;", `${key}_transaction_commit`);
}

requireFragment(
  sql.actor,
  "follower_profile_id = public.quata_chat_auth_profile_id()",
  "insert_actor_binding",
);
requireFragment(
  sql.actor,
  "for select to anon, authenticated using (true)",
  "public_read",
);
requireFragment(
  sql.actor,
  "security invoker",
  "invoker_guard",
);
requireFragment(
  sql.actor,
  "revoke all privileges on public.community_profile_follows from anon, authenticated",
  "table_grants_reset",
);
requireFragment(
  sql.actor,
  "grant select, insert, delete on public.community_profile_follows to authenticated",
  "android_contract",
);
requireFragment(
  sql.actor,
  "revoke execute on function public.toggle_follow_profile(uuid) from public, anon, authenticated",
  "legacy_toggle_deprecated",
);

requireFragment(
  sql.counters,
  "pg_advisory_xact_lock",
  "counter_lock",
);
requireFragment(
  sql.counters,
  "edge_fingerprint",
  "edge_fingerprint",
);
requireFragment(
  sql.counters,
  "after insert or update or delete on public.community_profile_follows",
  "counter_trigger",
);
requireFragment(
  sql.counters,
  "revoke execute on function public.recalculate_profile_follow_counts(uuid) from public, anon, authenticated",
  "manual_recalculate_restricted",
);
requireFragment(
  sql.countersRollback,
  "rollback refused: follow edges changed after snapshot",
  "rollback_fingerprint_gate",
);

if (
  !sql.counters.includes("__migration_version__") ||
  !sql.countersRollback.includes("__migration_version__")
) {
  throw new Error("follows_integrity_contract_missing:release_placeholder");
}

console.log("COMMUNITY_PROFILE_FOLLOWS_INTEGRITY_CONTRACT_OK");
