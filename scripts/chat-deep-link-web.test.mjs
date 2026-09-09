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

// Synthetic HTML exercises the runner, never stands in for Qüata acceptance.
for(const status of [200,502])test(`real Chrome adapter observes cold/warm focus with POST ${status}`,{skip:!modulePath||!chrome,timeout:60000},async()=>{
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
    const result=await adapter.run({session:{accessToken:"synthetic",refreshToken:"synthetic",webSessionToken:"synthetic",profileId:"synthetic",expiresAt:2000000000},clientInstanceId:"synthetic",target:{threadId:"123",messageId:"456"},body:"Synthetic message"});
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
