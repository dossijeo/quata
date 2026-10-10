#!/usr/bin/env node
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import pg from "pg";

const output = parseArgs(process.argv.slice(2));
const dbUrlFile = requiredFile("SUPABASE_DB_URL_FILE");
const tlsCaFile = requiredFile("SUPABASE_DB_TLS_CA_FILE");
const connectionUrl = new URL((await readFile(dbUrlFile, "utf8")).trim());
connectionUrl.searchParams.delete("sslmode");
const client = new pg.Client({
  connectionString: connectionUrl.toString(),
  application_name: "quata-account-details-collision-postdeploy",
  ssl: {
    ca: await readFile(tlsCaFile, "utf8"),
    rejectUnauthorized: true,
    servername: connectionUrl.hostname,
  },
});

try {
  await client.connect();
  await client.query("begin");
  await client.query("set local statement_timeout = '30s'");
  const probes = [];
  probes.push(await rejectDuplicate("phone_local", [
    "community_profiles_phone_local_key",
    "community_profiles_phone_local_uidx",
    "phone_unique",
    "unique_phone_normalized",
  ]));
  probes.push(await rejectDuplicate("phone_normalized", ["unique_phone_normalized"]));
  await client.query("rollback");

  const postflight = (await client.query(`
    select
      (select count(*) = 1 from supabase_migrations.schema_migrations
        where version = '20261010090000') as ledger_exact,
      not exists (
        select 1 from public.community_profiles where phone_local is not null
        group by phone_local having count(*) > 1
      ) as local_values_unique,
      not exists (
        select 1 from public.community_profiles where phone_normalized is not null
        group by phone_normalized having count(*) > 1
      ) as normalized_values_unique
  `)).rows[0] ?? {};
  if (Object.values(postflight).some((value) => value !== true)) {
    throw new Error("profile_phone_postdeploy_postcondition_failed");
  }

  const report = {
    version: 1,
    mode: "production-transactional-rollback",
    status: "passed",
    probes,
    postflight,
    residue: "zero",
    privacy: "No profile identifiers, phone values, emails or credentials are emitted.",
  };
  const serialized = `${JSON.stringify(report, null, 2)}\n`;
  if (output) await writeFile(output, serialized, { flag: "wx" });
  process.stdout.write(serialized);
} catch (error) {
  await client.query("rollback").catch(() => {});
  console.error(redact(error?.message ?? String(error)));
  process.exitCode = 1;
} finally {
  await client.end().catch(() => {});
}

async function rejectDuplicate(column, allowedConstraints) {
  const rows = (await client.query(`
    select id, ${column} as value
    from public.community_profiles
    where ${column} is not null
    order by id
    limit 2
  `)).rows;
  if (rows.length !== 2 || rows[0].value === rows[1].value) {
    throw new Error(`profile_phone_${column}_probe_fixture_unavailable`);
  }
  await client.query("savepoint collision_probe");
  try {
    await client.query(
      `update public.community_profiles set ${column} = $1 where id = $2`,
      [rows[0].value, rows[1].id],
    );
    throw new Error(`profile_phone_${column}_collision_not_rejected`);
  } catch (error) {
    if (error?.code !== "23505" || !allowedConstraints.includes(error?.constraint)) throw error;
    await client.query("rollback to savepoint collision_probe");
    const restored = (await client.query(
      `select ${column} = $1 as exact from public.community_profiles where id = $2`,
      [rows[1].value, rows[1].id],
    )).rows[0]?.exact;
    if (restored !== true) throw new Error(`profile_phone_${column}_rollback_failed`);
    return { column, sqlstate: "23505", constraint: error.constraint, rollback: "passed" };
  } finally {
    await client.query("release savepoint collision_probe").catch(() => {});
  }
}

function parseArgs(args) {
  if (args.length === 0) return null;
  if (args.length === 2 && args[0] === "--out" && args[1]?.trim()) return resolve(args[1]);
  throw new Error("invalid_arguments");
}

function requiredFile(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name.toLowerCase()}_required`);
  return value;
}

function redact(value) {
  return String(value)
    .replace(/postgres(?:ql)?:\/\/[^\s]+/gi, "[REDACTED_DB_URL]")
    .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/gi, "[uuid]")
    .slice(0, 300);
}
