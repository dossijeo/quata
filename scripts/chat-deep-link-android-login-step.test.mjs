import test from 'node:test';
import assert from 'node:assert/strict';
import {validateAndroidDeepLinkLoginInput,runAndroidDeepLinkLoginStep} from './e2e-fixtures/chat-deep-link-android-login-step.mjs';
const input={runId:'00000000-0000-4000-8000-000000000001',stepId:'00000000-0000-4000-8000-000000000002',
  countryCode:'240',phone:'99123456789',password:'synthetic-only-private-password',messageId:'123'};
test('private login transport refuses mixed or malformed commands before creating evidence or spawning adb',async()=>{
  assert.equal(validateAndroidDeepLinkLoginInput(input),undefined);
  for(const value of [{...input,accessToken:'must-not-be-accepted'},{...input,phone:'not-a-phone'},
    {...input,countryCode:'34'},{...input,runId:'wrong'},{...input,stepId:'wrong'},
    {...input,messageId:'0'},{...input,password:''},{...input,password:'x'.repeat(129)}]) {
    await assert.rejects(runAndroidDeepLinkLoginStep({adb:'must-not-spawn',serial:'emulator-5560',input:value,evidenceDirectory:'must-not-create'}),
      {message:'deep_link_android_login_input_invalid'});
  }
});
