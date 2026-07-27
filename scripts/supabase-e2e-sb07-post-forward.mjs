#!/usr/bin/env node
import { createHash, randomUUID } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { createRequire } from 'node:module';
import { buildSb01TlsConnection, loadSb01CertificateAuthority } from './supabase-e2e-sb01-tls.mjs';

const { Client } = createRequire(import.meta.url)('pg');
const sha = (value) => createHash('sha256').update(value).digest('hex');
const must = (condition, code) => { if (!condition) throw new Error(code); };
function args(values) { if (values.length !== 4 || values[0] !== '--mode' || !['preflight-auth', 'full'].includes(values[1]) || values[2] !== '--out' || !values[3]) throw new Error('invalid_arguments'); return { mode: values[1], out: values[3] }; }
function safeError(error) { const m = String(error?.message ?? 'unexpected'); return ['invalid_arguments','public_auth_failed','anon_select_shape_changed','own_insert_not_201_dto','spoof_insert_not_42501','outsider_delete_not_zero','outsider_row_not_intact','owner_delete_not_one','inactive_admin_not_blocked','inactive_admin_row_not_intact','active_admin_delete_not_one','update_not_blocked_42501','cleanup_residue_detected','cleanup_residual_pending'].find((x) => m.startsWith(x)) ?? 'unexpected_gate_failure'; }
const { mode, out } = args(process.argv.slice(2));
const config = await readFile(resolve('app/src/main/java/com/quata/core/config/AppConfig.kt'), 'utf8');
const base = config.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/$/, '');
const publicKey = config.match(/SUPABASE_ANON_KEY\s*=\s*"([^"]+)"/)?.[1];
must(base && publicKey, 'missing_public_runtime_config'); must(process.env.SUPABASE_DB_URL && process.env.SUPABASE_DB_TLS_CA_FILE && process.env.QUATA_SB07_RECOVERY_FILE, 'missing_secure_database_config');
const fixture = { actor: randomUUID(), outsider: randomUUID(), admin: randomUUID(), wall: randomUUID(), post: randomUUID() };
const marker = `sb07-forward-${randomUUID()}`, password = `Sb07-${randomUUID()}-A9`;
const report = { check: 'SB-07-post-forward', mode, status: 'failed', startedAt: new Date().toISOString(), fixture: { markerSha256: sha(marker), ids: Object.fromEntries(Object.entries(fixture).map(([k,v]) => [k, sha(v).slice(0,16)])) }, steps: [], cleanup: { status: 'not_started' } };
const ca = await loadSb01CertificateAuthority();
const db = new Client({ ...buildSb01TlsConnection(process.env.SUPABASE_DB_URL, ca), application_name: 'quata-sb07-post-forward', connectionTimeoutMillis: 15000, query_timeout: 20000 });
const headers = (token) => ({ apikey: publicKey, 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) });
async function http(url, options) { const response = await fetch(url, { ...options, signal: AbortSignal.timeout(15000) }); const text = await response.text(); let body = null; if (text) { try { body = JSON.parse(text); } catch { throw new Error('invalid_public_response_json'); } } return { status: response.status, body }; }
const api = (table, query='') => `${base}/rest/v1/${table}${query ? `?${query}` : ''}`;
async function login(profileId, phone) { const response = await http(`${base}/functions/v1/quata-auth-bridge`, { method: 'POST', headers: headers(), body: JSON.stringify({ action: 'web_login', profile_id: profileId, country_code: '34', phone_local: phone, password, client_instance_id: `sb07-${randomUUID()}` }) }); must(response.status === 200 && typeof response.body?.session?.access_token === 'string', 'public_auth_failed'); return response.body.session.access_token; }
async function insert(token, profileId, body) { return http(api('community_comments','select=id,post_id,profile_id,body,created_at'), { method:'POST', headers:{...headers(token),Prefer:'return=representation'}, body:JSON.stringify({ post_id: fixture.post, profile_id: profileId, body }) }); }
async function remove(token, id) { return http(api('community_comments',`id=eq.${encodeURIComponent(id)}`), { method:'DELETE', headers:{...headers(token),Prefer:'return=representation'} }); }
async function cleanup() {
  const profiles=[fixture.actor,fixture.outsider,fixture.admin]; report.cleanup={status:'running'};
  await db.query('delete from public.community_comments where post_id=$1 or profile_id=any($2::uuid[])',[fixture.post,profiles]);
  await db.query('delete from public.community_posts where id=$1',[fixture.post]);
  await db.query('delete from public.community_walls where id=$1',[fixture.wall]);
  const links=(await db.query('select id,auth_user_id from public.community_profiles where id=any($1::uuid[])',[profiles])).rows;
  const linkedAuthIds=links.map((row)=>row.auth_user_id), exactIds=new Set(links.map((row)=>row.id));
  const profileMappingExact=links.length===profiles.length && exactIds.size===profiles.length && profiles.every((id)=>exactIds.has(id)) && linkedAuthIds.every(Boolean) && new Set(linkedAuthIds).size===profiles.length;
  if (!profileMappingExact) { await writeFile(process.env.QUATA_SB07_RECOVERY_FILE, `${JSON.stringify({kind:'sb07_post_forward_recovery',createdAt:new Date().toISOString(),fixture},null,2)}\n`,{mode:0o600}); report.cleanup={status:'residual_pending_auth_mapping'}; throw new Error('cleanup_residual_pending'); }
  await db.query('delete from public.web_client_sessions where profile_id=any($1::uuid[])',[profiles]).catch(()=>undefined);
  await db.query('delete from public.community_profiles where id=any($1::uuid[])',[profiles]);
  await db.query('delete from auth.users where id=any($1::uuid[])',[linkedAuthIds]);
  const residue=await db.query('select (select count(*) from public.community_comments where post_id=$1 or profile_id=any($2::uuid[]))+(select count(*) from public.community_posts where id=$1)+(select count(*) from public.community_walls where id=$3)+(select count(*) from public.community_profiles where id=any($2::uuid[]))+(select count(*) from auth.users where id=any($4::uuid[])) n',[fixture.post,profiles,fixture.wall,linkedAuthIds]);
  must(Number(residue.rows[0].n)===0,'cleanup_residue_detected'); report.cleanup={status:'verified_zero_residue',authIdentitySource:'profile_link'};
}
try {
  await db.connect(); const digits=String(Date.now()).slice(-8); const fixtures=[['actor',fixture.actor,false],['outsider',fixture.outsider,false],['admin',fixture.admin,true]];
  for (let i=0;i<fixtures.length;i+=1) { const phone=`8${digits}${i}`; fixtures[i].push(phone); await db.query('insert into public.community_profiles(id,display_name,phone,pass_hash,phone_normalized,country_code,phone_local,is_admin,account_status) values($1,$2,$3,$4,$3,$5,$6,$7,$8)',[fixtures[i][1],`${marker}-${fixtures[i][0]}`,`+34${phone}`,sha(password),'34',phone,fixtures[i][2],'active']); }
  const [actor,outsider,admin] = await Promise.all(fixtures.map(([,id,,phone])=>login(id,phone)));
  report.steps.push('auth_preflight_three_active_profiles_login');
  if (mode === 'full') {
    await db.query('insert into public.community_walls(id,slug,name) values($1,$2,$3)',[fixture.wall,`sb07-${marker.slice(-12)}`,marker]); await db.query('insert into public.community_posts(id,wall_id,profile_id,body) values($1,$2,$3,$4)',[fixture.post,fixture.wall,fixture.actor,marker]);
    const anon=await http(api('community_comments',`select=id,post_id,profile_id,body,created_at&post_id=eq.${fixture.post}`),{headers:headers()}); must(anon.status===200&&Array.isArray(anon.body)&&anon.body.every((x)=>Object.keys(x).sort().join(',')==='body,created_at,id,post_id,profile_id'),'anon_select_shape_changed'); report.steps.push('anon_select_shape_identical');
    const own=await insert(actor,fixture.actor,`${marker}-owner`); must(own.status===201&&Array.isArray(own.body)&&own.body.length===1&&own.body[0].profile_id===fixture.actor&&own.body[0].post_id===fixture.post,'own_insert_not_201_dto'); const ownId=own.body[0].id; report.steps.push('auth_own_insert_201_dto');
    const spoof=await insert(actor,fixture.outsider,`${marker}-spoof`); must(spoof.body?.code==='42501','spoof_insert_not_42501'); report.steps.push('spoof_insert_42501');
    const outsiderDelete=await remove(outsider,ownId); must(outsiderDelete.status===200&&Array.isArray(outsiderDelete.body)&&outsiderDelete.body.length===0,'outsider_delete_not_zero'); must((await db.query('select count(*)::int n from public.community_comments where id=$1 and profile_id=$2',[ownId,fixture.actor])).rows[0].n===1,'outsider_row_not_intact'); report.steps.push('outsider_delete_zero_intact');
    const update=await http(api('community_comments',`id=eq.${ownId}`),{method:'PATCH',headers:{...headers(actor),Prefer:'return=representation'},body:JSON.stringify({body:`${marker}-update`})}); must(update.body?.code==='42501','update_not_blocked_42501'); report.steps.push('update_blocked_42501');
    const ownerDelete=await remove(actor,ownId); must(ownerDelete.status===200&&Array.isArray(ownerDelete.body)&&ownerDelete.body.length===1,'owner_delete_not_one'); report.steps.push('owner_delete_one');
    const moderated=await insert(actor,fixture.actor,`${marker}-moderated`); must(moderated.status===201&&moderated.body?.[0]?.id,'active_admin_fixture_failed'); const modId=moderated.body[0].id;
    await db.query("update public.community_profiles set account_status='deactivated',deactivated_at=now() where id=$1",[fixture.admin]); const inactive=await remove(admin,modId); must((inactive.status===200&&Array.isArray(inactive.body)&&inactive.body.length===0)||inactive.body?.code==='42501','inactive_admin_not_blocked'); must((await db.query('select count(*)::int n from public.community_comments where id=$1',[modId])).rows[0].n===1,'inactive_admin_row_not_intact'); report.steps.push('inactive_admin_blocked_intact');
    await db.query("update public.community_profiles set account_status='active',deactivated_at=null where id=$1",[fixture.admin]); const active=await remove(admin,modId); must(active.status===200&&Array.isArray(active.body)&&active.body.length===1,'active_admin_delete_not_one'); report.steps.push('active_admin_delete_one');
  }
  report.status='passed';
} catch(error) { report.error=safeError(error); }
try { await cleanup(); } catch(error) { report.status='failed'; report.error=safeError(error); }
await db.end().catch(()=>undefined); report.finishedAt=new Date().toISOString(); await mkdir(dirname(resolve(out)),{recursive:true}); await writeFile(resolve(out),`${JSON.stringify(report,null,2)}\n`,{mode:0o600}); console.log(JSON.stringify({check:report.check,status:report.status,steps:report.steps,cleanup:report.cleanup.status,error:report.error??null})); process.exitCode=report.status==='passed'&&report.cleanup.status==='verified_zero_residue'?0:1;
