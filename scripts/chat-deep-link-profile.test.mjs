import test from "node:test";
import assert from "node:assert/strict";
import {createDeepLinkProfile,retireDeepLinkProfile} from "./e2e-fixtures/chat-deep-link-profile.mjs";

function fixture() {
  const record={runId:"11111111-1111-4111-8111-111111111111",profileId:"22222222-2222-4222-8222-222222222222",
    authUserId:"33333333-3333-4333-8333-333333333333",countryCode:"240",phone:"12345678901",
    email:"deep-link-33333333-3333-4333-8333-333333333333@example.invalid"};
  let saved={...record,state:{sessions:[]}};
  const events=[];const refs=[];
  const row=rows=>({rowCount:rows.length,rows});
  const client={query:async(sql,args)=>{
    events.push(sql);
    if(sql.includes("as auth_absent"))return row([{auth_absent:true,profile_absent:true}]);
    if(sql.includes("select id,email"))return row([{id:record.authUserId,email:record.email,owner:{unit:"FLOW-DEEP-LINKS",run_id:record.runId}}]);
    if(sql.includes("select id from auth.users"))return row([{id:record.authUserId}]);
    if(sql.includes("select id,auth_user_id"))return row([{id:record.profileId,auth_user_id:record.authUserId}]);
    if(sql.includes("from pg_constraint"))return row(refs);
    if(sql.includes("as count"))return row([{count:"0"}]);
    if(sql.includes("as legacy_profile"))return row([{auth:true,profile:true,legacy_profile:true,identities:true,sessions:true,web_sessions:true,directory:true,terms:true,storage:true}]);
    return row([]);
  }};
  const journal={read:async()=>structuredClone(saved),checkpoint:async state=>{events.push("checkpoint");saved={...saved,state:structuredClone(state)};}};
  const args={client,journal,record,password:"synthetic-password-only-123",operationsSettled:async()=>true,
    adminRequest:async request=>{events.push("admin-request");assert.equal(saved.state.profileCreationStarted,true);
      assert.equal(request.body.id,record.authUserId);return {status:200,body:{id:record.authUserId}};}};
  return {args,events,refs,state:()=>saved};
}
test("creation records intent before Auth and creates only a fresh profile",async()=>{
  const f=fixture();await createDeepLinkProfile(f.args);
  assert.ok(f.events.indexOf("checkpoint")<f.events.indexOf("admin-request"));
  assert.equal(f.state().state.authCreated,true);assert.equal(f.state().state.profileCreated,true);
  await assert.rejects(createDeepLinkProfile(f.args),/already_started/);
  assert.equal(f.events.filter(e=>e==="admin-request").length,1);
});
test("terms cleanup permits only the journaled fixture version and cascade contract",async()=>{
  for(const scenario of ["owned","other_version","not_journaled","wrong_fk"]) {
    const f=fixture();await createDeepLinkProfile(f.args);
    if(scenario==="not_journaled") {
      const value=await f.args.journal.read();delete value.state.fixtureTermsVersion;await f.args.journal.checkpoint(value.state);
    }
    f.refs.push({schema:"public",table:"ugc_terms_acceptances",column:"profile_id",parent:"community_profiles",
      key_count:1,parent_column:"id",delete_action:scenario==="wrong_fk"?"n":"c"});
    const query=f.args.client.query;
    f.args.client.query=async(sql,args)=>{
      if(sql.startsWith("select terms_version"))return {rows:[{terms_version:scenario==="other_version"?"unexpected":"2026-07"}]};
      if(sql.includes('"ugc_terms_acceptances"'))return {rows:[{count:"1"}]};
      return query(sql,args);
    };
    f.events.length=0;
    if(scenario==="owned")assert.deepEqual(await retireDeepLinkProfile(f.args),{retired:true});
    else {await assert.rejects(retireDeepLinkProfile(f.args),/retirement_unresolved/);assert.ok(!f.events.some(sql=>sql.startsWith("delete ")));}
  }
});
test("uncertain Auth response retains intent and forbids repeated creation",async()=>{
  const f=fixture();f.args.adminRequest=async()=>{throw Error("private transport detail");};
  await assert.rejects(createDeepLinkProfile(f.args),{message:"deep_link_profile_auth_creation_uncertain"});
  assert.equal(f.state().state.profileCreationStarted,true);
  await assert.rejects(createDeepLinkProfile(f.args),/already_started/);
});
test("collision or failed journal prevents Auth creation",async()=>{
  const f=fixture();f.args.client.query=async()=>({rows:[{auth_absent:false,profile_absent:true}]});
  await assert.rejects(createDeepLinkProfile(f.args),/collision/);assert.ok(!f.events.includes("admin-request"));
  const g=fixture();g.args.journal.checkpoint=async()=>{throw Error("disk full");};
  await assert.rejects(createDeepLinkProfile(g.args),/disk full/);assert.ok(!g.events.includes("admin-request"));
});
test("owned empty fixture retires exact identities after dependency audit",async()=>{
  const f=fixture();await createDeepLinkProfile(f.args);f.events.length=0;
  assert.deepEqual(await retireDeepLinkProfile(f.args),{retired:true});
  assert.equal(f.state().state.profileRetired,true);
  assert.equal(f.events.filter(sql=>sql.startsWith("delete ")).length,2);
  assert.ok(f.events.findIndex(sql=>sql.includes("from pg_constraint"))<f.events.findIndex(sql=>sql.startsWith("delete ")));
});
test("unknown nonempty dependency rolls back before deleting anything",async()=>{
  const f=fixture();await createDeepLinkProfile(f.args);f.events.length=0;
  f.refs.push({schema:"public",table:"unrelated",column:"owner",parent:"community_profiles",key_count:1,parent_column:"id",delete_action:"c"});
  const query=f.args.client.query;f.args.client.query=async(sql,args)=>sql.includes('"unrelated"')?{rows:[{count:"1"}]}:query(sql,args);
  await assert.rejects(retireDeepLinkProfile(f.args),/retirement_unresolved/);
  assert.ok(f.events.includes("rollback"));assert.ok(!f.events.some(sql=>sql.startsWith("delete ")));
});
test("successful retirement commit with failed journal checkpoint reconciles without repeating deletes",async()=>{
  const f=fixture();await createDeepLinkProfile(f.args);
  const checkpoint=f.args.journal.checkpoint;
  f.args.journal.checkpoint=async()=>{throw Error("disk full after commit");};
  await assert.rejects(retireDeepLinkProfile(f.args),/disk full/);
  f.args.journal.checkpoint=checkpoint;
  const query=f.args.client.query;f.args.client.query=async(sql,args)=>
    sql.includes("select id,email")||sql.includes("select id,auth_user_id")?{rowCount:0,rows:[]}:query(sql,args);
  f.events.length=0;
  assert.deepEqual(await retireDeepLinkProfile(f.args),{retired:true});
  assert.ok(!f.events.some(sql=>sql.startsWith("delete ")));
});
test("foreign identity, Storage and unsettled activity never permit deletion",async()=>{
  for(const scenario of ["owner","storage","activity"]) {
    const f=fixture();await createDeepLinkProfile(f.args);f.events.length=0;const query=f.args.client.query;
    if(scenario==="activity")f.args.operationsSettled=async()=>false;
    else f.args.client.query=async(sql,args)=>{
      if(scenario==="owner"&&sql.includes("select id,email"))return {rowCount:0,rows:[]};
      if(scenario==="storage"&&sql.includes("storage.objects"))return {rows:[{count:"1"}]};
      return query(sql,args);
    };
    await assert.rejects(retireDeepLinkProfile(f.args));assert.ok(!f.events.some(sql=>sql.startsWith("delete ")));
  }
});
test("only a journaled login allows the exact bridge-normalized email",async()=>{
  for(const scenario of ["planned_login","no_login","other_email"]) {
    const f=fixture();await createDeepLinkProfile(f.args);
    const state=(await f.args.journal.read()).state;
    if(scenario!=="no_login")state.sessions.push({runId:f.args.record.runId,profileId:f.args.record.profileId,
      authUserId:f.args.record.authUserId,requestStarted:true});
    await f.args.journal.checkpoint(state);f.events.length=0;
    const query=f.args.client.query;
    f.args.client.query=async(sql,args)=>{
      const result=await query(sql,args);
      if(sql.includes("select id,email"))result.rows[0].email=scenario==="other_email"?"unrelated@example.invalid":`${f.args.record.countryCode}${f.args.record.phone}@phone.quata.app`;
      return result;
    };
    if(scenario==="planned_login")assert.deepEqual(await retireDeepLinkProfile(f.args),{retired:true});
    else {await assert.rejects(retireDeepLinkProfile(f.args),/retirement_unresolved/);assert.ok(!f.events.some(sql=>sql.startsWith("delete ")));}
  }
});
