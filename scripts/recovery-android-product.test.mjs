import assert from "node:assert/strict";
import test from "node:test";
import {EventEmitter} from "node:events";
import {createRecoveryAndroidProduct} from "./e2e-fixtures/recovery-android-product.mjs";

function fixture({timeout=1000}={}){
  const requests=[];let closeCalls=0,credentials;
  class Socket extends EventEmitter {
    setEncoding(value){assert.equal(value,"utf8");}
    write(line,callback){requests.push(JSON.parse(line));callback();}
    destroy(){this.emit("close");}
  }
  const socket=new Socket();
  const product=createRecoveryAndroidProduct({socket,timeout,record:{profileId:"profile",authUserId:"auth",countryCode:"240",phone:"synthetic"},
    verifyActor:async(record,ticket,value)=>{credentials=value;assert.equal(value.accessToken,"synthetic-token");return value.profileId===record.profileId;},
    closeResources:async()=>{closeCalls++;return true;}});
  const reply=result=>socket.emit("data",JSON.stringify({id:requests.at(-1).id,ok:true,result})+"\n");
  return {socket,product,requests,reply,get:()=>({closeCalls,credentials})};
}

test("Android adapter binds native login then serializes focal commands and clears private bearer",async()=>{
  const f=fixture();
  await assert.rejects(f.product.login("original",{clientInstanceId:"web"}),/native_ticket_required/);
  const login=f.product.login("original",{kind:"native",ticketId:"ticket"});
  f.reply({profileId:"profile",accessToken:"synthetic-token"});assert.equal(await login,true);
  assert.equal(f.get().credentials.accessToken,null);
  for(const [method,args,action] of [["openAccount",[],"open"],["configureSecret",["madre","synthetic-answer"],"configure"],["saveSecret",[],"save"],["logout",[],"logout"],["recoverPassword",["answer","temporary"],"recover"]]){
    const result=f.product[method](...args);assert.equal(f.requests.at(-1).action,action);f.reply(true);assert.equal(await result,true);
  }
  await assert.rejects(f.product.saveSecret(),/save_once/);
  assert.equal(f.product.operationsSettled(),true);
  const close=f.product.close();f.reply(true);assert.equal(await close,true);
  assert.equal(await f.product.close(),true);assert.equal(f.get().closeCalls,1);
});

test("a timeout retains uncertainty, disallows another command and cannot certify resources by socket closure alone",async()=>{
  const f=fixture({timeout:10});
  await assert.rejects(f.product.openAccount(),/channel_uncertain/);
  assert.equal(f.product.operationsSettled(),false);
  await assert.rejects(f.product.logout(),/channel_unavailable/);
  assert.equal(await f.product.close(),false);assert.equal(await f.product.close(),false);
  assert.equal(f.requests.length,1);assert.equal(f.get().closeCalls,1);
});

test("concurrent calls, wrong response identities and oversized frames cannot advance the protocol",async()=>{
  for(const scenario of ["identity","size","error"]){
    const f=fixture();const waiting=f.product.openAccount();
    await assert.rejects(f.product.logout(),/channel_unavailable/);
    if(scenario==="identity")f.socket.emit("data",JSON.stringify({id:9,ok:true,result:true})+"\n");
    if(scenario==="size")f.socket.emit("data","x".repeat(65537));
    if(scenario==="error")f.socket.emit("error",Error("private transport detail"));
    await assert.rejects(waiting,/channel_uncertain/);
    assert.equal(f.product.operationsSettled(),false);assert.equal(await f.product.close(),false);
  }
});

test("native failure diagnostics expose only fixed focal stages and retain uncertainty",async()=>{
  for(const phase of ["read_opening","read_details","read_verification","recovery_opening","recovery_login","recovery_form","recovery_question","reset_started","recovery_return","recovery_still_open","recovery_destination_missing","synthetic-private-detail"]){
    const f=fixture();const waiting=f.product.readPermittedState();
    f.socket.emit("data",JSON.stringify({id:f.requests.at(-1).id,ok:false,phase})+"\n");
    await assert.rejects(waiting,/channel_uncertain/);
    assert.equal(f.product.failurePhase(),phase!=="synthetic-private-detail"?phase:undefined);
    assert.equal(f.product.operationsSettled(),false);
    assert.equal(await f.product.close(),false);
  }
});
