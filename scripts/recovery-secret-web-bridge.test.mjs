import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { runInNewContext } from "node:vm";
import test from "node:test";

const source = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebRecoverySecretE2eBridge.kt", import.meta.url), "utf8");
const javascript = source.match(/@JsFun\(\s*"""([\s\S]*?)"""/)[1];
function install({hostname="localhost",query=true,optIn=true}={}) {
  const context={location:{hostname,search:query?"?quata-recovery-secret-e2e=1":""},URLSearchParams,
    localStorage:{getItem:()=>optIn?"I_ACCEPT_ACCOUNT_RECOVERY_SECRET_FIXTURE":null}};
  const calls=[];
  const dispose=runInNewContext(`(${javascript})`,context)(()=>calls.push("open"),(q,a)=>calls.push([q,a]),()=>calls.push("save"),()=>"true\u001Fpet\u001Ftrue\u001Ffalse\u001Ffalse\u001Ftrue");
  return {context,calls,dispose,bridge:context.__quataRecoverySecretE2eProduct};
}

test("recovery bridge requires localhost plus its own two opt-ins",()=>{
  for(const options of [{hostname:"quata.example"},{query:false},{optIn:false}])assert.equal(install(options).bridge,undefined);
  const h=install();assert.equal(h.bridge.version,1);
  h.context.location.search="";
  for(const action of [()=>h.bridge.open(),()=>h.bridge.configure("pet","synthetic"),()=>h.bridge.save(),()=>h.bridge.snapshot()])assert.throws(action,/not_enabled/);
  assert.deepEqual(h.calls,[]);
});

test("recovery bridge invokes focal callbacks and exposes only permitted state",()=>{
  const h=install();h.bridge.open();h.bridge.configure("pet","synthetic-private-answer");h.bridge.save();
  assert.deepEqual(h.calls,["open",["pet","synthetic-private-answer"],"save"]);
  const state=JSON.parse(JSON.stringify(h.bridge.snapshot()));
  assert.deepEqual(state,{visible:true,question:"pet",answerEmpty:true,saving:false,failed:false,saved:true});
  assert.equal(JSON.stringify(state).includes("synthetic-private-answer"),false);
  assert.throws(()=>h.bridge.configure({},"answer"),/input_invalid/);
  h.dispose();assert.equal(h.context.__quataRecoverySecretE2eProduct,undefined);
});

test("independent recovery bridge does not own or extend ACCOUNT-DETAILS",()=>{
  assert.doesNotMatch(source,/__quataAccountDetails|account_details|updateDetails|displayName|neighborhood|countryCode/);
});
