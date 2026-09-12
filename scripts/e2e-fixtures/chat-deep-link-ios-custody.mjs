const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const identity=["runId","profileId","authUserId","authSessionId"];
const sessionFields=[...identity,"accessToken","refreshToken","expiresAt","email","displayName","isOfficial"];

// Caller holds the run lock and device lease. execute must finish the exact
// selected native test and stop its passive host before resolving, then return only
// its receipt. No automatic retry: a lost response preserves the started entry.
export async function runIosDeepLinkSessionStep({journal,input,execute,platform="ios"}) {
  const custodyKey=platform==="android"?"androidSession":"iosSession";
  try {
    if(!["ios","android"].includes(platform))throw Error();
    if(typeof execute!=="function"||!["install","clear"].includes(input.stage)||
       ![...identity,"stepId"].every(key=>uuid.test(input[key]))||
       ["accessToken","refreshToken","email","displayName"].some(key=>typeof input[key]!=="string"||!input[key])||
       !Number.isSafeInteger(input.expiresAt)||input.expiresAt<=0||typeof input.isOfficial!=="boolean")throw Error();
    const saved=await journal.read();
    if(identity.slice(0,3).some(key=>saved[key]!==input[key]))throw Error();
    const matches=saved.state.sessions.filter(entry=>entry.authSessionId===input.authSessionId);
    if(matches.length!==1)throw Error();
    const entry=matches[0];
    if(identity.some(key=>entry[key]!==input[key])||entry.purpose!=="deep_link"||
       entry.requestStarted!==true||!uuid.test(entry.webSessionId)||entry.noSession!==undefined)throw Error();
    if(input.stage==="install") {
      if(entry[custodyKey]!==undefined||entry.refreshAttempt!==undefined||entry.revocation!==undefined)throw Error();
      const body=entry.privateLoginResponse?.body;
      if(entry.privateLoginResponse?.status!==200||body?.session?.access_token!==input.accessToken||
         body?.session?.refresh_token!==input.refreshToken||body?.session?.expires_at!==input.expiresAt)throw Error();
      entry[custodyKey]={install:{input:structuredClone(input),started:true,verified:false}};
    } else {
      const installed=entry[custodyKey]?.install;
      if(installed?.verified!==true||entry[custodyKey].clear!==undefined||installed.input.stepId===input.stepId||
         sessionFields.some(key=>installed.input[key]!==input[key]))throw Error();
      entry[custodyKey].clear={input:structuredClone(input),started:true,verified:false};
    }
    // The private input survives a coordinator/SSH interruption for reconciliation.
    await journal.checkpoint(saved.state);
    const receipt=await execute(structuredClone(input));
    if(!receipt||Object.keys(receipt).sort().join(",")!=="runId,stage,stepId,verified"||
       ["runId","stepId","stage"].some(key=>receipt[key]!==input[key])||receipt.verified!==true)throw Error();
    const current=await journal.read();
    if(identity.slice(0,3).some(key=>current[key]!==input[key]))throw Error();
    const updated=current.state.sessions.filter(item=>identity.every(key=>item[key]===input[key]));
    if(updated.length!==1)throw Error();
    const attempt=updated[0][custodyKey]?.[input.stage];
    if(attempt?.started!==true||attempt.verified!==false||
       ["stage","stepId",...sessionFields].some(key=>attempt.input[key]!==input[key]))throw Error();
    attempt.verified=true;
    await journal.checkpoint(current.state);
    return {stage:input.stage,verified:true};
  } catch {throw Error(platform==="android"?"deep_link_android_custody_unresolved":"deep_link_ios_custody_unresolved");}
}

export function iosDeepLinkCustodySettled(entry,platform="ios") {
  if(!["ios","android"].includes(platform))return false;
  if(!nativeLoginCustodySettled(entry,platform))return false;
  const custodyKey=platform==="android"?"androidSession":"iosSession";
  if(entry[custodyKey]===undefined)return true;
  const {install,clear}=entry[custodyKey]??{};
  return install?.verified===true&&clear?.verified===true&&install.started===true&&clear.started===true&&
    install.input?.stage==="install"&&clear.input?.stage==="clear"&&
    uuid.test(install.input.stepId)&&uuid.test(clear.input.stepId)&&install.input.stepId!==clear.input.stepId&&
    identity.every(key=>install.input[key]===entry[key])&&sessionFields.every(key=>clear.input[key]===install.input[key]);
}

// Reuse the same journal protocol while keeping Android receipts distinct from iOS evidence.
export const runAndroidDeepLinkCustodyStep = args => runIosDeepLinkSessionStep({...args,platform:"android"});
export function androidDeepLinkCustodySettled(entry) {
  return iosDeepLinkCustodySettled(entry,"android");
}

function nativeLoginCustodySettled(entry,platform) {
  const native=entry[platform==="android"?"androidNativeLogin":"iosNativeLogin"];
  if(native===undefined)return true;
  const {read,clear}=native??{},session=read?.privateSession;
  // iOS retains the private response until the coordinator has durably saved it
  // and acknowledged that exact read. A clear alone cannot settle a lost ACK.
  if(platform==="ios" && !(read?.acknowledgment?.started===true&&read.acknowledgment.verified===true&&
    read.acknowledgment.runId===entry.runId&&read.acknowledgment.stepId===read.input?.stepId))return false;
  return entry.kind==="native"&&entry.requestStarted===true&&native.observationVerified===true&&
    read?.started===true&&read.verified===true&&clear?.started===true&&clear.verified===true&&
    read.input?.stage==="read-owned"&&clear.input?.stage==="clear"&&
    uuid.test(read.input.stepId)&&uuid.test(clear.input.stepId)&&read.input.stepId!==clear.input.stepId&&
    identity.slice(0,3).every(key=>read.input[key]===entry[key]&&clear.input[key]===entry[key])&&
    identity.slice(1).every(key=>session?.[key]===entry[key])&&
    sessionFields.filter(key=>key!=="runId").every(key=>clear.input[key]===session?.[key]);
}
