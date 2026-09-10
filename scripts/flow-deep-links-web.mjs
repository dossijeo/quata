import {createHash} from "node:crypto";
import {readFile,readdir} from "node:fs/promises";
import {execFileSync} from "node:child_process";
import path from "node:path";
import {runDeepLinkChatTrial} from "./flow-deep-links-chat-trial.mjs";
import {createDeepLinkWebTrial} from "./e2e-fixtures/chat-deep-link-web.mjs";
import {deepLinkFixtureTermsVersion} from "./e2e-fixtures/chat-deep-link-profile.mjs";
const hash=value=>createHash("sha256").update(value).digest("hex");

export async function deepLinkDatabaseFingerprint(client) {
  const functions=await client.query(`select n.nspname,p.proname,pg_get_function_identity_arguments(p.oid) as args,
    md5(pg_get_functiondef(p.oid)) as md5 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where p.prokind='f' and (p.proname like 'quata_chat_%' or p.proname in ('quata_has_accepted_ugc_terms','quata_accept_ugc_terms') or p.oid in
      (select tgfoid from pg_trigger where not tgisinternal and tgrelid in
        ('auth.users'::regclass,'public.profiles'::regclass,'public.community_profiles'::regclass,'public.chat_threads'::regclass,
         'public.chat_messages'::regclass,'public.chat_participants'::regclass,'public.ugc_terms_acceptances'::regclass))) order by n.nspname,p.proname,args`);
  const constraints=await client.query(`select conrelid::regclass::text as child,confrelid::regclass::text as parent,
    conname,pg_get_constraintdef(oid) as definition from pg_constraint where contype='f' and confrelid in
    ('auth.users'::regclass,'public.profiles'::regclass,'public.community_profiles'::regclass,
     'public.chat_threads'::regclass,'public.chat_messages'::regclass) order by child,conname`);
  const triggers=await client.query(`select tgrelid::regclass::text as relation,tgname,tgenabled,pg_get_triggerdef(oid) as definition
    from pg_trigger where not tgisinternal and tgrelid in ('auth.users'::regclass,'public.profiles'::regclass,'public.community_profiles'::regclass,
      'public.chat_threads'::regclass,'public.chat_messages'::regclass,'public.chat_participants'::regclass,
      'public.ugc_terms_acceptances'::regclass) order by relation,tgname`);
  return hash(JSON.stringify({functions:functions.rows,constraints:constraints.rows,triggers:triggers.rows}));
}
export async function deepLinkDistributionFingerprint(distribution) {
  const files=[];
  async function visit(directory,relative="") {
    for(const entry of (await readdir(directory,{withFileTypes:true})).sort((a,b)=>a.name.localeCompare(b.name))) {
      const name=relative?`${relative}/${entry.name}`:entry.name;
      if(entry.isDirectory())await visit(path.join(directory,entry.name),name);
      else if(entry.isFile())files.push([name,hash(await readFile(path.join(directory,entry.name)))]);
      else throw Error("deep_link_distribution_unexpected_entry");
    }
  }
  await visit(path.resolve(distribution));return hash(JSON.stringify(files));
}

// Called from the private local coordinator; no CLI arguments/environment contain
// Admin credentials. It performs no deployment, flag changes or existing-user login.
export async function executeDeepLinkWebTrial({client,serviceKey,chromium,chrome,root,distribution,
  privateDirectory,outputDirectory,supabaseCli,expected,authenticationMode,sessionMode,targetMode}) {
  const backendUrl="https://yrrlankpwmhluexshxnw.supabase.co";
  const publicSource=await readFile(path.join(root,"core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt"),"utf8");
  const publicKey=/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(publicSource)?.[1];
  if(!publicKey?.startsWith("sb_publishable_") || typeof serviceKey!=="string" || serviceKey.length<32 ||
      !/^[0-9a-f]{40}$/.test(expected?.productSha??"") ||
      !/^[0-9a-f]{64}$/.test(expected?.databaseFingerprint??"") || !/^[0-9a-f]{64}$/.test(expected?.distributionFingerprint??""))throw Error("deep_link_web_configuration_invalid");
  let pending=0,uncertain=false;
  const adminRequest=async({method,path:requestPath,body})=>{
    if(method!=="POST" || requestPath!=="/auth/v1/admin/users")throw Error("deep_link_admin_scope_invalid");
    pending++;
    try {
      const response=await fetch(backendUrl+requestPath,{method,headers:{apikey:serviceKey,Authorization:`Bearer ${serviceKey}`,
        "content-type":"application/json"},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
      const result={status:response.status,body:await response.json()};
      if(result.status!==200)uncertain=true;
      return result;
    } catch {uncertain=true;throw Error("deep_link_admin_transport_uncertain");}
    finally {pending--;}
  };
  const preflight=async()=>{
    const termsSource=await readFile(path.join(root,"core/src/commonMain/kotlin/com/quata/core/moderation/ModerationModels.kt"),"utf8");
    if(/CurrentUgcTermsVersion\s*=\s*"([^"]+)"/.exec(termsSource)?.[1]!==deepLinkFixtureTermsVersion)return false;
    const diff=execFileSync("git",["diff",expected.productSha,"--","web","feature","core","app","ios-shared",
      "build.gradle.kts","settings.gradle.kts","gradle","third_party",
      ":(exclude,glob)**/src/commonTest/**"],{cwd:root,encoding:"utf8",windowsHide:true});
    if(diff.trim())return false;
    if(await deepLinkDistributionFingerprint(distribution)!==expected.distributionFingerprint)return false;
    const functions=JSON.parse(execFileSync(supabaseCli,["functions","list","--project-ref","yrrlankpwmhluexshxnw","--output","json"],{cwd:root,encoding:"utf8",windowsHide:true,timeout:30000}));
    for(const wanted of expected.edgeFunctions??[]) {
      const actual=functions.find(row=>row.slug===wanted.slug);
      if(!actual||actual.status!=="ACTIVE"||actual.version!==wanted.version||actual.ezbr_sha256!==wanted.ezbr_sha256)return false;
    }
    if(!expected.edgeFunctions?.some(row=>row.slug==="quata-auth-bridge")||!expected.edgeFunctions?.some(row=>row.slug==="quata-push-dispatch"))return false;
    await client.query("begin read only");
    try {return await deepLinkDatabaseFingerprint(client)===expected.databaseFingerprint;}
    finally {await client.query("rollback");}
  };
  const ui=createDeepLinkWebTrial({chromium,chrome,distribution,outputDirectory,backendUrl,publicKey,authenticationMode,sessionMode,targetMode});
  const report=await runDeepLinkChatTrial({client,privateDirectory,backendUrl,publicKey,adminRequest,preflight,ui,sessionMode,targetMode,
    transportSettled:async()=>pending===0&&!uncertain&&ui.operationsSettled()});
  return {...report,uiDiagnostics:ui.diagnostics(),productSha:expected.productSha,distributionFingerprint:expected.distributionFingerprint};
}
