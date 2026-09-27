import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { openRecoveryJournal } from "./e2e-fixtures/recovery-private-journal.mjs";
import { removeAccountLifecycleSeededStorage, retireAccountLifecycleFixture,
  verifyAccountDeleted } from "./e2e-fixtures/account-lifecycle.mjs";

const require = createRequire(import.meta.url);
let client;
let phase = "input";
let input;
try {
  let size = 0;
  const chunks = [];
  for await (const chunk of process.stdin) {
    size += chunk.length;
    if (size > 64 * 1024) throw new Error("input_too_large");
    chunks.push(chunk);
  }
  input = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  for (const chunk of chunks) chunk.fill(0);
  const { Client } = require(input.pgModule);
  phase = "connect";
  const databaseUrl = new URL((await readFile(input.databaseUrlFile, "utf8")).trim());
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) databaseUrl.searchParams.delete(key);
  client = new Client({ connectionString: databaseUrl.toString(),
    ssl: { ca: await readFile(input.databaseCaFile, "utf8"), rejectUnauthorized: true, servername: databaseUrl.hostname },
    connectionTimeoutMillis: 10_000, statement_timeout: 15_000 });
  await client.connect();
  phase = "journal";
  const journal = await openRecoveryJournal({ file: input.journalFile, identity: input.identity });
  const record = await journal.read();
  phase = "retire";
  if (record.state?.effectsAction === "delete") {
    await removeAccountLifecycleSeededStorage({ record, backendUrl: input.backendUrl, serviceKey: input.serviceKey });
  }
  await retireAccountLifecycleFixture({ client, journal, record, operationsSettled: async () => true });
  phase = "remove_journal";
  await journal.removeAfterVerification(async () => {
    await verifyAccountDeleted({ client, record });
    return { password: true, secret: true, sessions: true };
  });
  process.stdout.write(`${JSON.stringify({ recovered: true, residueCounts: 0 })}\n`);
} catch {
  let diagnostic;
  if (phase === "retire" && client) {
    try {
      const journal = await openRecoveryJournal({ file: input.journalFile, identity: input.identity });
      const record = await journal.read();
      const result = await client.query(`select
        exists(select 1 from auth.users where id=$1::uuid and email=$3
          and raw_app_meta_data->'quata_e2e'->>'unit'='ACCOUNT-LIFECYCLE'
          and raw_app_meta_data->'quata_e2e'->>'run_id'=$4) as auth_owned,
        exists(select 1 from public.community_profiles where id=$2::uuid and
          ((auth_user_id=$1::uuid and account_status='active') or
           (auth_user_id is null and deactivated_auth_user_id=$1::uuid and account_status='deactivated'))) as profile_owned,
        (select count(*)::int from public.community_posts where profile_id=$2::uuid or author_id=$2::uuid) as posts,
        (select count(*)::int from public.chat_participants where profile_id=$2::uuid) as participants,
        (select count(*)::int from public.chat_messages where sender_profile_id=$2::uuid) as messages,
        (select count(*)::int from storage.objects where owner=$1::uuid or owner_id=$1::uuid::text) as storage`,
      [record.authUserId, record.profileId, record.email, record.runId]);
      diagnostic = result.rows?.[0];
    } catch (error) { diagnostic = { unavailable: true, code: String(error?.code ?? "unknown").slice(0, 12) }; }
  }
  process.stdout.write(`${JSON.stringify({ recovered: false, phase, diagnostic })}\n`);
  process.exitCode = 1;
} finally {
  await client?.end().catch(() => {});
}
