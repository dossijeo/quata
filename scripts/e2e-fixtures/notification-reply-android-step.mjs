import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdir,open,writeFile} from 'node:fs/promises';
import path from 'node:path';
const exec=promisify(execFile);
const uuid=value=>typeof value==='string'&&/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value);
const positive=value=>typeof value==='string'&&/^[1-9][0-9]*$/.test(value)&&BigInt(value)<=9223372036854775807n;
const sameKeys=(value,keys)=>value&&Object.keys(value).sort().join(',')===[...keys].sort().join(',');

// The caller holds the candidate lease and installs the pinned test APK only
// with the product process closed. This adapter never installs, retries, clears
// sessions, sends an RPC, releases the lease, or infers remote closure from adb.
export async function runAndroidNotificationReplyStep({adb,serial,input,mode,evidenceDirectory,execute=exec}) {
  const submit=mode==='submit',observe=mode==='observe';
  const keys=submit?['runId','stepId','profileId','authUserId','threadId']:
    ['runId','stepId','attemptStepId','profileId','threadId'];
  if(!['submit','observe','cleanup','cleanup-empty'].includes(mode)||!path.isAbsolute(adb)||!path.isAbsolute(evidenceDirectory)||
    !/^emulator-\d+$/.test(serial)||!sameKeys(input,keys)||!positive(input.threadId)||
    keys.filter(key=>key!=='threadId').some(key=>!uuid(input[key]))||
    (!submit&&input.stepId===input.attemptStepId)||typeof execute!=='function')
    throw Error('notification_reply_android_configuration_invalid');
  const directory=path.join(evidenceDirectory,input.stepId);
  await mkdir(directory,{recursive:false}); // Exclusive attempt, including after a lost response.
  const intent=await open(path.join(directory,'intent.json'),'wx',0o600);
  try {await intent.writeFile(JSON.stringify({serial,input,mode,status:'prepared'}));await intent.sync();}
  finally {await intent.close();}
  let sequence=0;
  const command=async(args,timeout=15000)=>{
    const name=String(++sequence).padStart(2,'0'),record=path.join(directory,`${name}.json`);
    const argv=['-s',serial,...args];
    await writeFile(record,JSON.stringify({argv,status:'intent'}),{flag:'wx',mode:0o600});
    const operation=execute(adb,argv,{windowsHide:true,timeout,maxBuffer:1024*1024});
    const terminal=operation.then(value=>({value}),error=>({error}));
    try {
      await writeFile(record,JSON.stringify({argv,pid:operation.child?.pid??null,status:'running'}));
    } catch(error) {
      operation.child?.kill();await terminal;throw error;
    }
    try {
      const completed=await terminal;
      if(completed.error)throw completed.error;
      const value=completed.value;
      await writeFile(record,JSON.stringify({argv,pid:operation.child?.pid??null,status:'client-terminal',exitCode:0}));
      return value;
    } catch(error) {
      // Preserve the original partial transport output privately on uncertainty;
      // it may be the only witness that the test reached its Send boundary.
      for(const stream of ['stdout','stderr']) {
        const partial=error[stream];
        if(typeof partial==='string'&&partial.length)await writeFile(path.join(directory,`${name}.partial.${stream}`),
          partial,{flag:'wx',mode:0o600});
      }
      await writeFile(record,JSON.stringify({argv,pid:operation.child?.pid??null,status:'remote-state-unresolved',
        exitCode:Number.isInteger(error.code)?error.code:null}));
      throw error;
    }
  };
  const requireClosed=async()=>{
    try {
      const value=await command(['shell','pidof','com.quata']);
      if(value.stdout.trim()||value.stderr.trim())throw Error('notification_reply_android_host_still_running');
      // pidof success with an empty body is not the expected absence contract.
      throw Error('notification_reply_android_host_closure_unverified');
    } catch(error) {
      if(error.code!==1||typeof error.stdout!=='string'||typeof error.stderr!=='string'||error.stdout.trim()||error.stderr.trim())throw error;
    }
  };
  const runner=submit?'androidx.test.runner.AndroidJUnitRunner':'com.quata.core.navigation.DeepLinkSessionCustodyRunner';
  const method=submit?'submitsOneReplyThroughSystemUi':observe?'observesOwnedNotificationAbsent':
    mode==='cleanup-empty'?'reconcilesEmptyPreIntentFailure':'reconcilesOnlyOwnedNotification';
  const testClass=`com.quata.core.notifications.NotificationReply${submit?'Product':'Reconciliation'}InstrumentedTest`;
  const attemptStep=submit?input.stepId:input.attemptStepId;
  try {
    await requireClosed();
    const components=await command(['shell','pm','list','instrumentation']);
    const own=components.stdout.split(/\r?\n/).filter(line=>line.startsWith('instrumentation:com.quata.test/'));
    if(own.length!==1||own[0].trim()!==`instrumentation:com.quata.test/${runner} (target=com.quata)`)
      throw Error('notification_reply_android_runner_unverified');
    const args=['shell','am','instrument','-w','-r','-e','class',`${testClass}#${method}`,
      '-e',submit?'quataReplyProduct':'quataReplyReconciliation',submit?'1':mode,
      '-e','quataReplyRun',input.runId,'-e','quataReplyStep',attemptStep,'-e','quataReplyActor',input.profileId,
      '-e','quataReplyThread',input.threadId,
      ...(submit?['-e','quataReplyAuthActor',input.authUserId]:['-e','quataReplyObservationStep',input.stepId]),
      `com.quata.test/${runner}`];
    const result=await command(args,120000);
    await writeFile(path.join(directory,'instrumentation.log'),result.stdout+result.stderr,{flag:'wx',mode:0o600});
    // Even a failing JUnit run must establish remote host closure before another test.
    await requireClosed();
    await writeFile(path.join(directory,'host-closed.json'),JSON.stringify({serial,stepId:input.stepId,absent:true}),{flag:'wx'});
    const output=result.stdout;
    if(!output.includes(`INSTRUMENTATION_STATUS: class=${testClass}`)||
      !output.includes(`INSTRUMENTATION_STATUS: test=${method}`)||
      !/^OK \(1 test\)\s*$/m.test(output)||
      (output.match(/^INSTRUMENTATION_STATUS_CODE: 0\s*$/gm)??[]).length!==1||
      (output.match(/^INSTRUMENTATION_CODE: -1\s*$/gm)??[]).length!==1||
      /^INSTRUMENTATION_STATUS_CODE: -(?:[1-9]\d*)\s*$/m.test(output))
      throw Error('notification_reply_android_junit_unverified');
    const file=submit?'submitted.json':observe?'independent-outcome.json':'independent-cleanup.json';
    const receiptResult=await command(['exec-out','run-as','com.quata','cat',`files/reply-product/${attemptStep}/${file}`]);
    if(receiptResult.stderr.trim()||Buffer.byteLength(receiptResult.stdout)>4096)throw Error('notification_reply_android_receipt_unverified');
    const receipt=JSON.parse(receiptResult.stdout);
    const expected=submit?{runId:input.runId,stepId:input.stepId,submittedBySystemUi:true,backendVerified:false,
      replyMarker:`qadata-reply-text-${input.stepId}`,notificationMarker:`qadata-reply-alert-${input.stepId}`}:
      {runId:input.runId,stepId:input.stepId,attemptStepId:input.attemptStepId,notificationRemoved:true,backendVerified:false,reconciled:!observe,
        ...(mode==='cleanup-empty'?{intentAbsent:true}:{})};
    if(!sameKeys(receipt,Object.keys(expected))||Object.keys(expected).some(key=>receipt[key]!==expected[key]))
      throw Error('notification_reply_android_receipt_unverified');
    await writeFile(path.join(directory,'receipt.json'),JSON.stringify(receipt),{flag:'wx',mode:0o600});
    return receipt;
  } catch {
    // The caller retains process/device/backend custody and reconciles explicitly.
    throw Error('notification_reply_android_step_unresolved');
  }
}
