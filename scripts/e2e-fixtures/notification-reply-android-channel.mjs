import {execFile} from 'node:child_process';
import {promisify,isDeepStrictEqual} from 'node:util';
import {createHash} from 'node:crypto';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {openAndroidDeepLinkSessionChannel,runAndroidDeepLinkSessionStep} from './chat-deep-link-android-session-step.mjs';
import {runAndroidNotificationReplyStep} from './notification-reply-android-step.mjs';
const exec=promisify(execFile);
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const uuid=value=>typeof value==='string'&&/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value);

// Owns the test-package swaps, not emulator startup/shutdown. The outer runtime
// journal must retain that ownership until this channel has verifiably closed.
// Never uninstall, clear app data, force-stop, retry Send or infer remote closure.
export async function openAndroidNotificationReplyChannel({adb,aapt2,serial,avdName,leasePath,evidenceDirectory,
  appSha256,custodyApk,productApk,identity,execute=exec,sessionStepImpl=runAndroidDeepLinkSessionStep,
  replyStepImpl=runAndroidNotificationReplyStep}) {
  if(!path.isAbsolute(adb)||!path.isAbsolute(aapt2)||!path.isAbsolute(leasePath)||!path.isAbsolute(evidenceDirectory)||
    !/^emulator-\d+$/.test(serial)||serial==='emulator-5560'||!/^QuataReply[A-Za-z0-9]+$/.test(avdName)||
    !/^[0-9a-f]{64}$/.test(appSha256)||[custodyApk,productApk].some(apk=>!apk||!path.isAbsolute(apk.path)||!/^[0-9a-f]{64}$/.test(apk.sha256))||
    !identity||Object.keys(identity).sort().join(',')!=='base,head,merge,pr'||identity.pr!==339||
    ['base','head','merge'].some(key=>!/^[0-9a-f]{40}$/.test(identity[key])))
    throw Error('notification_reply_android_channel_configuration_invalid');
  await mkdir(evidenceDirectory,{recursive:false});
  await writeFile(path.join(evidenceDirectory,'identity.json'),JSON.stringify({serial,avdName,appSha256,custodyApk,productApk,identity}),{flag:'wx',mode:0o600});
  let sequence=0,busy=false,aborted=false,closed=false,session,owner,attempt,submitted=false,reconciled=false,observed=false;
  const commands=path.join(evidenceDirectory,'commands');await mkdir(commands);
  const replyDirectory=path.join(evidenceDirectory,'reply');await mkdir(replyDirectory);
  const command=async(args,{timeout=15000,binary=false,local=false}={})=>{
    const file=path.join(commands,`${String(++sequence).padStart(3,'0')}.json`),argv=local?args:['-s',serial,...args],tool=local?aapt2:adb;
    await writeFile(file,JSON.stringify({tool,argv,status:'intent'}),{flag:'wx',mode:0o600});
    const operation=execute(tool,argv,{windowsHide:true,timeout,maxBuffer:256*1024*1024,...(binary?{encoding:'buffer'}:{})});
    const terminal=operation.then(value=>({value}),error=>({error}));
    try {await writeFile(file,JSON.stringify({tool,argv,pid:operation.child?.pid??null,status:'running'}));}
    catch(error) {operation.child?.kill();await terminal;throw error;}
    const result=await terminal;
    await writeFile(file,JSON.stringify({tool,argv,pid:operation.child?.pid??null,status:'client-terminal',
      exitCode:result.error?(Number.isInteger(result.error.code)?result.error.code:null):0}));
    if(result.error)throw result.error;
    return result.value;
  };
  const requireClosed=async()=>{
    try {await command(['shell','pidof','com.quata']);}
    catch(error) {
      if(error.code===1&&typeof error.stdout==='string'&&typeof error.stderr==='string'&&!error.stdout.trim()&&!error.stderr.trim())return;
      throw error;
    }
    throw Error('notification_reply_android_process_unresolved');
  };
  const verifyInstalled=async(packageName,expected)=>{
    const result=await command(['shell','pm','path',packageName]);
    const match=/^package:(\/data\/app\/[A-Za-z0-9_+~\/=.-]+\/base\.apk)\s*$/.exec(result.stdout);
    if(!match||result.stderr.trim())throw Error('notification_reply_android_apk_path_invalid');
    const bytes=await command(['exec-out','cat',match[1]],{binary:true,timeout:30000});
    if(!Buffer.isBuffer(bytes.stdout)||bytes.stderr.length||hash(bytes.stdout)!==expected)
      throw Error('notification_reply_android_apk_mismatch');
  };
  const swap=async(kind)=>{
    await requireClosed();
    const avd=await command(['emu','avd','name']);
    if(avd.stderr.trim()||!isDeepStrictEqual(avd.stdout.split(/\r?\n/).map(line=>line.trim()).filter(Boolean),[avdName,'OK']))
      throw Error('notification_reply_android_avd_mismatch');
    await verifyInstalled('com.quata',appSha256);
    const apk=kind==='product'?productApk:custodyApk;
    if(hash(await readFile(apk.path))!==apk.sha256)throw Error('notification_reply_android_apk_mismatch');
    const manifest=await command(['dump','badging',apk.path],{local:true});
    if(manifest.stderr.trim()||(manifest.stdout.match(/^package: name='com\.quata\.test' /gm)??[]).length!==1||
      (manifest.stdout.match(/^package:/gm)??[]).length!==1)
      throw Error('notification_reply_android_test_package_invalid');
    const install=await command(['install','-r','-t',apk.path],{timeout:60000});
    if(install.stderr.trim()||!/^Success\s*$/m.test(install.stdout)||/Failure/.test(install.stdout))
      throw Error('notification_reply_android_test_install_unresolved');
    await requireClosed();await verifyInstalled('com.quata.test',apk.sha256);
    await verifyInstalled('com.quata',appSha256);
  };
  const exclusive=async action=>{
    if(busy||aborted||closed)throw Error('notification_reply_android_channel_order_invalid');
    busy=true;
    try {const result=await action();if(aborted)throw Error('notification_reply_android_channel_aborted');return result;} finally {busy=false;}
  };
  try {
    // The existing channel acquires the exclusive lease before this callback
    // can replace the test APK or probe the private session.
    session=await openAndroidDeepLinkSessionChannel({adb,serial,leasePath,
      evidenceDirectory:path.join(evidenceDirectory,'session'),stepImpl:async options=>{
        await swap('custody');const receipt=await sessionStepImpl(options);await requireClosed();return receipt;
      }});
  } catch {throw Error('notification_reply_android_channel_open_unresolved');}
  const matchAttempt=input=>{
    if(!attempt||input.runId!==attempt.runId||input.profileId!==attempt.profileId||input.threadId!==attempt.threadId||
      input.attemptStepId!==attempt.stepId||!uuid(input.stepId)||input.stepId===attempt.stepId)
      throw Error('notification_reply_android_attempt_identity_invalid');
  };
  const native=async(input,mode)=>{
    await swap(mode==='submit'?'product':'custody');
    return replyStepImpl({adb,serial,input,mode,evidenceDirectory:replyDirectory});
  };
  return {
    sessionStep:input=>exclusive(async()=>{
      if(input.stage==='install') {
        if(owner||!uuid(input.runId)||!uuid(input.profileId)||!uuid(input.authUserId))throw Error('notification_reply_android_session_order_invalid');
        owner={runId:input.runId,profileId:input.profileId,authUserId:input.authUserId};
      } else if(input.stage!=='clear'||!owner||Object.keys(owner).some(key=>input[key]!==owner[key])||attempt&&!reconciled)
        throw Error('notification_reply_android_session_order_invalid');
      return session.sessionStep(input);
    }),
    submitNotificationReply:input=>exclusive(async()=>{
      if(!owner||attempt||!uuid(input.stepId)||Object.keys(owner).some(key=>input[key]!==owner[key]))
        throw Error('notification_reply_android_submit_order_invalid');
      attempt=structuredClone(input); // Bind before any operation; failure never permits replay.
      const receipt=await native(input,'submit');submitted=true;return receipt;
    }),
    verifyNotificationReplyOutcome:input=>exclusive(async()=>{
      matchAttempt(input);if(!submitted||observed||reconciled)throw Error('notification_reply_android_observe_order_invalid');
      observed=true;return native(input,'observe');
    }),
    reconcileNotification:input=>exclusive(async()=>{
      matchAttempt(input);if(reconciled)throw Error('notification_reply_android_reconcile_order_invalid');
      await requireClosed();
      let mode='cleanup';
      try {await command(['shell','run-as','com.quata','test','-e',`files/reply-product/${attempt.stepId}/intent.json`]);}
      catch(error) {
        if(error.code!==1||typeof error.stdout!=='string'||typeof error.stderr!=='string'||error.stdout.trim()||error.stderr.trim())throw error;
        mode='cleanup-empty';
      }
      const receipt=await native(input,mode);reconciled=true;return receipt;
    }),
    close:input=>exclusive(async()=>{
      if(!isDeepStrictEqual(input,{runId:input?.runId})||!uuid(input.runId)||owner&&input.runId!==owner.runId||attempt&&!reconciled)
        throw Error('notification_reply_android_close_order_invalid');
      await requireClosed();await session.close();
      if(!session.settled()||aborted)throw Error('notification_reply_android_close_unresolved');
      closed=true;return {runId:input.runId,processClosed:true};
    }),
    settled:()=>closed&&!busy&&!aborted&&session.settled(),
    abort:()=>{if(closed)return;aborted=true;session.abort();},
  };
}
