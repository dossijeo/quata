import { createHash } from "node:crypto";

export const accountLifecycleFixtureTermsVersion = "2026-07";
const UNIT = "ACCOUNT-LIFECYCLE";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const bridgeEmail = (record) => `${record.countryCode}${record.phone}@phone.quata.app`;

function validate(record) {
  if (![record.runId, record.profileId, record.authUserId].every((value) => uuid.test(value)) ||
      record.email !== `account-lifecycle-${record.authUserId}@example.invalid` ||
      !/^[0-9]{1,3}$/.test(record.countryCode) || !/^[1-9][0-9]{8,14}$/.test(record.phone)) {
    throw new Error("account_lifecycle_fixture_invalid_plan");
  }
}

async function durable(journal, record) {
  validate(record);
  const value = await journal.read();
  if (["runId", "profileId", "authUserId", "email", "countryCode", "phone"]
    .some((key) => value[key] !== record[key])) {
    throw new Error("account_lifecycle_fixture_journal_mismatch");
  }
  return value;
}

// The coordinator owns a private durable journal and an Admin transport which
// accepts only the exact Auth Admin endpoints used here. Secrets stay in memory.
export async function createAccountLifecycleFixture({ client, journal, record, password, adminRequest }) {
  const value = await durable(journal, record);
  if (value.state.fixtureCreationStarted || typeof password !== "string" || password.length < 20) {
    throw new Error("account_lifecycle_fixture_already_started_or_invalid_password");
  }
  const absent = await client.query(`select
    not exists(select 1 from auth.users where id=$1::uuid or email=$2 or email=$5) as auth_absent,
    not exists(select 1 from public.community_profiles where id=$3::uuid or auth_user_id=$1::uuid
      or phone_local=$4 or phone_normalized=$4 or telefono=$4) as profile_absent`,
  [record.authUserId, record.email, record.profileId, record.phone, bridgeEmail(record)]);
  if (absent.rows?.[0]?.auth_absent !== true || absent.rows?.[0]?.profile_absent !== true) {
    throw new Error("account_lifecycle_fixture_collision");
  }
  value.state.fixtureCreationStarted = true;
  value.state.fixtureTermsVersion = accountLifecycleFixtureTermsVersion;
  await journal.checkpoint(value.state);
  let response;
  try {
    response = await adminRequest({ method: "POST", path: "/auth/v1/admin/users", body: {
      id: record.authUserId,
      email: record.email,
      password,
      email_confirm: true,
      app_metadata: { quata_e2e: { unit: UNIT, run_id: record.runId } },
      user_metadata: { username: `account-lifecycle-${record.authUserId}`, full_name: "Account lifecycle fixture" },
    } });
  } catch {
    throw new Error("account_lifecycle_fixture_auth_creation_uncertain");
  }
  if (response.status !== 200 || response.body?.id !== record.authUserId) {
    throw new Error("account_lifecycle_fixture_auth_creation_unresolved");
  }
  const authCreated = await durable(journal, record);
  authCreated.state.authCreated = true;
  await journal.checkpoint(authCreated.state);
  await client.query("begin");
  try {
    const owner = await client.query(`select id from auth.users where id=$1::uuid and email=$2
      and raw_app_meta_data->'quata_e2e'->>'unit'=$3
      and raw_app_meta_data->'quata_e2e'->>'run_id'=$4 for update`,
    [record.authUserId, record.email, UNIT, record.runId]);
    if (owner.rowCount !== 1) throw new Error("account_lifecycle_fixture_identity_mismatch");
    const fullPhone = `+${record.countryCode}${record.phone}`;
    await client.query(`insert into public.community_profiles
      (id,auth_user_id,display_name,nombre,phone,telefono,phone_normalized,phone_local,
       country_code,code,phone_e164,pass_hash,pass_plain,account_status,neighborhood,barrio)
      values ($1::uuid,$2::uuid,'Account lifecycle fixture','Account lifecycle fixture',$3,$3,$4,$4,
       $5,$5,$3,$6,null,'active',null,'')`,
    [record.profileId, record.authUserId, fullPhone, record.phone, record.countryCode,
      createHash("sha256").update(password).digest("hex")]);
    await client.query(`insert into public.ugc_terms_acceptances(profile_id,terms_version)
      values ($1::uuid,$2)`, [record.profileId, accountLifecycleFixtureTermsVersion]);
    await client.query("commit");
  } catch {
    await client.query("rollback").catch(() => {});
    throw new Error("account_lifecycle_fixture_insert_unresolved");
  }
  const ready = await durable(journal, record);
  ready.state.fixtureCreated = true;
  await journal.checkpoint(ready.state);
  return { profileId: record.profileId, authUserId: record.authUserId };
}

export async function verifyAccountDeactivated({ client, record, sessionRejected, protectedActionRejected }) {
  validate(record);
  if (typeof sessionRejected !== "function" || typeof protectedActionRejected !== "function") {
    throw new Error("account_lifecycle_deactivation_observers_required");
  }
  const state = await client.query(`select
    (select count(*)::int from public.community_profiles where id=$1::uuid and account_status='deactivated'
      and auth_user_id is null and deactivated_auth_user_id=$2::uuid) as profile_count,
    (select count(*)::int from auth.users where id=$2::uuid and email in ($3,$6)
      and raw_app_meta_data->'quata_e2e'->>'unit'=$4
      and raw_app_meta_data->'quata_e2e'->>'run_id'=$5 and banned_until>now()) as banned_auth_count,
    (select count(*)::int from public.push_tokens where user_id=$1::uuid or auth_user_id=$2::uuid) as push_count,
    (select count(*)::int from public.web_client_sessions
      where (profile_id=$1::uuid or auth_user_id=$2::uuid) and revoked_at is null) as web_session_count`,
  [record.profileId, record.authUserId, record.email, UNIT, record.runId, bridgeEmail(record)]);
  const row = state.rows?.[0];
  if (row?.profile_count !== 1 || row?.banned_auth_count !== 1 || row?.push_count !== 0 || row?.web_session_count !== 0 ||
      await sessionRejected() !== true || await protectedActionRejected() !== true) {
    throw new Error("account_lifecycle_deactivation_not_verified");
  }
  return { deactivated: true, sessionRejected: true, protectedActionRejected: true };
}

export async function verifyAccountDeleted({ client, record }) {
  validate(record);
  const result = await client.query(`select
    not exists(select 1 from auth.users where id=$1::uuid or email=$3 or email=$4) as auth,
    not exists(select 1 from public.community_profiles where id=$2::uuid or auth_user_id=$1::uuid
      or deactivated_auth_user_id=$1::uuid) as profile,
    not exists(select 1 from public.profiles where id=$1::uuid) as legacy_profile,
    not exists(select 1 from auth.identities where user_id=$1::uuid) as identities,
    not exists(select 1 from auth.sessions where user_id=$1::uuid) as sessions,
    not exists(select 1 from public.web_client_sessions where profile_id=$2::uuid or auth_user_id=$1::uuid) as web_sessions,
    not exists(select 1 from public.push_tokens where user_id=$2::uuid or auth_user_id=$1::uuid) as push_tokens,
    not exists(select 1 from public.account_deletion_requests where auth_user_id=$1::uuid or profile_id=$2::uuid) as deletion_request,
    not exists(select 1 from storage.objects where owner=$1::uuid or owner_id=$1::uuid::text) as storage`,
  [record.authUserId, record.profileId, record.email, bridgeEmail(record)]);
  const row = result.rows?.[0] ?? {};
  const required = ["auth", "profile", "legacy_profile", "identities", "sessions", "web_sessions", "push_tokens", "deletion_request", "storage"];
  if (required.some((key) => row[key] !== true)) throw new Error("account_lifecycle_deletion_residue");
  return { deleted: true, residueCounts: Object.fromEntries(required.map((key) => [key, 0])) };
}

export async function retireAccountLifecycleFixture({ client, journal, record, operationsSettled }) {
  const value = await durable(journal, record);
  if (!value.state.fixtureCreationStarted || typeof operationsSettled !== "function" || await operationsSettled() !== true) {
    throw new Error("account_lifecycle_fixture_retirement_not_ready");
  }
  await client.query("begin");
  try {
    await client.query("set local lock_timeout='5s'");
    const auth = await client.query(`select id,email,raw_app_meta_data->'quata_e2e' as owner from auth.users
      where id=$1::uuid for update`, [record.authUserId]);
    const profile = await client.query(`select id,auth_user_id,deactivated_auth_user_id,account_status
      from public.community_profiles where id=$1::uuid for update`, [record.profileId]);
    if (auth.rowCount === 0 && profile.rowCount === 0) {
      await verifyAccountDeleted({ client, record });
      await client.query("commit");
      const reconciled = await durable(journal, record);
      reconciled.state.fixtureRetired = true;
      await journal.checkpoint(reconciled.state);
      return { retired: true };
    }
    const loginStarted = value.state.sessions?.some((ticket) => ticket.runId === record.runId &&
      ticket.profileId === record.profileId && ticket.authUserId === record.authUserId && ticket.requestStarted === true);
    const expectedEmail = auth.rows?.[0]?.email === record.email ||
      (loginStarted && auth.rows?.[0]?.email === bridgeEmail(record));
    const activeOwned = profile.rows?.[0]?.auth_user_id === record.authUserId &&
      profile.rows?.[0]?.deactivated_auth_user_id == null && profile.rows?.[0]?.account_status === "active";
    const deactivatedOwned = profile.rows?.[0]?.auth_user_id === null &&
      profile.rows?.[0]?.deactivated_auth_user_id === record.authUserId && profile.rows?.[0]?.account_status === "deactivated";
    if (auth.rowCount !== 1 || !expectedEmail || auth.rows[0].owner?.unit !== UNIT ||
        auth.rows[0].owner?.run_id !== record.runId || profile.rowCount !== 1 || profile.rows[0].id !== record.profileId ||
        (!activeOwned && !deactivatedOwned)) {
      throw new Error("account_lifecycle_fixture_ownership_mismatch");
    }
    const unexpected = await client.query(`select
      (select count(*)::int from public.community_posts where profile_id=$1::uuid or author_id=$1::uuid) +
      (select count(*)::int from public.chat_participants where profile_id=$1::uuid) +
      (select count(*)::int from public.chat_messages where sender_profile_id=$1::uuid) +
      (select count(*)::int from storage.objects where owner=$2::uuid or owner_id=$2::uuid::text) as count`,
    [record.profileId, record.authUserId]);
    if (unexpected.rows?.[0]?.count !== 0) throw new Error("account_lifecycle_fixture_unexpected_dependency");
    await client.query(`delete from public.community_profiles where id=$1::uuid and
      (auth_user_id=$2::uuid or deactivated_auth_user_id=$2::uuid)`, [record.profileId, record.authUserId]);
    await client.query("delete from auth.users where id=$1::uuid", [record.authUserId]);
    await verifyAccountDeleted({ client, record });
    await client.query("commit");
  } catch {
    await client.query("rollback").catch(() => {});
    throw new Error("account_lifecycle_fixture_retirement_unresolved");
  }
  const retired = await durable(journal, record);
  retired.state.fixtureRetired = true;
  await journal.checkpoint(retired.state);
  return { retired: true };
}

export const retireDeactivatedAccountFixture = retireAccountLifecycleFixture;
