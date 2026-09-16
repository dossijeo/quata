import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,readFile} from 'node:fs/promises';
import {unlinkSync,mkdirSync} from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {runAndroidNotificationReplyStep} from './e2e-fixtures/notification-reply-android-step.mjs';

async function fixture(mode='submit',failure) {
  const evidenceDirectory=await mkdtemp(path.join(os.tmpdir(),'quata-android-reply-step-'));
  const input={runId:randomUUID(),stepId:randomUUID(),profileId:randomUUID(),threadId:'123',
    ...(mode==='submit'?{authUserId:randomUUID()}:{attemptStepId:randomUUID()})};
  const submit=mode==='submit',runner=submit?'androidx.test.runner.AndroidJUnitRunner':'com.quata.core.navigation.DeepLinkSessionCustodyRunner';
  const testClass=`com.quata.core.notifications.NotificationReply${submit?'Product':'Reconciliation'}InstrumentedTest`;
  const method=submit?'submitsOneReplyThroughSystemUi':mode==='observe'?'observesOwnedNotificationAbsent':
    mode==='cleanup-empty'?'reconcilesEmptyPreIntentFailure':'reconcilesOnlyOwnedNotification';
  const receipt=submit?{runId:input.runId,stepId:input.stepId,submittedBySystemUi:true,backendVerified:false,
    replyMarker:`qadata-reply-text-${input.stepId}`,notificationMarker:`qadata-reply-alert-${input.stepId}`}:
    {runId:input.runId,stepId:input.stepId,attemptStepId:input.attemptStepId,notificationRemoved:true,backendVerified:false,reconciled:mode!=='observe',
      ...(mode==='cleanup-empty'?{intentAbsent:true}:{})};
  const calls=[];let pidChecks=0;
  const execute=async(adb,args)=>{
    calls.push(args);assert.equal(args[0],'-s');assert.equal(args[1],'emulator-5562');
    if(args.includes('pidof')) {
      pidChecks++;
      if((failure==='live-before'&&pidChecks===1)||(failure==='live-after'&&pidChecks===2))return {stdout:'1234\n',stderr:''};
      if(failure==='adb-error')throw Object.assign(Error('offline'),{code:1,stdout:'',stderr:'device offline'});
      throw Object.assign(Error('absent'),{code:1,stdout:'',stderr:''});
    }
    if(args.includes('instrumentation'))return {stdout:`instrumentation:com.quata.test/${failure==='wrong-runner'?'other.Runner':runner} (target=com.quata)\n`,stderr:''};
    if(args.includes('instrument')) {
      const intent=JSON.parse(await readFile(path.join(evidenceDirectory,input.stepId,'intent.json'),'utf8'));
      assert.equal(intent.input.stepId,input.stepId);
      assert.ok(args.includes(`${testClass}#${method}`));
      if(failure==='timeout')throw Object.assign(Error('timeout'),{killed:true,signal:'SIGTERM',stdout:'partial native status\n',stderr:'partial transport error\n'});
      return {stdout:`INSTRUMENTATION_STATUS: class=${testClass}\nINSTRUMENTATION_STATUS: test=${method}\nINSTRUMENTATION_STATUS_CODE: ${failure==='skip'?'-3':'0'}\nOK (${failure==='zero'?'0':'1'} test)\nINSTRUMENTATION_CODE: -1\n`,stderr:''};
    }
    if(args.includes('cat'))return {stdout:JSON.stringify({...receipt,...(failure==='false-backend'?{backendVerified:true}:{})}),stderr:''};
    assert.fail(`unexpected command ${args.join(' ')}`);
  };
  return {args:{adb:path.resolve('adb.exe'),serial:'emulator-5562',input,mode,evidenceDirectory,execute},calls,receipt,
    close:()=>{
      assert.equal(path.dirname(path.resolve(evidenceDirectory)),path.resolve(os.tmpdir()));
      assert.ok(path.basename(evidenceDirectory).startsWith('quata-android-reply-step-'));
      return rm(evidenceDirectory,{recursive:true,force:true});
    }};
}

test('native step keeps submission, passive observation and reconciliation receipts distinct',async()=>{
  for(const mode of ['submit','observe','cleanup','cleanup-empty']) {
    const f=await fixture(mode);
    try {
      assert.deepEqual(await runAndroidNotificationReplyStep(f.args),f.receipt);
      assert.equal(f.calls.filter(args=>args.includes('instrument')).length,1);
      assert.equal(f.calls.filter(args=>args.includes('pidof')).length,2);
      const closed=JSON.parse(await readFile(path.join(f.args.evidenceDirectory,f.args.input.stepId,'host-closed.json'),'utf8'));
      assert.equal(closed.absent,true);
      await assert.rejects(runAndroidNotificationReplyStep(f.args));
      assert.equal(f.calls.filter(args=>args.includes('instrument')).length,1);
    } finally {await f.close();}
  }
});

test('wrong runner or unresolved existing host cannot start an instrumentation',async()=>{
  for(const failure of ['wrong-runner','live-before','adb-error']) {
    const f=await fixture('submit',failure);
    try {await assert.rejects(runAndroidNotificationReplyStep(f.args));assert.equal(f.calls.some(args=>args.includes('instrument')),false);}
    finally {await f.close();}
  }
});

test('timeout, live remote host, skipped test and dishonest receipt retain the attempt without replay',async()=>{
  for(const failure of ['timeout','live-after','skip','zero','false-backend']) {
    const f=await fixture('submit',failure);
    try {
      await assert.rejects(runAndroidNotificationReplyStep(f.args));
      await assert.rejects(runAndroidNotificationReplyStep(f.args));
      assert.equal(f.calls.filter(args=>args.includes('instrument')).length,1);
      if(['timeout','live-after','skip','zero'].includes(failure))assert.equal(f.calls.some(args=>args.includes('cat')),false);
      if(failure==='timeout') {
        const directory=path.join(f.args.evidenceDirectory,f.args.input.stepId);
        assert.equal(await readFile(path.join(directory,'03.partial.stdout'),'utf8'),'partial native status\n');
        assert.equal(await readFile(path.join(directory,'03.partial.stderr'),'utf8'),'partial transport error\n');
      }
    } finally {await f.close();}
  }
});

test('the client PID is recorded and a post-launch journal failure closes only its client',async()=>{
  for(const diskFailure of [false,true]) {
    const f=await fixture();const original=f.args.execute;let killed=0;
    f.args.execute=(adb,args,options)=>{
      if(diskFailure&&args.includes('instrument')) {
        const record=path.join(f.args.evidenceDirectory,f.args.input.stepId,'03.json');
        assert.equal(path.dirname(path.dirname(record)),f.args.evidenceDirectory);
        unlinkSync(record);mkdirSync(record);
        let finish;
        const operation=new Promise(resolve=>{finish=resolve;});
        operation.child={pid:4321,kill(){killed++;finish({stdout:'',stderr:''});}};
        return operation;
      }
      const operation=original(adb,args,options);
      operation.child={pid:4321,kill(){assert.fail('terminal client must not be killed');}};
      return operation;
    };
    try {
      if(diskFailure) {await assert.rejects(runAndroidNotificationReplyStep(f.args));assert.equal(killed,1);}
      else {
        await runAndroidNotificationReplyStep(f.args);
        const entry=JSON.parse(await readFile(path.join(f.args.evidenceDirectory,f.args.input.stepId,'03.json'),'utf8'));
        assert.equal(entry.pid,4321);assert.equal(entry.status,'client-terminal');assert.equal(killed,0);
      }
    } finally {await f.close();}
  }
});

test('inputs cannot carry credentials or conflate the observation and send attempt',async()=>{
  const f=await fixture('observe');
  try {
    for(const input of [{...f.args.input,accessToken:'forbidden'},{...f.args.input,attemptStepId:f.args.input.stepId},
      {...f.args.input,threadId:'0'}])await assert.rejects(runAndroidNotificationReplyStep({...f.args,input}));
    assert.equal(f.calls.length,0);
  } finally {await f.close();}
});
