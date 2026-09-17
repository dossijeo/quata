import {createServer} from 'node:http';
import {readFile,stat,mkdir} from 'node:fs/promises';
import path from 'node:path';
import {createDeepLinkBrowserLogin} from './chat-deep-link-browser-login.mjs';
import {createWebNotificationBrowserTransport} from './web-notification-browser-transport.mjs';

export async function waitWebNotificationWorker({page,origin,timeoutMs=20000}) {
  if(!Number.isFinite(timeoutMs)||timeoutMs<=0||timeoutMs>20000)throw Error('web_notification_worker_timeout_invalid');
  const deadline=Date.now()+timeoutMs;let timer;
  try {
    await Promise.race([(async()=>{
      do {
        const ready=await page.evaluate(async expected=>{
          const registration=await navigator.serviceWorker.getRegistration();
          return registration?.scope===expected+'/'&&registration.active?.state==='activated'&&
            registration.active.scriptURL===expected+'/quata-sw.js';
        },origin);
        if(ready===true)return;
        await new Promise(resolve=>setTimeout(resolve,Math.min(100,Math.max(0,deadline-Date.now()))));
      }while(Date.now()<deadline);
      throw Error('web_notification_worker_unverified');
    })(),new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('web_notification_worker_unverified')),timeoutMs);})]);
  } finally {clearTimeout(timer);}
}

// Native callbacks must use the observed OS UI. They must not dispatch worker
// events, navigate to Chat, or inject a response. No permission-prompt acceptance
// is inferred from an already granted permission. They must check signal before
// each action and cease when aborted; a timeout permanently withholds cleanup.
export function createWebNotificationBrowserUi({chromium,chrome,distribution,profileDirectory,outputDirectory,
  backendUrl,publicKey,nativePermission,nativeNotificationClick}) {
  if(![distribution,profileDirectory,outputDirectory].every(path.isAbsolute)||
    [nativePermission,nativeNotificationClick].some(fn=>typeof fn!=='function'))throw Error('web_notification_ui_configuration_invalid');
  const root=path.resolve(distribution);
  let context,page,server,origin,runId,transport,login,started=false,sendAttempted=false;
  let nativeUncertain=false;const nativeControllers=new Set();
  const native=async(callback,input)=>{
    const controller=new AbortController();nativeControllers.add(controller);let timer;
    const deadline=Date.now()+30000;
    try {
      return await Promise.race([Promise.resolve().then(()=>callback({...input,signal:controller.signal,deadline})),
        new Promise((_,reject)=>{timer=setTimeout(()=>{nativeUncertain=true;controller.abort();reject(Error('web_notification_native_timeout'));},30000);})]);
    } finally {clearTimeout(timer);controller.abort();nativeControllers.delete(controller);}
  };
  const tag=value=>page.locator(`[id="${value}"], [title="${value}"]`).first();
  const capture=async name=>page.screenshot({path:path.join(outputDirectory,name+'.png')});
  const assertRun=input=>{if(input.runId!==runId)throw Error('web_notification_ui_run_mismatch');};
  const click=async locator=>{
    await locator.waitFor({state:'visible',timeout:20000});
    const box=await locator.boundingBox();if(!box?.width||!box?.height)throw Error('web_notification_ui_anchor_missing');
    await page.mouse.click(box.x+box.width/2,box.y+box.height/2);
  };
  const settings=async()=>{
    // Setup/cleanup destination only. The notification-to-Chat transition below
    // exclusively comes from the native click callback and product worker.
    await page.evaluate(()=>{location.hash='settings';});
    await page.waitForFunction(()=>document.documentElement.getAttribute('data-quata-shell-route')==='settings',null,{timeout:60000});
    await tag('quata-splash-root').waitFor({state:'hidden',timeout:20000});
  };
  const mime={'.html':'text/html','.js':'text/javascript','.mjs':'text/javascript','.wasm':'application/wasm',
    '.json':'application/json','.css':'text/css','.png':'image/png','.svg':'image/svg+xml','.ttf':'font/ttf','.woff2':'font/woff2'};
  const attr=value=>value.replaceAll('&','&amp;').replaceAll('"','&quot;');
  return {
    async start(input) {
      if(started)throw Error('web_notification_ui_already_started');started=true;runId=input.runId;
      await mkdir(outputDirectory,{recursive:true});
      // Exclusive short profile: never attach to a user's existing browser data.
      await mkdir(profileDirectory);
      server=createServer(async(req,res)=>{
        try {
          if(!['GET','HEAD'].includes(req.method))return res.writeHead(405).end();
          const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
          const file=path.resolve(root,'.'+(pathname==='/'?'/index.html':pathname));
          if(!file.startsWith(root+path.sep)||!(await stat(file).catch(()=>null))?.isFile())return res.writeHead(404).end();
          let bytes=await readFile(file);
          if(file.endsWith('index.html'))bytes=bytes.toString()
            .replace(/(<meta name="quata-supabase-url" content=")[^"]*(">)/,(_,a,b)=>a+attr(backendUrl)+b)
            .replace(/(<meta name="quata-supabase-publishable-key" content=")[^"]*(">)/,(_,a,b)=>a+attr(publicKey)+b);
          res.writeHead(200,{'Content-Type':mime[path.extname(file)]??'application/octet-stream','Cache-Control':'no-store',
            'Cross-Origin-Opener-Policy':'same-origin','Cross-Origin-Embedder-Policy':'require-corp'}).end(req.method==='HEAD'?undefined:bytes);
        } catch {res.writeHead(500).end();}
      });
      await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
      origin=`http://127.0.0.1:${server.address().port}`;
      context=await chromium.launchPersistentContext(profileDirectory,{executablePath:chrome,headless:false,
        locale:'es-ES',viewport:{width:1100,height:850},serviceWorkers:'allow',
        args:['--use-angle=swiftshader','--enable-unsafe-swiftshader','--force-renderer-accessibility']});
      transport=await createWebNotificationBrowserTransport({context,backendUrl,allowSubscriptionReconciliation:true,
        permittedMutations:[
          // Login/restore use the existing product auth bridge. The private
          // login adapter verifies the exact original single login request.
          {method:'POST',path:'/functions/v1/quata-auth-bridge'},
          {method:'POST',path:'/auth/v1/token'},
          // PostgREST exposes reads as POST as well as read/delivery receipts.
          ...['quata_chat_get_inbox','quata_chat_get_thread','quata_chat_get_favorites',
            'quata_chat_mark_thread_read','quata_chat_mark_messages_state'].map(name=>({method:'POST',path:'/rest/v1/rpc/'+name})),
        ]});
      await context.addInitScript(id=>localStorage.setItem('quata_web_client_instance_id',id),input.clientInstanceId);
      page=context.pages()[0]??await context.newPage();
      await page.goto(origin+'/?quata-auth-e2e=1#feed',{waitUntil:'domcontentloaded',timeout:60000});
      await tag('quata-splash-root').waitFor({state:'visible',timeout:20000});
      await tag('quata-splash-root').waitFor({state:'hidden',timeout:20000});
      await page.getByRole('button',{name:/Avisos/}).first().waitFor({state:'visible',timeout:60000});
      const anonymous=await page.evaluate(()=>!localStorage.getItem('quata_web_access_token'));
      if(!anonymous)throw Error('web_notification_ui_not_anonymous');
      await waitWebNotificationWorker({page,origin});
      await capture('public-feed');
      login=createDeepLinkBrowserLogin({page,backendUrl,clientInstanceId:input.clientInstanceId});
      return {runId,publicReady:true,anonymous};
    },
    async requestLogin(url,options) {
      await page.evaluate(()=>globalThis.__quataAuthE2eProduct.openLogin());
      await page.waitForFunction(()=>document.documentElement.getAttribute('data-quata-auth-destination')==='login');
      return login.requestLogin(url,options);
    },
    async enablePush(input) {
      assertRun(input);
      if(!login.diagnostics().productAuthenticated)throw Error('web_notification_ui_login_unverified');
      await settings();
      await waitWebNotificationWorker({page,origin});
      transport.arm('subscription',input.capture);
      const button=page.getByRole('button',{name:'Activar notificaciones',exact:true});
      await click(button);
      if(await page.evaluate(()=>Notification.permission)==='default')await native(nativePermission,{runId,origin});
      await page.waitForFunction(()=>Notification.permission==='granted',null,{timeout:20000});
      await page.getByRole('button',{name:'Desactivar notificaciones',exact:true}).waitFor({state:'visible',timeout:30000});
      if(!transport.diagnostics().subscriptionCaptured)throw Error('web_notification_ui_subscribe_uncaptured');
      await capture('push-enabled');return {runId,productSubscribed:true};
    },
    async clickNotification(input) {
      assertRun(input);
      const receipt=await native(nativeNotificationClick,{...input,origin});
      if(receipt?.runId!==runId||receipt.clickedViaSystemUi!==true||receipt.threadId!==input.threadId||
        receipt.messageId!==input.messageId)throw Error('web_notification_click_unverified');
      // Observe the worker's resulting navigation; never set the target hash.
      await page.waitForFunction(thread=>document.documentElement.getAttribute('data-quata-shell-route')===`chat/sb:${thread}`,input.threadId,{timeout:60000});
      await tag('chat.composer.input').waitFor({state:'visible',timeout:20000});
      await capture('notification-chat');return {...receipt,chatVisible:true};
    },
    async sendReply(input) {
      assertRun(input);if(sendAttempted)throw Error('web_notification_ui_send_already_attempted');sendAttempted=true;
      if(await page.evaluate(()=>document.documentElement.getAttribute('data-quata-shell-route'))!==`chat/sb:${input.threadId}`)
        throw Error('web_notification_ui_wrong_chat');
      transport.arm('message',input.capture);
      await click(tag('chat.composer.input'));await page.keyboard.insertText(input.marker);
      await click(tag('chat.composer.send'));
      const deadline=Date.now()+20000;
      while(!transport.diagnostics().messageCaptured&&Date.now()<deadline)await new Promise(resolve=>setTimeout(resolve,100));
      if(!transport.diagnostics().messageCaptured)throw Error('web_notification_ui_send_uncaptured');
      await capture('reply-submitted');return {runId,sentViaChatUi:true};
    },
    async closeOwnedState(input) {
      if(runId&&input.runId!==runId)throw Error('web_notification_ui_run_mismatch');
      const receipt={runId:input.runId,browserClosed:false,serverClosed:false,transportSettled:false,
        ownedNotificationsRemoved:false,browserSubscriptionRemoved:false};
      if(nativeControllers.size){nativeUncertain=true;for(const controller of nativeControllers)controller.abort();}
      try {
        if(page&&!page.isClosed()) {
          const hasSubscription=await page.evaluate(async()=>{
            const r=await navigator.serviceWorker.getRegistration();return Boolean(r&&await r.pushManager.getSubscription());
          });
          if(hasSubscription) {
            await settings();await click(page.getByRole('button',{name:'Desactivar notificaciones',exact:true}));
            await page.getByRole('button',{name:'Activar notificaciones',exact:true}).waitFor({state:'visible',timeout:20000});
          }
          const removed=await page.evaluate(async target=>{
            const r=await navigator.serviceWorker.getRegistration();if(!r)return {subscription:true,notifications:true};
            const notifications=await r.getNotifications();
            if(notifications.some(n=>!target||n.tag!==`chat:${target.messageId}`))return {subscription:false,notifications:false};
            for(const n of notifications)n.close();
            return {subscription:!(await r.pushManager.getSubscription()),notifications:(await r.getNotifications()).length===0};
          },input.target??null);
          receipt.browserSubscriptionRemoved=removed.subscription;receipt.ownedNotificationsRemoved=removed.notifications;
        } else if(!context) {receipt.browserSubscriptionRemoved=true;receipt.ownedNotificationsRemoved=true;}
      } finally {
        try {receipt.transportSettled=transport?(await transport.gateAndDrain()).settled:true;}
        finally {
          try {await context?.close();receipt.browserClosed=true;}
          finally {if(server)await new Promise(resolve=>server.close(resolve));receipt.serverClosed=true;}
        }
      }
      receipt.transportSettled=receipt.transportSettled&&!nativeUncertain&&(!login||login.operationsSettled());
      return receipt;
    },
    operationsSettled(){return !nativeUncertain&&nativeControllers.size===0&&(!transport||transport.operationsSettled())&&(!login||login.operationsSettled());},
  };
}
