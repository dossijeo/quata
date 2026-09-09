import {createServer} from "node:http";
import {readFile,stat,mkdir} from "node:fs/promises";
import path from "node:path";

export function createDeepLinkWebTrial({chromium,chrome,distribution,outputDirectory,backendUrl,publicKey}) {
  const root=path.resolve(distribution),output=path.resolve(outputDirectory);
  let server,browser;
  let networkUncertain=false;
  const networks=new Map();
  const failures=[];
  const backendOrigin=new URL(backendUrl).origin;
  async function trackContext(context) {
    const network={gated:false,pending:new Set()};networks.set(context,network);
    await context.route(`${backendOrigin}/**`,async route=>{
      if(network.gated)return route.abort(); // No request forwarded after shutdown starts.
      const request=route.request();
      const mutating=!["GET","HEAD","OPTIONS"].includes(request.method());
      if(mutating)network.pending.add(request);
      try {await route.continue();}catch{if(mutating){networkUncertain=true;network.pending.delete(request);}}
    });
    context.on("requestfinished",async request=>{
      if(!network.pending.has(request))return;
      try {const response=await request.response();if(!response||response.status()>=400)networkUncertain=true;}
      catch {networkUncertain=true;}
      finally {network.pending.delete(request);}
    });
    context.on("requestfailed",request=>{
      if(network.pending.delete(request))networkUncertain=true;
    });
  }
  async function closeContext(context) {
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
    async run({session,clientInstanceId,target,body}) {
      const origin=await start();
      const expectedRoute=`chat/sb:${target.threadId}`;
      const fragment=`#chat-${encodeURIComponent(`sb:${target.threadId}`)}?message=${encodeURIComponent(target.messageId)}`;
      const observations=[];
      for(const mode of ["cold","warm"]) {
        const context=await browser.newContext({locale:"es-ES",viewport:{width:430,height:930},deviceScaleFactor:1,serviceWorkers:"block"});
        let page,pageErrors=0,stage="setup";
        try {
          await trackContext(context);
          await context.addInitScript(({storage})=>{
            for(const [key,value] of Object.entries(storage))localStorage.setItem(key,value);
            // Observation only: never changes a product marker or calls app APIs.
            globalThis.__quataDeepLinkObserved=[];
            let previous=null;
            new MutationObserver(()=>{
              const selected=document.documentElement?.getAttribute("data-quata-chat-focused-message-selected")??null;
              if(selected!==previous){globalThis.__quataDeepLinkObserved.push({selected,at:performance.now()});previous=selected;}
            }).observe(document,{attributes:true,subtree:true,attributeFilter:["data-quata-chat-focused-message-selected"]});
          },{storage:{quata_web_access_token:session.accessToken,quata_web_refresh_token:session.refreshToken,
            quata_web_session_token:session.webSessionToken,quata_web_user_id:session.profileId,
            quata_web_expires_at:String(session.expiresAt),"web.auth.session_ready":"true",quata_web_client_instance_id:clientInstanceId}});
          page=await context.newPage();page.on("pageerror",()=>pageErrors++);
          stage="open";
          let beforeOrigin;
          if(mode==="warm") {
            await page.goto(`${origin}/#chat`,{waitUntil:"domcontentloaded",timeout:60000});
            await page.waitForFunction(()=>document.documentElement.getAttribute("data-quata-shell-route")==="chat",null,{timeout:60000});
            await page.locator('[id="quata-splash-root"], [title="quata-splash-root"]').waitFor({state:"hidden",timeout:15000});
            beforeOrigin=await page.evaluate(()=>performance.timeOrigin);
            await page.evaluate(hash=>{location.hash=hash;},fragment);
          } else await page.goto(`${origin}/${fragment}`,{waitUntil:"domcontentloaded",timeout:60000});
          stage="route";
          await page.waitForFunction(route=>document.documentElement.getAttribute("data-quata-shell-route")===route,expectedRoute,{timeout:60000});
          stage="message_anchor";
          const messageAnchor=page.locator(`[id="chat.message.${target.messageId}"], [id="chat.message.${target.messageId}.selected"], [title="chat.message.${target.messageId}"], [title="chat.message.${target.messageId}.selected"]`).first();
          await messageAnchor.waitFor({state:"visible",timeout:15000});
          stage="message_text";
          // Common Chat semantics expose the bubble as Button with the complete
          // sender/body label; Canvas text is not necessarily a DOM text node.
          // Require BOTH the exact message ID and exact accessible name.
          await messageAnchor.and(page.getByRole("button",{name:`Deep link fixture: ${body}`,exact:true}))
            .waitFor({state:"visible",timeout:15000});
          stage="focus";
          await page.waitForFunction(id=>globalThis.__quataDeepLinkObserved.some(event=>event.selected===id),target.messageId,{timeout:15000});
          const timeOrigin=await page.evaluate(()=>performance.timeOrigin);
          if(mode==="warm"&&timeOrigin!==beforeOrigin)throw Error("deep_link_warm_document_reloaded");
          stage="uncovered_selection";
          // A semantic node may exist under an opaque Compose splash. Require
          // selection while the real covering layers are absent, not merely an
          // earlier marker recorded while the user could not see the message.
          await page.locator('[id="quata-splash-root"], [title="quata-splash-root"]').waitFor({state:"hidden",timeout:15000});
          await page.locator('[id^="quata-ugc-terms-"], [title^="quata-ugc-terms-"]').first().waitFor({state:"hidden",timeout:15000});
          if(await page.evaluate(id=>document.documentElement.getAttribute("data-quata-chat-focused-message-selected")===id,target.messageId)!==true)
            throw Error("deep_link_selection_expired_behind_covering_layer");
          await page.screenshot({path:path.join(output,`web-chat-${mode}-target.png`)});
          stage="focus_clear";
          await page.waitForFunction(()=>!document.documentElement.hasAttribute("data-quata-chat-focused-message-selected"),null,{timeout:15000});
          const selected=await page.evaluate(()=>globalThis.__quataDeepLinkObserved);
          if(selected.filter(event=>event.selected===target.messageId).length!==1)throw Error("deep_link_focus_consumed_more_than_once");
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
          if(exited.route!=="chat"||exited.events.filter(event=>event.selected===target.messageId).length!==1)throw Error("deep_link_reopened_after_back");
          await page.screenshot({path:path.join(output,`web-chat-${mode}-back.png`)});
          stage="reload";
          await page.reload({waitUntil:"domcontentloaded",timeout:60000});
          await page.waitForFunction(()=>document.documentElement.getAttribute("data-quata-shell-route")==="chat",null,{timeout:60000});
          await page.waitForTimeout(2000);
          const reloaded=await page.evaluate(()=>({route:document.documentElement.getAttribute("data-quata-shell-route"),events:globalThis.__quataDeepLinkObserved}));
          if(reloaded.route!=="chat"||reloaded.events.some(event=>event.selected!==null))throw Error("deep_link_reopened_after_reload");
          if(pageErrors!==0)throw Error("deep_link_page_errors");
          observations.push({mode,exactThreadId:target.threadId,exactMessageId:target.messageId,accessibleTextMatched:true,
            selectedEpisodes:1,uncoveredSelection:true,focusCleared:true,sameDocument:mode==="warm"?timeOrigin===beforeOrigin:null,
            backRoute:exited.route,reloadedRoute:reloaded.route,pageErrors});
        } catch {
          const failure={mode,stage,pageErrors};
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
        } finally {await closeContext(context);}
      }
      return {passed:true,observations,limits:["No iOS/Android claim","No expired-session claim",
        "No OS background/foreground claim","Service workers blocked for request accounting","Post-exit observation window: 2 seconds"]};
    },
    async close() {
      try {
        for(const context of networks.keys())await closeContext(context);
        await browser?.close();
      } finally {
        if(server){server.closeAllConnections();await new Promise((resolve,reject)=>server.close(error=>error?reject(error):resolve()));}
      }
    },
    operationsSettled(){return !networkUncertain&&networks.size===0;},
    diagnostics(){return failures.map(failure=>({...failure}));},
  };
}
