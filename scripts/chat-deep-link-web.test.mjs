import test from "node:test";
import assert from "node:assert/strict";
import {createRequire} from "node:module";
import {createServer} from "node:http";
import {mkdir,mkdtemp,writeFile,rm,access} from "node:fs/promises";
import path from "node:path";
import {createDeepLinkWebTrial} from "./e2e-fixtures/chat-deep-link-web.mjs";
const require=createRequire(import.meta.url);
const modulePath=process.env.QUATA_TEST_PLAYWRIGHT_MODULE;
const chrome=process.env.QUATA_TEST_CHROME;
const root=path.resolve("build-reports/flow-deep-links/local-web-adapter-tests");

test("stalled diagnostic evaluation still reaches context cleanup",{timeout:10000},async()=>{
  await mkdir(root,{recursive:true});const dir=await mkdtemp(path.join(root,"test-"));
  let closed=false;
  const page={on(){},goto:async()=>{throw Error("synthetic navigation failure");},
    evaluate:()=>new Promise(()=>{}),screenshot:async()=>{}};
  const context={route:async()=>{},on(){},addInitScript:async()=>{},newPage:async()=>page,close:async()=>{closed=true;}};
  const adapter=createDeepLinkWebTrial({chromium:{launch:async()=>({newContext:async()=>context,close:async()=>{}})},
    chrome:"unused",distribution:dir,outputDirectory:dir,backendUrl:"https://example.test",publicKey:"synthetic"});
  try {
    await assert.rejects(adapter.run({session:{},clientInstanceId:"synthetic",target:{threadId:"1",messageId:"2"},body:"synthetic"}),/deep_link_web_open_failed/);
    assert.equal(closed,true);assert.equal(adapter.diagnostics()[0].observationUnavailable,true);
  } finally {
    await adapter.close();if(path.dirname(path.resolve(dir))!==root)throw Error("unsafe_test_cleanup");await rm(dir,{recursive:true,force:true});
  }
});

// Synthetic HTML exercises the runner, never stands in for Qüata acceptance.
for(const {status,missingAnchor} of [{status:200},{status:502},{status:200,missingAnchor:true}])test(`real Chrome adapter POST ${status}, missing anchor ${!!missingAnchor}`,{skip:!modulePath||!chrome,timeout:60000},async()=>{
  await access(chrome);const {chromium}=require(modulePath);
  await mkdir(root,{recursive:true});const dir=await mkdtemp(path.join(root,"test-"));
  let posts=0;
  const backend=createServer((req,res)=>{posts++;res.writeHead(status,{"Access-Control-Allow-Origin":"*","Cross-Origin-Resource-Policy":"cross-origin"}).end("synthetic response");});
  await new Promise(resolve=>backend.listen(0,"127.0.0.1",resolve));
  const backendUrl=`http://127.0.0.1:${backend.address().port}`;
  let adapter;
  try {
    const html=`<!doctype html><html><head><meta name="quata-supabase-url" content=""><meta name="quata-supabase-publishable-key" content=""></head><body><main></main><script>
      function render(){
        const detail=location.hash.includes('chat-sb%3A123');
        document.documentElement.setAttribute('data-quata-shell-route',detail?'chat/sb:123':'chat');
        document.querySelector('main').innerHTML=detail?'<div id="chat.message.456">Synthetic message</div><button id="chat.back">Back</button>':'Conversation list';
        if(detail){document.documentElement.setAttribute('data-quata-chat-focused-message-selected','456');
          setTimeout(()=>document.documentElement.removeAttribute('data-quata-chat-focused-message-selected'),300);
          document.getElementById('chat.back').onclick=()=>location.hash='chat';}
      }
      addEventListener('hashchange',render);render();
      fetch('${backendUrl}/probe',{method:'POST',body:'synthetic'}).catch(()=>{});
      </script></body></html>`;
    await writeFile(path.join(dir,"index.html"),html);
    adapter=createDeepLinkWebTrial({chromium,chrome,distribution:dir,outputDirectory:path.join(dir,"screenshots"),backendUrl,publicKey:"synthetic"});
    const operation=adapter.run({session:{accessToken:"synthetic",refreshToken:"synthetic",webSessionToken:"synthetic",profileId:"synthetic",expiresAt:2000000000},clientInstanceId:"synthetic",target:{threadId:"123",messageId:missingAnchor?"999":"456"},body:"Synthetic message"});
    if(missingAnchor) {
      await assert.rejects(operation,{message:"deep_link_web_message_anchor_failed"});
      const diagnostic=adapter.diagnostics()[0];
      assert.equal(diagnostic.stage,"message_anchor");assert.equal(diagnostic.expectedRouteReached,true);
      await access(path.join(dir,"screenshots",diagnostic.screenshot));
      await adapter.close();assert.equal(adapter.operationsSettled(),true);return;
    }
    const result=await operation;
    await adapter.close();
    assert.equal(result.passed,true);assert.equal(result.observations.length,2);
    assert.equal(result.observations[1].sameDocument,true);
    assert.ok(posts>=2);assert.equal(adapter.operationsSettled(),status===200);
  } finally {
    await adapter?.close().catch(()=>{});
    backend.closeAllConnections();await new Promise(resolve=>backend.close(resolve));
    if(path.dirname(path.resolve(dir))!==root)throw Error("unsafe_test_cleanup");
    await rm(dir,{recursive:true,force:true});
  }
});
