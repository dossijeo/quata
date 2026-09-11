import {createHash} from "node:crypto";
import {readFile} from "node:fs/promises";
import {execFileSync} from "node:child_process";
import path from "node:path";
import {deepLinkDatabaseFingerprint} from "./flow-deep-links-web.mjs";
import {runDeepLinkChatTrial} from "./flow-deep-links-chat-trial.mjs";
import {openIosDeepLinkChannel} from "./e2e-fixtures/chat-deep-link-ios-channel.mjs";
import {createIosDeepLinkUi} from "./e2e-fixtures/chat-deep-link-ios.mjs";
import {deepLinkFixtureTermsVersion} from "./e2e-fixtures/chat-deep-link-profile.mjs";
const hash=value=>createHash("sha256").update(value).digest("hex");

export async function executeDeepLinkIosTrial({client,serviceKey,root,macRoot,products,privateDirectory,supabaseCli,expected,targetMode}) {
  if(targetMode!==undefined&&targetMode!=="missing-thread")throw Error("deep_link_ios_configuration_invalid");
  const backendUrl="https://yrrlankpwmhluexshxnw.supabase.co";
  const publicSource=await readFile(path.join(root,"core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt"),"utf8");
  const publicKey=/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(publicSource)?.[1];
  if(!publicKey?.startsWith("sb_publishable_")||typeof serviceKey!=="string"||serviceKey.length<32||
     !/^[0-9a-f]{40}$/.test(expected?.productSha??"")||!/^[0-9a-f]{64}$/.test(expected?.databaseFingerprint??"")||
     !expected.nativeBuild||expected.nativeBuild.productSha!==expected.productSha)throw Error("deep_link_ios_configuration_invalid");
  const channel=await openIosDeepLinkChannel({root:macRoot,products});
  let pending=0,uncertain=false,preflightPhase="not_started";
  const adminRequest=async({method,path:requestPath,body})=>{
    if(method!=="POST"||requestPath!=="/auth/v1/admin/users")throw Error("deep_link_admin_scope_invalid");
    pending++;
    try {
      const response=await fetch(backendUrl+requestPath,{method,headers:{apikey:serviceKey,Authorization:`Bearer ${serviceKey}`,
        "content-type":"application/json"},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
      const result={status:response.status,body:await response.json()};
      if(result.status!==200)uncertain=true;
      return result;
    }catch {uncertain=true;throw Error("deep_link_admin_transport_uncertain");}
    finally {pending--;}
  };
  const preflight=async()=>{
    preflightPhase="terms";
    const terms=await readFile(path.join(root,"core/src/commonMain/kotlin/com/quata/core/moderation/ModerationModels.kt"),"utf8");
    if(/CurrentUgcTermsVersion\s*=\s*"([^"]+)"/.exec(terms)?.[1]!==deepLinkFixtureTermsVersion)return false;
    preflightPhase="local_product";
    const diff=execFileSync("git",["diff",expected.productSha,"--","feature","core","app","ios-shared","iosApp/iosApp",
      "iosApp/iosShareQueue","iosApp/iosShareExtension","build.gradle.kts","settings.gradle.kts","gradle","third_party",
      ":(exclude,glob)**/src/commonTest/**"],{cwd:root,encoding:"utf8",windowsHide:true});
    if(diff.trim())return false;
    preflightPhase="native_build";
    const native=JSON.parse(execFileSync("ssh",["-o","BatchMode=yes","-o","ConnectTimeout=15","quata-mac",
      "python3",macRoot+"/scripts/flow-deep-links-ios-manifest.py","--root",macRoot,"--products",products],
      {encoding:"utf8",windowsHide:true,timeout:180000}));
    if(Object.keys(native).sort().join(",")!==Object.keys(expected.nativeBuild).sort().join(",")||
       Object.keys(native).some(key=>native[key]!==expected.nativeBuild[key])||native.productSourcesClean!==true||
       ![hash(backendUrl),hash(new URL(backendUrl).href)].includes(native.builtBackendUrlSha256)||native.builtPublicKeySha256!==hash(publicKey))return false;
    for(const [key,relative] of [["sessionSourceSha256","iosApp/iosAppTests/QuataIosDeepLinkSessionTests.swift"],
      ["observerSourceSha256","iosApp/iosAppUITests/QuataIosExternalChatLinkUITests.swift"],
      ["workerSha256","scripts/flow-deep-links-ios-worker.py"]])if(native[key]!==hash(await readFile(path.join(root,relative))))return false;
    preflightPhase="edge_functions";
    const functions=JSON.parse(execFileSync(supabaseCli,["functions","list","--project-ref","yrrlankpwmhluexshxnw","--output","json"],
      {cwd:root,encoding:"utf8",windowsHide:true,timeout:30000}));
    for(const wanted of expected.edgeFunctions??[]){
      const actual=functions.find(row=>row.slug===wanted.slug);
      if(!actual||actual.status!=="ACTIVE"||actual.version!==wanted.version||actual.ezbr_sha256!==wanted.ezbr_sha256)return false;
    }
    if(!["quata-auth-bridge","quata-push-dispatch"].every(slug=>expected.edgeFunctions?.some(row=>row.slug===slug)))return false;
    preflightPhase="database";
    await client.query("begin read only");
    try {const verified=await deepLinkDatabaseFingerprint(client)===expected.databaseFingerprint;if(verified)preflightPhase="verified";return verified;}
    finally {await client.query("rollback");}
  };
  try {
    const report=await runDeepLinkChatTrial({client,privateDirectory,backendUrl,publicKey,adminRequest,preflight,
      ui:createIosDeepLinkUi({channel,targetMode}),targetMode,transportSettled:async()=>pending===0&&!uncertain});
    return {...report,preflightPhase,productSha:expected.productSha,nativeBuild:expected.nativeBuild};
  }finally {if(!channel.settled())channel.abort();}
}
