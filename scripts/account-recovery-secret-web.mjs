import {prepareRecoverySecretEvidence,runRecoverySecretEvidence} from "./account-recovery-secret-evidence.mjs";
import {createRecoveryBackend} from "./e2e-fixtures/recovery-backend.mjs";
import {createRecoveryWebProduct} from "./e2e-fixtures/recovery-web-product.mjs";

// Coordinator entry point. Caller owns a dedicated DB connection and an isolated
// browser/server lifecycle. Never pass credentials through argv or public reports.
export async function runRecoveryWebEvidence({client,record,directory,backendUrl,publicKey,
  openPage,closeResources,verifyCandidate,fetchImpl=fetch}) {
  const actor=structuredClone(record);
  let prepared,product,pageStarted=false,resourcesClosed=false;
  const close=async()=>{
    if(resourcesClosed || !pageStarted)return true;
    resourcesClosed=(await closeResources())===true;return resourcesClosed;
  };
  const report={check:"ACCOUNT-RECOVERY-SECRET-REAL-001",status:"failed",steps:[],
    failedPhase:"preflight",journalCreated:false,browserStarted:false,resourcesClosed:null};
  try {
    const probe=createRecoveryBackend({client,record:actor,backendUrl,publicKey,fetchImpl,
      journal:{read:async()=>{throw Error("recovery_preflight_journal_unavailable");}},pageOperationsSettled:()=>false});
    if(await probe.preflight(actor)!==true)return report;
    if(typeof openPage!=="function" || typeof closeResources!=="function" || typeof verifyCandidate!=="function")throw Error("recovery_coordinator_dependencies_required");
    report.failedPhase="candidate_verification";
    if(await verifyCandidate()!==true)return report;
    report.failedPhase="preparation";
    report.journalCreated=null; // Creation may succeed before a later preparation failure.
    prepared=await prepareRecoverySecretEvidence({client,directory,record:actor});
    report.journalCreated=true;
    const durable=await prepared.journal.read();
    const backend=createRecoveryBackend({client,journal:prepared.journal,record:durable,backendUrl,publicKey,fetchImpl,
      pageOperationsSettled:()=>product?.operationsSettled()===true});
    report.failedPhase="browser_start";
    pageStarted=true;report.browserStarted=true;
    const page=await openPage();
    product=createRecoveryWebProduct({page,record:durable,backendOrigin:backendUrl,verifyActor:backend.verifyActor,closeResources:close});
    return await runRecoverySecretEvidence({...prepared,product,backend});
  } catch {
    // Setup errors never erase an existing journal. The explicit resume entry
    // point must verify restitution before it can be removed.
    return report;
  } finally {
    if(pageStarted)report.resourcesClosed=resourcesClosed || await close().catch(()=>false);
  }
}
