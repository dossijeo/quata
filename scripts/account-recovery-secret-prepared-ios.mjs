import {loadRecoveryInput,recoveryTransportEnvironment} from './e2e-fixtures/recovery-runtime-config.mjs';
import {readFile,writeFile,open,rename,mkdir,unlink} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {execFile,execFileSync} from 'node:child_process';
import {createHash,randomUUID} from 'node:crypto';
import {createInterface} from 'node:readline';
import {openRecoveryJournal} from './e2e-fixtures/recovery-private-journal.mjs';
import {resumeRecoverySecretSnapshot} from './e2e-fixtures/account-recovery-secret.mjs';
import {createRecoveryBackend} from './e2e-fixtures/recovery-backend.mjs';
import {createRecoveryIosProduct} from './e2e-fixtures/recovery-ios-product.mjs';
import {runRecoverySecretEvidence} from './account-recovery-secret-evidence.mjs';

const fatal=()=>{console.log(JSON.stringify({state:'failed',error:'ios_caller_initialization_failed'}));process.exit(1);};
process.on('uncaughtException',fatal);
process.on('unhandledRejection',fatal);
const {input,runtime}=await loadRecoveryInput(process.argv[2],'ios');
const root=runtime.root;
const mac=runtime.macWorktree;
const device=runtime.simulator;
const ssh=runtime.sshExecutable;
const sshEnv=recoveryTransportEnvironment();

const ledger=JSON.parse(await readFile(input.ledger,'utf8'));
const journal=await openRecoveryJournal({file:ledger.focalJournal,
  identity:{runId:ledger.focalRunId,profileId:ledger.profileId,authUserId:ledger.authUserId}});
const record=await journal.read();
const callerLock=runtime.resourceDirectory+'/ios-run-'+record.runId+'.lock';
const callerLockHandle=await open(callerLock,'wx');
await callerLockHandle.writeFile(JSON.stringify({runId:record.runId,pid:process.pid}));
await callerLockHandle.sync();await callerLockHandle.close();
const {Client}=createRequire(runtime.dependencyPackage)('pg');
const url=new URL((await readFile(runtime.databaseUrlFile,'utf8')).trim());
for(const key of ['sslmode','sslrootcert','sslcert','sslkey'])url.searchParams.delete(key);
const client=new Client({connectionString:url.toString(),ssl:{ca:await readFile(runtime.databaseCaFile,'utf8'),rejectUnauthorized:true},
  connectionTimeoutMillis:5000,statement_timeout:5000});
const lines=createInterface({input:process.stdin})[Symbol.asyncIterator]();
let callerPhase='readiness',product,resourcesClosed=false;
let report={status:'failed'},steps=[],reviewed=new Set();
const resourcesFile=runtime.resourceDirectory+'/ios-resources-'+record.runId+'.json';
const resources=async()=>{
  const temporary=resourcesFile+'.'+randomUUID()+'.tmp';
  const handle=await open(temporary,'wx');
  try {await handle.writeFile(JSON.stringify({runId:record.runId,device,mac,steps},null,2)+'\n');await handle.sync();}
  finally{await handle.close();}
  await rename(temporary,resourcesFile);
};
const requireTrue=value=>{if(value!==true)throw Error('ios_verification_failed');};
const remote=(command,payload,timeout=25000)=>new Promise((resolve,reject)=>{
  const child=execFile(ssh,['-o','BatchMode=yes','-o','ConnectTimeout=10','-o','ServerAliveInterval=10','-o','ServerAliveCountMax=3',runtime.sshHost,command],
    {env:sshEnv,windowsHide:true,timeout,maxBuffer:12*1024*1024,encoding:'utf8'},
    (error,stdout)=>error?reject(Error('ios_private_transport_uncertain')):resolve(stdout));
  child.stdin.on('error',()=>{});
  child.stdin.end(payload===undefined?'':JSON.stringify(payload));
});
const stepCommand=action=>{
  requireTrue(['run','status','release','capture','purge','stop'].includes(action));
  return `python3 ${mac}/scripts/e2e-fixtures/recovery-ios-step.py ${action} --worktree ${mac} --simulator ${device}`;
};
const transport=async(action,payload)=>{
  const result=JSON.parse(await remote(stepCommand(action),payload,action==='run'?400000:action==='capture'?80000:action==='stop'?60000:25000));
  requireTrue(!result.error);return result;
};
const decision=async token=>{
  let timer;
  try {
    const answer=await Promise.race([lines.next(),new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('review_timeout')),600000);})]);
    requireTrue(answer.value===token);
  }finally{clearTimeout(timer);}
};
const identity=value=>Object.fromEntries(['runId','stepId','stage','profileId','authUserId'].map(key=>[key,value[key]]));
const same=(a,b)=>['runId','stepId','stage','profileId','authUserId'].every(key=>a?.[key]===b[key]);
const runStep=async payload=>{
  callerPhase='step_'+payload.stage;
  const item={...identity(payload),phase:'planned',
    reportDirectory:mac+'/build/reports/ios/recovery-private-'+record.runId+'/step-'+payload.stepId,
    exchangeDirectory:mac+'/build/reports/ios/recovery-private-'+record.runId+'/recovery-secret-'+payload.stepId};
  steps.push(item);await resources(); // Durable path ownership precedes Mac writes.
  const result=await transport('run',payload);
  requireTrue(result.terminal===true && result.exitCode===0 && same(result.receipt,payload));
  item.phase='test_passed';await resources();
  if(['open','read','recover'].includes(payload.stage)){
    const capture=await transport('capture',identity(payload));
    const expected={open:'recovery-secret-account-before-configure',read:'recovery-secret-account-read-answer-empty',recover:'recovery-secret-login-return'}[payload.stage];
    requireTrue(capture.name===expected && typeof capture.png==='string');
    const bytes=Buffer.from(capture.png,'base64');
    requireTrue(bytes.subarray(0,8).toString('hex')==='89504e470d0a1a0a' && createHash('sha256').update(bytes).digest('hex')===capture.sha256);
    const file=input.evidenceDirectory+'/'+expected+'.png';
    await writeFile(file,bytes,{flag:'wx'});
    item.capture={file,sha256:capture.sha256};await resources();
    console.log(JSON.stringify({state:'visual_review_ready',stage:payload.stage,stepId:payload.stepId,file}));
    await decision('reviewed:'+record.runId+':'+payload.stepId);
    reviewed.add(payload.stage);item.captureReviewed=true;await resources();
  }
  return result; // Private bearer remains inside this process and the adapter.
};
const releaseStep=async payload=>{
  const item=steps.find(step=>step.stepId===payload.stepId);
  requireTrue(!!item && item.phase==='test_passed');
  requireTrue((await transport('release',identity(payload))).released===true);
  item.phase='released';await resources();return true;
};
const verifyLogout=async()=>{
  const durable=await journal.read();
  const tickets=durable.state.sessions.filter(ticket=>ticket.purpose==='producer' && ticket.kind==='native');
  requireTrue(tickets.length===1 && !!tickets[0].authSessionId && tickets[0].profileId===record.profileId && tickets[0].authUserId===record.authUserId);
  const deadline=Date.now()+15000;
  do {
    const current=await client.query('select exists(select 1 from auth.sessions where id=$1::uuid and user_id=$2::uuid) as active',
      [tickets[0].authSessionId,record.authUserId]);
    if(current.rows[0].active===false)return true;
    await new Promise(resolve=>setTimeout(resolve,500));
  }while(Date.now()<deadline);
  return false;
};
const closeResources=async()=>{
  if(resourcesClosed)return true;
  if(steps.some(step=>step.phase!=='released' || (['open','read','recover'].includes(step.stage)&&step.captureReviewed!==true)))return false;
  requireTrue(steps.at(-1)?.stage==='empty');
  requireTrue((await transport('stop',identity(steps.at(-1)))).hostsStopped===true);
  for(const item of steps){
    const result=await transport('purge',{...identity(item),evidenceReviewed:true});
    requireTrue(result.purged===true);
    item.rawArtifactsRemoved=true;item.testLogSha256=result.testLogSha256;await resources();
  }
  resourcesClosed=true;return true;
};
try {
  callerPhase='source_verification';
  requireTrue(/^[0-9a-f]{40}$/.test(input.builtSourceSha) && /^[0-9a-f]{40}$/.test(input.macSourceSha));
  requireTrue(!execFileSync('git',['diff',input.builtSourceSha,'--','iosApp','ios-shared','core','feature','designsystem'],{cwd:root,encoding:'utf8'}).trim());
  requireTrue((await remote(`cd ${mac} && git rev-parse HEAD`)).trim()===input.macSourceSha);
  await remote(`cd ${mac} && git diff --quiet HEAD -- iosApp ios-shared core feature designsystem && test ! -e build/reports/ios/recovery-ios-active.json`);
  const digest=async relative=>{
    requireTrue(/^[a-zA-Z0-9_./-]+$/.test(relative) && !relative.includes('..'));
    return (await remote(`shasum -a 256 ${mac}/${relative}`)).trim().split(/\s+/)[0];
  };
  requireTrue(await digest('scripts/e2e-fixtures/recovery-ios-step.py')===input.stepScriptSha256);
  requireTrue(Array.isArray(input.macArtifacts) && input.macArtifacts.length>=3);
  for(const artifact of input.macArtifacts)requireTrue(await digest(artifact.path)===artifact.sha256);
  requireTrue(await digest('iosApp/Configuration/QuataPublicRuntime.local.xcconfig')===input.publicConfigurationSha256);
  const devices=JSON.parse(await remote('xcrun simctl list devices --json'));
  const owned=Object.values(devices.devices).flat().filter(item=>item.udid===device);
  requireTrue(owned.length===1 && owned[0].name===runtime.simulatorName && owned[0].state==='Booted' && owned[0].isAvailable===true);
  callerPhase='mac_disk_preflight';
  const disk=(await remote(`df -Pk ${mac}`)).trim().split(/\r?\n/).at(-1).trim().split(/\s+/);
  requireTrue(Number(disk[3])>=512*1024);
  callerPhase='journal_verification';
  requireTrue(record.state.phase==='prepared' && record.state.sessions.length===0 && !record.state.secretPotentiallyChanged && !record.state.passwordPotentiallyChanged);
  await client.connect();
  const snapshot=await resumeRecoverySecretSnapshot({client,profileId:record.profileId,authUserId:record.authUserId,storageFormat:record.storageFormat,original:record.secretSnapshot});
  const backend=createRecoveryBackend({client,journal,record,backendUrl:input.backendUrl,publicKey:input.publicKey,sessionKind:'native',
    pageOperationsSettled:()=>product?.operationsSettled()===true});
  requireTrue(await snapshot.verify() && await backend.verifyNonSecretState(record) && await backend.auditRecoverySessions(record,[]));
  product=createRecoveryIosProduct({record,displayName:input.displayName,questionLabel:input.questionLabel,
    runStep,releaseStep,verifyActor:backend.verifyActor,verifyLogout,closeResources});
  await mkdir(input.evidenceDirectory,{recursive:true});
  console.log(JSON.stringify({state:'ready',runId:record.runId,builtSourceSha:input.builtSourceSha}));
  await decision('execute:'+record.runId);
  report=await runRecoverySecretEvidence({journal,snapshot,product,backend});
  report.builtSourceSha=input.builtSourceSha;report.runnerSource=execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
}catch {report.status='failed';report.failure='prepared_ios_execution_failed';}
finally {
  report.callerPhase=callerPhase;report.resourcesClosed=resourcesClosed;
  report.visualReviews=[...reviewed];report.resourcesLedger=resourcesFile;
  report.navigationScope='Account through native Feed/Profile; recovery through existing auth-recovery-real entry, not normal Login navigation';
  if(report.status!=='passed' || !resourcesClosed || !['open','read','recover'].every(stage=>reviewed.has(stage)))report.status='failed';
  await client.end().catch(()=>{});
  await writeFile(input.reportPath,JSON.stringify(report,null,2)+'\n');
  if(report.status==='passed')await unlink(callerLock);
  console.log(JSON.stringify({state:'finished',report}));process.stdin.destroy();
}
process.exitCode=report.status==='passed'?0:1;
