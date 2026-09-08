import assert from "node:assert/strict";
import test from "node:test";
import {auditRecoveryProfileFixture} from "./e2e-fixtures/recovery-profile-fixture.mjs";

const canonical={display_name:"Fixture",nombre:"Fixture",neighborhood:"",barrio:"",
  country_code:"240",code:"240",phone_local:"123456",telefono:"123456",
  phone:"+240123456",avatar_url:null,avatar:null,contacts:0};
const audit=async(patch={})=>auditRecoveryProfileFixture({profileId:"profile",authUserId:"auth",
  client:{query:async(sql,values)=>{assert.deepEqual(values,["profile","auth"]);
    return {rowCount:1,rows:[{...canonical,...patch}]};}}});

test("fixed-point fixture returns only eligibility and an opaque baseline",async()=>{
  const result=await audit();assert.equal(result.eligible,true);
  assert.match(result.baselineDigest,/^[a-f0-9]{64}$/);
  assert.deepEqual(Object.keys(result),["eligible","baselineDigest"]);
  assert.notEqual((await audit({display_name:"Changed",nombre:"Changed"})).baselineDigest,result.baselineDigest);
});

test("Save normalization and emergency-contact rewriting are rejected",async()=>{
  for(const patch of [{nombre:"Legacy"},{barrio:null},{code:"0240"},{telefono:"123 456"},
    {phone:"123456"},{avatar_url:" https://example.test/a "},{avatar:"https://example.test/a"}]) {
    assert.deepEqual(await audit(patch),{eligible:false,reason:"profile_save_would_change_other_fields"});
  }
  assert.deepEqual(await audit({contacts:1}),{eligible:false,reason:"profile_has_emergency_contacts"});
});

test("identity mismatch and database errors never expose row contents",async()=>{
  const input={profileId:"profile",authUserId:"auth",client:{query:async()=>({rowCount:0,rows:[]})}};
  assert.deepEqual(await auditRecoveryProfileFixture(input),{eligible:false,reason:"profile_identity_mismatch"});
  input.client.query=async()=>{throw Error("private database detail");};
  await assert.rejects(auditRecoveryProfileFixture(input),{message:"recovery_profile_fixture_audit_failed"});
});
