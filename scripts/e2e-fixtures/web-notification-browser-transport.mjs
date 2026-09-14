// Private browser boundary: never log bodies, headers, endpoints or responses.
// Arm only immediately before the corresponding product gesture. This module
// forwards the original request once; it never manufactures or retries a Send.
export async function createWebNotificationBrowserTransport({context,backendUrl,permittedMutations=[]}) {
  const origin=new URL(backendUrl).origin;
  if(!context?.route||!context?.on)throw Error('web_notification_transport_configuration_invalid');
  const permitted=new Set();
  for(const entry of permittedMutations) {
    if(!entry||!['POST','PATCH','DELETE','PUT'].includes(entry.method)||
      typeof entry.path!=='string'||!/^\/[a-zA-Z0-9_/-]+$/.test(entry.path)||
      ['/rest/v1/rpc/quata_chat_send_message','/functions/v1/quata-web-push'].includes(entry.path))
      throw Error('web_notification_transport_configuration_invalid');
    permitted.add(`${entry.method} ${entry.path}`);
  }
  let gated=false,uncertain=false;
  const slots=new Map(),pending=new Set(),handlers=new Set();
  const diagnostics=()=>({gated,uncertain,pending:pending.size,handlers:handlers.size,
    subscriptionCaptured:slots.get('subscription')?.captured===true,messageCaptured:slots.get('message')?.captured===true});
  const abort=async route=>{try{await route.abort();}catch{uncertain=true;}};
  const handler=async route=>{
    const request=route.request(),token={};handlers.add(token);
    let forwarded=false,tracked=false;
    try {
      if(gated)return await abort(route);
      const url=new URL(request.url()),method=request.method();
      if(url.origin!==origin)throw Error();
      if(!['GET','HEAD','OPTIONS'].includes(method)) {
        let kind,payload;
        if(url.pathname==='/rest/v1/rpc/quata_chat_send_message') {
          if(method!=='POST')throw Error();
          kind='message';payload=request.postDataJSON();
        } else if(url.pathname==='/functions/v1/quata-web-push') {
          if(method!=='POST')throw Error();
          const body=request.postDataJSON();
          if(body?.action==='subscribe'){kind='subscription';payload=body.subscription;}
          else if(!['unsubscribe','logout'].includes(body?.action))throw Error();
        } else if(!permitted.has(`${method} ${url.pathname}`))throw Error();
        if(kind) {
          const slot=slots.get(kind);
          if(!slot||slot.attempted)throw Error();
          slot.attempted=true;
          const result=await slot.capture(payload);
          if(result?.captured!==true)throw Error();
          slot.captured=true;
        }
        // Shutdown can begin while the durable checkpoint is being written.
        if(gated)return await abort(route);
        pending.add(request);tracked=true;
      }
      forwarded=true;
      await route.continue();
    } catch {
      // A failed checkpoint or unexpected producer invalidates the trial even
      // when nothing escaped. A continue failure may have reached the server.
      uncertain=true;
      if(!forwarded)await abort(route);
      if(tracked)pending.delete(request);
    } finally {handlers.delete(token);}
  };
  const finished=async request=>{
    if(!pending.has(request))return;
    try {const response=await request.response();if(!response||response.status()<200||response.status()>=300)uncertain=true;}
    catch {uncertain=true;}
    finally {pending.delete(request);}
  };
  const failed=request=>{if(pending.delete(request))uncertain=true;};
  context.on('requestfinished',finished);context.on('requestfailed',failed);
  await context.route(`${origin}/**`,handler);
  return {
    arm(kind,capture) {
      if(gated||!['subscription','message'].includes(kind)||typeof capture!=='function'||slots.has(kind))
        throw Error('web_notification_transport_arm_invalid');
      slots.set(kind,{capture,attempted:false,captured:false});
    },
    diagnostics,
    operationsSettled(){return gated&&!uncertain&&pending.size===0&&handlers.size===0;},
    async gateAndDrain({timeoutMs=20000}={}) {
      if(!Number.isFinite(timeoutMs)||timeoutMs<0||timeoutMs>20000)throw Error('web_notification_transport_timeout_invalid');
      gated=true;
      const deadline=Date.now()+timeoutMs;
      while((pending.size||handlers.size)&&Date.now()<deadline)await new Promise(resolve=>setTimeout(resolve,25));
      // Timeout is uncertainty, never cancellation or proof of completion.
      if(pending.size||handlers.size)uncertain=true;
      return {settled:!uncertain&&pending.size===0&&handlers.size===0};
    },
  };
}
