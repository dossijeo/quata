import test from "node:test"; import assert from "node:assert/strict";
import {parseRegistrationConfig,isAllowedOrigin,verifyTurnstileChallenge} from "./http-policy.mjs";
const base={SUPABASE_URL:"https://x",SUPABASE_SERVICE_ROLE_KEY:"s",QUATA_WEB_REGISTRATION_API_KEY:"p",
 QUATA_WEB_REGISTRATION_PEPPER:"a".repeat(32),QUATA_INTERNAL_AUTH_PASSWORD_SECRET:"b".repeat(32),
 QUATA_INTERNAL_AUTH_PASSWORD_SECRET_VERSION:"v1",
 QUATA_WEB_REGISTRATION_ENABLED:"true",QUATA_WEB_REGISTRATION_TURNSTILE_SECRET:"t",
 QUATA_REGISTRATION_QUARANTINE_ENABLED:"true",
 QUATA_WEB_REGISTRATION_ALLOWED_ORIGINS:"https://app.example"};
base.QUATA_TURNSTILE_ALLOWED_HOSTNAMES="app.example";
test("config is fail closed and exact origins only",()=>{
 assert.throws(()=>parseRegistrationConfig(n=>({...base,QUATA_WEB_REGISTRATION_TURNSTILE_SECRET:""})[n]));
 const c=parseRegistrationConfig(n=>base[n]); assert.equal(isAllowedOrigin("https://app.example",c.allowedOrigins),true);
 assert.equal(isAllowedOrigin("https://evil.example",c.allowedOrigins),false);
});
test("Turnstile sends trusted IP and rejects failed challenge",async()=>{
 let sent; const ok=await verifyTurnstileChallenge("s","token","1.2.3.4","register_web",["app.example"],async(_u,o)=>(sent=o.body,{ok:true,json:async()=>({success:true,action:"register_web",hostname:"app.example"})}));
 assert.equal(ok,true); assert.equal(sent.get("remoteip"),"1.2.3.4");
 assert.equal(await verifyTurnstileChallenge("s","",null,"register_web",["app.example"],async()=>{throw Error("called")}),false);
});
