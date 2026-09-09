import {loadRecoveryInput,recoveryTransportEnvironment} from './e2e-fixtures/recovery-runtime-config.mjs';
import {readFile,writeFile,mkdir,stat} from 'node:fs/promises';
import {createServer} from 'node:http';
import {createInterface} from 'node:readline';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {openRecoveryJournal} from './e2e-fixtures/recovery-private-journal.mjs';
import {resumeRecoverySecretSnapshot} from './e2e-fixtures/account-recovery-secret.mjs';
import {createRecoveryBackend} from './e2e-fixtures/recovery-backend.mjs';
import {createRecoveryWebProduct} from './e2e-fixtures/recovery-web-product.mjs';
import {runRecoverySecretEvidence} from './account-recovery-secret-evidence.mjs';
const lines=createInterface({input:process.stdin})[Symbol.asyncIterator]();

const fatal=()=>{console.log(JSON.stringify({state:'failed',error:'prepared_caller_initialization_failed'}));process.exit(1);};
process.on('uncaughtException',fatal);
process.on('unhandledRejection',fatal);
const {input,runtime}=await loadRecoveryInput(process.argv[2],'web');
const root=runtime.root;
const distribution=runtime.distribution;
const require=createRequire(runtime.dependencyPackage);
const {Client}=require('pg');const {chromium}=require('playwright-core');
const ledger=JSON.parse(await readFile(input.ledger,'utf8'));
const identity={runId:ledger.focalRunId,profileId:ledger.profileId,authUserId:ledger.authUserId};
const journal=await openRecoveryJournal({file:ledger.focalJournal,identity});
const record=await journal.read();
const connection=new URL((await readFile(runtime.databaseUrlFile,'utf8')).trim());
for(const key of ['sslmode','sslrootcert','sslcert','sslkey'])connection.searchParams.delete(key);
const client=new Client({connectionString:connection.toString(),ssl:{ca:await readFile(runtime.databaseCaFile,'utf8'),rejectUnauthorized:true},connectionTimeoutMillis:5000,statement_timeout:5000});
let server,browser,page,product,closed=false,callerUncertain=false,report={status:'failed',phase:'readiness'},pageErrors=0;
const boundedEvaluate=async operation=>{
  let timer;
  try{return await Promise.race([page.evaluate(operation),new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('caller_evaluation_timeout')),45000);})]);}
  catch(error){callerUncertain=true;throw error;}
  finally{clearTimeout(timer);}
};
const close=async()=>{
  if(closed)return true;
  await browser?.close();
  if(server){server.closeAllConnections();await new Promise((resolve,reject)=>server.close(error=>error?reject(error):resolve()));}
  closed=true;return true;
};
try {
  const manifest=JSON.parse(await readFile(runtime.preparationManifest,'utf8'));
  for(const file of manifest.files){const bytes=await readFile(path.join(distribution,file.path));if(bytes.length!==file.bytes || createHash('sha256').update(bytes).digest('hex')!==file.sha256)throw Error('asset_mismatch');}
  if((await readFile(path.join(distribution,'quata-source-revision.txt'),'utf8')).trim()!==manifest.sourceRevision)throw Error('source_mismatch');
  if(execFileSync('git',['diff',manifest.sourceRevision+'..HEAD','--','web','feature','app','iosApp','core'],{cwd:root,encoding:'utf8'}).trim())throw Error('product_source_changed');
  const runnerSource=execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
  if(record.state.phase!=='prepared' || record.state.sessions.length || record.state.secretPotentiallyChanged || record.state.passwordPotentiallyChanged)throw Error('journal_not_prepared');
  await client.connect();
  const snapshot=await resumeRecoverySecretSnapshot({client,...identity,storageFormat:record.storageFormat,original:record.secretSnapshot});
  const backend=createRecoveryBackend({client,journal,record,backendUrl:input.backendUrl,publicKey:input.publicKey,pageOperationsSettled:()=>!callerUncertain && product?.operationsSettled()===true});
  if(!await snapshot.verify() || !await backend.preflight(record) || !await backend.verifyNonSecretState(record))throw Error('fixture_changed');
  const mime={'.html':'text/html','.js':'application/javascript','.wasm':'application/wasm','.json':'application/json','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.woff2':'font/woff2','.ttf':'font/ttf'};
  const attr=value=>value.replaceAll('&','&amp;').replaceAll('"','&quot;');
  server=createServer(async(request,response)=>{
    try {
      const url=new URL(request.url,'http://127.0.0.1');
      if(!['GET','HEAD'].includes(request.method))return response.writeHead(405).end();
      if(url.pathname.startsWith('/wordpress-proxy/')){
        const target='https://egquata.com'+url.pathname.slice('/wordpress-proxy'.length)+url.search;
        const upstream=await fetch(target,{signal:AbortSignal.timeout(20000)});
        response.writeHead(upstream.status,{'content-type':upstream.headers.get('content-type')??'application/octet-stream'});
        return response.end(Buffer.from(await upstream.arrayBuffer()));
      }
      const file=path.resolve(distribution,'.'+(url.pathname==='/'?'/index.html':decodeURIComponent(url.pathname)));
      if(!file.startsWith(distribution+path.sep))return response.writeHead(403).end();
      if(!(await stat(file).catch(()=>null))?.isFile())return response.writeHead(404).end();
      let body=await readFile(file);
      if(file.endsWith('index.html'))body=Buffer.from(body.toString('utf8')
        .replace(/<meta name="quata-supabase-url" content="[^"]*">/,`<meta name="quata-supabase-url" content="${attr(input.backendUrl)}">`)
        .replace(/<meta name="quata-supabase-publishable-key" content="[^"]*">/,`<meta name="quata-supabase-publishable-key" content="${attr(input.publicKey)}">`));
      response.writeHead(200,{'content-type':mime[path.extname(file)]??'application/octet-stream','cross-origin-opener-policy':'same-origin','cross-origin-embedder-policy':'require-corp','cache-control':'no-store'});response.end(body);
    }catch{if(!response.headersSent)response.writeHead(502);response.end();}
  });
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  browser=await chromium.launch({executablePath:runtime.browserExecutable,headless:true,args:['--use-angle=swiftshader','--enable-unsafe-swiftshader','--force-renderer-accessibility']});
  const context=await browser.newContext({locale:'es-ES',viewport:{width:430,height:930},deviceScaleFactor:1});
  await context.addInitScript(()=>{localStorage.setItem('quata_recovery_secret_e2e_opt_in','I_ACCEPT_ACCOUNT_RECOVERY_SECRET_FIXTURE');sessionStorage.setItem('quata.auth.e2e','1');});
  page=await context.newPage();page.on('pageerror',()=>pageErrors++);
  await page.goto(`http://127.0.0.1:${server.address().port}/?quata-recovery-secret-e2e=1&quata-auth-e2e=1#auth`,{waitUntil:'domcontentloaded',timeout:45000});
  await page.waitForFunction(()=>globalThis.__quataAuthE2eProduct?.version===1,null,{timeout:60000});
  product=createRecoveryWebProduct({page,record,backendOrigin:input.backendUrl,verifyActor:backend.verifyActor,closeResources:close});
  await mkdir(input.evidenceDirectory,{recursive:true});
  console.log(JSON.stringify({state:'ready',productSource:manifest.sourceRevision,runnerSource,assetsVerified:manifest.files.length,freshBrowser:true}));
  let timer;const command=await Promise.race([lines.next(),new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('execution_not_authorized')),180000);})]).finally(()=>clearTimeout(timer));
  if(command.value!==`execute:${record.runId}`)throw Error('execution_not_authorized');
  const capture=name=>page.screenshot({path:path.join(input.evidenceDirectory,name+'.png')});
  const focalProduct={...product,
    login:async(...args)=>{
      const result=await product.login(...args);
      await page.waitForFunction(()=>document.documentElement.getAttribute('data-quata-ugc-terms-state')==='accepted',null,{timeout:20000});return result;
    },
    openAccount:async()=>{
      await product.openAccount();await capture('account-before-secret');
      console.log(JSON.stringify({state:'account_visual_review_ready'}));
      let timer;const decision=await Promise.race([lines.next(),new Promise((_,reject)=>{timer=setTimeout(()=>reject(Error('visual_review_not_accepted')),180000);})]).finally(()=>clearTimeout(timer));
      if(decision.value!==`account-visible:${record.runId}`)throw Error('visual_review_not_accepted');
    },
    readPermittedState:async()=>{const state=await product.readPermittedState();if(state.answerEmpty===true)await capture('account-secret-saved-answer-empty');return state;},
    recoverPassword:async(...args)=>{
      await product.recoverPassword(...args);
      await boundedEvaluate(()=>globalThis.__quataAuthE2eProduct.openLogin());
      await page.waitForFunction(()=>document.documentElement.getAttribute('data-quata-auth-destination')==='login',null,{timeout:20000});
      await capture('login-opened-after-recovery');
    },
  };
  report=await runRecoverySecretEvidence({journal,snapshot,product:focalProduct,backend});
  report.productSource=manifest.sourceRevision;report.runnerSource=runnerSource;report.pageErrors=pageErrors;
} catch {report.failure='prepared_web_execution_failed';}
finally {
  report.resourcesClosed=await close().catch(()=>false);
  await client.end().catch(()=>{});
  await writeFile(input.reportPath,JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({state:'finished',report}));
  process.stdin.destroy();
}
process.exitCode=report.status==='passed'?0:1;
