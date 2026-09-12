import {readFile} from "node:fs/promises";
import {execFileSync} from "node:child_process";
import path from "node:path";
import {deepLinkDatabaseFingerprint} from "./flow-deep-links-web.mjs";
import {runDeepLinkChatTrial} from "./flow-deep-links-chat-trial.mjs";
import {openAndroidDeepLinkSessionChannel,runAndroidDeepLinkSessionStep} from "./e2e-fixtures/chat-deep-link-android-session-step.mjs";
import {runNativeDeepLinkChatTrial} from "./flow-deep-links-native-chat-trial.mjs";
import {createAndroidNativeDeepLinkUi} from "./e2e-fixtures/chat-deep-link-android-native-ui.mjs";
import {createAndroidDeepLinkUi} from "./e2e-fixtures/chat-deep-link-android.mjs";
import {deepLinkFixtureTermsVersion} from "./e2e-fixtures/chat-deep-link-profile.mjs";

export const isDeepLinkAndroidAvd = output => /^QuataDeepLinksApi35\nOK\n?$/.test(output.replaceAll("\r",""));

export async function executeDeepLinkAndroidTrial({client,serviceKey,root,privateDirectory,supabaseCli,expected,
  adb,serial,leasePath,evidenceDirectory,targetMode,nativeLoginMode}) {
  if(nativeLoginMode!==undefined&&(!['cold','warm'].includes(nativeLoginMode)||targetMode!==undefined))throw Error("deep_link_android_configuration_invalid");
  if(targetMode!==undefined&&!["missing-thread","missing-message"].includes(targetMode))throw Error("deep_link_android_configuration_invalid");
  const backendUrl="https://yrrlankpwmhluexshxnw.supabase.co";
  const publicSource=await readFile(path.join(root,"core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt"),"utf8");
  const publicKey=/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(publicSource)?.[1];
  if(!publicKey?.startsWith("sb_publishable_")||typeof serviceKey!=="string"||serviceKey.length<32||
    !/^[0-9a-f]{40}$/.test(expected?.productSha??"")||
    [expected.databaseFingerprint,expected.androidApkSha256,expected.senderTestApkSha256,expected.custodyTestApkSha256].some(value=>!/^[0-9a-f]{64}$/.test(value??"")))
    throw Error("deep_link_android_configuration_invalid");
  const local=(file,args)=>execFileSync(file,args,{cwd:root,encoding:"utf8",windowsHide:true,timeout:30000});
  if(!isDeepLinkAndroidAvd(local(adb,["-s",serial,"emu","avd","name"])))
    throw Error("deep_link_android_wrong_avd");
  let pending=0,uncertain=false,preflightPhase="not_started";
  const adminRequest=async({method,path:requestPath,body})=>{
    if(method!=="POST"||requestPath!=="/auth/v1/admin/users")throw Error("deep_link_admin_scope_invalid");
    pending++;
    try {
      const response=await fetch(backendUrl+requestPath,{method,headers:{apikey:serviceKey,Authorization:`Bearer ${serviceKey}`,
        "content-type":"application/json"},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
      const result={status:response.status,body:await response.json()};if(result.status!==200)uncertain=true;return result;
    } catch {uncertain=true;throw Error("deep_link_admin_transport_uncertain");} finally {pending--;}
  };
  const preflight=async()=>{
    preflightPhase="terms";
    const terms=await readFile(path.join(root,"core/src/commonMain/kotlin/com/quata/core/moderation/ModerationModels.kt"),"utf8");
    if(/CurrentUgcTermsVersion\s*=\s*"([^"]+)"/.exec(terms)?.[1]!==deepLinkFixtureTermsVersion)return false;
    preflightPhase="product_inputs";
    const diff=local("git",["diff",expected.productSha,"--","feature","core","app","build.gradle.kts","settings.gradle.kts","gradle","third_party",
      ":(exclude)app/src/androidTest",":(exclude)app/build.gradle.kts"]);
    if(diff.trim())return false;
    const original=local("git",["show",`${expected.productSha}:app/build.gradle.kts`]).replaceAll("\r\n","\n");
    const current=(await readFile(path.join(root,"app/build.gradle.kts"),"utf8")).replaceAll("\r\n","\n");
    const optIn='testInstrumentationRunner = if (providers.gradleProperty("quataDeepLinkCustody").orNull == "true")\n            "com.quata.core.navigation.DeepLinkSessionCustodyRunner" else "androidx.test.runner.AndroidJUnitRunner"';
    if(current!==original&&current.replace(optIn,'testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"')!==original)return false;
    preflightPhase="installed_apks";
    for(const [pkg,wanted] of [["com.quata",expected.androidApkSha256],["com.quata.deeplinksender.test",expected.senderTestApkSha256],["com.quata.test",expected.custodyTestApkSha256]]) {
      const location=local(adb,["-s",serial,"shell","pm","path",pkg]).trim();
      if(!/^package:\/data\/app\/[A-Za-z0-9_.=~+/-]+\/base\.apk$/.test(location))return false;
      if(local(adb,["-s",serial,"shell","sha256sum",location.slice(8)]).split(/\s+/)[0]!==wanted)return false;
    }
    preflightPhase="public_domains";
    if(!/egquata.com: verified/.test(local(adb,["-s",serial,"shell","pm","get-app-links","com.quata"])))return false;
    preflightPhase="edge_functions";
    const functions=JSON.parse(local(supabaseCli,["functions","list","--project-ref","yrrlankpwmhluexshxnw","--output","json"]));
    for(const wanted of expected.edgeFunctions??[]) {
      const actual=functions.find(row=>row.slug===wanted.slug);
      if(!actual||actual.status!=="ACTIVE"||actual.version!==wanted.version||actual.ezbr_sha256!==wanted.ezbr_sha256)return false;
    }
    if(!["quata-auth-bridge","quata-push-dispatch"].every(slug=>expected.edgeFunctions?.some(row=>row.slug===slug)))return false;
    preflightPhase="database";await client.query("begin read only");
    try {const ok=await deepLinkDatabaseFingerprint(client)===expected.databaseFingerprint;if(ok)preflightPhase="verified";return ok;}
    finally {await client.query("rollback");}
  };
  // Verify the installed test APK before executing even its passive empty probe:
  // instrumentation shares the product UID and can access its session storage.
  if(!await preflight())throw Error("deep_link_android_preflight_identity_invalid");
  const channel=await openAndroidDeepLinkSessionChannel({adb,serial,leasePath,evidenceDirectory});
  try {
    if(nativeLoginMode!==undefined) {
      const report=await runNativeDeepLinkChatTrial({client,privateDirectory,backendUrl,publicKey,adminRequest,preflight,channel,
        mode:nativeLoginMode,ui:createAndroidNativeDeepLinkUi({adb,serial,evidenceDirectory}),
        sessionStep:input=>runAndroidDeepLinkSessionStep({adb,serial,input,logPath:path.join(evidenceDirectory,`native-session-${input.stepId}.log`)}),
        transportSettled:async()=>pending===0&&!uncertain});
      return {...report,preflightPhase,productSha:expected.productSha,androidApkSha256:expected.androidApkSha256,
        senderTestApkSha256:expected.senderTestApkSha256,custodyTestApkSha256:expected.custodyTestApkSha256};
    }
    const report=await runDeepLinkChatTrial({client,privateDirectory,backendUrl,publicKey,adminRequest,preflight,
      targetMode,ui:createAndroidDeepLinkUi({channel,adb,serial,evidenceDirectory,targetMode}),transportSettled:async()=>pending===0&&!uncertain});
    return {...report,preflightPhase,productSha:expected.productSha,androidApkSha256:expected.androidApkSha256,
      senderTestApkSha256:expected.senderTestApkSha256,custodyTestApkSha256:expected.custodyTestApkSha256};
  } finally {if(!channel.settled())channel.abort();}
}
