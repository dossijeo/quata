import {loadRecoveryInput,recoveryTransportEnvironment} from './e2e-fixtures/recovery-runtime-config.mjs';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {execFile,execFileSync,spawn} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import {createConnection} from 'node:net';
import {createInterface} from 'node:readline';
import {openRecoveryJournal} from './e2e-fixtures/recovery-private-journal.mjs';
import {resumeRecoverySecretSnapshot} from './e2e-fixtures/account-recovery-secret.mjs';
import {createRecoveryBackend} from './e2e-fixtures/recovery-backend.mjs';
import {createRecoveryAndroidProduct} from './e2e-fixtures/recovery-android-product.mjs';
import {runRecoverySecretEvidence} from './account-recovery-secret-evidence.mjs';
const fatal=()=>{console.log(JSON.stringify({state:'failed',error:'prepared_caller_initialization_failed'}));process.exit(1);};
process.on('uncaughtException',fatal);
process.on('unhandledRejection',fatal);
const {input,runtime}=await loadRecoveryInput(process.argv[2],'android');
const root=runtime.root;
const adb=runtime.adbExecutable;
const serial=runtime.serial;

const ledger=JSON.parse(await readFile(input.ledger,'utf8'));
const journal=await openRecoveryJournal({file:ledger.focalJournal,identity:{runId:ledger.focalRunId,profileId:ledger.profileId,authUserId:ledger.authUserId}});
const record=await journal.read();
const {Client}=createRequire(runtime.dependencyPackage)('pg');
const url=new URL((await readFile(runtime.databaseUrlFile,'utf8')).trim());
for(const key of ['sslmode','sslrootcert','sslcert','sslkey'])url.searchParams.delete(key);
const client=new Client({connectionString:url.toString(),ssl:{ca:await readFile(runtime.databaseCaFile,'utf8'),rejectUnauthorized:true},connectionTimeoutMillis:5000,statement_timeout:5000});
const lines=createInterface({input:process.stdin})[Symbol.asyncIterator]();
const socketName='quata-recovery-'+randomUUID();
const command=(args,binary=false)=>new Promise((resolve,reject)=>execFile(adb,['-s',serial,...args],{windowsHide:true,timeout:15000,maxBuffer:binary?16777216:1048576,encoding:binary?'buffer':'utf8'},(e,out)=>e?reject(Error('owned_adb_command_failed')):resolve(out)));
let callerPhase='readiness',callerFailure,permittedReadVisuallyReviewed=false;
const decision=async prefix=>{
  callerPhase=prefix==='execute:'?'awaiting_execute':prefix==='read-visible:'?'awaiting_read_review':'awaiting_account_review';
  let timer;
  try {
    const result=await Promise.race([lines.next(),new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('review_timeout')),600000);})]);
    if(result.value!==prefix+record.runId)throw Error('review_response_mismatch');
    callerPhase='review_accepted';
  } catch(error){callerFailure=error.message==='review_timeout'?'review_timeout':'review_response_mismatch';throw Error(callerFailure);}
  finally{clearTimeout(timer);}
};
let port,socket,product,nativeProcess,nativeDone,nativeTerminal=false,nativeOutput='',nativeOverflow=false,resourcesClosed=false;
let report={status:'failed',phase:'readiness'};
const closeResources=async()=>{
  if(resourcesClosed)return true;
  if(nativeDone){let timer;await Promise.race([nativeDone,new Promise(resolve=>{timer=setTimeout(resolve,20000);})]).finally(()=>clearTimeout(timer));}
  let forwardRemoved=!port;
  if(port){
    const mappings=await command(['forward','--list']);
    if(mappings.split(/\r?\n/).some(line=>line.trim()===`${serial} tcp:${port} localabstract:${socketName}`)){
      await command(['forward','--remove',`tcp:${port}`]);forwardRemoved=true;port=undefined;
    }
  }
  // The final framework result proves remote instrumentation ended; adb EOF alone does not.
  const remoteFinished=nativeTerminal&&!nativeOverflow&&/(?:^|\n)INSTRUMENTATION_CODE: -?\d+\s*$/.test(nativeOutput.trim());
  resourcesClosed=(!nativeProcess || remoteFinished) && forwardRemoved;
  return resourcesClosed;
};
try {
  callerPhase='artifact_verification';
  const manifest=JSON.parse(await readFile(runtime.preparationManifest,'utf8'));
  for(const artifact of manifest.artifacts){const bytes=await readFile(root+'/'+artifact.path);if(bytes.length!==artifact.bytes||createHash('sha256').update(bytes).digest('hex')!==artifact.sha256)throw Error('apk_mismatch');}
  if(execFileSync('git',['diff',manifest.sourceRevision,'--','app','feature','core'],{cwd:root,encoding:'utf8'}).trim())throw Error('android_source_changed');
  callerPhase='avd_verification';
  if((await command(['emu','avd','name'])).split(/\r?\n/)[0].trim()!==runtime.avdName || (await command(['shell','getprop','sys.boot_completed'])).trim()!=='1' || (await command(['shell','getprop','ro.build.version.sdk'])).trim()!==runtime.androidApi)throw Error('owned_avd_not_ready');
  callerPhase='installed_apk_verification';
  for(const [index,pkg] of ['com.quata','com.quata.test'].entries()){
    const installed=(await command(['shell','pm','path',pkg])).trim();
    if(!/^package:\/data\/app\/[^\r\n ]+\.apk$/.test(installed))throw Error('installed_apk_path_unverified');
    const digest=(await command(['shell','sha256sum',installed.slice(8)])).trim().split(/\s+/)[0];
    if(digest!==manifest.artifacts[index].sha256)throw Error('installed_apk_mismatch');
  }
  callerPhase='journal_verification';
  if(record.state.phase!=='prepared'||record.state.sessions.length||record.state.secretPotentiallyChanged||record.state.passwordPotentiallyChanged)throw Error('journal_not_prepared');
  callerPhase='database_connection';
  await client.connect();
  callerPhase='fixture_verification';
  const snapshot=await resumeRecoverySecretSnapshot({client,profileId:record.profileId,authUserId:record.authUserId,storageFormat:record.storageFormat,original:record.secretSnapshot});
  const backend=createRecoveryBackend({client,journal,record,backendUrl:input.backendUrl,publicKey:input.publicKey,sessionKind:'native',pageOperationsSettled:()=>product?.operationsSettled()===true});
  if(!await snapshot.verify()||!await backend.preflight(record)||!await backend.verifyNonSecretState(record))throw Error('fixture_changed');
  callerPhase='native_forward';
  port=(await command(['forward','tcp:0','localabstract:'+socketName])).trim();
  if(!/^[0-9]+$/.test(port))throw Error('forward_identity_invalid');
  callerPhase='native_launch';
  nativeProcess=spawn(adb,['-s',serial,'shell','am','instrument','-w','-r','-e','class','com.quata.feature.profile.presentation.RecoverySecretRealInstrumentedTest#accountSecretRoundtripControlledByFocalCoordinator','-e','quataRecoverySocket',socketName,'com.quata.test/androidx.test.runner.AndroidJUnitRunner'],{windowsHide:true,stdio:['ignore','pipe','pipe']});
  for(const stream of [nativeProcess.stdout,nativeProcess.stderr])stream.on('data',chunk=>{nativeOutput+=chunk.toString();if(nativeOutput.length>1048576){nativeOverflow=true;nativeOutput=nativeOutput.slice(-1048576);}});
  nativeDone=new Promise(resolve=>{nativeProcess.once('error',()=>{nativeTerminal=true;resolve();});nativeProcess.once('close',()=>{nativeTerminal=true;resolve();});});
  const until=Date.now()+35000;let listening=false;
  while(Date.now()<until && !nativeTerminal){
    const sockets=await command(['shell','cat','/proc/net/unix']);
    if(sockets.includes('@'+socketName)){listening=true;break;}
    await new Promise(resolve=>setTimeout(resolve,500));
  }
  if(!listening)throw Error('native_socket_not_ready');
  socket=await new Promise((resolve,reject)=>{const value=createConnection({host:'127.0.0.1',port:Number(port)},()=>{value.removeListener('error',reject);resolve(value);});value.once('error',reject);});
  product=createRecoveryAndroidProduct({socket,record,verifyActor:backend.verifyActor,closeResources});
  await mkdir(input.evidenceDirectory,{recursive:true});
  const capture=async name=>{
    const bytes=await command(['exec-out','run-as','com.quata','cat',`files/recovery-secret-evidence/${socketName}/${name}.png`],true);
    if(bytes.subarray(0,8).toString('hex')!=='89504e470d0a1a0a')throw Error('native_capture_invalid');
    await writeFile(input.evidenceDirectory+'/'+name+'.png',bytes);
  };
  console.log(JSON.stringify({state:'ready',productSource:manifest.sourceRevision,nativeSocketReady:true}));
  await decision('execute:');
  const focal={...product,openAccount:async()=>{await product.openAccount();await capture('account-before-secret');console.log(JSON.stringify({state:'account_visual_review_ready',reviewTimeoutSeconds:600}));await decision('account-visible:');},
    saveSecret:async()=>{await product.saveSecret();await capture('after-save-navigation');return true;},
    readPermittedState:async()=>{const value=await product.readPermittedState();if(value.answerEmpty!==true)throw Error('answer_not_empty');await capture('account-secret-saved-answer-empty');console.log(JSON.stringify({state:'permitted_read_visual_review_ready',reviewTimeoutSeconds:600}));await decision('read-visible:');permittedReadVisuallyReviewed=true;return value;},
    recoverPassword:async(...args)=>{await product.recoverPassword(...args);await capture('login-after-recovery');}};
  report=await runRecoverySecretEvidence({journal,snapshot,product:focal,backend});
  report.productSource=manifest.sourceRevision;report.runnerSource=execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
  report.nativeTestPassed=nativeTerminal&&!nativeOverflow&&/OK \(1 test\)/.test(nativeOutput)&&!/(FAILURES!!!|INSTRUMENTATION_FAILED)/.test(nativeOutput);
  if(!report.nativeTestPassed)report.status='failed';
} catch(error) {report.status='failed';report.failure='prepared_android_execution_failed';if(/^[A-Z_0-9]+$/.test(error?.code??''))report.failureCode=error.code;} 
finally {
  socket?.destroy();
  report.resourcesClosed=await closeResources().catch(()=>false);
  report.permittedReadVisuallyReviewed=permittedReadVisuallyReviewed;report.navigationScope="Account reopened through existing evidence entry; Feed and normal navigation continuity not certified";
  report.nativeFailurePhase=product?.failurePhase?.();
  report.callerPhase=callerPhase;if(callerFailure)report.callerFailure=callerFailure;
  report.nativeResources={serial,socketName,localAdbTerminal:nativeTerminal,remoteResultReceived:!nativeOverflow&&/(?:^|\n)INSTRUMENTATION_CODE: -?\d+\s*$/.test(nativeOutput.trim())};
  if(report.status!=='passed'||report.nativeTestPassed!==true||report.resourcesClosed!==true||report.permittedReadVisuallyReviewed!==true)report.status='failed';
  await client.end().catch(()=>{});
  await writeFile(input.reportPath,JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({state:'finished',report}));process.stdin.destroy();
}
process.exitCode=report.status==='passed'&&report.nativeTestPassed===true&&report.resourcesClosed===true&&report.permittedReadVisuallyReviewed===true?0:1;
