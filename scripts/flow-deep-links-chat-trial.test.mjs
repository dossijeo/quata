import test from "node:test";
import assert from "node:assert/strict";
import {mkdir,mkdtemp,readdir,rm,readFile,writeFile} from "node:fs/promises";
import path from "node:path";
import {runDeepLinkChatTrial} from "./flow-deep-links-chat-trial.mjs";
import {openRecoveryJournal} from "./e2e-fixtures/recovery-private-journal.mjs";

const root=path.resolve("build-reports/flow-deep-links/local-coordinator-tests");
test("UI login requires paired preparation and request adapters before any side effect",async()=>{
  for(const adapter of [{prepareLogin:async()=>{}},{requestLogin:async()=>{}},{prepareLogin:true,requestLogin:async()=>{}}]) {
    await assert.rejects(runDeepLinkChatTrial({privateDirectory:root,preflight:async()=>{throw Error("unexpected preflight");},
      transportSettled:async()=>true,ui:{run:async()=>{},close:async()=>{},...adapter}}),
      /ui_login_configuration_invalid/);
  }
});
async function directory(run) {
  await mkdir(root,{recursive:true});const dir=await mkdtemp(path.join(root,"test-"));
  try {await run(dir);} finally {
    const resolved=path.resolve(dir);
    if(path.dirname(resolved)!==root || !path.basename(resolved).startsWith("test-"))throw Error("unsafe_test_cleanup");
    await rm(resolved,{recursive:true,force:true});
  }
}
test("failed preflight closes UI and releases lock without creating an actor",async()=>directory(async privateDirectory=>{
  let closed=false;
  const report=await runDeepLinkChatTrial({privateDirectory,preflight:async()=>false,transportSettled:async()=>true,
    ui:{run:async()=>{throw Error("unexpected UI");},close:async()=>{closed=true;}},
    client:{query:async()=>{throw Error("unexpected database");}}});
  assert.equal(closed,true);assert.equal(report.status,"failed");assert.equal(report.cleanupComplete,true);
  assert.deepEqual(await readdir(privateDirectory),[]);
}));
test("uncertain Admin request keeps the actual DPAPI journal and run lock",{skip:process.platform!=="win32"},async()=>directory(async privateDirectory=>{
  let calls=0;const sql=[];
  const report=await runDeepLinkChatTrial({privateDirectory,preflight:async()=>true,transportSettled:async()=>false,
    ui:{run:async()=>{throw Error("unexpected UI");},close:async()=>{}},
    client:{query:async query=>{sql.push(query);return {rows:[{auth_absent:true,profile_absent:true}]};}},
    adminRequest:async()=>{calls++;throw Error("simulated ambiguous response");}});
  assert.equal(calls,1);assert.equal(report.status,"failed_cleanup_pending");
  assert.equal(report.cleanupComplete,false);assert.equal(sql.length,1);
  const files=await readdir(privateDirectory);
  assert.equal(files.filter(file=>file.endsWith(".dpapi")).length,1);
  assert.ok(files.includes("flow-deep-links.lock"));
  const bytes=await readFile(path.join(privateDirectory,files.find(file=>file.endsWith(".dpapi"))));
  assert.ok(!bytes.toString().includes('"password"'));
  assert.ok(!JSON.stringify(report).includes("@example.invalid"));
  // A second invocation is rejected by the actual lock before any work.
  await assert.rejects(runDeepLinkChatTrial({privateDirectory,preflight:async()=>true,transportSettled:async()=>true,
    ui:{run:async()=>{},close:async()=>{}}}),{code:"EEXIST"});
}));
test("failed UI close preserves the lock and reports cleanup pending",async()=>directory(async privateDirectory=>{
  const report=await runDeepLinkChatTrial({privateDirectory,preflight:async()=>false,transportSettled:async()=>true,
    ui:{run:async()=>{},close:async()=>{throw Error("close failed");}}});
  assert.equal(report.uiCloseFailed,true);assert.equal(report.status,"failed_cleanup_pending");
  assert.deepEqual(await readdir(privateDirectory),["flow-deep-links.lock"]);
}));

for(const pendingOperation of ["refreshAttempt","revocation","iosSession"])test(`closed browser with unresolved ${pendingOperation} preserves journals before any retirement`,{skip:process.platform!=="win32"},async()=>directory(async privateDirectory=>{
  const sql=[];let identity;
  const report=await runDeepLinkChatTrial({privateDirectory,preflight:async()=>true,transportSettled:async()=>true,
    adminRequest:async request=>{
      const file=(await readdir(privateDirectory)).find(name=>name.endsWith(".dpapi"));
      identity={authUserId:request.body.id,runId:request.body.app_metadata.quata_e2e.run_id,profileId:file.slice(9,-6)};
      throw Error("synthetic stop before further fixture work");
    },
    client:{query:async query=>{sql.push(query);return {rows:[{auth_absent:true,profile_absent:true}]};}},
    ui:{run:async()=>{throw Error("unexpected UI");},close:async()=>{
      const journal=await openRecoveryJournal({file:path.join(privateDirectory,`recovery-${identity.profileId}.dpapi`),identity});
      const current=await journal.read();
      current.state.sessions.push({...identity,[pendingOperation]:{requestStarted:true,started:true}});
      await journal.checkpoint(current.state);
    }},
  });
  assert.equal(report.status,"failed_cleanup_pending");assert.equal(report.cleanupComplete,false);
  assert.equal(sql.length,1);
  const files=await readdir(privateDirectory);assert.ok(files.includes("flow-deep-links.lock"));
  assert.equal(files.filter(file=>file.endsWith(".dpapi")).length,1);
}));

test("two actor absence reconciliation retains both journals until both retirements finish",{skip:process.platform!=="win32"},async()=>directory(async privateDirectory=>{
  let creates=0,retirements=0;const journalCounts=[];
  const report=await runDeepLinkChatTrial({privateDirectory,preflight:async()=>true,transportSettled:async()=>true,
    ui:{run:async()=>{throw Error("unexpected UI");},close:async()=>{}},
    adminRequest:async request=>{
      if(++creates===2)throw Error("synthetic stop after two journals exist");
      return {status:200,body:{id:request.body.id}};
    },
    client:{query:async(query,args)=>{
      if(query.includes("as auth_absent"))return {rows:[{auth_absent:true,profile_absent:true}]};
      if(query.includes("select id from auth.users"))return {rowCount:1,rows:[{id:args[0]}]};
      if(query.includes("select id,email")) {
        retirements++;journalCounts.push((await readdir(privateDirectory)).filter(file=>file.endsWith(".dpapi")).length);
        return {rowCount:0,rows:[]};
      }
      if(query.includes("as auth,"))return {rows:[{auth:true,profile:true,legacy_profile:true,identities:true,
        sessions:true,web_sessions:true,directory:true,terms:true,storage:true}]};
      return {rowCount:0,rows:[]};
    }},
  });
  assert.equal(creates,2);assert.equal(retirements,2);assert.deepEqual(journalCounts,[2,2]);
  assert.equal(report.cleanupComplete,true);assert.equal(report.status,"failed");
  assert.deepEqual(await readdir(privateDirectory),[]);
}));
test("login timeout plus unreadable journal never starts retirement",{skip:process.platform!=="win32"},async()=>directory(async privateDirectory=>{
  const sql=[];let logins=0;
  const report=await runDeepLinkChatTrial({privateDirectory,backendUrl:"https://example.test",publicKey:"synthetic",
    preflight:async()=>true,transportSettled:async()=>true,ui:{run:async()=>{throw Error("unexpected UI");},close:async()=>{}},
    adminRequest:async request=>({status:200,body:{id:request.body.id}}),
    client:{query:async(query,args)=>{
      sql.push(query);
      if(query.includes("as auth_absent"))return {rows:[{auth_absent:true,profile_absent:true}]};
      if(query.includes("select id from auth.users"))return {rowCount:1,rows:[{id:args[0]}]};
      if(query.includes("as owned"))return {rowCount:1,rows:[{owned:true,unique_active:true,phone_matches:true,no_sessions:true}]};
      return {rowCount:0,rows:[]};
    }},
    fetchImpl:async()=>{
      logins++;
      for(const file of await readdir(privateDirectory))if(file.endsWith(".dpapi"))await writeFile(path.join(privateDirectory,file),"simulated unreadable journal");
      throw Error("simulated timeout");
    }});
  assert.equal(logins,1);assert.equal(report.status,"failed_cleanup_pending");
  assert.ok(!sql.some(query=>query.startsWith("delete ")||query.includes("select id,email")));
  const files=await readdir(privateDirectory);assert.ok(files.includes("flow-deep-links.lock"));
  assert.equal(files.filter(file=>file.endsWith(".dpapi")).length,2);
}));
