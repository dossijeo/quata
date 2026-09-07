#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import pg from "pg";

const defaultCredentialsFile = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const fixtureNames = Object.freeze({ a: "Gabrielo", b: "Gabrielu" });

const credentialsFile = process.env.QUATA_ACCOUNT_DETAILS_CREDENTIALS_FILE?.trim() || defaultCredentialsFile;
const dbUrlFile = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
const caFile = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultCaFile;

const credentials = JSON.parse(await readFile(credentialsFile, "utf8"));
const connectionUrl = new URL((await readFile(dbUrlFile, "utf8")).trim());
connectionUrl.searchParams.delete("sslmode");
const ca = await readFile(caFile, "utf8");
const client = new pg.Client({
  connectionString: connectionUrl.toString(),
  ssl: { ca, rejectUnauthorized: true, servername: connectionUrl.hostname },
});

try {
  await client.connect();
  await client.query("begin");
  const restored = [];
  for (const key of ["a", "b"]) {
    const profile = credentials[key];
    if (!profile?.country_code || !profile?.phone) throw new Error(`fixture_credentials_missing:${key}`);
    const countryCode = String(profile.country_code);
    const phoneLocal = localPhone(countryCode, profile.phone);
    const phoneE164 = `+${normalizedDigits(countryCode)}${phoneLocal}`;
    const phoneFullDigits = normalizedDigits(phoneE164);
    const lookup = await client.query(
      `select p.id
         from public.community_profiles p
         left join auth.users u on u.id = p.auth_user_id
        where regexp_replace(coalesce(u.phone, ''), '\\D', '', 'g') = $1
           or regexp_replace(coalesce(p.phone, ''), '\\D', '', 'g') = $1
           or regexp_replace(coalesce(p.phone_local, ''), '\\D', '', 'g') = $2
           or regexp_replace(coalesce(p.telefono, ''), '\\D', '', 'g') = $2
           or regexp_replace(coalesce(p.phone_local, ''), '\\D', '', 'g') like $3`,
      [phoneFullDigits, phoneLocal, `${phoneLocal}%`],
    );
    if (lookup.rowCount !== 1) throw new Error(`fixture_profile_lookup_not_unique:${key}:${lookup.rowCount}`);
    const update = await client.query(
      `update public.community_profiles
          set display_name = $1,
              nombre = $1,
              country_code = $2,
              code = $2,
              phone_local = $3,
              phone = $4,
              telefono = $3,
              neighborhood = case when coalesce(neighborhood, '') like $5 then null else neighborhood end,
              barrio = case when coalesce(barrio, '') like $5 then null else barrio end
        where id = $6
        returning id`,
      [fixtureNames[key], countryCode, phoneLocal, phoneE164, "Bata QA %", lookup.rows[0].id],
    );
    if (update.rowCount !== 1) throw new Error(`fixture_profile_restore_failed:${key}`);
    restored.push(key);
  }
  await client.query("commit");
  console.log(JSON.stringify({ status: "restored", profiles: restored.length }));
} catch (error) {
  await client.query("rollback").catch(() => {});
  console.error(redacted(error?.message ?? String(error)));
  process.exitCode = 1;
} finally {
  await client.end().catch(() => {});
}

function localPhone(countryCode, phone) {
  const country = normalizedDigits(countryCode);
  const digits = normalizedDigits(phone);
  return country && digits.startsWith(country) ? digits.slice(country.length) : digits;
}

function normalizedDigits(value) {
  return String(value ?? "").replace(/\D/g, "");
}

function redacted(value) {
  return String(value)
    .replace(/\b\d{6,}\b/g, "[digits]")
    .replace(/(postgresql?:\/\/)[^@\s]+@/gi, "$1[REDACTED]@");
}
