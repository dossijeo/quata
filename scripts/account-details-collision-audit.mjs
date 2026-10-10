#!/usr/bin/env node
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import pg from "pg";

const output = parseArgs(process.argv.slice(2));
const dbUrlFile = requiredFile("SUPABASE_DB_URL_FILE");
const tlsCaFile = requiredFile("SUPABASE_DB_TLS_CA_FILE");
const connectionUrl = new URL((await readFile(dbUrlFile, "utf8")).trim());
connectionUrl.searchParams.delete("sslmode");
const ca = await readFile(tlsCaFile, "utf8");
const client = new pg.Client({
  connectionString: connectionUrl.toString(),
  application_name: "quata-account-details-collision-audit",
  ssl: { ca, rejectUnauthorized: true, servername: connectionUrl.hostname },
});

try {
  await client.connect();
  await client.query("begin read only");
  await client.query("set local statement_timeout = '30s'");

  const summary = await client.query(`
    with normalized as (
      select
        nullif(regexp_replace(coalesce(country_code, code, ''), '\\D', '', 'g'), '') as country_digits,
        nullif(regexp_replace(coalesce(phone_local, phone_normalized, telefono, ''), '\\D', '', 'g'), '') as local_digits,
        nullif(regexp_replace(coalesce(phone_e164, ''), '\\D', '', 'g'), '') as e164_digits,
        nullif(regexp_replace(coalesce(phone_normalized, ''), '\\D', '', 'g'), '') as normalized_digits
      from public.community_profiles
    )
    select
      count(*)::int as total_profiles,
      count(*) filter (where country_digits is null)::int as missing_country,
      count(*) filter (where local_digits is null)::int as missing_local,
      count(*) filter (where e164_digits is null)::int as missing_e164,
      count(*) filter (
        where country_digits is not null
          and local_digits is not null
          and e164_digits is not null
          and e164_digits <> country_digits || local_digits
      )::int as inconsistent_e164,
      count(*) filter (
        where normalized_digits is not null
          and local_digits is not null
          and normalized_digits <> local_digits
      )::int as inconsistent_normalized
    from normalized
  `);

  const duplicates = await client.query(`
    with normalized as (
      select
        nullif(regexp_replace(coalesce(country_code, code, ''), '\\D', '', 'g'), '') as country_digits,
        nullif(regexp_replace(coalesce(phone_local, phone_normalized, telefono, ''), '\\D', '', 'g'), '') as local_digits
      from public.community_profiles
    ), duplicate_groups as (
      select count(*)::int as group_size
      from normalized
      where country_digits is not null and local_digits is not null
      group by country_digits, local_digits
      having count(*) > 1
    )
    select
      count(*)::int as duplicate_groups,
      coalesce(sum(group_size), 0)::int as affected_profiles,
      coalesce(max(group_size), 0)::int as largest_group
    from duplicate_groups
  `);

  const e164Duplicates = await client.query(`
    with duplicate_groups as (
      select count(*)::int as group_size
      from public.community_profiles
      where phone_e164 is not null
      group by phone_e164
      having count(*) > 1
    )
    select
      count(*)::int as duplicate_groups,
      coalesce(sum(group_size), 0)::int as affected_profiles,
      coalesce(max(group_size), 0)::int as largest_group
    from duplicate_groups
  `);

  const indexes = await client.query(`
    select
      i.relname as name,
      x.indisunique as is_unique,
      x.indisvalid as is_valid,
      pg_get_indexdef(i.oid) as definition
    from pg_class t
    join pg_namespace n on n.oid = t.relnamespace
    join pg_index x on x.indrelid = t.oid
    join pg_class i on i.oid = x.indexrelid
    where n.nspname = 'public'
      and t.relname = 'community_profiles'
      and (
        pg_get_indexdef(i.oid) ilike '%country_code%'
        or pg_get_indexdef(i.oid) ilike '%phone_local%'
        or pg_get_indexdef(i.oid) ilike '%phone_normalized%'
        or pg_get_indexdef(i.oid) ilike '%phone_e164%'
        or pg_get_indexdef(i.oid) ilike '%telefono%'
      )
    order by i.relname
  `);

  const report = {
    version: 2,
    mode: "production-read-only-aggregate",
    status: "passed",
    summary: integerRow(summary.rows[0]),
    countryLocalCollisions: integerRow(duplicates.rows[0]),
    e164Collisions: integerRow(e164Duplicates.rows[0]),
    indexes: indexes.rows.map(({ name, is_unique, is_valid, definition }) => ({
      name,
      unique: is_unique,
      valid: is_valid,
      definition,
    })),
    privacy: "No profile identifiers, phone values, emails or credentials are emitted.",
  };
  await client.query("rollback");
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

function integerRow(row) {
  return Object.fromEntries(Object.entries(row ?? {}).map(([key, value]) => [key, Number(value)]));
}

function redact(value) {
  return String(value)
    .replace(/postgres(?:ql)?:\/\/[^\s]+/gi, "[REDACTED_DB_URL]")
    .replace(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/gi, "[uuid]")
    .slice(0, 300);
}
