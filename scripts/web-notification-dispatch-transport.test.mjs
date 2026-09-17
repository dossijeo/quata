import test from 'node:test';
import assert from 'node:assert/strict';
import {createWebNotificationDispatchTransport} from './e2e-fixtures/web-notification-dispatch-transport.mjs';
const backendUrl='https://synthetic.invalid',endpoint=backendUrl+'/functions/v1/quata-push-dispatch';
const response=()=>({url:endpoint,redirected:false,status:200,json:async()=>({result:true})});
test('one fixed POST supplies secret only in header and preserves response',async()=>{
  let count=0;
  const transport=createWebNotificationDispatchTransport({backendUrl,loadSecret:async()=> 'synthetic-private',fetchImpl:async(url,options)=>{
    count++;assert.equal(url,endpoint);assert.equal(options.method,'POST');assert.equal(options.redirect,'error');
    assert.equal(options.headers['x-quata-push-secret'],'synthetic-private');assert.equal(options.body,'{"message_id":123}');return response();
  }});
  assert.deepEqual(await transport.execute({messageId:'123'}),{status:200,body:{result:true}});
  assert.equal(transport.operationsSettled(),true);
  await assert.rejects(transport.execute({messageId:'123'}),/already_attempted/);assert.equal(count,1);
  assert.equal(JSON.stringify(transport.diagnostics()).includes('synthetic-private'),false);
});
for(const mode of ['redirect','network','body','secret'])test(`${mode} withholds settlement and redacts errors`,async()=>{
  const transport=createWebNotificationDispatchTransport({backendUrl,loadSecret:async()=>{if(mode==='secret')throw Error('synthetic-private');return 'synthetic-private';},
    fetchImpl:async()=>{if(mode==='network')throw Error('synthetic-private');const value=response();
      if(mode==='redirect')value.redirected=true;
      if(mode==='body')value.json=async()=>{throw Error('synthetic-private');};return value;
    }});
  await assert.rejects(transport.execute({messageId:'123'}),error=>error.message==='web_notification_dispatch_transport_unverified');
  assert.equal(transport.operationsSettled(),false);
});
test('timeout during secret load prevents late POST',async()=>{
  let resolveSecret,calls=0;const secret=new Promise(resolve=>{resolveSecret=resolve;});
  const transport=createWebNotificationDispatchTransport({backendUrl,timeoutMs:10,loadSecret:()=>secret,fetchImpl:async()=>{calls++;return response();}});
  await assert.rejects(transport.execute({messageId:'123'}),/unverified/);resolveSecret('synthetic-private');
  await new Promise(resolve=>setImmediate(resolve));assert.equal(calls,0);assert.equal(transport.operationsSettled(),false);
});
test('late HTTP response after timeout never converts uncertainty to settlement',async()=>{
  let resolveFetch;const pending=new Promise(resolve=>{resolveFetch=resolve;});
  const transport=createWebNotificationDispatchTransport({backendUrl,timeoutMs:10,loadSecret:async()=> 'synthetic-private',fetchImpl:()=>pending});
  await assert.rejects(transport.execute({messageId:'123'}),/unverified/);resolveFetch(response());
  await new Promise(resolve=>setImmediate(resolve));assert.equal(transport.operationsSettled(),false);
});
