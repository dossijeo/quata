import test from "node:test";
import assert from "node:assert/strict";
import {createRequire} from "node:module";
import {createServer} from "node:http";
import {mkdir,mkdtemp,writeFile,rm} from "node:fs/promises";
import path from "node:path";
import {createDeepLinkWebTrial} from "./e2e-fixtures/chat-deep-link-web.mjs";
const require=createRequire(import.meta.url);
const modulePath=process.env.QUATA_TEST_PLAYWRIGHT_MODULE,chrome=process.env.QUATA_TEST_CHROME;
const root=path.resolve("build-reports/flow-deep-links/local-web-refresh-tests");

test("refresh mode rejects incompatible login mode before browser startup",()=>{
  assert.throws(()=>createDeepLinkWebTrial({sessionMode:"refresh",authenticationMode:"resume"}),/session_mode_invalid/);
});

// Synthetic HTML exercises the interaction/accounting mechanism, not product acceptance.
for(const deliveryMode of ["cold","warm"])for(const variant of (deliveryMode==="warm"?["renew","skip","changed_token","corrupt_expiry"]:["renew","skip"]))test(`Chrome ${deliveryMode} local-expiry refresh: ${variant}`,{skip:!modulePath||!chrome,timeout:30000},async()=>{
  const skipRefresh=variant==="skip",invalidBootstrap=["changed_token","corrupt_expiry"].includes(variant);
  await mkdir(root,{recursive:true});const directory=await mkdtemp(path.join(root,"test-"));
  let posts=0,observations=0;
  const backend=createServer((req,res)=>{
    if(req.method==="POST")posts++;
    res.writeHead(200,{"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"apikey,content-type",
      "Access-Control-Allow-Methods":"POST,OPTIONS","Cross-Origin-Resource-Policy":"cross-origin","Content-Type":"application/json"})
      .end(JSON.stringify({access_token:"renewed-private",refresh_token:"rotated-private",expires_at:Math.floor(Date.now()/1000)+3600}));
  });
  await new Promise(resolve=>backend.listen(0,"127.0.0.1",resolve));
  const backendUrl=`http://127.0.0.1:${backend.address().port}`;
  let adapter;
  try {
    await writeFile(path.join(directory,"index.html"),`<!doctype html><html><head><meta name="quata-supabase-url" content=""><meta name="quata-supabase-publishable-key" content=""></head><body><main></main><script>
      async function render(){
        await renew();
        if(location.hash==='#feed'){
          if(${variant==="changed_token"})localStorage.quata_web_access_token='unexpected-token';
          if(${variant==="corrupt_expiry"})localStorage.quata_web_expires_at='corrupt';
        }
        const detail=location.hash.includes('chat-sb%3A123');
        document.documentElement.setAttribute('data-quata-shell-route',detail?'chat/sb:123':location.hash==='#feed'?'feed':'chat');
        document.querySelector('main').innerHTML=detail?'<button id="chat.message.456" aria-label="Deep link fixture: Synthetic message" style="width:200px;height:60px"></button><button id="chat.back">Back</button>':'Conversation list';
        if(detail){document.documentElement.setAttribute('data-quata-chat-focused-message-selected','456');
          setTimeout(()=>document.documentElement.removeAttribute('data-quata-chat-focused-message-selected'),600);
          document.getElementById('chat.back').onclick=()=>location.hash='chat';}
      }
      async function renew(){
        if(!${skipRefresh} && Number(localStorage.quata_web_expires_at)<=Date.now()/1000){
          const response=await fetch('${backendUrl}/auth/v1/token?grant_type=refresh_token',{method:'POST',headers:{apikey:'public','content-type':'application/json'},body:JSON.stringify({refresh_token:localStorage.quata_web_refresh_token}),signal:AbortSignal.timeout(700)});
          const renewed=await response.json();localStorage.quata_web_expires_at=String(renewed.expires_at);
          localStorage.quata_web_access_token=renewed.access_token;localStorage.quata_web_refresh_token=renewed.refresh_token;
        }
      }
      addEventListener('hashchange',render);render();
      </script></body></html>`);
    adapter=createDeepLinkWebTrial({chromium:require(modulePath).chromium,chrome,distribution:directory,
      outputDirectory:path.join(directory,"screenshots"),backendUrl,publicKey:"public",sessionMode:"refresh",sessionDeliveryMode:deliveryMode});
    const operation=adapter.run({session:{accessToken:"old-private",refreshToken:"original-private",webSessionToken:"web-private",profileId:"owned",expiresAt:2000000000},
      clientInstanceId:"synthetic-client",target:{threadId:"123",messageId:"456"},body:"Synthetic message",
      observeRefresh:async(transport,journaled)=>{
        observations++;
        const response=await transport(new URL("/auth/v1/token?grant_type=refresh_token",backendUrl),{
          method:"POST",body:JSON.stringify({refresh_token:"original-private"}),
        });
        assert.equal(response.status,200);assert.equal((await response.json()).refresh_token,"rotated-private");
        await journaled();
        // Verification deliberately outlasts the synthetic product deadline.
        await new Promise(resolve=>setTimeout(resolve,1200));
        return {verified:true};
      }});
    if(invalidBootstrap)await assert.rejects(operation,/deep_link_web_open_failed/);
    else if(skipRefresh)await assert.rejects(operation,/deep_link_web_reload_failed/);
    else {
      const report=await operation;assert.equal(report.passed,true);assert.equal(report.observations.length,1);
      const {timings,...refresh}=report.observations[0].refresh;
      assert.deepEqual(refresh,{attempts:1,verified:true,delivered:true,failed:false});
      assert.ok(timings.prepared<=timings.request&&timings.delivered<=timings.verified);
      assert.ok(timings.verified-timings.delivered>=1000);
      assert.equal(report.observations[0].reloadedRoute,"chat");
      assert.equal(report.observations[0].mode,deliveryMode);
      if(deliveryMode==="warm"){
        assert.equal(report.observations[0].sameDocument,true);
        assert.equal(report.observations[0].expirySetImmediatelyBeforeDelivery,true);
        assert.equal(report.observations[0].refreshAttemptsBeforeDelivery,0);
      }
    }
    await adapter.close();assert.equal(adapter.operationsSettled(),true);
    assert.equal(posts,skipRefresh||invalidBootstrap?0:1);assert.equal(observations,1);
  } finally {
    await adapter?.close().catch(()=>{});backend.closeAllConnections();await new Promise(resolve=>backend.close(resolve));
    if(path.dirname(path.resolve(directory))!==root)throw Error("unsafe_test_cleanup");
    await rm(directory,{recursive:true,force:true});
  }
});
