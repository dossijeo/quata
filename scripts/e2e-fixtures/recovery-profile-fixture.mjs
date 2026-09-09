import {createHash} from "node:crypto";

// Require a fixed point of common Save INCLUDING the deployed profile triggers.
// No contacts/community memberships: this guard never restores unrelated data.
export async function auditRecoveryProfileFixture({client,profileId,authUserId}) {
  let result;
  try {
    result=await client.query(`select p.display_name,p.nombre,p.neighborhood,p.barrio,p.country_code,p.code,
      p.phone_local,p.phone,p.telefono,p.phone_normalized,p.phone_e164,p.avatar_url,p.avatar,
      (select count(*)::int from public.community_emergency_contacts c where c.profile_id=p.id) as contacts,
      (select count(*)::int from public.community_members m where m.profile_id=p.id) as memberships,
      array(select d.phone_key from public.quata_profile_phone_directory d where d.profile_id=p.id order by d.phone_key) as phone_keys
      from public.community_profiles p where p.id=$1::uuid and p.auth_user_id=$2::uuid`,[profileId,authUserId]);
  } catch {throw Error("recovery_profile_fixture_audit_failed");}
  if(result.rowCount!==1)return {eligible:false,reason:"profile_identity_mismatch"};
  const row=result.rows[0];
  const clean=value=>typeof value==="string" && value.trim()?value.trim():null;
  const pair=(a,b)=>typeof row[a]==="string" && row[a]===row[a].trim() && row[a]===row[b];
  const canonical=pair("display_name","nombre") && !!row.display_name &&
    pair("country_code","code") && /^[0-9]+$/.test(row.country_code) &&
    typeof row.phone_local==="string" && /^[1-9][0-9]*$/.test(row.phone_local) && !row.phone_local.startsWith(row.country_code) &&
    row.phone===`+${row.country_code}${row.phone_local}` && row.telefono===row.phone &&
    row.phone_e164===row.phone && row.phone_normalized===row.phone_local &&
    row.avatar_url===(clean(row.avatar_url)??clean(row.avatar)) &&
    (row.avatar_url===null || /^https?:\/\//.test(row.avatar_url));
  if(!canonical)return {eligible:false,reason:"profile_save_would_change_other_fields"};
  // Save sends empty strings; the live trigger converts neighborhood to null,
  // retains barrio="", and deletes memberships without creating a wall.
  if(row.neighborhood!==null || row.barrio!=="" || row.memberships!==0)
    return {eligible:false,reason:"profile_has_community_state"};
  if(row.contacts!==0)return {eligible:false,reason:"profile_has_emergency_contacts"};
  const expectedKeys=[...new Set([row.phone_local,`${row.country_code}${row.phone_local}`,`${row.country_code}${row.country_code}${row.phone_local}`])]
    .filter(value=>value.length>=6 && value.length<=20).sort();
  if(JSON.stringify(row.phone_keys)!==JSON.stringify(expectedKeys))return {eligible:false,reason:"profile_phone_directory_mismatch"};
  const fields=["display_name","nombre","neighborhood","barrio","country_code","code","phone_local","phone","telefono","phone_normalized","phone_e164","avatar_url","avatar","phone_keys"];
  const digest=createHash("sha256").update(JSON.stringify(fields.map(key=>row[key]))).digest("hex");
  return {eligible:true,baselineDigest:digest};
}
