// Called only after dispatchWebNotificationSeed has durably recorded its single
// attempt. Load the dispatcher secret privately; never include it in diagnostics.
export function createWebNotificationDispatchTransport({backendUrl,loadSecret,fetchImpl=fetch,timeoutMs=30000}) {
  const base=new URL(backendUrl);
  if(base.protocol!=='https:'||base.username||base.password||base.pathname!=='/'||base.search||base.hash||
    typeof loadSecret!=='function'||typeof fetchImpl!=='function'||!Number.isFinite(timeoutMs)||timeoutMs<=0||timeoutMs>30000)
    throw Error('web_notification_dispatch_transport_configuration_invalid');
  const endpoint=base.origin+'/functions/v1/quata-push-dispatch';
  let attempted=false,settled=true,uncertain=false;
  return {
    async execute({messageId}) {
      if(attempted)throw Error('web_notification_dispatch_transport_already_attempted');
      attempted=true;
      if(typeof messageId!=='string'||!/^[1-9][0-9]*$/.test(messageId)||!Number.isSafeInteger(Number(messageId)))
        throw Error('web_notification_dispatch_transport_input_invalid');
      settled=false;const controller=new AbortController();let timer;
      try {
        const result=await Promise.race([(async()=>{
          const secret=await loadSecret();
          if(controller.signal.aborted||typeof secret!=='string'||!secret||secret.trim()!==secret||/[\r\n]/.test(secret))throw Error();
          const response=await fetchImpl(endpoint,{method:'POST',redirect:'error',signal:controller.signal,
            headers:{'Content-Type':'application/json','x-quata-push-secret':secret},body:JSON.stringify({message_id:Number(messageId)})});
          // fetch rejects redirects instead of forwarding credentials elsewhere.
          if(response.redirected||response.url!==endpoint)throw Error();
          const body=await response.json();
          if(!body||typeof body!=='object'||Array.isArray(body))throw Error();
          return {status:response.status,body};
        })(),new Promise((_,reject)=>{timer=setTimeout(()=>{uncertain=true;controller.abort();reject(Error());},timeoutMs);})]);
        if(uncertain)throw Error();
        settled=true;return result;
      } catch {
        uncertain=true;throw Error('web_notification_dispatch_transport_unverified');
      } finally {clearTimeout(timer);controller.abort();}
    },
    operationsSettled(){return settled&&!uncertain;},
    diagnostics(){return {attempted,settled,uncertain};},
  };
}
