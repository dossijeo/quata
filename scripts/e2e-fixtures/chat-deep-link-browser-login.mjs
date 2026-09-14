// Private adapter: never returns credentials or backend bodies in diagnostics.
// The coordinator's journal/ownership audit MUST precede requestLogin.
export function createDeepLinkBrowserLogin({page,backendUrl,clientInstanceId,timeoutMs=30000}) {
  const origin=new URL(backendUrl).origin;
  if(!page || typeof clientInstanceId!=="string" || clientInstanceId.length<8 ||
      !Number.isFinite(timeoutMs) || timeoutMs<=0 || timeoutMs>30000)throw Error("deep_link_browser_login_configuration_invalid");
  let used=false,settled=true,productAuthenticated=false;
  return {
    async requestLogin(url,options) {
      if(used)throw Error("deep_link_browser_login_already_attempted");
      let expected;
      try {expected=JSON.parse(options.body);}catch{throw Error("deep_link_browser_login_request_invalid");}
      if(new URL(url).origin!==origin || new URL(url).pathname!=="/functions/v1/quata-auth-bridge" ||
          options.method!=="POST" || expected.action!=="web_login" || expected.client_instance_id!==clientInstanceId ||
          ["profile_id","country_code","phone_local","password"].some(key=>typeof expected[key]!=="string" || !expected[key])) {
        throw Error("deep_link_browser_login_request_invalid");
      }
      if(options.signal?.aborted)throw Error("deep_link_browser_login_aborted");
      used=true;settled=false;
      let count=0,mismatch=false,timer,abort;
      const isLogin=request=>{
        try {return request.method()==="POST" && new URL(request.url()).origin===origin &&
          new URL(request.url()).pathname==="/functions/v1/quata-auth-bridge" && request.postDataJSON()?.action==="web_login";}
        catch{return false;}
      };
      const onRequest=request=>{
        if(!isLogin(request))return;
        count++;
        const actual=request.postDataJSON();
        if(["country_code","phone_local","password","client_instance_id"].some(key=>actual[key]!==expected[key]) ||
            (actual.profile_id!==undefined && actual.profile_id!==expected.profile_id))mismatch=true;
      };
      page.on("request",onRequest);
      try {
        // Register observation before invoking the product repository. No token
        // injection, restore, second login request, or navigation to the target.
        const response=page.waitForResponse(value=>isLogin(value.request()),{timeout:timeoutMs})
          .then(async value=>({status:value.status(),body:await value.json()}));
        const invocation=Promise.resolve().then(()=>page.evaluate(async ({countryCode,phone,password})=>{
          const bridge=globalThis.__quataAuthE2eProduct;
          if(bridge?.version!==1 || typeof bridge.login!=="function")throw Error("product_login_unavailable");
          return await bridge.login(countryCode,phone,password);
        },{countryCode:expected.country_code,phone:expected.phone_local,password:expected.password}));
        const deadline=new Promise((_,reject)=>{
          timer=setTimeout(()=>reject(Error("deep_link_browser_login_unsettled")),timeoutMs);
          abort=()=>reject(Error("deep_link_browser_login_unsettled"));
          options.signal?.addEventListener("abort",abort,{once:true});
          if(options.signal?.aborted)abort();
        });
        const [received,invoked]=await Promise.race([Promise.allSettled([response,invocation]),deadline]);
        if(received.status!=="fulfilled" || count!==1 || mismatch)throw Error("deep_link_browser_login_response_uncertain");
        // Preserve a real response for the journal even if the coordinator/UI
        // reports failure after HTTP completed. Acceptance checks that separately.
        productAuthenticated=invoked.status==="fulfilled" && invoked.value==="authenticated";
        settled=true;
        return {status:received.value.status,json:async()=>received.value.body};
      } catch {throw Error("deep_link_browser_login_response_uncertain");}
      finally {
        clearTimeout(timer);options.signal?.removeEventListener("abort",abort);
        page.off("request",onRequest);
      }
    },
    operationsSettled(){return settled;},
    diagnostics(){return {attempted:used,settled,productAuthenticated};},
  };
}
