import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { test } from "node:test";

// An isolated PostgreSQL/Wasm engine, never a Supabase connection or production fixture.
if (!process.env.QUATA_PGLITE_MODULE_FILE) throw new Error("QUATA_PGLITE_MODULE_FILE is required");
const { PGlite } = await import(pathToFileURL(process.env.QUATA_PGLITE_MODULE_FILE).href);
const read = (name) => readFileSync(new URL(`../supabase/migrations/${name}`, import.meta.url), "utf8");
const readRollback = (name) => readFileSync(new URL(`../supabase/rollbacks/${name}`, import.meta.url), "utf8");

test("APNs SQL enforces actor/environment, keeps Android RPC intact and claims delivery once", async () => {
  const db = new PGlite();
  try {
    await db.exec(`
      create role anon; create role authenticated; create role service_role;
      create schema auth;
      create function auth.uid() returns uuid language sql as
        $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
      create table auth.users(id uuid primary key);
      create table public.community_profiles(id uuid primary key, auth_user_id uuid references auth.users);
      create table public.chat_messages(id bigint primary key);
    `);
    const initial = read("20260629_0008_chat_push_notifications.sql");
    for (const table of ["push_tokens", "push_delivery_log"]) {
      await db.exec(initial.match(new RegExp(`create table if not exists public\\.${table} \\([\\s\\S]*?\\n\\);`))[0]);
    }
    await db.exec("alter table push_tokens add disabled_at timestamptz, add last_error_text text");
    const legacy = read("20260723_0001_multidevice_fcm_and_web_push.sql").split("-- Restore only tokens")[0];
    await db.exec(legacy);
    const definition = async () => (await db.query("select pg_get_functiondef('quata_register_push_token(uuid,text,text)'::regprocedure) as body")).rows[0].body;
    const before = await definition();
    const migration = read("20260914135400_apns_registration_environment.sql");
    await db.exec(migration);
    await db.exec(migration);
    assert.equal(await definition(), before);
    const actorA = "10000000-0000-4000-8000-000000000001";
    const actorB = "10000000-0000-4000-8000-000000000002";
    await db.exec(`insert into auth.users values ('${actorA}'),('${actorB}');
      insert into community_profiles values ('${actorA}','${actorA}'),('${actorB}','${actorB}');
      insert into chat_messages values (1);
      set role authenticated;
    `);
    const token = "ab".repeat(32);
    const register = (actor, environment, value = token) => db.query("select quata_register_apns_token($1,$2,$3) as result", [actor, value, environment]);
    await assert.rejects(register(actorA, "sandbox"), { code: "42501" });
    await db.exec(`set request.jwt.claim.sub = '${actorA}'`);
    await assert.rejects(register(actorB, "sandbox"), { code: "42501" });
    await assert.rejects(register(actorA, "auto"), { code: "22023" });
    await assert.rejects(register(actorA, null), { code: "22023" });
    await assert.rejects(register(actorA, "sandbox", "abc"), { code: "22023" });
    const id = (await register(actorA, "sandbox")).rows[0].result.id;
    await db.query("select quata_register_push_token($1,'legacy-fcm','android')", [actorA]);
    await assert.rejects(db.query("select quata_reserve_apns_delivery(1,$1,$2,'sandbox',now())", [actorA, id]), { code: "42501" });
    await db.exec("reset role");
    const snapshot = (await db.query("select * from push_tokens where id=$1", [id])).rows[0];
    assert.equal(snapshot.platform, "ios");
    assert.equal(snapshot.apns_environment, "sandbox");
    assert.equal((await db.query("select apns_environment from push_tokens where token='legacy-fcm'")).rows[0].apns_environment, null);
    const claim = async (actor = actorA, environment = "sandbox", updated = snapshot.updated_at) =>
      (await db.query("select quata_reserve_apns_delivery(1,$1,$2,$3,$4) as claimed", [actor, id, environment, updated])).rows[0].claimed;
    assert.equal(await claim(actorB), false);
    assert.equal(await claim(actorA, "production"), false);
    assert.equal(await claim(), true);
    assert.equal(await claim(), false);
    await db.exec("update push_delivery_log set status='error'");
    assert.equal(await claim(), true);
    await db.exec("update push_delivery_log set status='reserved',created_at=now()-interval '61 seconds'");
    assert.equal(await claim(), true);
    await db.exec("update push_delivery_log set status='sent'");
    assert.equal(await claim(), false);
    await db.exec("update push_delivery_log set status='error'; update push_tokens set disabled_at=now() where platform='ios'");
    assert.equal(await claim(), false);
    await db.exec(`set role authenticated; set request.jwt.claim.sub='${actorB}'`);
    assert.equal((await register(actorB, "production")).rows[0].result.id, id);
    await db.exec("reset role");
    assert.equal(await claim(), false);
    const current = (await db.query("select * from push_tokens where id=$1", [id])).rows[0];
    assert.equal(current.user_id, actorB);
    assert.equal(current.disabled_at, null);
    assert.equal(await claim(actorB, "production", current.updated_at), true);

    const rollback = readRollback("20260914135400_apns_registration_environment.rollback.sql");
    await assert.rejects(db.exec(rollback), /APNs rollback refused: iOS registrations exist/);
    await db.exec("rollback");
    assert.equal((await db.query(`select to_regprocedure('public.quata_register_apns_token(uuid,text,text)') is not null as present`)).rows[0].present, true);

    await db.exec("delete from push_tokens where platform='ios'");
    await db.exec(`
      insert into push_delivery_log(message_id, profile_id, push_token_id, status)
      select 1, '${actorA}', id, 'sent' from push_tokens where token='legacy-fcm'
    `);
    await db.exec(rollback);
    assert.equal(await definition(), before);
    assert.equal((await db.query(`select to_regprocedure('public.quata_register_apns_token(uuid,text,text)') is null as missing`)).rows[0].missing, true);
    assert.equal((await db.query(`select to_regprocedure('public.quata_reserve_apns_delivery(bigint,uuid,uuid,text,timestamp with time zone)') is null as missing`)).rows[0].missing, true);
    assert.equal((await db.query(`select count(*)::int as count from information_schema.columns where table_schema='public' and table_name='push_tokens' and column_name='apns_environment'`)).rows[0].count, 0);
    assert.equal((await db.query("select token from push_tokens where token='legacy-fcm'")).rows[0].token, "legacy-fcm");
    assert.equal((await db.query("select count(*)::int as count from push_delivery_log where status='sent'")).rows[0].count, 1);

    await db.exec(migration);
    const anchoredDrift = migration.replace(
      "return jsonb_build_object('result', true, 'id', v_token_id);",
      "perform 1;\n    return jsonb_build_object('result', true, 'id', v_token_id);",
    );
    await db.exec(anchoredDrift);
    await assert.rejects(db.exec(rollback), /APNs rollback function fingerprint mismatch/);
    await db.exec("rollback");
    assert.equal((await db.query(`select count(*)::int as count from information_schema.columns where table_schema='public' and table_name='push_tokens' and column_name='apns_environment'`)).rows[0].count, 1);

    await db.exec(migration);
    await db.exec("create index push_tokens_apns_environment_later_idx on push_tokens(apns_environment)");
    await assert.rejects(db.exec(rollback), /APNs rollback dependency anchor mismatch/);
    await db.exec("rollback");
    assert.equal((await db.query("select to_regclass('public.push_tokens_apns_environment_later_idx') is not null as present")).rows[0].present, true);
  } finally { await db.close(); }
});
