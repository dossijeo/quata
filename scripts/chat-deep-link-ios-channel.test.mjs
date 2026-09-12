import test from "node:test";
import assert from "node:assert/strict";
import {EventEmitter} from "node:events";
import {PassThrough,Writable} from "node:stream";
import {openIosDeepLinkChannel} from "./e2e-fixtures/chat-deep-link-ios-channel.mjs";
const root="/Users/gabriel/StudioProjects/quata-flow-deep-links-edbb970b";
const ownedInput={runId:'00000000-0000-4000-8000-000000000001',stepId:'00000000-0000-4000-8000-000000000002',
  stage:'read-owned',profileId:'00000000-0000-4000-8000-000000000003',authUserId:'00000000-0000-4000-8000-000000000004'};
function ownedReceipt(){
  const authSessionId='00000000-0000-4000-8000-000000000005',expiresAt=2000000000;
  const payload=Buffer.from(JSON.stringify({sub:ownedInput.authUserId,session_id:authSessionId,exp:expiresAt})).toString('base64url');
  return {runId:ownedInput.runId,stepId:ownedInput.stepId,stage:'read-owned',verified:true,
    privateSession:{profileId:ownedInput.profileId,authUserId:ownedInput.authUserId,authSessionId,expiresAt,
      accessToken:'synthetic.'+payload+'.synthetic',refreshToken:'synthetic-private-refresh',email:'fixture@example.invalid',displayName:'Synthetic',isOfficial:false}};
}
function fixture(handler) {
  let child,launch;
  const commands=[];
  const send=value=>child.stdout.write(JSON.stringify(value)+"\n");
  const spawnImpl=(...args)=>{
    launch=args;child=new EventEmitter();child.stdout=new PassThrough();child.stderr=new PassThrough();
    child.kill=()=>{};
    child.stdin=new Writable({write(bytes,encoding,callback){
      const request=JSON.parse(bytes.toString());commands.push(request);
      queueMicrotask(()=>handler(request,send,child));callback();
    }});
    queueMicrotask(()=>send({ready:true,simulator:"F2E1EA50-FBAD-443C-A98F-2A576C14C70B"}));
    return child;
  };
  return {options:{root,products:root+"/build/Products",spawnImpl,timeoutMs:100},commands,get:()=>({child,launch})};
}
test("private session travels only through stdin; settled requires close receipt and exit",async()=>{
  const f=fixture((request,send,child)=>{
    if(request.action==="session")send({runId:request.input.runId,stepId:request.input.stepId,stage:request.input.stage,verified:true});
    if(request.action==="close"){send({closed:true});queueMicrotask(()=>child.emit("close",0));}
  });
  const channel=await openIosDeepLinkChannel(f.options);
  await channel.sessionStep({runId:"run",stepId:"step",stage:"install",accessToken:"private-access"});
  assert.equal(JSON.stringify(f.get().launch).includes("private-access"),false);
  assert.equal(f.commands[0].input.accessToken,"private-access");assert.equal(channel.settled(),false);
  await channel.close();assert.equal(channel.settled(),true);
});

test('native Login sends its private fields only through stdin and rejects extra receipt fields',async()=>{
 const input={runId:ownedInput.runId,stepId:ownedInput.stepId,ticketId:ownedInput.profileId,countryCode:'240',
  phone:'799000000000',password:'Synthetic-private-password',messageId:'456'};
 for(const extra of [false,true]) {
  const f=fixture((request,send,child)=>{
   if(request.action==='native-login')send({runId:input.runId,stepId:input.stepId,passed:true,...(extra?{password:input.password}:{})});
   if(request.action==='close'){send({closed:true});queueMicrotask(()=>child.emit('close',0));}
  });
  const channel=await openIosDeepLinkChannel(f.options);
  if(extra)await assert.rejects(channel.nativeLogin(input),{message:'deep_link_ios_channel_unresolved'});
  else await channel.nativeLogin(input);
  assert.deepEqual(f.commands[0],{action:'native-login',input});
  assert.equal(JSON.stringify(f.get().launch).includes(input.password),false);
  assert.equal(JSON.stringify(f.get().launch).includes(input.phone),false);
  if(!extra)await channel.close();else assert.equal(channel.settled(),false);
 }
});

test('owned read returns a bounded private receipt and ACK contains only the read identity',async()=>{
  const receipt=ownedReceipt();receipt.privateSession.displayName='Synthetic'.repeat(650);
  const f=fixture((request,send,child)=>{
    if(request.action==='session')send(receipt);
    if(request.action==='read-ack')send({runId:request.runId,stepId:request.stepId,acknowledged:true});
    if(request.action==='close'){send({closed:true});queueMicrotask(()=>child.emit('close',0));}
  });
  const channel=await openIosDeepLinkChannel(f.options);
  assert.deepEqual(await channel.sessionStep(ownedInput),receipt);
  assert.equal(JSON.stringify(f.get().launch).includes(receipt.privateSession.refreshToken),false);
  assert.equal(JSON.stringify(f.commands).includes(receipt.privateSession.refreshToken),false);
  await channel.acknowledgeOwnedRead(ownedInput);
  assert.deepEqual(f.commands[1],{action:'read-ack',runId:ownedInput.runId,stepId:ownedInput.stepId});
  await channel.close();assert.equal(channel.settled(),true);
});

test('owned read rejects foreign ownership, mixed expiry, extra secrets, and oversized responses',async()=>{
  for(const change of [r=>{r.privateSession.profileId=ownedInput.authUserId;},r=>{r.privateSession.expiresAt+=1;},
    r=>{r.extra='unexpected';},r=>{r.privateSession.extra='unexpected';},r=>{r.privateSession.displayName='x'.repeat(33000);},
    r=>{r.stepId=ownedInput.runId;},r=>{delete r.privateSession;}]){
    const receipt=ownedReceipt();change(receipt);
    const f=fixture((request,send)=>send(receipt));const channel=await openIosDeepLinkChannel(f.options);
    await assert.rejects(channel.sessionStep(ownedInput),{message:'deep_link_ios_channel_unresolved'});
    assert.equal(channel.settled(),false);assert.equal(f.commands.length,1);
  }
});

test('owned read rejects malformed private commands before transmission',async()=>{
  const f=fixture(()=>{});const channel=await openIosDeepLinkChannel(f.options);
  for(const input of [{...ownedInput,accessToken:'must-not-send'},{...ownedInput,profileId:'wrong'}])
    await assert.rejects(channel.sessionStep(input),{message:'deep_link_ios_channel_unresolved'});
  assert.equal(f.commands.length,0);channel.abort();
});
test("wrong or extra receipt fields fail without exposing remote contents",async()=>{
  for(const reply of [{error:"private-access"},{runId:"run",stepId:"step",probe:true,verified:true,token:"private-access"}]){
    const f=fixture((request,send)=>send(reply));const channel=await openIosDeepLinkChannel(f.options);
    await assert.rejects(channel.probe({runId:"run",stepId:"step"}),{message:"deep_link_ios_channel_unresolved"});
    assert.equal(channel.settled(),false);assert.equal(f.commands.length,1);
  }
});
test("SSH exit zero without receipt is unresolved",async()=>{
  const f=fixture((request,send,child)=>child.emit("close",0));const channel=await openIosDeepLinkChannel(f.options);
  await assert.rejects(channel.close());assert.equal(channel.settled(),false);
});
test("close receipt followed by failed SSH exit is unresolved",async()=>{
  const f=fixture((request,send,child)=>{send({closed:true});queueMicrotask(()=>child.emit("close",1));});
  const channel=await openIosDeepLinkChannel(f.options);await assert.rejects(channel.close());assert.equal(channel.settled(),false);
});
test("timeout rejects once without sending a retry",async()=>{
  const f=fixture(()=>{});const channel=await openIosDeepLinkChannel({...f.options,timeoutMs:15});
  await assert.rejects(channel.probe({runId:"run",stepId:"step"}));
  await assert.rejects(channel.probe({runId:"run",stepId:"step"}));assert.equal(f.commands.length,1);
});
test("remote argument metacharacters are rejected before spawn",async()=>{
  for(const bad of [root+";echo",root+"/../other",root+" space"]){
    let called=false;await assert.rejects(openIosDeepLinkChannel({root:bad,products:bad+"/build",spawnImpl:()=>{called=true;}}));
    assert.equal(called,false);
  }
});
test("explicit abort rejects pending response and cannot prove remote closure",async()=>{
  const f=fixture(()=>{});const channel=await openIosDeepLinkChannel(f.options);
  const response=channel.probe({runId:"run",stepId:"step"});channel.abort();
  await assert.rejects(response);assert.equal(channel.settled(),false);
  await assert.rejects(channel.close());
});

test("negative receipts cannot be confused with ordinary or other negative acceptance",async()=>{
  for(const targetMode of ["missing-thread","missing-message"])for(const receivedMode of [undefined,"missing-thread","missing-message"]){
    const f=fixture((request,send,child)=>{
      if(request.action==="chat")send({runId:request.runId,stepId:request.stepId,mode:request.mode,passed:true,
        ...(receivedMode?{targetMode:receivedMode}:{})});
      if(request.action==="close"){send({closed:true});queueMicrotask(()=>child.emit("close",0));}
    });
    const channel=await openIosDeepLinkChannel(f.options);
    const action=channel.observeChat({runId:"run",stepId:"step",mode:"cold",targetMode});
    if(receivedMode===targetMode){await action;await channel.close();assert.equal(channel.settled(),true);}
    else {await assert.rejects(action);assert.equal(channel.settled(),false);}
  }
});
