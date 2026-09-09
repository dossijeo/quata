// Adapter for an owned localhost Playwright page. No SQL producer or secret captures.
// The coordinator owns browser/server lifecycle and journals tickets before login.
export function createRecoveryWebProduct({page,record,backendOrigin,verifyActor,closeResources,timeout=45000}) {
  if (!page || typeof verifyActor !== "function" || typeof closeResources !== "function") throw Error("recovery_web_dependencies_required");
  const origin=new URL(backendOrigin).origin;
  const pending=new Set();let uncertain=false,saveCount=0,closed=false;
  const relevant=request=>new URL(request.url()).origin===origin && !["GET","HEAD","OPTIONS"].includes(request.method());
  const requested=request=>{if(relevant(request))pending.add(request);};
  const finished=request=>{pending.delete(request);};
  const failed=request=>{if(pending.delete(request))uncertain=true;};
  const crashed=()=>{uncertain=true;};
  page.on("request",requested);page.on("requestfinished",finished);page.on("requestfailed",failed);page.on("crash",crashed);
  const wait=(fn,arg)=>page.waitForFunction(fn,arg,{timeout});
  const evaluate=async(fn,arg)=>{
    let timer;
    try {
      return await Promise.race([page.evaluate(fn,arg),new Promise((_,reject)=>{
        timer=setTimeout(()=>{uncertain=true;reject(Error("recovery_web_bridge_timeout"));},timeout);
      })]);
    } finally {clearTimeout(timer);}
  };
  const operation=fn=>async(...args)=>{
    try {
      if(closed)throw Error("recovery_web_closed");
      const url=new URL(page.url());
      if(!["localhost","127.0.0.1"].includes(url.hostname) || url.searchParams.get("quata-recovery-secret-e2e")!=="1") throw Error("recovery_web_local_opt_in_required");
      return await fn(...args);
    } catch {uncertain=true;throw Error("recovery_web_operation_failed");}
  };
  return Object.freeze({
    login:operation(async(password,ticket)=>{
      if(typeof ticket?.clientInstanceId!=="string" || ticket.clientInstanceId.length<8)throw Error("recovery_web_ticket_required");
      await wait(()=>globalThis.__quataAuthE2eProduct?.version===1);
      const result=await evaluate(async({countryCode,phone,password,clientInstanceId})=>{
        localStorage.setItem("quata_web_client_instance_id",clientInstanceId);
        return globalThis.__quataAuthE2eProduct.login(countryCode,phone,password);
      },{countryCode:record.countryCode,phone:record.phone,password,clientInstanceId:ticket.clientInstanceId});
      if(result!=="authenticated")throw Error("recovery_web_login_failed");
      await wait(profileId=>document.documentElement.getAttribute("data-quata-ugc-terms-profile-id")===profileId,record.profileId);
      const credentials=await evaluate(()=>({
        profileId:localStorage.getItem("quata_web_user_id"),
        accessToken:localStorage.getItem("quata_web_access_token"),
        webSessionToken:localStorage.getItem("quata_web_session_token"),
      }));
      try {
        if(credentials.profileId!==record.profileId || !credentials.accessToken || !credentials.webSessionToken ||
            await verifyActor(record,ticket,credentials)!==true)throw Error("recovery_web_actor_mismatch");
      } finally {
        credentials.accessToken=null;credentials.webSessionToken=null;
      }
      return true;
    }),
    openAccount:operation(async()=>{
      await evaluate(()=>{location.hash="profile";});
      await wait(()=>globalThis.__quataRecoverySecretE2eProduct?.version===1);
      await evaluate(()=>globalThis.__quataRecoverySecretE2eProduct.open());
      await wait(()=>globalThis.__quataRecoverySecretE2eProduct?.snapshot().visible===true);
    }),
    configureSecret:operation(async(question,answer)=>{
      await evaluate(({question,answer})=>globalThis.__quataRecoverySecretE2eProduct.configure(question,answer),{question,answer});
      await wait(question=>{
        const state=globalThis.__quataRecoverySecretE2eProduct?.snapshot();
        return state?.visible===true && state.question===question && state.answerEmpty===false && state.saving===false && state.saved===false && state.failed===false;
      },question);
    }),
    saveSecret:operation(async()=>{
      if(saveCount++)throw Error("recovery_web_duplicate_save");
      await evaluate(()=>globalThis.__quataRecoverySecretE2eProduct.save());
      await wait(()=>{
        const state=globalThis.__quataRecoverySecretE2eProduct?.snapshot();
        return state?.visible===true && state.answerEmpty===true && state.saving===false && state.saved===true && state.failed===false;
      });
    }),
    readPermittedState:operation(()=>evaluate(()=>globalThis.__quataRecoverySecretE2eProduct.snapshot())),
    logout:operation(async()=>{
      const result=await evaluate(()=>globalThis.__quataAuthE2eProduct.logout());
      if(result!=="logged_out")throw Error("recovery_web_logout_failed");
      await wait(()=>!document.documentElement.hasAttribute("data-quata-ugc-terms-profile-id"));
    }),
    recoverPassword:operation(async(answer,password)=>{
      await evaluate(()=>globalThis.__quataAuthE2eProduct.openRecovery());
      await wait(()=>document.documentElement.getAttribute("data-quata-auth-destination")==="recovery");
      const question=await evaluate(({countryCode,phone})=>globalThis.__quataAuthE2eProduct.recoveryQuestion(countryCode,phone),{countryCode:record.countryCode,phone:record.phone});
      if(question!==record.temporaryQuestion)throw Error("recovery_web_question_mismatch");
      const result=await evaluate(({countryCode,phone,answer,password})=>globalThis.__quataAuthE2eProduct.resetPassword(countryCode,phone,answer,password),
        {countryCode:record.countryCode,phone:record.phone,answer,password});
      if(result!=="password_reset")throw Error("recovery_web_reset_failed");
    }),
    // Only this page's requests are covered. The backend adapter must separately
    // audit its own operations and actor ownership; false remains sticky.
    operationsSettled:()=>!uncertain && pending.size===0,
    async close(){
      if(closed)return true;
      if(await closeResources()!==true)return false;
      closed=true;
      page.off("request",requested);page.off("requestfinished",finished);page.off("requestfailed",failed);page.off("crash",crashed);
      return true;
    },
  });
}
