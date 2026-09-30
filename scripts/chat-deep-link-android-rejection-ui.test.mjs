import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,rm} from 'node:fs/promises';
import path from 'node:path';
import {createAndroidDeepLinkUi} from './e2e-fixtures/chat-deep-link-android.mjs';
const root=path.resolve('build-reports/android-external-sender/rejection-ui-contracts');
const body='Deep link 00000000-0000-4000-8000-000000000001';
test('rejection mode excludes renewal and missing targets before device access',()=>{
  for(const extra of [{nativeRejectionMode:'cold',nativeRenewalMode:'cold'},
    {nativeRejectionMode:'cold',targetMode:'missing-thread'}])
    assert.throws(()=>createAndroidDeepLinkUi({channel:{},adb:'synthetic',serial:'emulator-5560',evidenceDirectory:root,...extra}));
});
test('warm rejection first proves the exact chat in one cold delivery, then preserves its product PID through rejection',async()=>{
  await mkdir(root,{recursive:true});const directory=await mkdtemp(path.join(root,'test-'));
  let pidReads=0,instruments=0,pulls=0,clocks=0,lastRunId,lastUrl;const signals=[];
  const pidValues=['','1234','1234','1234','1234','1234'];
  const execute=async(_file,args)=>{
    if(args.includes('pidof'))return {stdout:pidValues[pidReads++]??'1234'};
    if(args.includes('date'))return {stdout:++clocks===1?'1800000000.1':'1800000002.1'};
    if(args.includes('kill')){signals.push(args[args.indexOf('kill')+1]);return {stdout:'',stderr:''};}
    if(args.includes('instrument')) {
      instruments++;const value=key=>args.includes(key)?args[args.indexOf(key)+1]:undefined;lastRunId=value('runId');lastUrl=value('publicUrl');
      assert.equal(value('expectedMessageId'),instruments===1?'456':undefined);
      assert.equal(value('anonymousAction'),instruments===2?'cancel':undefined);
      return {stdout:'OK (1 test)\nINSTRUMENTATION_CODE: -1',stderr:''};
    }
    if(args.includes('pull')) {
      pulls++;const destination=args.at(-1);await mkdir(destination);
      await writeFile(path.join(destination,'report.json'),JSON.stringify({runId:lastRunId,url:lastUrl,
        status:pulls===1?'chat_passed_pending_visual_review':'anonymous_passed_pending_visual_review',
        ...(pulls===1?{focusedMessageId:'456'}:{anonymousAction:'cancel'}),postExitObservationMs:2000,
        senderPackage:'com.quata.deeplinksender',beforeForegroundPackage:pulls===1?null:'com.quata',
        resolvedPackage:'com.quata',explicitPackage:null,explicitComponent:null}));return {stdout:'',stderr:''};
    }
    if(args.includes('logcat'))return {stdout:'1800000001.1 1234 1 W SupabaseHttpClient: Supabase session refresh failed with status=401'};
    assert.fail('unexpected command');
  };
  try {
    const ui=createAndroidDeepLinkUi({channel:{},adb:'synthetic',serial:'emulator-5560',evidenceDirectory:directory,nativeRejectionMode:'warm',execute});
    const args={target:{threadId:'123',messageId:'456'},body};
    const prelude=await ui.prepareRejection(args);
    assert.equal(prelude.receipt.afterPid,'1234');assert.equal(prelude.receipt.focusedMessageId,'456');
    const result=await ui.run(args),receipt=result.receipts[0];
    assert.equal(receipt.mode,'warm');assert.equal(receipt.beforePid,'1234');assert.equal(receipt.afterPid,'1234');
    assert.equal(receipt.rejection.status,401);assert.equal(instruments,2);assert.deepEqual(signals,['-STOP','-CONT']);await ui.close();
  } finally {await rm(directory,{recursive:true,force:true});}
});
test('lost STOP response retains the PID so close issues the compensating CONT',async()=>{
  await mkdir(root,{recursive:true});const directory=await mkdtemp(path.join(root,'test-'));
  let pidReads=0,lastRunId,lastUrl;const signals=[];
  const execute=async(_file,args)=>{
    if(args.includes('pidof'))return {stdout:['','1234','1234'][pidReads++]??'1234'};
    if(args.includes('instrument')) {
      const value=key=>args[args.indexOf(key)+1];lastRunId=value('runId');lastUrl=value('publicUrl');
      return {stdout:'OK (1 test)\nINSTRUMENTATION_CODE: -1',stderr:''};
    }
    if(args.includes('pull')) {
      const destination=args.at(-1);await mkdir(destination);await writeFile(path.join(destination,'report.json'),JSON.stringify({
        runId:lastRunId,url:lastUrl,status:'chat_passed_pending_visual_review',focusedMessageId:'456',
        senderPackage:'com.quata.deeplinksender',beforeForegroundPackage:null,resolvedPackage:'com.quata',
        explicitPackage:null,explicitComponent:null}));return {stdout:'',stderr:''};
    }
    if(args.includes('kill')) {
      const signal=args[args.indexOf('kill')+1];signals.push(signal);
      if(signal==='-STOP')throw Object.assign(Error('lost'),{code:1});
      return {stdout:'',stderr:''};
    }
    assert.fail('unexpected command');
  };
  try {
    const ui=createAndroidDeepLinkUi({channel:{},adb:'synthetic',serial:'emulator-5560',evidenceDirectory:directory,
      nativeRejectionMode:'warm',execute});
    await assert.rejects(ui.prepareRejection({target:{threadId:'123',messageId:'456'},body}));
    await ui.close();assert.deepEqual(signals,['-STOP','-CONT']);
  } finally {await rm(directory,{recursive:true,force:true});}
});
for(const outcome of ['complete','no-http','foreign-pid','wrong-run','wrong-action','wrong-url','focused-chat','warm-start','lost-delivery'])
test(`native rejection UI ${outcome} binds external target, barrier and bounded HTTP witness`,async()=>{
  await mkdir(root,{recursive:true});const directory=await mkdtemp(path.join(root,'test-'));
  let pidReads=0,clocks=0,runId,url;const calls=[];
  const execute=async(_file,args)=>{
    calls.push(args);
    if(args.includes('pidof'))return {stdout:++pidReads===1&&outcome!=='warm-start'?'':'1234'};
    if(args.includes('date'))return {stdout:++clocks===1?'1800000000.123456789':'1800000002.123456789'};
    if(args.includes('instrument')) {
      if(outcome==='lost-delivery')throw Error('private transport error');
      const value=key=>args[args.indexOf(key)+1];runId=value('runId');url=value('publicUrl');
      assert.equal(value('anonymousAction'),'cancel');
      assert.equal(args.includes('expectedMessageId'),false);
      assert.equal(args.includes('expectedMarker'),false);
      assert.equal(url,'https://egquata.com/#chat-sb%3A123?message=456');
      return {stdout:'OK (1 test)\nINSTRUMENTATION_CODE: -1',stderr:''};
    }
    if(args.includes('pull')) {
      const destination=args.at(-1);await mkdir(destination);
      const report={runId:outcome==='wrong-run'?'foreign':runId,url:outcome==='wrong-url'?'https://example.test':url,
        status:'anonymous_passed_pending_visual_review',anonymousAction:outcome==='wrong-action'?'open-login-back':'cancel',
        postExitObservationMs:2000,senderPackage:'com.quata.deeplinksender',resolvedPackage:'com.quata',explicitPackage:null,explicitComponent:null,
        ...(outcome==='focused-chat'?{focusedMessageId:'456'}:{})};
      await writeFile(path.join(destination,'report.json'),JSON.stringify(report));return {stdout:'',stderr:''};
    }
    if(args.includes('logcat'))return {stdout:outcome==='no-http'?'':
      `1800000001.123456 ${outcome==='foreign-pid'?'9999':'1234'} 5678 W SupabaseHttpClient: Supabase session refresh failed with status=400`};
    assert.fail('unexpected command');
  };
  const ui=createAndroidDeepLinkUi({channel:{},adb:'synthetic',serial:'emulator-5560',evidenceDirectory:directory,nativeRejectionMode:'cold',execute});
  try {
    const action=ui.run({target:{threadId:'123',messageId:'456'},body});
    if(outcome==='complete') {
      const result=await action;assert.equal(result.passed,true);assert.equal(result.receipts.length,1);
      const receipt=result.receipts[0];assert.equal(receipt.rejection.status,400);assert.equal(receipt.rejection.pid,receipt.afterPid);
      assert.equal(receipt.beforePid,null);assert.equal(receipt.focusedMessageId,undefined);
      assert.equal(receipt.targetMessageId,'456');assert.equal(receipt.anonymousAction,'cancel');
    }else await assert.rejects(action);
    if(outcome==='lost-delivery')await assert.rejects(ui.close());else await ui.close();
    await assert.rejects(ui.run({target:{threadId:'123',messageId:'456'},body}));
    assert.ok(calls.filter(args=>args.includes('instrument')).length<=1);
    assert.equal(calls.some(args=>args.includes('force-stop')||args.includes('logcat')&&args.includes('-c')),false);
  }finally {
    assert.equal(path.dirname(path.resolve(directory)),root);assert.ok(path.basename(directory).startsWith('test-'));
    await rm(directory,{recursive:true,force:true});
  }
});
