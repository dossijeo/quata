import test from "node:test";
import assert from "node:assert/strict";
import {createRequire} from "node:module";
import {createServer} from "node:http";
import {mkdir,mkdtemp,writeFile,rm} from "node:fs/promises";
import path from "node:path";
import {createDeepLinkWebTrial} from "./e2e-fixtures/chat-deep-link-web.mjs";
const require=createRequire(import.meta.url);
const modulePath=process.env.QUATA_TEST_PLAYWRIGHT_MODULE,chrome=process.env.QUATA_TEST_CHROME;
const root=path.resolve("build-reports/flow-deep-links/local-web-auth-adapter-tests");

// A synthetic page tests runner mechanics only; never Qüata acceptance evidence.
for(const {mode,missingFocus=false,residualFocus=false,stalledObservation=false} of [
  {mode:"resume"},{mode:"cancel"},{mode:"resume",missingFocus:true},{mode:"cancel",residualFocus:true},{mode:"resume",stalledObservation:true},
])test(`Chrome anonymous ${mode}: missing focus ${missingFocus}, residual focus ${residualFocus}, stalled ${stalledObservation}`,
  {skip:!modulePath||!chrome,timeout:45000},async()=>{
    const {chromium}=require(modulePath);
    await mkdir(root,{recursive:true});const dir=await mkdtemp(path.join(root,"test-"));
    let posts=0,adapter;
    const backend=createServer((req,res)=>{
      res.setHeader("Access-Control-Allow-Origin","*");res.setHeader("Access-Control-Allow-Headers","content-type");
      if(req.method==="OPTIONS")return res.writeHead(204).end();
      posts++;res.setHeader("Content-Type","application/json");
      res.end(JSON.stringify({profile:{id:"owned-profile"},session:{access_token:"synthetic"}}));
    });
    await new Promise(resolve=>backend.listen(0,"127.0.0.1",resolve));
    const backendUrl=`http://127.0.0.1:${backend.address().port}`;
    try {
      await writeFile(path.join(dir,"index.html"),`<!doctype html><html><head><meta charset="utf-8"></head><body><main></main><script>
        const root=document.documentElement,main=document.querySelector('main');let pending=false;
        root.setAttribute('data-quata-shell-route','feed');
        main.innerHTML='<div id="quata-splash-root" style="position:fixed;inset:0;background:white">Splash</div>';
        setTimeout(()=>main.innerHTML='<p>Feed</p>',1000);
        const openLogin=()=>{root.setAttribute('data-quata-auth-destination','login');main.innerHTML='<p>Login</p>';};
        addEventListener('hashchange',()=>{
          if(!location.hash.startsWith('#chat-'))return;
          pending=true;
          main.innerHTML='<div class="gate" style="position:fixed;left:55px;top:200px;width:320px;height:250px"><h2>Únete a QÜATA para participar</h2><button>Crear cuenta</button><button id="login">Ya tengo cuenta</button></div>';
          document.getElementById('login').onclick=event=>{event.stopPropagation();openLogin();};
        });
        document.addEventListener('click',event=>{const gate=document.querySelector('.gate');if(gate&&!gate.contains(event.target)){pending=false;main.innerHTML='<p>Feed</p>';}});
        globalThis.__quataAuthE2eProduct={version:1,openLogin,login:async(countryCode,phone,password)=>{
          if(localStorage.getItem('quata_web_access_token'))throw Error('unexpected injected session');
          await fetch('${backendUrl}/functions/v1/quata-auth-bridge',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({action:'web_login',country_code:countryCode,phone_local:phone,password,client_instance_id:localStorage.getItem('quata_web_client_instance_id')})});
          root.removeAttribute('data-quata-auth-destination');
          if(pending){root.setAttribute('data-quata-shell-route','chat/sb:123');main.innerHTML='<button id="chat.message.456" aria-label="Deep link fixture: Synthetic message" style="width:200px;height:100px">Synthetic message</button>';if(!${missingFocus})root.setAttribute('data-quata-chat-focused-message-selected','456');}
          else {root.setAttribute('data-quata-shell-route','feed');main.innerHTML='<p>Feed</p>';if(${residualFocus})root.setAttribute('data-quata-chat-focused-message-selected','456');}
          return 'authenticated';
        }};
      </script></body></html>`);
      const browserDriver=stalledObservation?{launch:async options=>{
        const browser=await chromium.launch(options),createContext=browser.newContext.bind(browser);
        browser.newContext=async options=>{
          const context=await createContext(options),createPage=context.newPage.bind(context);
          context.newPage=async()=>{
            const page=await createPage(),evaluate=page.evaluate.bind(page);
            page.evaluate=(fn,arg)=>String(fn).includes("episodes:")?new Promise(()=>{}):evaluate(fn,arg);
            return page;
          };
          return context;
        };
        return browser;
      }}:chromium;
      adapter=createDeepLinkWebTrial({chromium:browserDriver,chrome,distribution:dir,outputDirectory:path.join(dir,"captures"),backendUrl,publicKey:"synthetic",authenticationMode:mode,
        authObservationTimeoutMs:stalledObservation?1000:45000});
      const target={threadId:"123",messageId:"456"};
      await adapter.prepareLogin({target,body:"Synthetic message",clientInstanceId:"owned-client"});
      const response=await adapter.requestLogin(`${backendUrl}/functions/v1/quata-auth-bridge`,{method:"POST",body:JSON.stringify({
        action:"web_login",profile_id:"owned-profile",country_code:"240",phone_local:"123456",password:"synthetic",client_instance_id:"owned-client"})});
      assert.equal(response.status,200);assert.equal((await response.json()).profile.id,"owned-profile");
      const result=await adapter.run({target});
      assert.equal(result.passed,!missingFocus&&!residualFocus&&!stalledObservation,JSON.stringify(result));assert.equal(result.mode,mode);
      if(result.passed) {
        assert.equal(result.route,mode==="resume"?"chat/sb:123":"feed");
        assert.equal(result.selectedEpisodes,mode==="resume"?1:0);
      } else assert.equal(result.failureCode,"deep_link_web_auth_observation_failed");
      assert.equal(posts,1);
      await adapter.close();assert.equal(adapter.operationsSettled(),true);adapter=undefined;
    } finally {
      await adapter?.close();backend.closeAllConnections();await new Promise(resolve=>backend.close(resolve));
      if(path.dirname(path.resolve(dir))!==root)throw Error("unsafe_test_cleanup");
      await rm(dir,{recursive:true,force:true});
    }
  });
