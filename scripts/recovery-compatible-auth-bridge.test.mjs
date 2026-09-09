import { readFile } from 'node:fs/promises';
import { stripTypeScriptTypes } from 'node:module';
import { runInNewContext } from 'node:vm';
import { webcrypto, createHash } from 'node:crypto';
import assert from 'node:assert/strict';
import test from 'node:test';
const source = await readFile(new URL('../supabase/deployment-packages/account-recovery-secret-v21/supabase/functions/quata-auth-bridge/index.ts',import.meta.url),'utf8');
test('versioned deployment source and configuration match the certified package',async()=>{
  const root=new URL('../supabase/deployment-packages/account-recovery-secret-v21/',import.meta.url);
  const manifest=JSON.parse(await readFile(new URL('manifest.json',root),'utf8'));
  const config=await readFile(new URL('supabase/config.toml',root));
  assert.equal(createHash('sha256').update(source).digest('hex'),manifest.sourceSha256);
  assert.equal(createHash('sha256').update(config).digest('hex'),manifest.configSha256);
  assert.equal(manifest.writeEnabledByDefault,false);
  assert.equal(manifest.requiresSchemaChange,false);
});
const javascript = stripTypeScriptTypes(source.replace(/^import .*;\r?\n/gm,''));
function harness({actors=[{id:'actor'}],valid=true,enabled=true,updated=[{id:'actor'}]}={}) {
  const writes=[]; let handler;
  const profile={id:'actor',auth_user_id:'auth',email:'fixture@example.invalid',secret_question:'pet',secret_answer:'Luna'};
  const env={SUPABASE_URL:'https://example.invalid',SUPABASE_SERVICE_ROLE_KEY:'fixture-service',SUPABASE_ANON_KEY:'fixture-public',QUATA_WEB_REGISTRATION_PEPPER:'fixture-pepper-'.repeat(4),QUATA_RECOVERY_SECRET_WRITE_ENABLED:String(enabled)};
  const admin={auth:{getUser:async()=>({data:{user:valid?{id:'auth'}:null},error:null}),admin:{updateUserById:async()=>({data:{user:{id:'auth'}},error:null})}},from(table){
    assert.equal(table,'community_profiles'); let patch; const filters=[];
    const query={select(){return query},update(value){patch=value;return query},eq(...value){filters.push(value);return query},limit(){return query},maybeSingle:async()=>({data:profile,error:null}),then(resolve,reject){
      if(patch)writes.push({patch,filters});
      return Promise.resolve({data:patch?updated:actors,error:null}).then(resolve,reject);
    }}; return query;
  }};
  const context={createClient:()=>admin,crypto:webcrypto,TextEncoder,Uint8Array,Response,console:{error:()=>{}},Deno:{serve(fn){handler=fn},env:{get:key=>env[key]}}};
  runInNewContext(javascript,context);
  return {writes,profile,env,context,admin,async request(body,bearer=true){const response=await handler(new Request('https://example.invalid',{method:'POST',headers:{'content-type':'application/json',apikey:'fixture-public',...(bearer?{authorization:'Bearer fixture-token'}:{})},body:JSON.stringify(body)}));return {status:response.status,body:await response.json()};}};
}
const save={action:'update_recovery_secret',version:1,secret_question:'pet',secret_answer:'Luna',profile_id:'untrusted-other-actor'};
test('authenticated producer rejects absent/invalid bearer and wrong version without writes',async()=>{
  for(const [h,body,bearer,status] of [[harness(),save,false,401],[harness({valid:false}),save,true,401],[harness(),{...save,version:2},true,400]]){
    assert.equal((await h.request(body,bearer)).status,status);assert.equal(h.writes.length,0);
  }
});
test('zero or multiple linked profiles are rejected before mutation',async()=>{
  for(const actors of [[],[{id:'one'},{id:'two'}]]){const h=harness({actors});assert.equal((await h.request(save)).status,409);assert.equal(h.writes.length,0);}
});
test('producer preserves published plaintext format only on authenticated actor',async()=>{const h=harness(); const r=await h.request(save);assert.deepEqual(r,{status:200,body:{ok:true,version:1}});assert.equal(h.writes.length,1);assert.deepEqual(JSON.parse(JSON.stringify(h.writes[0])),{patch:{secret_question:'pet',secret_answer:'Luna'},filters:[['id','actor'],['auth_user_id','auth']]});});
test('missing flag, invalid fields and lost link fail closed',async()=>{const h=harness();delete h.env.QUATA_RECOVERY_SECRET_WRITE_ENABLED;assert.equal((await h.request(save)).status,503);assert.equal(h.writes.length,0);for(const patch of [{secret_answer:' '},{secret_question:42}]){const x=harness();assert.equal((await x.request({...save,...patch})).status,400);assert.equal(x.writes.length,0);}const lost=harness({updated:[]});assert.equal((await lost.request(save)).status,409);});
test('API key admission and public question response preserve deployed contract',async()=>{const h=harness();h.env.QUATA_AUTH_BRIDGE_API_KEY='other';assert.equal((await h.request(save)).status,401);assert.equal(h.writes.length,0);delete h.env.QUATA_AUTH_BRIDGE_API_KEY;assert.deepEqual(await h.request({action:'recovery_question',profile_id:'actor'},false),{status:200,body:{secret_question:'pet'}});});
test('producer and published consumer use the same answer; disabling producer preserves recovery',async()=>{const h=harness();assert.equal((await h.request(save)).status,200);Object.assign(h.profile,h.writes[0].patch);assert.equal(h.profile.secret_answer.trim().toLowerCase(),save.secret_answer.trim().toLowerCase());h.writes.length=0;h.env.QUATA_RECOVERY_SECRET_WRITE_ENABLED='false';assert.equal((await h.request(save)).status,503);assert.equal(h.writes.length,0);h.context.ensureAuthUser=async()=> 'auth';const r=await h.context.handlePasswordReset({admin:h.admin,profile:h.profile,payload:{secret_answer:'LUNA',new_password:'fixture-new-password'},serviceRoleKey:'fixture-service'});assert.equal(r.status,200);assert.match(h.writes[0].patch.pass_hash,/^[0-9a-f]{64}$/);});
test('legacy wrong answer cannot change password and reset implementation is byte-identical to v21',async()=>{const method=s=>s.slice(s.indexOf('async function handlePasswordReset('),s.indexOf('async function findProfile('));assert.equal(createHash('sha256').update(method(source)).digest('hex'),'166512959814a61b894da144073c574808a3854c4840bc5185ec5ee5b8b27884');const h=harness();const r=await h.context.handlePasswordReset({admin:h.admin,profile:h.profile,payload:{secret_answer:'wrong',new_password:'fixture-new-password'},serviceRoleKey:'fixture-service'});assert.equal(r.status,401);assert.equal(h.writes.length,0);});
