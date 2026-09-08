import {createHash} from "node:crypto";

// Require a fixed point of the existing common Save projection, with no contacts
// to delete/recreate. This guard never normalizes or restores unrelated product data.
export async function auditRecoveryProfileFixture({client,profileId,authUserId}) {
  let result;
  try {
    result=await client.query(`select p.display_name,p.nombre,p.neighborhood,p.barrio,p.country_code,p.code,
      p.phone_local,p.phone,p.telefono,p.avatar_url,p.avatar,
      (select count(*)::int from public.community_emergency_contacts c where c.profile_id=p.id) as contacts
      from public.community_profiles p where p.id=$1::uuid and p.auth_user_id=$2::uuid`,[profileId,authUserId]);
  } catch {throw Error("recovery_profile_fixture_audit_failed");}
  if(result.rowCount!==1)return {eligible:false,reason:"profile_identity_mismatch"};
  const row=result.rows[0];
  const clean=value=>typeof value==="string" && value.trim()?value.trim():null;
  const pair=(a,b)=>typeof row[a]==="string" && row[a]===row[a].trim() && row[a]===row[b];
  const canonical=pair("display_name","nombre") && !!row.display_name && pair("neighborhood","barrio") &&
    pair("country_code","code") && /^[0-9]+$/.test(row.country_code) && pair("phone_local","telefono") &&
    /^[0-9]+$/.test(row.phone_local) && row.phone===`+${row.country_code}${row.phone_local}` &&
    row.avatar_url===(clean(row.avatar_url)??clean(row.avatar)) &&
    (row.avatar_url===null || /^https?:\/\//.test(row.avatar_url));
  if(!canonical)return {eligible:false,reason:"profile_save_would_change_other_fields"};
  if(row.contacts!==0)return {eligible:false,reason:"profile_has_emergency_contacts"};
  const fields=["display_name","nombre","neighborhood","barrio","country_code","code","phone_local","phone","telefono","avatar_url","avatar"];
  const digest=createHash("sha256").update(JSON.stringify(fields.map(key=>row[key]))).digest("hex");
  return {eligible:true,baselineDigest:digest};
}
