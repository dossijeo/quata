import {createHash} from "node:crypto";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const bridgeEmail=record=>`${record.countryCode}${record.phone}@phone.quata.app`;
function validate(record) {
  if (![record.runId,record.profileId,record.authUserId].every(id=>uuid.test(id)) ||
      record.email !== `deep-link-${record.authUserId}@example.invalid` ||
      !/^[0-9]{1,3}$/.test(record.countryCode) || !/^[1-9][0-9]{8,14}$/.test(record.phone)) {
    throw Error("deep_link_profile_invalid_plan");
  }
}
async function durable(journal,record) {
  validate(record);
  const value=await journal.read();
  if (["runId","profileId","authUserId","email","countryCode","phone"].some(key=>value[key]!==record[key])) {
    throw Error("deep_link_profile_journal_mismatch");
  }
  return value;
}

// Caller owns a durable exclusive run lock. adminRequest is private server-side
// transport, never browser code; it must return {status, body} without logging.
// Intended IDs are journaled before Auth creation, which supports a custom UUID.
export async function createDeepLinkProfile({client,journal,record,password,adminRequest}) {
  const value=await durable(journal,record);
  if (value.state.profileCreationStarted || value.state.sessions.length || typeof password!=="string" || password.length<20) {
    throw Error("deep_link_profile_already_started_or_invalid_password");
  }
  const absent=await client.query(`select
    not exists(select 1 from auth.users where id=$1::uuid or email=$2 or email=$5) as auth_absent,
    not exists(select 1 from public.community_profiles where id=$3::uuid or auth_user_id=$1::uuid
      or phone_local=$4 or phone_normalized=$4 or telefono=$4) as profile_absent`,
    [record.authUserId,record.email,record.profileId,record.phone,bridgeEmail(record)]);
  if (absent.rows?.[0]?.auth_absent!==true || absent.rows?.[0]?.profile_absent!==true) {
    throw Error("deep_link_profile_collision");
  }
  value.state.profileCreationStarted=true;
  await journal.checkpoint(value.state);
  let response;
  try {
    response=await adminRequest({method:"POST",path:"/auth/v1/admin/users",body:{
      id:record.authUserId,email:record.email,password,email_confirm:true,
      app_metadata:{quata_e2e:{unit:"FLOW-DEEP-LINKS",run_id:record.runId}},
      user_metadata:{username:`deep-link-${record.authUserId}`,full_name:"Deep link fixture"},
    }});
  } catch {throw Error("deep_link_profile_auth_creation_uncertain");}
  if (response.status!==200 || response.body?.id!==record.authUserId) {
    throw Error("deep_link_profile_auth_creation_unresolved");
  }
  const created=await durable(journal,record);
  created.state.authCreated=true;
  await journal.checkpoint(created.state);
  await client.query("begin");
  try {
    const owner=await client.query(`select id from auth.users where id=$1::uuid and email=$2
      and raw_app_meta_data->'quata_e2e'->>'unit'='FLOW-DEEP-LINKS'
      and raw_app_meta_data->'quata_e2e'->>'run_id'=$3 for update`,[record.authUserId,record.email,record.runId]);
    if (owner.rowCount!==1)throw Error("identity_mismatch");
    const fullPhone=`+${record.countryCode}${record.phone}`;
    await client.query(`insert into public.community_profiles
      (id,auth_user_id,display_name,nombre,phone,telefono,phone_normalized,phone_local,
       country_code,code,phone_e164,pass_hash,pass_plain,account_status,neighborhood,barrio)
      values ($1::uuid,$2::uuid,'Deep link fixture','Deep link fixture',$3,$3,$4,$4,$5,$5,$3,$6,null,'active',null,'')`,
      [record.profileId,record.authUserId,fullPhone,record.phone,record.countryCode,createHash("sha256").update(password).digest("hex")]);
    await client.query("commit");
  } catch {await client.query("rollback").catch(()=>{});throw Error("deep_link_profile_insert_unresolved");}
  const ready=await durable(journal,record);ready.state.profileCreated=true;await journal.checkpoint(ready.state);
  return {profileId:record.profileId,authUserId:record.authUserId};
}

const quote=name=>'"'+name.replaceAll('"','""')+'"';
// All application dependencies must be gone before retiring the identities.
// Only their generated phone directory, empty legacy profile, owned sessions and
// Auth identities may cascade. New/unknown dependencies fail closed.
const allowed=new Set(["auth.identities.user_id>auth.users:c","auth.sessions.user_id>auth.users:c",
  "public.profiles.id>auth.users:c","public.quata_profile_phone_directory.profile_id>public.community_profiles:c",
  "public.web_client_sessions.profile_id>public.community_profiles:c","public.web_client_sessions.auth_user_id>auth.users:c"]);
async function verifyAbsent(client,record) {
  const gone=await client.query(`select
    not exists(select 1 from auth.users where id=$1::uuid or email=$3 or email=$4) as auth,
    not exists(select 1 from public.community_profiles where id=$2::uuid or auth_user_id=$1::uuid) as profile,
    not exists(select 1 from public.profiles where id=$1::uuid) as legacy_profile,
    not exists(select 1 from auth.identities where user_id=$1::uuid) as identities,
    not exists(select 1 from auth.sessions where user_id=$1::uuid) as sessions,
    not exists(select 1 from public.web_client_sessions where auth_user_id=$1::uuid or profile_id=$2::uuid) as web_sessions,
    not exists(select 1 from public.quata_profile_phone_directory where profile_id=$2::uuid) as directory,
    not exists(select 1 from storage.objects where owner=$1::uuid or owner_id=$1::uuid::text) as storage`,
    [record.authUserId,record.profileId,record.email,bridgeEmail(record)]);
  if (["auth","profile","legacy_profile","identities","sessions","web_sessions","directory","storage"].some(key=>gone.rows?.[0]?.[key]!==true)) {
    throw Error("residue");
  }
}
export async function retireDeepLinkProfile({client,journal,record,operationsSettled}) {
  const value=await durable(journal,record);
  if (!value.state.profileCreationStarted || typeof operationsSettled!=="function" || await operationsSettled()!==true) {
    throw Error("deep_link_profile_retirement_not_ready");
  }
  await client.query("begin");
  try {
    await client.query("set local lock_timeout='5s'");
    const auth=await client.query(`select id,email,raw_app_meta_data->'quata_e2e' as owner from auth.users
      where id=$1::uuid for update`,[record.authUserId]);
    const profile=await client.query(`select id,auth_user_id from public.community_profiles
      where id=$1::uuid or auth_user_id=$2::uuid for update`,[record.profileId,record.authUserId]);
    if (auth.rowCount===0 && profile.rowCount===0) {
      // Includes a successful commit followed by a failed local checkpoint.
      await verifyAbsent(client,record);
      await client.query("commit");
      const reconciled=await durable(journal,record);reconciled.state.profileRetired=true;await journal.checkpoint(reconciled.state);
      return {retired:true};
    }
    const loginStarted=value.state.sessions.some(ticket=>ticket.runId===record.runId && ticket.profileId===record.profileId &&
      ticket.authUserId===record.authUserId && ticket.requestStarted===true);
    const expectedEmail=auth.rows?.[0]?.email===record.email || (loginStarted && auth.rows?.[0]?.email===bridgeEmail(record));
    if (auth.rowCount!==1 || !expectedEmail || auth.rows[0].owner?.unit!=="FLOW-DEEP-LINKS" ||
        auth.rows[0].owner?.run_id!==record.runId || profile.rowCount>1 ||
        (profile.rowCount===1 && (profile.rows[0].id!==record.profileId || profile.rows[0].auth_user_id!==record.authUserId))) {
      throw Error("ownership_mismatch");
    }
    // Lock the legacy profile too: otherwise references could arrive while audited.
    await client.query("select id from public.profiles where id=$1::uuid for update",[record.authUserId]);
    const refs=await client.query(`select n.nspname as schema,c.relname as table,a.attname as column,
      f.confrelid::regclass::text as parent,cardinality(f.conkey) as key_count,
      f.confdeltype as delete_action,pa.attname as parent_column
      from pg_constraint f join pg_class c on c.oid=f.conrelid join pg_namespace n on n.oid=c.relnamespace
      join pg_attribute a on a.attrelid=f.conrelid and a.attnum=f.conkey[1]
      join pg_attribute pa on pa.attrelid=f.confrelid and pa.attnum=f.confkey[1]
      where f.contype='f' and f.confrelid in
        ('auth.users'::regclass,'public.community_profiles'::regclass,'public.profiles'::regclass)`);
    for (const ref of refs.rows) {
      if (ref.key_count!==1 || ref.parent_column!=="id")throw Error("unsupported_dependency");
      if (ref.schema==="public" && ref.table==="community_profiles" && ref.column==="auth_user_id")continue;
      const target=ref.parent==="community_profiles" || ref.parent==="public.community_profiles" ? record.profileId : record.authUserId;
      const found=await client.query(`select count(*)::text as count from ${quote(ref.schema)}.${quote(ref.table)} where ${quote(ref.column)}=$1`,[target]);
      if (!/^\d+$/.test(found.rows?.[0]?.count??""))throw Error("incomplete_dependency_audit");
      const parent=ref.parent.includes(".")?ref.parent:`public.${ref.parent}`;
      if (found.rows[0].count!=="0" && !allowed.has(`${ref.schema}.${ref.table}.${ref.column}>${parent}:${ref.delete_action}`))throw Error("unexpected_dependency");
    }
    // Storage ownership is not consistently backed by an Auth FK.
    const storage=await client.query("select count(*)::text as count from storage.objects where owner=$1::uuid or owner_id=$1::uuid::text",[record.authUserId]);
    if (storage.rows?.[0]?.count!=="0")throw Error("storage_dependency");
    if (profile.rowCount===1)await client.query("delete from public.community_profiles where id=$1::uuid and auth_user_id=$2::uuid",[record.profileId,record.authUserId]);
    await client.query("delete from auth.users where id=$1::uuid",[record.authUserId]);
    await verifyAbsent(client,record);
    await client.query("commit");
  } catch {await client.query("rollback").catch(()=>{});throw Error("deep_link_profile_retirement_unresolved");}
  const retired=await durable(journal,record);retired.state.profileRetired=true;await journal.checkpoint(retired.state);
  // The coordinator verifies absence again before retiring the private journal.
  return {retired:true};
}
