import test from "node:test";
import assert from "node:assert/strict";
import {randomUUID,createHash} from "node:crypto";
import {mkdtemp,readdir,rm} from "node:fs/promises";
import {tmpdir} from "node:os";
import path from "node:path";
import vm from "node:vm";
import {EventEmitter} from "node:events";
import {runRecoveryWebEvidence} from "./account-recovery-secret-web.mjs";

// Plumbing integration only: real DPAPI, simulated DB/Auth and browser callbacks.
test("assembled coordinator roundtrip persists receipts and restores synthetic state",{skip:process.platform!=="win32"},async()=>{
  const directory=await mkdtemp(path.join(tmpdir(),"quata-recovery-assembly-test-"));
  const record={runId:randomUUID(),profileId:randomUUID(),authUserId:randomUUID(),countryCode:"240",phone:"synthetic-phone",
    originalPassword:"synthetic-original",temporaryPassword:"synthetic-temporary",temporaryQuestion:"pet",temporaryAnswer:"synthetic-answer"};
  const original={secret_question:"old",secret_answer:"synthetic-old-answer"};
  let secret={...original},password=record.originalPassword,saves=0,resets=0,closed=0;
  const auth=new Set(),web=new Map(),storage=new Map(),attrs=new Map();
  const hash=value=>createHash("sha256").update(value).digest("hex");
  const reply=(status,body)=>({status,ok:status>=200&&status<300,json:async()=>body});
  const fetchImpl=async(url,options)=>{
    if(new URL(url).pathname==="/auth/v1/user"){
      const claims=JSON.parse(Buffer.from(options.headers.Authorization.split(".")[1],"base64url"));
      return reply(auth.has(claims.session_id)?200:401,{id:record.authUserId});
    }
    const request=JSON.parse(options.body);
    if(request.action==="update_recovery_secret")return reply(401,{error:"authentication_required"});
    if(request.action==="recovery_question")return reply(200,{secret_question:secret.secret_question});
    if(request.action==="reset_password"){
      assert.equal(request.secret_answer,secret.secret_answer);password=request.new_password;resets++;return reply(200,{ok:true});
    }
    assert.equal(request.action,"web_login");
    if(request.password!==password)return reply(401,{error:"invalid_credentials"});
    const id=randomUUID(),webId=randomUUID(),token=randomUUID();auth.add(id);
    web.set(webId,{client:request.client_instance_id,hash:hash(token),revoked:false});
    const access=`synthetic.${Buffer.from(JSON.stringify({sub:record.authUserId,session_id:id})).toString("base64url")}.signature`;
    return reply(200,{session:{access_token:access},web_session:{id:webId,token}});
  };
  const client={query:async(sql,values=[])=>{
    if(["begin","commit","rollback"].includes(sql))return {rowCount:0};
    if(sql.startsWith("select p.display_name"))return {rowCount:1,rows:[{display_name:"Fixture",nombre:"Fixture",neighborhood:"",barrio:"",country_code:"240",code:"240",phone_local:"123456",telefono:"123456",phone:"+240123456",avatar_url:null,avatar:null,contacts:0}]};
    if(sql.includes("linked_profiles"))return {rowCount:1,rows:[{account_status:"active",linked_profiles:1,unowned_auth_sessions:[...auth].filter(id=>!values[2].includes(id)).length,unrevoked_web_sessions:[...web.values()].filter(w=>!w.revoked).length}]};
    if(sql.startsWith("select secret_question"))return {rowCount:1,rows:[{...secret}]};
    if(sql.startsWith("select (secret_question"))return {rowCount:1,rows:[{matches:secret.secret_question===values[2]&&secret.secret_answer===values[3]}]};
    if(sql.startsWith("select s.id")){
      const matches=[...web].filter(([,w])=>w.client===values[3]&&w.hash===values[4]&&!w.revoked);
      return {rowCount:auth.has(values[0])?matches.length:0,rows:matches.map(([id])=>({auth_session_id:values[0],web_session_id:id}))};
    }
    if(sql.startsWith("update public.community_profiles")){
      assert.deepEqual(values.slice(4),[secret.secret_question,secret.secret_answer]);assert.equal(password,record.originalPassword);
      secret={secret_question:values[0],secret_answer:values[1]};return {rowCount:1};
    }
    if(sql.startsWith("update public.web_client_sessions")){web.get(values[0]).revoked=true;return {rowCount:1};}
    if(sql.startsWith("delete from auth.sessions")){auth.delete(values[0]);return {rowCount:1};}
    if(sql.includes("auth_count"))return {rowCount:1,rows:[{auth_count:auth.has(values[0])?1:0,web_count:web.get(values[2])?.revoked?0:1}]};
    throw Error("unexpected_simulated_query");
  }};
  let state={visible:false,question:"old",answerEmpty:true,saving:false,failed:false,saved:false};
  const context=vm.createContext({location:{hash:""},localStorage:{setItem:(k,v)=>storage.set(k,v),getItem:k=>storage.get(k)??null},
    document:{documentElement:{getAttribute:k=>attrs.get(k)??null,hasAttribute:k=>attrs.has(k)}}});
  const call=async request=>(await fetchImpl("https://backend.example/functions/v1/quata-auth-bridge",{body:JSON.stringify(request)})).json();
  context.__quataAuthE2eProduct={version:1,login:async(countryCode,phone,candidate)=>{
    const body=await call({action:"web_login",password:candidate,client_instance_id:storage.get("quata_web_client_instance_id")});
    storage.set("quata_web_user_id",record.profileId);storage.set("quata_web_access_token",body.session.access_token);storage.set("quata_web_session_token",body.web_session.token);
    attrs.set("data-quata-ugc-terms-profile-id",record.profileId);return "authenticated";},
    logout:async()=>{attrs.delete("data-quata-ugc-terms-profile-id");return "logged_out";},
    openRecovery:()=>attrs.set("data-quata-auth-destination","recovery"),recoveryQuestion:async()=>secret.secret_question,
    resetPassword:async(_,__,answer,newPassword)=>{await call({action:"reset_password",secret_answer:answer,new_password:newPassword});return "password_reset";}};
  context.__quataRecoverySecretE2eProduct={version:1,open:()=>{state.visible=true;},
    configure:(question,answer)=>{state={...state,question,answerEmpty:false};assert.equal(answer,record.temporaryAnswer);},
    save:()=>{saves++;secret={secret_question:state.question,secret_answer:record.temporaryAnswer};state={...state,answerEmpty:true,saved:true};},snapshot:()=>({...state})};
  const page=new EventEmitter();page.url=()=>"http://localhost:8000/?quata-recovery-secret-e2e=1";
  page.evaluate=async(fn,arg)=>{context.argument=arg;return vm.runInContext(`(${fn.toString()})(argument)`,context);};
  page.waitForFunction=async(fn,arg)=>assert.equal(await page.evaluate(fn,arg),true);
  try {
    const report=await runRecoveryWebEvidence({client,record,directory,backendUrl:"https://backend.example",publicKey:"public",fetchImpl,
      verifyCandidate:async()=>true,openPage:async()=>page,closeResources:async()=>{closed++;return true;}});
    assert.equal(report.status,"passed",JSON.stringify(report));assert.equal(saves,1);assert.equal(resets,2);assert.equal(closed,1);
    assert.deepEqual(secret,original);assert.equal(password,record.originalPassword);assert.equal(auth.size,0);
    assert.ok([...web.values()].every(w=>w.revoked));assert.deepEqual(await readdir(directory),[]);
    for(const value of [record.originalPassword,record.temporaryAnswer])assert.equal(JSON.stringify(report).includes(value),false);
  } finally {
    assert.equal(path.dirname(path.resolve(directory)),path.resolve(tmpdir()));
    assert.ok(path.basename(directory).startsWith("quata-recovery-assembly-test-"));
    await rm(directory,{recursive:true,force:true});
  }
});
