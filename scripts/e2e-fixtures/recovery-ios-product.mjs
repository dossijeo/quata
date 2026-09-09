import {randomUUID} from "node:crypto";

// runStep owns a fresh private directory and returns only after XCTest is terminal.
// releaseStep removes it after receipt verification (and durable bearer registration).
// Neither callback may log private inputs/receipts or retry an uncertain step.
export function createRecoveryIosProduct({record,displayName,questionLabel,runStep,releaseStep,verifyActor,verifyLogout,closeResources}) {
  if (![runStep,releaseStep,verifyActor,verifyLogout,closeResources].every(value=>typeof value==="function") ||
      !displayName || !questionLabel || record?.temporaryQuestion!=="madre") throw Error("recovery_ios_configuration_invalid");
  let phase="fresh",busy=false,uncertain=false,closed=false,closeResult=false,configured;
  const requireTrue=value=>{if(value!==true)throw Error("recovery_ios_step_unverified");};
  const exact=(value,expected)=>value && typeof value==="object" && !Array.isArray(value) &&
    Object.keys(value).sort().join(",")===Object.keys(expected).sort().join(",") &&
    Object.entries(expected).every(([key,item])=>value[key]===item);
  const step=async(stage,args,expected,accept)=>{
    const input={runId:record.runId,stepId:randomUUID(),stage,profileId:record.profileId,authUserId:record.authUserId,
      countryCode:record.countryCode,phone:record.phone,question:record.temporaryQuestion,displayName,questionLabel,...args};
    const result=await runStep(input);
    const receipt=result?.receipt;
    requireTrue(result?.terminal===true && result.exitCode===0 &&
      exact(receipt,{runId:input.runId,stepId:input.stepId,stage,profileId:input.profileId,authUserId:input.authUserId,result:receipt?.result}));
    if (accept) await accept(receipt.result);
    else requireTrue(exact(receipt.result,expected));
    requireTrue(await releaseStep(input));
    return receipt.result;
  };
  const operation=async(expected,next,action)=>{
    if(closed || uncertain || busy || phase!==expected)throw Error("recovery_ios_operation_unavailable");
    busy=true;
    try {const value=await action();phase=next;return value;}
    catch {uncertain=true;throw Error("recovery_ios_operation_unverified");}
    finally {busy=false;}
  };
  const identity=()=>step("identity",{}, {storedIdentityMatched:true});
  return Object.freeze({
    login:(password,ticket)=>operation("fresh","logged",async()=>{
      requireTrue(ticket?.kind==="native" && typeof ticket.ticketId==="string");
      await step("login",{password,ticketId:ticket.ticketId},null,async result=>{
        requireTrue(typeof result?.accessToken==="string" && !!result.accessToken &&
          exact(result,{ticketId:ticket.ticketId,accessToken:result.accessToken,sessionRestored:true}));
        const credentials={profileId:record.profileId,accessToken:result.accessToken};
        try {requireTrue(await verifyActor(record,ticket,credentials));}
        finally {credentials.accessToken=null;result.accessToken=null;}
      });
      return true;
    }),
    openAccount:()=>operation("logged","opened",async()=>{
      await identity();
      await step("open",{}, {accountVisible:true,answerEmpty:true,accountIdentityMatched:true});
      return true;
    }),
    // Keep configuration in memory. The combined UI configure/Save step runs only
    // from saveSecret, after the core has durably checkpointed before_save_secret.
    configureSecret:(question,answer)=>operation("opened","configured",async()=>{
      requireTrue(question===record.temporaryQuestion && answer===record.temporaryAnswer);
      configured={answer};return true;
    }),
    saveSecret:()=>operation("configured","saved",async()=>{
      await identity();
      try {await step("configure",configured,{saveDispatched:true,saveCount:1,accountIdentityMatched:true});}
      finally {configured=undefined;}
      return true;
    }),
    readPermittedState:()=>operation("saved","read",async()=>{
      await identity();
      await step("read",{}, {questionMatched:true,answerEmpty:true,accountIdentityMatched:true,
        accountVisible:true,saveEnabled:true,errorAbsent:true});
      // The core verifies persisted secret/non-secret state before invoking this.
      return {visible:true,question:record.temporaryQuestion,answerEmpty:true,saving:false,failed:false,saved:true};
    }),
    logout:()=>operation("read","logged-out",async()=>{
      await step("logout",{}, {sessionEmpty:true});
      // The repository clears Keychain before its asynchronous remote logout.
      // Require the coordinator to observe revocation of the exact journaled
      // producer session before starting recovery or reporting settled work.
      requireTrue(await verifyLogout(record));return true;
    }),
    recoverPassword:(answer,password)=>operation("logged-out","recovered",async()=>{
      requireTrue(answer===record.temporaryAnswer && password===record.temporaryPassword);
      await step("empty",{}, {sessionEmpty:true});
      await step("recover",{answer,password},{loginReturned:true,submitCount:1});return true;
    }),
    operationsSettled:()=>!uncertain && !busy,
    async close(){
      if(closed)return closeResult;
      if(busy)return false;
      closed=true;configured=undefined;
      let localEmpty=false;
      try {
        if(!uncertain){
          await step("clear-owned",{}, {sessionEmpty:true});
          await step("empty",{}, {sessionEmpty:true}); // Another fresh test-host process.
          localEmpty=true;
        }
      } catch {uncertain=true;}
      let resources=false;
      try {resources=(await closeResources())===true;}catch{/* Retain the journal. */}
      closeResult=localEmpty && resources && !uncertain;
      return closeResult;
    },
  });
}
