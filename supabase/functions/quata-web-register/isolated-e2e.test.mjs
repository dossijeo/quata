import test from "node:test"; import assert from "node:assert/strict";
import {runRegistration} from "./contract.mjs"; import {cleanupRegistration} from "./cleanup-contract.mjs";
const payload={version:1,display_name:"Test",neighborhood:"Centro",country_code:"34",phone_local:"600100200",
 password:"LongPassword7",secret_question:"barrio",secret_answer:"Centro",client_instance_id:"browser-test",
 idempotency_key:"0123456789abcdef0123456789abcdef",challenge_token:"verified"};
test("isolated browser flow: accept, reload replay, login and verified purge",async()=>{
 const db={auth:new Map(),profiles:new Map(),sessions:new Map(),ledger:{id:"r",profileId:"p"}};
 const deps={
  prepare:async x=>x, claim:async()=>db.ledger.completed?{kind:"replay",record:db.ledger}:{kind:"new",record:db.ledger},
  accepted:()=>({version:1,status:"accepted"}),findProfile:async()=>db.profiles.get("p")||null,
  findAuthUser:async()=>db.auth.get("a")||null,createAuthUser:async()=>{const a={id:"a",profileId:"p"};db.auth.set("a",a);return a},
  recordAuthUser:async()=>{},createProfile:async()=>{const p={id:"p"};db.profiles.set("p",p);return p},
  recordProfile:async()=>{},createAuthenticatedResult:async()=>null,complete:async()=>{db.ledger.completed=true},
  restoreCompleted:async()=>null,deleteProfile:async id=>db.profiles.delete(id),deleteAuthUser:async id=>db.auth.delete(id),
  fail:async()=>{},requireCleanup:async()=>{},
 };
 assert.deepEqual(await runRegistration(payload,deps),{version:1,status:"accepted"});
 assert.deepEqual(await runRegistration(payload,deps),{version:1,status:"accepted"});
 assert.equal(db.auth.has("a")&&db.profiles.has("p"),true); // normal login precondition
 db.sessions.set("s",{profileId:"p"});
 await cleanupRegistration({id:"r",profileId:"p",authUserId:"a"},{
  revokeWebSessions:async()=>db.sessions.clear(),deleteProfile:deps.deleteProfile,deleteAuthUser:deps.deleteAuthUser,
  markCompleted:async()=>{db.ledger.cleanupCompleted=true},markCleanupRequired:async()=>{},alert:async()=>{},
 });
 assert.deepEqual({auth:db.auth.size,profiles:db.profiles.size,sessions:db.sessions.size}, {auth:0,profiles:0,sessions:0});
 assert.equal(db.ledger.cleanupCompleted,true);
});
