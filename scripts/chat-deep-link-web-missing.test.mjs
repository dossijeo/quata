import test from "node:test";
import assert from "node:assert/strict";
import {createRequire} from "node:module";
import {createServer} from "node:http";
import {mkdir,mkdtemp,writeFile,rm} from "node:fs/promises";
import path from "node:path";
import {createDeepLinkWebTrial} from "./e2e-fixtures/chat-deep-link-web.mjs";
const require=createRequire(import.meta.url);
const modulePath=process.env.QUATA_TEST_PLAYWRIGHT_MODULE,chrome=process.env.QUATA_TEST_CHROME;
const root=path.resolve("build-reports/flow-deep-links/local-web-missing-tests");
test("missing target mode excludes unrelated session/auth modes",()=>{
  for(const extra of [{sessionMode:"refresh"},{authenticationMode:"resume"},{targetMode:"unknown"}])
    assert.throws(()=>createDeepLinkWebTrial({targetMode:"missing-message",...extra}),/target_mode_invalid/);
});
for(const kind of ["success","network-error","wrong-focus","late-invalid-body"])test(`Chrome missing message ${kind}`,{skip:!modulePath||!chrome,timeout:45000},async()=>{
  await mkdir(root,{recursive:true});const directory=await mkdtemp(path.join(root,"test-"));
  const backend=createServer((req,res)=>{
    res.writeHead(kind==="network-error"?503:200,{"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"content-type",
      "Access-Control-Allow-Methods":"POST,OPTIONS","Cross-Origin-Resource-Policy":"cross-origin","Content-Type":"application/json"})
      .end(JSON.stringify({thread:{id:123},messages:[{id:456,thread_id:123}]}));
  });
  await new Promise(resolve=>backend.listen(0,"127.0.0.1",resolve));
  const backendUrl=`http://127.0.0.1:${backend.address().port}`;let adapter;
  try {
    await writeFile(path.join(directory,"index.html"),`<!doctype html><html><body><main></main><script>
      async function render(){const detail=location.hash.includes('chat-sb%3A123');
        document.documentElement.setAttribute('data-quata-shell-route',detail?'chat/sb:123':'chat');
        document.querySelector('main').innerHTML=detail?'<button id="chat.message.456" aria-label="Deep link fixture: Synthetic message" style="width:200px;height:60px"></button><button id="chat.back">Back</button>':'Conversation list';
        if(detail){sessionStorage.detailOpened='yes';document.getElementById('chat.back').onclick=()=>location.hash='chat';
          if(${kind==="wrong-focus"})document.documentElement.setAttribute('data-quata-chat-focused-message-selected','456');
          await fetch('${backendUrl}/rest/v1/rpc/quata_chat_get_thread',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({p_actor_profile_id:'owned',p_thread_id:123,p_limit:250,p_known_message_ids:[]})});}
        else if(${kind==="late-invalid-body"}&&sessionStorage.detailOpened==='yes')fetch('${backendUrl}/rest/v1/rpc/quata_chat_get_thread',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({p_actor_profile_id:'owned',p_thread_id:123,p_limit:250,p_known_message_ids:[],late:true})});}
      addEventListener('hashchange',render);render();</script></body></html>`);
    const chromium={launch:async options=>{
      const browser=await require(modulePath).chromium.launch(options);const newContext=browser.newContext.bind(browser);
      browser.newContext=async options=>{const context=await newContext(options);const on=context.on.bind(context);
        context.on=(event,handler)=>on(event,event==="requestfinished"?request=>{
          if(kind==="late-invalid-body"&&request.method()==="POST"&&request.postDataJSON()?.late) {
            const original=request.response.bind(request);
            request.response=async()=>{const response=await original();return new Proxy(response,{get(object,key){
              if(key==="json")return async()=>{await new Promise(resolve=>setTimeout(resolve,9000));return {thread:{id:123},messages:[]};};
              const value=Reflect.get(object,key);return typeof value==="function"?value.bind(object):value;
            }});};
          }
          return handler(request);
        }:handler);return context;};return browser;
    }};
    adapter=createDeepLinkWebTrial({chromium,chrome,distribution:directory,outputDirectory:path.join(directory,"ui"),backendUrl,publicKey:"public",targetMode:"missing-message"});
    const operation=adapter.run({session:{profileId:"owned",expiresAt:2000000000},clientInstanceId:"synthetic",target:{threadId:"123",messageId:"999",visibleMessageId:"456"},body:"Synthetic message"});
    if(kind==="success") {const result=await operation;assert.equal(result.passed,true);assert.equal(result.observations.length,2);for(const row of result.observations){assert.equal(row.selectedEpisodes,0);assert.equal(row.read.historyExhausted,true);}}
    else {
      await assert.rejects(operation,/deep_link_web_missing_message_read_failed/);
      // No earlier UI catch failed: the invalid body must be caught by the
      // post-drain check, after provisional UI success.
      if(kind==="late-invalid-body")assert.equal(adapter.diagnostics().length,0);
    }
  } finally {await adapter?.close();backend.closeAllConnections();await new Promise(resolve=>backend.close(resolve));if(path.dirname(directory)!==root)throw Error("unsafe_cleanup");await rm(directory,{recursive:true,force:true});}
});
