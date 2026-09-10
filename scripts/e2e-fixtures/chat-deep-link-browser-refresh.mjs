function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});promise.catch(()=>{});return {promise,resolve,reject};}

// Arming precedes navigation. Delivery waits for the private response checkpoint;
// receipt verification remains live afterwards and gates PASS and cleanup.
export function createDeepLinkBrowserRefresh({backendUrl,publicKey,observeRefresh,timeoutMs=25000}) {
  const endpoint=new URL("/auth/v1/token?grant_type=refresh_token",backendUrl).href;
  if(typeof observeRefresh!=="function" || !Number.isFinite(timeoutMs) || timeoutMs<=0 || timeoutMs>25000)
    throw Error("deep_link_browser_refresh_configuration_invalid");
  const ready=deferred(),http=deferred();
  let attempts=0,observerActive=false,forwarded=false,verified=false,delivered=false,failed=false,closed=false,uncertain=false;
  let armed=false,prepared=false,expected,route,response,operation,handlers=0;
  const started=performance.now(),timings={};
  const mark=key=>{timings[key]=Math.round(performance.now()-started);};
  async function bounded(promise){let timer;try{return await Promise.race([promise,new Promise((_,reject)=>{
    timer=setTimeout(()=>reject(Error("timeout")),timeoutMs);
  })]);}finally{clearTimeout(timer);}}
  function stop(){closed=true;http.reject(Error("closed"));}
  const api={
    async prepare(){
      if(armed||closed)throw Error("deep_link_browser_refresh_already_armed");
      armed=true;observerActive=true;mark("arming");
      operation=(async()=>{
        try {
          const result=await observeRefresh(async(url,options)=>{
            if(closed||expected||url.href!==endpoint||options.method!=="POST")throw Error();
            const payload=JSON.parse(options.body);
            if(Object.keys(payload).length!==1||typeof payload.refresh_token!=="string")throw Error();
            expected=payload;prepared=true;mark("prepared");ready.resolve();
            return http.promise;
          },async()=>{
            mark("responseJournaled");
            if(closed||!route||!response){failed=true;return;}
            try{await route.fulfill({response});delivered=true;mark("delivered");}
            catch{failed=true;}
          });
          verified=result?.verified===true;
          if(!verified)failed=true;else mark("verified");
        }catch{failed=true;if(forwarded)uncertain=true;}
        finally{observerActive=false;ready.reject(Error("observer_finished"));}
      })();
      try{await bounded(ready.promise);}
      catch{failed=true;stop();throw Error("deep_link_browser_refresh_prepare_failed");}
    },
    async handle(incoming){
      attempts++;mark("request");
      if(closed||!prepared||attempts!==1){failed=true;await incoming.abort().catch(()=>{});return;}
      route=incoming;
      handlers++;
      try{
        const request=route.request(),payload=request.postDataJSON();
        if(request.url()!==endpoint||request.method()!=="POST"||!payload||Object.keys(payload).length!==1||
            payload.refresh_token!==expected.refresh_token)throw Error();
        const headers=await request.allHeaders();
        if(closed||headers.apikey!==publicKey)throw Error();
        forwarded=true;mark("forwarded");
        response=await route.fetch({maxRedirects:0,maxRetries:0,timeout:15000});mark("response");
        http.resolve({status:response.status(),json:()=>response.json()});
        await bounded(operation);
      }catch{
        failed=true;if(forwarded&&!verified)uncertain=true;
        stop();await incoming.abort().catch(()=>{});
      }finally{handlers--;}
    },
    async finish(){if(!operation)return false;if(attempts===0){failed=true;stop();}try{await bounded(operation);}catch{failed=true;stop();}return api.passed();},
    close(){stop();},
    operationsSettled(){return !handlers&&!observerActive&&!uncertain;},
    diagnostics(){return {attempts,verified,delivered,failed,timings:{...timings}};},
    passed(){return attempts===1&&verified&&delivered&&!failed&&!observerActive;},
  };
  return api;
}
