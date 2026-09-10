import {createServer} from "node:http";
import {readFile,stat,mkdir} from "node:fs/promises";
import path from "node:path";
import {createDeepLinkBrowserLogin} from "./chat-deep-link-browser-login.mjs";
import {createDeepLinkBrowserRefresh} from "./chat-deep-link-browser-refresh.mjs";
import {createMissingMessageReadObserver} from "./chat-deep-link-missing-message.mjs";
import {createMissingThreadReadObserver,missingThreadFunctions} from "./chat-deep-link-missing-thread.mjs";

export function createDeepLinkWebTrial({chromium,chrome,distribution,outputDirectory,backendUrl,publicKey,authenticationMode,sessionMode,sessionDeliveryMode,targetMode,authObservationTimeoutMs=45000}) {
  if(sessionDeliveryMode!==undefined&&(!sessionMode||!["cold","warm"].includes(sessionDeliveryMode)))throw Error("deep_link_web_session_delivery_invalid");
  if(targetMode!==undefined&&(!["missing-message","missing-thread"].includes(targetMode)||sessionMode!==undefined||authenticationMode!==undefined))throw Error("deep_link_web_target_mode_invalid");
  if(sessionMode!==undefined && (!["refresh","revoked"].includes(sessionMode) || authenticationMode!==undefined))throw Error("deep_link_web_session_mode_invalid");
  if(authenticationMode!==undefined && !["resume","cancel"].includes(authenticationMode))throw Error("deep_link_web_auth_mode_invalid");
  if(!Number.isFinite(authObservationTimeoutMs)||authObservationTimeoutMs<=0||authObservationTimeoutMs>45000)throw Error("deep_link_web_auth_timeout_invalid");
  const root=path.resolve(distribution),output=path.resolve(outputDirectory);
  let server,browser;
  let auth;
  let refresh;
  let networkUncertain=false;
  const networks=new Map();
  const failures=[];
  const backendOrigin=new URL(backendUrl).origin;
  async function trackContext(context,missingRead,missingThread) {
    const network={gated:false,pending:new Set(),refreshRequests:new Set()};networks.set(context,network);
    await context.route(`${backendOrigin}/**`,async route=>{
      if(network.gated)return route.abort(); // No request forwarded after shutdown starts.
      const request=route.request();
      const mutating=!["GET","HEAD","OPTIONS"].includes(request.method());
      if(mutating)network.pending.add(request);
      if(refresh && new URL(request.url()).pathname==="/auth/v1/token" && request.method()!=="OPTIONS") {
        // The specialized observer verifies expected rejections as well as
        // renewals. Its live operation, receipt and uncertainty gate settlement.
        network.refreshRequests.add(request);
        try{await refresh.handle(route);}finally{network.pending.delete(request);}
        return;
      }
      try {await route.continue();}catch{if(mutating){networkUncertain=true;network.pending.delete(request);}}
    });
    context.on("requestfinished",async request=>{
      if(network.refreshRequests.has(request))return;
      if(!network.pending.has(request))return;
      try {const response=await request.response();
        const functionName=new URL(request.url()).pathname.replace(/^\/rest\/v1\/rpc\//,"");
        if(missingThread&&missingThreadFunctions.has(functionName)) {
          if(!missingThread.observe({functionName,request:request.postDataJSON(),status:response?.status(),body:await response?.json()}))networkUncertain=true;
        } else if(!response||response.status()>=400)networkUncertain=true;
        if(missingRead&&new URL(request.url()).pathname==="/rest/v1/rpc/quata_chat_get_thread") {
          try {missingRead.observe({request:request.postDataJSON(),status:response?.status(),body:await response?.json()});}
          catch {missingRead.fail();}
        }
      }
      catch {networkUncertain=true;missingThread?.fail();}
      finally {network.pending.delete(request);}
    });
    context.on("requestfailed",request=>{
      if(missingRead&&new URL(request.url()).pathname==="/rest/v1/rpc/quata_chat_get_thread")missingRead.fail();
      if(missingThread&&missingThreadFunctions.has(new URL(request.url()).pathname.replace(/^\/rest\/v1\/rpc\//,"")))missingThread.fail();
      if(network.refreshRequests.has(request))return;
      if(network.pending.delete(request))networkUncertain=true;
    });
  }
  async function closeContext(context) {
    refresh?.close();
    const network=networks.get(context);
    if(network) {
      network.gated=true;
      const deadline=Date.now()+20000;
      while(network.pending.size&&Date.now()<deadline)await new Promise(resolve=>setTimeout(resolve,100));
      if(network.pending.size)networkUncertain=true;
    }
    await context.close();networks.delete(context);
  }
  const mime={".html":"text/html",".js":"text/javascript",".mjs":"text/javascript",".wasm":"application/wasm",
    ".json":"application/json",".css":"text/css",".png":"image/png",".svg":"image/svg+xml",".ttf":"font/ttf",".woff2":"font/woff2"};
  const attr=value=>value.replaceAll("&","&amp;").replaceAll('"',"&quot;");
  async function start() {
    await mkdir(output,{recursive:true});
    server=createServer(async(req,res)=>{
      try {
        if(!["GET","HEAD"].includes(req.method))return res.writeHead(405).end();
        const pathname=decodeURIComponent(new URL(req.url,"http://localhost").pathname);
        const file=path.resolve(root,"."+(pathname==="/"?"/index.html":pathname));
        if(!file.startsWith(root+path.sep)||!(await stat(file).catch(()=>null))?.isFile())return res.writeHead(404).end();
        let bytes=await readFile(file);
        if(file.endsWith("index.html"))bytes=bytes.toString()
          .replace(/(<meta name="quata-supabase-url" content=")[^"]*(">)/,(_,a,b)=>a+attr(backendUrl)+b)
          .replace(/(<meta name="quata-supabase-publishable-key" content=")[^"]*(">)/,(_,a,b)=>a+attr(publicKey)+b);
        res.writeHead(200,{"Content-Type":mime[path.extname(file)]??"application/octet-stream","Cache-Control":"no-store",
          "Cross-Origin-Opener-Policy":"same-origin","Cross-Origin-Embedder-Policy":"require-corp"}).end(req.method==="HEAD"?undefined:bytes);
      } catch {res.writeHead(500).end();}
    });
    await new Promise((resolve,reject)=>{server.once("error",reject);server.listen(0,"127.0.0.1",resolve);});
    browser=await chromium.launch({executablePath:chrome,headless:true,
      args:["--use-angle=swiftshader","--enable-unsafe-swiftshader","--force-renderer-accessibility"]});
    return `http://127.0.0.1:${server.address().port}`;
  }
  return {
    ...(authenticationMode?{
      async prepareLogin({target,body,clientInstanceId}) {
        if(auth)throw Error("deep_link_web_auth_already_prepared");
        const origin=await start();
        const context=await browser.newContext({locale:"es-ES",viewport:{width:430,height:930},serviceWorkers:"block"});
        auth={context,target,body,pageErrors:0,observation:{passed:false}};
        await trackContext(context);
        await context.addInitScript(id=>{
          localStorage.setItem("quata_web_client_instance_id",id);
          globalThis.__quataDeepLinkObserved=[];
          let previous=null;
          new MutationObserver(()=>{
            const selected=document.documentElement?.getAttribute("data-quata-chat-focused-message-selected")??null;
            if(selected!==previous){globalThis.__quataDeepLinkObserved.push({selected});previous=selected;}
          }).observe(document,{attributes:true,subtree:true,attributeFilter:["data-quata-chat-focused-message-selected"]});
        },clientInstanceId);
        const page=await context.newPage();auth.page=page;
        page.on("pageerror",()=>auth.pageErrors++);
        await page.goto(`${origin}/?quata-auth-e2e=1#feed`,{waitUntil:"domcontentloaded",timeout:60000});
        const splash=page.locator('[id="quata-splash-root"], [title="quata-splash-root"]');
        await splash.waitFor({state:"visible",timeout:20000});await splash.waitFor({state:"hidden",timeout:20000});
        auth.timeOrigin=await page.evaluate(()=>performance.timeOrigin);
        await page.evaluate(fragment=>{location.hash=fragment;},`#chat-${encodeURIComponent(`sb:${target.threadId}`)}?message=${encodeURIComponent(target.messageId)}`);
        const login=page.getByText("Ya tengo cuenta",{exact:true});
        await login.waitFor({state:"visible",timeout:15000});
        if(await page.evaluate(()=>Boolean(localStorage.getItem("quata_web_access_token"))))throw Error("deep_link_web_auth_not_anonymous");
        await page.screenshot({path:path.join(output,`web-chat-auth-${authenticationMode}-barrier.png`)});
        if(authenticationMode==="cancel") {
          const box=await login.evaluate(element=>{
            for(let parent=element.parentElement;parent;parent=parent.parentElement){
              const b=parent.getBoundingClientRect();
              if(parent.textContent.includes("Crear cuenta")&&parent.textContent.includes("Únete a QÜATA para participar")&&
                b.width<innerWidth&&b.height<innerHeight&&b.y>0)return {x:b.x,y:b.y,width:b.width,height:b.height};
            }
            return null;
          });
          if(!box)throw Error("deep_link_web_auth_backdrop_missing");
          await page.mouse.click(box.x+box.width/2,box.y/2);
          await login.waitFor({state:"hidden",timeout:15000});
          await page.waitForFunction(()=>document.documentElement.getAttribute("data-quata-shell-route")==="feed");
          // Same product entry point; this does not claim manual login-form entry.
          await page.evaluate(()=>globalThis.__quataAuthE2eProduct.openLogin());
        } else {
          const box=await login.boundingBox();
          if(!box?.width||!box?.height)throw Error("deep_link_web_auth_login_anchor_missing");
          await page.mouse.click(box.x+box.width/2,box.y+box.height/2);
        }
        await page.waitForFunction(()=>document.documentElement.getAttribute("data-quata-auth-destination")==="login");
        auth.login=createDeepLinkBrowserLogin({page,backendUrl,clientInstanceId});
      },
      async requestLogin(url,options) {
        if(!auth?.login)throw Error("deep_link_web_auth_not_prepared");
        const response=await auth.login.requestLogin(url,options);
        // Capture transient focus before the coordinator verifies remote receipts.
        // A UI assertion failure must not discard a received session response.
        let observationTimer,stage="product_result",observationFinished=false;
        try {
          auth.observation=await Promise.race([(async()=>{
          if(!auth.login.diagnostics().productAuthenticated)throw Error("product_login_failed");
          const {page,target,body}=auth;
          const expectedRoute=authenticationMode==="resume"?`chat/sb:${target.threadId}`:"feed";
          stage="route";
          await page.waitForFunction(({route,cancel})=>{
            const current=document.documentElement.getAttribute("data-quata-shell-route");
            return current===route || (cancel&&current==="whats-new");
          },{route:expectedRoute,cancel:authenticationMode==="cancel"},{timeout:30000});
          stage="covering_layers";
          await page.locator('[id="quata-splash-root"], [title="quata-splash-root"]').waitFor({state:"hidden",timeout:15000});
          await page.locator('[id^="quata-ugc-terms-"], [title^="quata-ugc-terms-"]').first().waitFor({state:"hidden",timeout:15000});
          if(authenticationMode==="resume") {
            stage="message_focus";
            const anchor=page.locator(`[id="chat.message.${target.messageId}"], [id="chat.message.${target.messageId}.selected"], [title="chat.message.${target.messageId}"], [title="chat.message.${target.messageId}.selected"]`).first();
            await anchor.and(page.getByRole("button",{name:`Deep link fixture: ${body}`,exact:true})).waitFor({state:"visible",timeout:15000});
            if(!await page.evaluate(id=>document.documentElement.getAttribute("data-quata-chat-focused-message-selected")===id,target.messageId))throw Error("focus_not_uncovered");
          } else {
            stage="startup_presentation";
            auth.whatsNewDismissed=false;
            const dismiss=page.locator('[id="whats-new-dismiss"], [title="whats-new-dismiss"]').first();
            // First-login Novedades is product behavior. Observe its optional
            // arrival and dismiss once; never suppress it or force a Feed route.
            for(const deadline=Date.now()+5000;Date.now()<deadline;) {
              if(observationFinished)throw Error("auth_observation_expired");
              if(await dismiss.isVisible()) {
                if(observationFinished)throw Error("auth_observation_expired");
                await page.screenshot({path:path.join(output,"web-chat-auth-cancel-whats-new.png")});
                const box=await dismiss.boundingBox();
                if(!box?.width||!box?.height)throw Error("startup_dismiss_anchor_missing");
                if(observationFinished)throw Error("auth_observation_expired");
                await page.mouse.click(box.x+box.width/2,box.y+box.height/2);
                if(observationFinished)throw Error("auth_observation_expired");
                await dismiss.waitFor({state:"hidden",timeout:10000});
                auth.whatsNewDismissed=true;
                break;
              }
              const route=await page.evaluate(()=>document.documentElement.getAttribute("data-quata-shell-route"));
              if(!["feed","whats-new"].includes(route))throw Error("cancelled_route_replayed");
              await page.waitForTimeout(100);
            }
            await page.waitForFunction(()=>document.documentElement.getAttribute("data-quata-shell-route")==="feed",null,{timeout:10000});
            for(const deadline=Date.now()+2000;Date.now()<deadline;) {
              if(!await page.evaluate(()=>document.documentElement.getAttribute("data-quata-shell-route")==="feed"))throw Error("cancelled_route_replayed");
              await page.waitForTimeout(100);
            }
          }
          stage="final_state";
          const state=await page.evaluate(()=>({route:document.documentElement.getAttribute("data-quata-shell-route"),
            episodes:globalThis.__quataDeepLinkObserved.filter(event=>event.selected!==null),timeOrigin:performance.timeOrigin,
            authDestination:document.documentElement.getAttribute("data-quata-auth-destination")}));
          if(state.timeOrigin!==auth.timeOrigin||state.route!==expectedRoute||state.authDestination||auth.pageErrors)throw Error("auth_continuation_invalid");
          if(authenticationMode==="cancel"&&state.episodes.length)throw Error("cancelled_target_replayed");
          if(authenticationMode==="resume"&&(state.episodes.length!==1||state.episodes[0].selected!==target.messageId))throw Error("focus_consumption_invalid");
          stage="capture";
          await page.screenshot({path:path.join(output,`web-chat-auth-${authenticationMode}-result.png`)});
          return {passed:true,mode:authenticationMode,route:state.route,exactThreadId:authenticationMode==="resume"?target.threadId:null,
            exactMessageId:authenticationMode==="resume"?target.messageId:null,selectedEpisodes:state.episodes.length,sameDocument:true,pageErrors:0,
            ...(authenticationMode==="cancel"?{whatsNewDismissed:auth.whatsNewDismissed}:{}),
            limits:["Product repository bridge login, not manual form submission",
              ...(authenticationMode==="cancel"?["Cancel observed before reload for 2 seconds"]:[]),"No Android/iOS claim"]};
          })(),new Promise((_,reject)=>{observationTimer=setTimeout(()=>{
            observationFinished=true;reject(Error("auth_observation_timeout"));
          },authObservationTimeoutMs);})]);
        } catch {
          auth.observation={passed:false,mode:authenticationMode,failureCode:"deep_link_web_auth_observation_failed",stage,
            pageErrors:auth.pageErrors,login:auth.login.diagnostics()};
          let diagnosticTimer;
          try {
            auth.observation.state=await Promise.race([auth.page.evaluate(()=>({
              route:document.documentElement.getAttribute("data-quata-shell-route"),
              authDestination:document.documentElement.getAttribute("data-quata-auth-destination"),
              selectedEpisodes:globalThis.__quataDeepLinkObserved?.filter(event=>event.selected!==null).length??0,
            })),new Promise((_,reject)=>{diagnosticTimer=setTimeout(()=>reject(Error("diagnostic_timeout")),2000);})]);
          } catch {auth.observation.diagnosticUnavailable=true;}
          finally {clearTimeout(diagnosticTimer);}
          try {
            const screenshot=`web-chat-auth-${authenticationMode}-failure.png`;
            await auth.page.screenshot({path:path.join(output,screenshot),timeout:5000});
            auth.observation.screenshot=screenshot;
          } catch {auth.observation.screenshotUnavailable=true;}
        } finally {observationFinished=true;clearTimeout(observationTimer);}
        return response;
      },
    }:{}),
    async run({session,clientInstanceId,target,body,observeRefresh}) {
      if(authenticationMode) {
        if(!auth?.login)throw Error("deep_link_web_auth_not_prepared");
        return auth.observation;
      }
      const origin=await start();
      const expectedRoute=`chat/sb:${target.threadId}`;
      const fragment=`#chat-${encodeURIComponent(`sb:${target.threadId}`)}?message=${encodeURIComponent(target.messageId)}`;
      const observations=[];
      if(sessionMode){refresh=createDeepLinkBrowserRefresh({backendUrl,publicKey,observeRefresh});await refresh.prepare();}
      for(const mode of (sessionMode?[sessionDeliveryMode??"cold"]:["cold","warm"])) {
        const context=await browser.newContext({locale:"es-ES",viewport:{width:430,height:930},deviceScaleFactor:1,serviceWorkers:"block"});
        const missingRead=targetMode==="missing-message"?createMissingMessageReadObserver({target,profileId:session.profileId}):null;
        const missingThread=targetMode==="missing-thread"?createMissingThreadReadObserver({target,profileId:session.profileId}):null;
        const absent=missingRead??missingThread;
        let page,pageErrors=0,stage="setup";
        try {
          await trackContext(context,missingRead,missingThread);
          await context.addInitScript(({storage,preserveRenewed})=>{
            if(!preserveRenewed || localStorage.getItem("quata_web_client_instance_id")!==storage.quata_web_client_instance_id)
              for(const [key,value] of Object.entries(storage))localStorage.setItem(key,value);
            // Observation only: never changes a product marker or calls app APIs.
            globalThis.__quataDeepLinkObserved=[];
            globalThis.__quataDeepLinkRoutes=[];
            let previous=null;
            new MutationObserver(()=>{
              const selected=document.documentElement?.getAttribute("data-quata-chat-focused-message-selected")??null;
              if(selected!==previous){globalThis.__quataDeepLinkObserved.push({selected,at:performance.now()});previous=selected;}
              const route=document.documentElement?.getAttribute("data-quata-shell-route");
              if(route&&globalThis.__quataDeepLinkRoutes.at(-1)!==route)globalThis.__quataDeepLinkRoutes.push(route);
            }).observe(document,{attributes:true,subtree:true,attributeFilter:["data-quata-chat-focused-message-selected","data-quata-shell-route"]});
          },{storage:{quata_web_access_token:session.accessToken,quata_web_refresh_token:session.refreshToken,
            quata_web_session_token:session.webSessionToken,quata_web_user_id:session.profileId,
            quata_web_expires_at:sessionMode&&mode==="cold"?"0":String(session.expiresAt),"web.auth.session_ready":"true",quata_web_client_instance_id:clientInstanceId},preserveRenewed:!!sessionMode});
          page=await context.newPage();page.on("pageerror",()=>pageErrors++);
          stage="open";
          let beforeOrigin;
          if(mode==="warm") {
            const initialRoute=sessionMode?"feed":"chat";
            await page.goto(`${origin}/#${initialRoute}`,{waitUntil:"domcontentloaded",timeout:60000});
            await page.waitForFunction(route=>document.documentElement.getAttribute("data-quata-shell-route")===route,initialRoute,{timeout:60000});
            await page.locator('[id="quata-splash-root"], [title="quata-splash-root"]').waitFor({state:"hidden",timeout:15000});
            beforeOrigin=await page.evaluate(()=>performance.timeOrigin);
            if(sessionMode&&refresh.diagnostics().attempts!==0)throw Error("deep_link_warm_refreshed_before_delivery");
            // One JS turn: expire only local metadata immediately before URL
            // delivery. No app API, token replacement or reload is involved.
            await page.evaluate(({hash,expire,original})=>{
              if(expire){
                const storedExpiry=Number(localStorage.getItem("quata_web_expires_at"));
                if(localStorage.getItem("quata_web_access_token")!==original.accessToken||
                   localStorage.getItem("quata_web_refresh_token")!==original.refreshToken||
                   localStorage.getItem("quata_web_session_token")!==original.webSessionToken||
                   !Number.isFinite(storedExpiry)||storedExpiry<=Date.now()/1000||
                   globalThis.__quataDeepLinkObserved.some(event=>event.selected!==null)||
                   globalThis.__quataDeepLinkRoutes.some(route=>route==="chat"||route.startsWith("chat/")))
                  throw Error("deep_link_warm_session_changed_before_delivery");
                localStorage.setItem("quata_web_expires_at","0");
              }
              location.hash=hash;
            },{hash:fragment,expire:!!sessionMode,original:sessionMode?session:null});
          } else await page.goto(`${origin}/${fragment}`,{waitUntil:"domcontentloaded",timeout:60000});
          if(sessionMode==="revoked") {
            stage="revoked_barrier";
            await page.getByText("Ya tengo cuenta",{exact:true}).waitFor({state:"visible",timeout:60000});
            await page.locator('[id="quata-splash-root"], [title="quata-splash-root"]').waitFor({state:"hidden",timeout:15000});
            if(!await refresh.finish()||refresh.diagnostics().rejected!==true)throw Error("deep_link_revoked_refresh_unverified");
            await page.waitForTimeout(2000);
            const denied=await page.evaluate(()=>({route:document.documentElement.getAttribute("data-quata-shell-route"),
              selected:globalThis.__quataDeepLinkObserved.some(event=>event.selected!==null),
              privateRoute:globalThis.__quataDeepLinkRoutes.some(route=>route==="chat"||route.startsWith("chat/")),
              privateMessage:!!document.querySelector('[id^="chat.message."], [title^="chat.message."]'),timeOrigin:performance.timeOrigin}));
            if(denied.route!=="feed"||denied.selected||denied.privateRoute||denied.privateMessage||pageErrors!==0)
              throw Error("deep_link_revoked_private_content_visible");
            if(mode==="warm"&&denied.timeOrigin!==beforeOrigin)throw Error("deep_link_warm_document_reloaded");
            await page.getByText("Ya tengo cuenta",{exact:true}).waitFor({state:"visible",timeout:1000});
            await page.screenshot({path:path.join(output,`web-chat-${mode}-revoked-barrier.png`)});
            observations.push({mode,accessDenied:true,underlyingRoute:denied.route,selectedEpisodes:0,privateRouteObserved:false,
              privateMessageVisible:false,pageErrors,sameDocument:mode==="warm"?denied.timeOrigin===beforeOrigin:null,
              ...(mode==="warm"?{expirySetImmediatelyBeforeDelivery:true,refreshAttemptsBeforeDelivery:0}:{}),refresh:refresh.diagnostics()});
            continue;
          }
          stage="route";
          await page.waitForFunction(route=>document.documentElement.getAttribute("data-quata-shell-route")===route,expectedRoute,{timeout:60000});
          if(missingThread) {
            stage="missing_thread_failure_ui";
            await page.getByRole("button",{name:"Reintentar mensajes",exact:true}).waitFor({state:"visible",timeout:15000});
            const failureMessage=page.getByText("No se pudieron cargar los mensajes.",{exact:true});
            await failureMessage.waitFor({state:"visible",timeout:15000});
            if(await failureMessage.count()!==1||await page.getByText(/web_postgrest_|postgrest_rpc_http_|rlsdenied/).count()!==0)
              throw Error("deep_link_missing_thread_technical_error_visible");
          } else {
          stage="message_anchor";
          const visibleId=targetMode?target.visibleMessageId:target.messageId;
          const messageAnchor=page.locator(`[id="chat.message.${visibleId}"], [id="chat.message.${visibleId}.selected"], [title="chat.message.${visibleId}"], [title="chat.message.${visibleId}.selected"]`).first();
          await messageAnchor.waitFor({state:"visible",timeout:15000});
          stage="message_text";
          // Common Chat semantics expose the bubble as Button with the complete
          // sender/body label; Canvas text is not necessarily a DOM text node.
          // Require BOTH the exact message ID and exact accessible name.
          await messageAnchor.and(page.getByRole("button",{name:`Deep link fixture: ${body}`,exact:true}))
            .waitFor({state:"visible",timeout:15000});
          }
          stage="focus";
          if(!absent)await page.waitForFunction(id=>globalThis.__quataDeepLinkObserved.some(event=>event.selected===id),target.messageId,{timeout:15000});
          const timeOrigin=await page.evaluate(()=>performance.timeOrigin);
          if(mode==="warm"&&timeOrigin!==beforeOrigin)throw Error("deep_link_warm_document_reloaded");
          stage="uncovered_selection";
          // A semantic node may exist under an opaque Compose splash. Require
          // selection while the real covering layers are absent, not merely an
          // earlier marker recorded while the user could not see the message.
          await page.locator('[id="quata-splash-root"], [title="quata-splash-root"]').waitFor({state:"hidden",timeout:15000});
          await page.locator('[id^="quata-ugc-terms-"], [title^="quata-ugc-terms-"]').first().waitFor({state:"hidden",timeout:15000});
          if(missingThread) {
            stage="missing_thread_read";
            const deadline=Date.now()+15000;
            while(!missingThread.passed()&&!missingThread.diagnostics().failed&&Date.now()<deadline)await page.waitForTimeout(100);
            await page.waitForTimeout(2000);
            if(!missingThread.passed()||await page.evaluate(()=>globalThis.__quataDeepLinkObserved.some(event=>event.selected!==null))||
                await page.locator('[id^="chat.message."], [title^="chat.message."]').count()!==0)throw Error("deep_link_missing_thread_unverified");
          } else if(missingRead) {
            stage="missing_message_read";
            const deadline=Date.now()+15000;
            while(!missingRead.passed()&&!missingRead.diagnostics().failed&&Date.now()<deadline)await page.waitForTimeout(100);
            await page.waitForTimeout(2000);
            if(!missingRead.passed()||await page.evaluate(()=>globalThis.__quataDeepLinkObserved.some(event=>event.selected!==null)))throw Error("deep_link_missing_message_unverified");
            if(await page.locator(`[id="chat.message.${target.messageId}"], [id="chat.message.${target.messageId}.selected"], [title="chat.message.${target.messageId}"], [title="chat.message.${target.messageId}.selected"]`).count()!==0)
              throw Error("deep_link_missing_message_visible");
          } else if(await page.evaluate(id=>document.documentElement.getAttribute("data-quata-chat-focused-message-selected")===id,target.messageId)!==true)
            throw Error("deep_link_selection_expired_behind_covering_layer");
          await page.screenshot({path:path.join(output,`web-chat-${mode}-target.png`)});
          stage="focus_clear";
          await page.waitForFunction(()=>!document.documentElement.hasAttribute("data-quata-chat-focused-message-selected"),null,{timeout:15000});
          const selected=await page.evaluate(()=>globalThis.__quataDeepLinkObserved);
          if(selected.filter(event=>absent?event.selected!==null:event.selected===target.messageId).length!==(absent?0:1))throw Error("deep_link_focus_consumed_more_than_once");
          stage="back";
          const back=page.locator('[id="chat.back"], [aria-label*="chat.back"], [title*="chat.back"]').first();
          await back.waitFor({state:"attached",timeout:15000});
          const box=await back.boundingBox();
          if(!box||box.width<=0||box.height<=0)throw Error("deep_link_back_bounds_unavailable");
          await page.mouse.click(box.x+box.width/2,box.y+box.height/2);
          await page.waitForFunction(()=>document.documentElement.getAttribute("data-quata-shell-route")==="chat",null,{timeout:15000});
          await page.waitForTimeout(2000);
          const exited=await page.evaluate(()=>({route:document.documentElement.getAttribute("data-quata-shell-route"),
            hash:location.hash,events:globalThis.__quataDeepLinkObserved}));
          if(exited.route!=="chat"||exited.events.filter(event=>absent?event.selected!==null:event.selected===target.messageId).length!==(absent?0:1))throw Error("deep_link_reopened_after_back");
          await page.screenshot({path:path.join(output,`web-chat-${mode}-back.png`)});
          stage="reload";
          await page.reload({waitUntil:"domcontentloaded",timeout:60000});
          await page.waitForFunction(()=>document.documentElement.getAttribute("data-quata-shell-route")==="chat",null,{timeout:60000});
          await page.waitForTimeout(2000);
          const reloaded=await page.evaluate(()=>({route:document.documentElement.getAttribute("data-quata-shell-route"),events:globalThis.__quataDeepLinkObserved}));
          if(reloaded.route!=="chat"||reloaded.events.some(event=>event.selected!==null))throw Error("deep_link_reopened_after_reload");
          if(pageErrors!==0)throw Error("deep_link_page_errors");
          if(absent&&!absent.passed())throw Error("deep_link_missing_destination_unverified");
          if(refresh && (!await refresh.finish() || await page.evaluate(()=>Number(localStorage.getItem("quata_web_expires_at"))>Date.now()/1000)!==true))
            throw Error("deep_link_refresh_not_observed");
          observations.push({mode,exactThreadId:target.threadId,exactMessageId:target.messageId,accessibleTextMatched:!missingThread,
            selectedEpisodes:absent?0:1,uncoveredSelection:!absent,focusCleared:true,sameDocument:mode==="warm"?timeOrigin===beforeOrigin:null,
            ...(sessionMode&&mode==="warm"?{expirySetImmediatelyBeforeDelivery:true,refreshAttemptsBeforeDelivery:0}:{}),
            ...(missingRead?{missingMessage:true,visibleMessageId:target.visibleMessageId,read:missingRead.diagnostics()}:{}),
            ...(missingThread?{missingThread:true,readFailureVisible:true,read:missingThread.diagnostics()}:{}),
            backRoute:exited.route,reloadedRoute:reloaded.route,pageErrors,...(refresh?{refresh:refresh.diagnostics()}:{} )});
        } catch {
          const failure={mode,stage,pageErrors,...(refresh?{refresh:refresh.diagnostics()}:{} )};
          if(page) {
            let observationTimer;
            try {Object.assign(failure,await Promise.race([page.evaluate(({route,id})=>({
              expectedRouteReached:document.documentElement.getAttribute("data-quata-shell-route")===route,
              selectionSeen:globalThis.__quataDeepLinkObserved?.some(event=>event.selected===id)===true,
            }),{route:expectedRoute,id:target.messageId}),new Promise((_,reject)=>{
              observationTimer=setTimeout(()=>reject(Error("observation_timeout")),2000);
            })]));} catch {failure.observationUnavailable=true;}
            finally {clearTimeout(observationTimer);}
            try {const file=`web-chat-${mode}-failure-${stage}.png`;await page.screenshot({path:path.join(output,file),timeout:10000});failure.screenshot=file;}
            catch {failure.screenshotUnavailable=true;}
          }
          failures.push(failure);throw Error(`deep_link_web_${stage}_failed`);
        } finally {
          await closeContext(context);
          if(absent&&!absent.passed())throw Error(missingThread?"deep_link_web_missing_thread_read_failed":"deep_link_web_missing_message_read_failed");
          if(absent&&observations.at(-1)?.mode===mode)observations.at(-1).read=absent.diagnostics();
        }
      }
      return {passed:true,observations,limits:["No iOS/Android claim",sessionMode==="revoked"?"Revoked own session with expired local metadata; no claim of early JWT invalidation or local storage deletion":sessionMode?"Local expiry metadata only; no real JWT expiry or revoked-session claim":"No expired-session claim",
        "No OS background/foreground claim","Service workers blocked for request accounting","Post-exit observation window: 2 seconds",
        ...(sessionMode==="revoked"?["Message absence sampled at final barrier; route/focus transitions observed through MutationObserver"]:[]),
        ...(missingTargetLimits(targetMode))]};
    },
    async close() {
      try {
        for(const context of networks.keys())await closeContext(context);
        await browser?.close();
      } finally {
        if(server){server.closeAllConnections();await new Promise((resolve,reject)=>server.close(error=>error?reject(error):resolve()));}
      }
    },
    operationsSettled(){return !networkUncertain&&networks.size===0&&(!auth?.login||auth.login.operationsSettled())&&(!refresh||refresh.operationsSettled());},
    diagnostics(){return failures.map(failure=>({...failure}));},
  };
}

function missingTargetLimits(mode) {
  if(mode==="missing-message")return ["Missing message inside independently verified owned one-message thread; no missing-thread claim",
    "Backend history exhausted; no explicit unavailable notice claim; focus transitions observed through MutationObserver"];
  if(mode==="missing-thread")return ["Thread absence independently checked in DB; RPC alone does not distinguish inaccessible from absent",
    "Read failure UI and retry control observed; retry not executed; no explicit not-found notice claim"];
  return [];
}
