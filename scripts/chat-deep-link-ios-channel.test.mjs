import test from "node:test";
import assert from "node:assert/strict";
import {EventEmitter} from "node:events";
import {PassThrough,Writable} from "node:stream";
import {openIosDeepLinkChannel} from "./e2e-fixtures/chat-deep-link-ios-channel.mjs";
const root="/Users/gabriel/StudioProjects/quata-flow-deep-links-edbb970b";
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

test("missing-thread receipt cannot be confused with ordinary message acceptance",async()=>{
  for(const includeMode of [false,true]){
    const f=fixture((request,send,child)=>{
      if(request.action==="chat")send({runId:request.runId,stepId:request.stepId,mode:request.mode,passed:true,
        ...(includeMode?{targetMode:"missing-thread"}:{})});
      if(request.action==="close"){send({closed:true});queueMicrotask(()=>child.emit("close",0));}
    });
    const channel=await openIosDeepLinkChannel(f.options);
    const action=channel.observeChat({runId:"run",stepId:"step",mode:"cold",targetMode:"missing-thread"});
    if(includeMode){await action;await channel.close();assert.equal(channel.settled(),true);}
    else {await assert.rejects(action);assert.equal(channel.settled(),false);}
  }
});
