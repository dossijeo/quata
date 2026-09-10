// Owns the single paused product request. No tokens enter diagnostics. The
// observer journals intent before invoking the supplied transport callback.
export function createDeepLinkBrowserRefresh({backendUrl,publicKey,observeRefresh,timeoutMs=25000}) {
  const endpoint=new URL("/auth/v1/token?grant_type=refresh_token",backendUrl).href;
  if(typeof observeRefresh!=="function" || !Number.isFinite(timeoutMs) || timeoutMs<=0 || timeoutMs>25000)
    throw Error("deep_link_browser_refresh_configuration_invalid");
  let attempts=0,pending=false,observerActive=false,forwarded=false,verified=false,delivered=false,failed=false,closed=false,uncertain=false;
  return {
    async handle(route) {
      attempts++;
      if(closed || attempts!==1) {failed=true;await route.abort().catch(()=>{});return;}
      pending=true;
      let timer,stopped=false;
      const operation=async()=>{
        const request=route.request();
        if(request.url()!==endpoint || request.method()!=="POST")throw Error();
        const payload=request.postDataJSON();
        if(!payload || Object.keys(payload).length!==1 || typeof payload.refresh_token!=="string")throw Error();
        const headers=await request.allHeaders();
        if(headers.apikey!==publicKey)throw Error();
        let response;
        const result=await observeRefresh(async(url,options)=>{
          if(stopped || closed || forwarded || url.href!==endpoint || options.method!=="POST" ||
              JSON.parse(options.body).refresh_token!==payload.refresh_token || options.signal?.aborted)throw Error();
          forwarded=true;
          response=await route.fetch({maxRedirects:0,maxRetries:0,timeout:15000});
          return {status:response.status(),json:()=>response.json()};
        });
        if(stopped || closed)throw Error();
        if(result?.verified!==true || !forwarded || !response)throw Error();
        verified=true;
        await route.fulfill({response});
        delivered=true;
      };
      try {
        // An observation timeout is not completion of SQL/journal work in the
        // observer. Retain its live state until that exact promise settles.
        observerActive=true;
        const observed=operation().finally(()=>{observerActive=false;});
        await Promise.race([observed,new Promise((_,reject)=>{
          timer=setTimeout(()=>{stopped=true;reject(Error());},timeoutMs);
        })]);
      } catch {
        failed=true;if(forwarded && !verified)uncertain=true;
        await route.abort().catch(()=>{});
      } finally {stopped=true;clearTimeout(timer);pending=false;}
    },
    close(){closed=true;},
    operationsSettled(){return !pending&&!observerActive&&!uncertain;},
    diagnostics(){return {attempts,verified,delivered,failed};},
    passed(){return attempts===1&&verified&&delivered&&!failed;},
  };
}
