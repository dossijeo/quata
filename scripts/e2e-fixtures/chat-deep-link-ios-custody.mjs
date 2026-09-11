const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const identity=["runId","profileId","authUserId","authSessionId"];
const sessionFields=[...identity,"accessToken","refreshToken","expiresAt","email","displayName","isOfficial"];

// Caller holds the run lock and simulator lease. execute must finish the exact
// selected XCTest and stop its passive host before resolving, then return only
// its receipt. No automatic retry: a lost response preserves the started entry.
export async function runIosDeepLinkSessionStep({journal,input,execute}) {
  try {
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
      if(entry.iosSession!==undefined||entry.refreshAttempt!==undefined||entry.revocation!==undefined)throw Error();
      const body=entry.privateLoginResponse?.body;
      if(entry.privateLoginResponse?.status!==200||body?.session?.access_token!==input.accessToken||
         body?.session?.refresh_token!==input.refreshToken||body?.session?.expires_at!==input.expiresAt)throw Error();
      entry.iosSession={install:{input:structuredClone(input),started:true,verified:false}};
    } else {
      const installed=entry.iosSession?.install;
      if(installed?.verified!==true||entry.iosSession.clear!==undefined||installed.input.stepId===input.stepId||
         sessionFields.some(key=>installed.input[key]!==input[key]))throw Error();
      entry.iosSession.clear={input:structuredClone(input),started:true,verified:false};
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
    const attempt=updated[0].iosSession?.[input.stage];
    if(attempt?.started!==true||attempt.verified!==false||
       ["stage","stepId",...sessionFields].some(key=>attempt.input[key]!==input[key]))throw Error();
    attempt.verified=true;
    await journal.checkpoint(current.state);
    return {stage:input.stage,verified:true};
  } catch {throw Error("deep_link_ios_custody_unresolved");}
}

export function iosDeepLinkCustodySettled(entry) {
  if(entry.iosSession===undefined)return true;
  const {install,clear}=entry.iosSession??{};
  return install?.verified===true&&clear?.verified===true&&install.started===true&&clear.started===true&&
    install.input?.stage==="install"&&clear.input?.stage==="clear"&&
    uuid.test(install.input.stepId)&&uuid.test(clear.input.stepId)&&install.input.stepId!==clear.input.stepId&&
    identity.every(key=>install.input[key]===entry[key])&&sessionFields.every(key=>clear.input[key]===install.input[key]);
}
