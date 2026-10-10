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

  const fixture = (await client.query(`
    select id, country_code, phone_local
    from public.community_profiles
    where country_code is not null and phone_local is not null
    order by id
    limit 2
  `)).rows;
  if (fixture.length !== 2) throw new Error("profile_phone_probe_fixture_unavailable");

  const alternateCountry = (await client.query(`
    select candidate
    from (values ('999'), ('998'), ('997')) choices(candidate)
    where candidate <> $1
      and not exists (
        select 1 from public.community_profiles
        where country_code = candidate and phone_local = $2
      )
    limit 1
  `, [fixture[0].country_code, fixture[0].phone_local])).rows[0]?.candidate;
  if (!alternateCountry) throw new Error("profile_phone_alternate_country_fixture_unavailable");

  const differentCountry = await acceptSameLocalInDifferentCountry(
    fixture[1].id,
    alternateCountry,
    fixture[0].phone_local,
  );
  const exactIdentity = await rejectExactIdentityDuplicate(
    fixture[1].id,
    fixture[0].country_code,
    fixture[0].phone_local,
  );
  await client.query("rollback");

  const postflight = (await client.query(`
    select
      (select count(*) = 1 from supabase_migrations.schema_migrations
        where version = '20261010101500') as ledger_exact,
      to_regclass('public.community_profiles_country_phone_local_uidx') is not null
        as country_local_index_present,
      to_regclass('public.community_profiles_phone_e164_uidx') is not null
        as e164_index_present,
      to_regclass('public.community_profiles_phone_local_key') is null
        and to_regclass('public.community_profiles_phone_local_uidx') is null
        and to_regclass('public.phone_unique') is null
        and to_regclass('public.unique_phone_normalized') is null
        as obsolete_global_local_indexes_absent,
      not exists (
        select 1 from public.community_profiles
        where country_code is not null and phone_local is not null
        group by country_code, phone_local having count(*) > 1
      ) as country_local_values_unique,
      not exists (
        select 1 from public.community_profiles where phone_e164 is not null
        group by phone_e164 having count(*) > 1
      ) as e164_values_unique
  `)).rows[0] ?? {};
  if (Object.values(postflight).some((value) => value !== true)) {
    throw new Error("profile_phone_postdeploy_postcondition_failed");
  }

  const report = {
    version: 2,
    mode: "production-transactional-rollback",
    status: "passed",
    probes: [differentCountry, exactIdentity],
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

async function acceptSameLocalInDifferentCountry(id, country, local) {
  await client.query("savepoint different_country_probe");
  try {
    const stored = (await client.query(`
      update public.community_profiles
      set country_code = $1,
          code = $1,
          phone_local = $2,
          phone_normalized = $2,
          telefono = $2,
          phone_e164 = '+' || $1 || $2,
          phone = '+' || $1 || $2
      where id = $3
      returning country_code = $1
        and phone_local = $2
        and phone_e164 = '+' || $1 || $2 as exact
    `, [country, local, id])).rows[0]?.exact;
    if (stored !== true) throw new Error("profile_phone_different_country_update_mismatch");
    return { scenario: "same-local-different-country", accepted: true, rollback: "passed" };
  } finally {
    await client.query("rollback to savepoint different_country_probe");
    await client.query("release savepoint different_country_probe");
  }
}

async function rejectExactIdentityDuplicate(id, country, local) {
  await client.query("savepoint exact_identity_probe");
  try {
    await client.query(`
      update public.community_profiles
      set country_code = $1,
          code = $1,
          phone_local = $2,
          phone_normalized = $2,
          telefono = $2,
          phone_e164 = '+' || $1 || $2,
          phone = '+' || $1 || $2
      where id = $3
    `, [country, local, id]);
    throw new Error("profile_phone_exact_identity_collision_not_rejected");
  } catch (error) {
    const allowed = [
      "community_profiles_country_phone_local_uidx",
      "community_profiles_phone_e164_uidx",
    ];
    if (error?.code !== "23505" || !allowed.includes(error?.constraint)) throw error;
    return {
      scenario: "same-country-and-local",
      sqlstate: "23505",
      constraint: error.constraint,
      rollback: "passed",
    };
  } finally {
    await client.query("rollback to savepoint exact_identity_probe");
    await client.query("release savepoint exact_identity_probe");
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
