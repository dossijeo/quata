import test from "node:test";
import assert from "node:assert/strict";
import {createRequire} from "node:module";
import {createServer} from "node:http";
import {mkdir,mkdtemp,writeFile,rm} from "node:fs/promises";
import path from "node:path";
import {createDeepLinkWebTrial} from "./e2e-fixtures/chat-deep-link-web.mjs";
const require=createRequire(import.meta.url),modulePath=process.env.QUATA_TEST_PLAYWRIGHT_MODULE,chrome=process.env.QUATA_TEST_CHROME;
const root=path.resolve("build-reports/flow-deep-links/local-web-revoked-tests");

for(const variant of ["denied","private_flash","unverified"])test(`Chrome revoked session: ${variant}`,{skip:!modulePath||!chrome,timeout:30000},async()=>{
  await mkdir(root,{recursive:true});const dir=await mkdtemp(path.join(root,"test-"));let posts=0;
  const backend=createServer((req,res)=>{if(req.method==="POST")posts++;
    res.writeHead(req.method==="OPTIONS"?200:400,{"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"apikey,content-type",
      "Access-Control-Allow-Methods":"POST,OPTIONS","Cross-Origin-Resource-Policy":"cross-origin","Content-Type":"application/json"})
      .end(JSON.stringify({error_code:"refresh_token_not_found"}));
  });await new Promise(resolve=>backend.listen(0,"127.0.0.1",resolve));
  const backendUrl=`http://127.0.0.1:${backend.address().port}`;let adapter;
  try{
    await writeFile(path.join(dir,"index.html"),`<!doctype html><html><head><meta name="quata-supabase-url" content=""><meta name="quata-supabase-publishable-key" content=""></head><body><main></main><script>
      (async()=>{
        const r=await fetch('${backendUrl}/auth/v1/token?grant_type=refresh_token',{method:'POST',headers:{apikey:'public','content-type':'application/json'},body:JSON.stringify({refresh_token:localStorage.quata_web_refresh_token})});
        if(r.ok)throw Error('expected rejection');
        if(${variant==="private_flash"}){
          document.documentElement.setAttribute('data-quata-shell-route','chat/sb:123');
          document.documentElement.setAttribute('data-quata-chat-focused-message-selected','456');
          await new Promise(resolve=>setTimeout(resolve,50));
          document.documentElement.removeAttribute('data-quata-chat-focused-message-selected');
        }
        document.documentElement.setAttribute('data-quata-shell-route','feed');
        document.querySelector('main').innerHTML='<p>Feed</p><div><button>Crear cuenta</button><button>Ya tengo cuenta</button></div>';
      })();
      </script></body></html>`);
    adapter=createDeepLinkWebTrial({chromium:require(modulePath).chromium,chrome,distribution:dir,outputDirectory:path.join(dir,"ui"),backendUrl,publicKey:"public",sessionMode:"revoked"});
    const operation=adapter.run({session:{profileId:"owned",accessToken:"old-private",refreshToken:"original-private",webSessionToken:"web-private",expiresAt:2000000000},
      clientInstanceId:"synthetic-client",target:{threadId:"123",messageId:"456"},body:"Synthetic message",
      observeRefresh:async(transport,journaled)=>{
        const response=await transport(new URL("/auth/v1/token?grant_type=refresh_token",backendUrl),{method:"POST",body:JSON.stringify({refresh_token:"original-private"})});
        assert.equal(response.status,400);assert.equal((await response.json()).error_code,"refresh_token_not_found");
        await journaled();return {verified:true,rejected:variant!=="unverified"};
      }});
    if(variant==="denied"){
      const report=await operation;assert.equal(report.passed,true);assert.equal(report.observations[0].accessDenied,true);
      assert.equal(report.observations[0].refresh.rejected,true);assert.equal(report.observations[0].selectedEpisodes,0);
    }else await assert.rejects(operation,/deep_link_web_revoked_barrier_failed/);
    await adapter.close();assert.equal(posts,1);assert.equal(adapter.operationsSettled(),true);
  }finally{
    await adapter?.close().catch(()=>{});backend.closeAllConnections();await new Promise(resolve=>backend.close(resolve));
    if(path.dirname(path.resolve(dir))!==root)throw Error("unsafe_test_cleanup");await rm(dir,{recursive:true,force:true});
  }
});
