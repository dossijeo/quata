import { snapshotRecoverySecret, resumeRecoverySecretSnapshot } from "./e2e-fixtures/account-recovery-secret.mjs";
import { createRecoveryJournal } from "./e2e-fixtures/recovery-private-journal.mjs";

// Prepare the exact actor's snapshot and durable journal as one operation.
// No product/backend mutation is available through this entry point.
export async function prepareRecoverySecretEvidence({ client, directory, record }) {
  const prepared = structuredClone({
    runId: record.runId, profileId: record.profileId, authUserId: record.authUserId,
    countryCode: record.countryCode, phone: record.phone,
    originalPassword: record.originalPassword, temporaryPassword: record.temporaryPassword,
    temporaryQuestion: record.temporaryQuestion, temporaryAnswer: record.temporaryAnswer,
    storageFormat: "legacy-v32",
    state: { phase: "prepared", sessions: [], secretPotentiallyChanged: false, passwordPotentiallyChanged: false },
  });
  if (![prepared.originalPassword, prepared.temporaryPassword, prepared.temporaryQuestion, prepared.temporaryAnswer]
      .every(value => typeof value === "string" && value.trim()) ||
      prepared.originalPassword === prepared.temporaryPassword ||
      prepared.temporaryQuestion !== prepared.temporaryQuestion.trim() ||
      prepared.temporaryAnswer !== prepared.temporaryAnswer.trim()) {
    throw new Error("recovery_preparation_values_invalid");
  }
  let journal;
  const snapshot = await snapshotRecoverySecret({ client, profileId: prepared.profileId,
    authUserId: prepared.authUserId, storageFormat: prepared.storageFormat,
    persistSnapshot: async secretSnapshot => {
      journal = await createRecoveryJournal({ directory, record: { ...prepared, secretSnapshot } });
    },
  });
  return Object.freeze({ journal, snapshot });
}

// Focal flow owner. Platform adapters must drive the real Account/Auth surfaces;
// backend adapters only audit, plan/revoke sessions and restore owned fixture data.
// This module never prints private journal contents or raw adapter exceptions.
export async function runRecoverySecretEvidence({ journal, snapshot, product, backend }) {
  return executeRecoverySecret({ journal, snapshot, product, backend });
}

// Restitution only: this entry point must never certify or replay a product flow.
export async function resumeRecoverySecretCleanup({ journal, client, product, backend }) {
  const record = await journal.read();
  if (!Array.isArray(record.state?.sessions) || record.state.sessions.some(ticket => !ticket || typeof ticket !== "object" || Array.isArray(ticket))) {
    throw new Error("recovery_interrupted_state_invalid");
  }
  if (typeof backend?.confirmInterruptedRunSettled !== "function") throw new Error("recovery_settlement_check_required");
  let settled = false;
  try { settled = (await backend.confirmInterruptedRunSettled(record)) === true; } catch { /* Retain the journal. */ }
  if (!settled) throw new Error("recovery_interrupted_operations_unresolved");
  let snapshot;
  try {
    snapshot = await resumeRecoverySecretSnapshot({ client, profileId: record.profileId,
      authUserId: record.authUserId, storageFormat: record.storageFormat, original: record.secretSnapshot });
  } catch {
    const report = { check: "ACCOUNT-RECOVERY-SECRET-CLEANUP-001", status: "failed", steps: [],
      cleanupFailure: "recovery_snapshot_unavailable",
      cleanup: {password:false,secret:false,sessions:false,resources:false,journalRemoved:false} };
    try {
      await backend.revokeSessions(record.state.sessions);
      report.cleanup.sessions = (await backend.sessionsClean(record.state.sessions)) === true;
    } catch { /* Keep the journal for another cleanup attempt. */ }
    try { report.cleanup.resources = (await product.close()) === true; } catch { /* Still retained. */ }
    await journal.checkpoint({...record.state,phase:"cleanup_required"}).catch(()=>{});
    return report;
  }
  return executeRecoverySecret({ journal, snapshot, product, backend, recoveryRecord: record });
}

async function executeRecoverySecret({ journal, snapshot, product, backend, recoveryRecord }) {
  const recovering = recoveryRecord !== undefined;
  const report = { check: "ACCOUNT-RECOVERY-SECRET-REAL-001", status: "failed", steps: [],
    cleanup: { password: false, secret: false, sessions: false, resources: false, journalRemoved: false } };
  if (recovering) report.check = "ACCOUNT-RECOVERY-SECRET-CLEANUP-001";
  const required = { product: recovering ? ["close"] : ["login", "openAccount", "configureSecret", "saveSecret", "readPermittedState", "logout", "recoverPassword", "close"],
    backend: ["planSession", "secretMatchesPlanned", "restorePassword", "verifyLogin", "revokeSessions", "sessionsClean", "auditRecoverySessions",
      ...(recovering ? [] : ["preflight", "readRecoveryQuestion", "confirmOperationsSettled"])] };
  for (const [name, methods] of Object.entries(required)) {
    const adapter = name === "product" ? product : backend;
    if (methods.some(method => typeof adapter?.[method] !== "function")) throw new Error("recovery_adapter_incomplete");
  }
  const record = recoveryRecord ?? await journal.read();
  if (record.storageFormat !== "legacy-v32" || (!recovering && (record.state?.phase !== "prepared" ||
      (record.state.sessions?.length ?? 0) !== 0 || record.state.secretPotentiallyChanged || record.state.passwordPotentiallyChanged)) ||
      ![record.originalPassword, record.temporaryPassword, record.temporaryQuestion, record.temporaryAnswer].every(value => typeof value === "string" && value.trim())) {
    throw new Error("recovery_prepared_journal_required");
  }
  if (record.temporaryPassword === record.originalPassword) throw new Error("recovery_distinct_password_required");
  if (recovering && (!Array.isArray(record.state?.sessions) ||
      typeof record.state.secretPotentiallyChanged !== "boolean" || typeof record.state.passwordPotentiallyChanged !== "boolean" ||
      record.state.sessions.some(ticket => !ticket || typeof ticket !== "object" || Array.isArray(ticket)))) {
    throw new Error("recovery_interrupted_state_invalid");
  }
  const state = recovering ? structuredClone(record.state) : { phase: "prepared", sessions: [], secretPotentiallyChanged: false, passwordPotentiallyChanged: false };
  const persist = async phase => { state.phase = phase; await journal.checkpoint(structuredClone(state)); };
  const session = async (purpose, operation) => {
    if (await backend.auditRecoverySessions(record, structuredClone(state.sessions)) !== true) throw new Error("recovery_unowned_sessions_detected");
    const ticket = await backend.planSession({ purpose, runId: record.runId, profileId: record.profileId, authUserId: record.authUserId });
    if (!ticket || typeof ticket !== "object") throw new Error("recovery_session_plan_required");
    state.sessions.push(ticket);
    await persist(`before_session_${purpose}`);
    return operation(ticket);
  };
  const requireTrue = value => { if (value !== true) throw new Error("recovery_check_failed"); };
  let completed = false;
  try {
    if (!recovering) {
      requireTrue(await backend.preflight(record));
      requireTrue(await snapshot.verify());
      requireTrue(await session("producer", ticket => product.login(record.originalPassword, ticket)));
      await product.openAccount();
      await product.configureSecret(record.temporaryQuestion, record.temporaryAnswer);
      state.secretPotentiallyChanged = true;
      await persist("before_save_secret");
      await product.saveSecret(); // Exactly one product Save, never SQL preparation.
      requireTrue(await backend.secretMatchesPlanned(record));
      report.steps.push("account_secret_produced");
      const visible = await product.readPermittedState();
      const permittedStateKeys = ["visible", "question", "answerEmpty", "saving", "failed", "saved"];
      if (visible?.question !== record.temporaryQuestion || visible.answerEmpty !== true ||
          Object.keys(visible).length !== permittedStateKeys.length ||
          Object.keys(visible).some(key => !permittedStateKeys.includes(key)) ||
          visible.visible !== true || visible.saving !== false || visible.failed !== false || visible.saved !== true) throw new Error("recovery_visible_read_failed");
      const permitted = await backend.readRecoveryQuestion(record);
      if (Object.keys(permitted ?? {}).sort().join(",") !== "secret_question" || permitted.secret_question !== record.temporaryQuestion) {
        throw new Error("recovery_public_read_failed");
      }
      report.steps.push("permitted_question_read_without_answer");
      await product.logout();
      requireTrue(await backend.auditRecoverySessions(record, structuredClone(state.sessions)));
      state.passwordPotentiallyChanged = true;
      await persist("before_password_reset");
      await product.recoverPassword(record.temporaryAnswer, record.temporaryPassword);
      requireTrue(await session("temporary_verification", ticket => backend.verifyLogin(record.temporaryPassword, ticket)));
      report.steps.push("authorized_recovery_verified");
      completed = true;
    }
  } catch {
    report.failedPhase = state.phase;
  } finally {
    let operationsSettled = recovering;
    try {
      if (!recovering) operationsSettled = (await backend.confirmOperationsSettled(record, structuredClone(state))) === true;
      requireTrue(operationsSettled);
      // A crash may follow successful restoration but precede journal deletion.
      // Check the original first so restart does not depend on a removed temporary secret.
      let originalVerified = false;
      if (recovering) {
        const result = await session("original_verification", ticket => backend.verifyLogin(record.originalPassword, ticket));
        if (result !== true && result !== false) throw new Error("recovery_login_result_uncertain");
        originalVerified = result;
      }
      if (state.passwordPotentiallyChanged && !originalVerified) {
        requireTrue(await backend.auditRecoverySessions(record, structuredClone(state.sessions)));
        await persist("before_password_restore");
        requireTrue(await backend.restorePassword(record));
      }
      if (!originalVerified && (state.sessions.length || state.secretPotentiallyChanged || state.passwordPotentiallyChanged)) {
        requireTrue(await session("original_verification", ticket => backend.verifyLogin(record.originalPassword, ticket)));
      }
      report.cleanup.password = true;
      // Never overwrite a third-party secret or remove the temporary recovery path
      // before the original password is known to work.
      if (!(await snapshot.verify())) {
        requireTrue(state.secretPotentiallyChanged);
        requireTrue(await backend.secretMatchesPlanned(record));
        requireTrue(await snapshot.restore({ secret_question: record.temporaryQuestion, secret_answer: record.temporaryAnswer }));
      }
      report.cleanup.secret = await snapshot.verify();
    } catch {
      report.cleanupFailure = "password_or_secret_restoration_failed";
    }
    try {
      await backend.revokeSessions(state.sessions);
      report.cleanup.sessions = (await backend.sessionsClean(state.sessions)) === true && operationsSettled;
    } catch { report.cleanup.sessions = false; }
    try { report.cleanup.resources = (await product.close()) === true; }
    catch { report.cleanup.resources = false; }
    if (Object.entries(report.cleanup).filter(([key]) => key !== "journalRemoved").every(([,value]) => value === true)) {
      try {
        await journal.removeAfterVerification(async () => ({password:report.cleanup.password,
          secret:await snapshot.verify(),sessions:await backend.sessionsClean(state.sessions)}));
        report.cleanup.journalRemoved = true;
      } catch { report.cleanupFailure = "journal_cleanup_unverified"; }
    }
    if (!report.cleanup.journalRemoved) await persist("cleanup_required").catch(() => {});
  }
  if (Object.values(report.cleanup).every(value => value === true)) {
    if (recovering) report.status = "restored";
    else if (completed) report.status = "passed";
  }
  return report;
}
