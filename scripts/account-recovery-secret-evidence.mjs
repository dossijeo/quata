// Focal flow owner. Platform adapters must drive the real Account/Auth surfaces;
// backend adapters only audit, plan/revoke sessions and restore owned fixture data.
// This module never prints private journal contents or raw adapter exceptions.
export async function runRecoverySecretEvidence({ journal, snapshot, product, backend }) {
  const report = { check: "ACCOUNT-RECOVERY-SECRET-REAL-001", status: "failed", steps: [],
    cleanup: { password: false, secret: false, sessions: false, resources: false, journalRemoved: false } };
  const required = { product: ["login", "openAccount", "configureSecret", "saveSecret", "readPermittedState", "logout", "recoverPassword", "close"],
    backend: ["preflight", "planSession", "secretMatchesPlanned", "readRecoveryQuestion", "restorePassword", "verifyLogin", "revokeSessions", "sessionsClean"] };
  for (const [name, methods] of Object.entries(required)) {
    const adapter = name === "product" ? product : backend;
    if (methods.some(method => typeof adapter?.[method] !== "function")) throw new Error("recovery_adapter_incomplete");
  }
  const record = await journal.read();
  if (record.storageFormat !== "legacy-v32" || record.state?.phase !== "prepared" ||
      (record.state.sessions?.length ?? 0) !== 0 || record.state.secretPotentiallyChanged || record.state.passwordPotentiallyChanged ||
      ![record.originalPassword, record.temporaryPassword, record.temporaryQuestion, record.temporaryAnswer].every(value => typeof value === "string" && value.trim())) {
    throw new Error("recovery_prepared_journal_required");
  }
  if (record.temporaryPassword === record.originalPassword) throw new Error("recovery_distinct_password_required");
  const state = { phase: "prepared", sessions: [], secretPotentiallyChanged: false, passwordPotentiallyChanged: false };
  const persist = async phase => { state.phase = phase; await journal.checkpoint(structuredClone(state)); };
  const session = async (purpose, operation) => {
    const ticket = await backend.planSession({ purpose, runId: record.runId, profileId: record.profileId, authUserId: record.authUserId });
    if (!ticket || typeof ticket !== "object") throw new Error("recovery_session_plan_required");
    state.sessions.push(ticket);
    await persist(`before_session_${purpose}`);
    return operation(ticket);
  };
  const requireTrue = value => { if (value !== true) throw new Error("recovery_check_failed"); };
  let completed = false;
  try {
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
    state.passwordPotentiallyChanged = true;
    await persist("before_password_reset");
    await product.recoverPassword(record.temporaryAnswer, record.temporaryPassword);
    requireTrue(await session("temporary_verification", ticket => backend.verifyLogin(record.temporaryPassword, ticket)));
    report.steps.push("authorized_recovery_verified");
    completed = true;
  } catch {
    report.failedPhase = state.phase;
  } finally {
    try {
      if (state.passwordPotentiallyChanged) {
        await persist("before_password_restore");
        requireTrue(await backend.restorePassword(record));
      }
      if (state.sessions.length || state.secretPotentiallyChanged || state.passwordPotentiallyChanged) {
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
      report.cleanup.sessions = await backend.sessionsClean(state.sessions);
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
  if (completed && Object.values(report.cleanup).every(value => value === true)) report.status = "passed";
  return report;
}
