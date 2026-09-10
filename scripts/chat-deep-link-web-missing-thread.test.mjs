import test from "node:test";
import assert from "node:assert/strict";
import {createRequire} from "node:module";
import {createServer} from "node:http";
import {mkdir,mkdtemp,writeFile,rm} from "node:fs/promises";
import path from "node:path";
import {createDeepLinkWebTrial} from "./e2e-fixtures/chat-deep-link-web.mjs";
const require=createRequire(import.meta.url);
const modulePath=process.env.QUATA_TEST_PLAYWRIGHT_MODULE,chrome=process.env.QUATA_TEST_CHROME;
const root=path.resolve("build-reports/flow-deep-links/local-web-missing-thread-tests");
for(const kind of ["expected-rejection","network-failure","private-content","technical-error"])test(`Chrome missing thread ${kind}`,{skip:!modulePath||!chrome,timeout:45000},async()=>{
  await mkdir(root,{recursive:true});const directory=await mkdtemp(path.join(root,"test-"));
  const backend=createServer((req,res)=>{
    const cleanup=req.url.endsWith("quata_chat_cleanup_empty_private_thread");
    res.writeHead(req.method==="OPTIONS"?200:kind==="network-failure"?503:cleanup?200:403,{"Access-Control-Allow-Origin":"*",
      "Access-Control-Allow-Headers":"content-type","Access-Control-Allow-Methods":"POST,OPTIONS","Cross-Origin-Resource-Policy":"cross-origin","Content-Type":"application/json"})
      .end(JSON.stringify(cleanup?{deleted:false,thread_id:999,reason:"not_participant"}:{code:"42501",message:"profile is not a participant of this thread"}));
  });
  await new Promise(resolve=>backend.listen(0,"127.0.0.1",resolve));const backendUrl=`http://127.0.0.1:${backend.address().port}`;let adapter;
  try {
    await writeFile(path.join(directory,"index.html"),`<!doctype html><html><body><main></main><script>
      const rpc=name=>fetch('${backendUrl}/rest/v1/rpc/'+name,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({p_actor_profile_id:'owned',p_thread_id:999})});
      async function render(){const detail=location.hash.includes('chat-sb%3A999');document.documentElement.setAttribute('data-quata-shell-route',detail?'chat/sb:999':'chat');
        document.querySelector('main').innerHTML=detail?'<p>No se pudieron cargar los mensajes.</p><button>Reintentar mensajes</button><button id="chat.back">Back</button>${kind==="private-content"?'<button id="chat.message.456">Private</button>':''}${kind==="technical-error"?'<p>web_postgrest_rlsdenied:postgrest_rpc_http_403</p>':''}':'Conversation list';
        if(detail){document.getElementById('chat.back').onclick=()=>{rpc('quata_chat_cleanup_empty_private_thread');location.hash='chat';};await rpc('quata_chat_get_thread');await rpc('quata_chat_mark_thread_read');}}
      addEventListener('hashchange',render);render();</script></body></html>`);
    adapter=createDeepLinkWebTrial({chromium:require(modulePath).chromium,chrome,distribution:directory,outputDirectory:path.join(directory,"ui"),backendUrl,publicKey:"public",targetMode:"missing-thread"});
    const operation=adapter.run({session:{profileId:"owned",expiresAt:2000000000},clientInstanceId:"synthetic",target:{threadId:"999",messageId:"888",ownedThreadId:"123"},body:"Synthetic"});
    if(kind==="expected-rejection") {const result=await operation;assert.equal(result.passed,true);assert.equal(result.observations.length,2);assert.equal(adapter.operationsSettled(),true);
      for(const row of result.observations){assert.equal(row.read.rejectedReads,1);assert.equal(row.selectedEpisodes,0);assert.equal(row.read.responses.quata_chat_cleanup_empty_private_thread,1);}}
    else {await assert.rejects(operation,/deep_link_web_missing_thread_(read|failure_ui)_failed/);if(kind==="network-failure")assert.equal(adapter.operationsSettled(),false);}
  } finally {await adapter?.close();backend.closeAllConnections();await new Promise(resolve=>backend.close(resolve));if(path.dirname(directory)!==root)throw Error("unsafe_cleanup");await rm(directory,{recursive:true,force:true});}
});
