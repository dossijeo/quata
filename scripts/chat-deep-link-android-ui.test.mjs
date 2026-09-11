import test from "node:test";
import assert from "node:assert/strict";
import path from "node:path";
import {createAndroidDeepLinkUi} from "./e2e-fixtures/chat-deep-link-android.mjs";
const options={channel:{},adb:"must-not-execute",serial:"emulator-5560",evidenceDirectory:path.resolve("unused-evidence")};
const body="Deep link 00000000-0000-4000-8000-000000000001";

test("Android rejects unsupported negative scenarios before device access",()=>{
  for(const targetMode of ["missing-message","revoked",""])
    assert.throws(()=>createAndroidDeepLinkUi({...options,targetMode}),/deep_link_android_ui_invalid/);
});

test("Android missing-thread observer requires a distinct owned control thread",async()=>{
  for(const ownedThreadId of [undefined,"",0,"12","invalid"]){
    const ui=createAndroidDeepLinkUi({...options,targetMode:"missing-thread"});
    await assert.rejects(ui.run({target:{threadId:"12",messageId:"14",ownedThreadId},body}),/deep_link_android_ui_invalid/);
    await ui.close();
  }
});

test("Android positive observer cannot silently certify a negative target",async()=>{
  for(const extra of [{ownedThreadId:"15"},{visibleMessageId:"16"}]){
    const ui=createAndroidDeepLinkUi(options);
    await assert.rejects(ui.run({target:{threadId:"12",messageId:"14",...extra},body}),/deep_link_android_ui_invalid/);
  }
});
