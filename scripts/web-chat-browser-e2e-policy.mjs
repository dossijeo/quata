const REQUIRED = Object.freeze([
  "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY",
  "QUATA_E2E_CHAT_A_COUNTRY_CODE", "QUATA_E2E_CHAT_A_PHONE", "QUATA_E2E_CHAT_A_PASSWORD",
  "QUATA_E2E_CHAT_B_COUNTRY_CODE", "QUATA_E2E_CHAT_B_PHONE", "QUATA_E2E_CHAT_B_PASSWORD",
  "QUATA_E2E_CHAT_A_E2E_SCOPE", "QUATA_E2E_CHAT_B_E2E_SCOPE",
  "QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP", "QUATA_E2E_CHAT_MANAGER_AUTHORIZATION",
]);

export const CHAT_E2E_MANAGER_AUTHORIZATION = "MANAGER_APPROVED_ISOLATED_CHAT_E2E";
export const CHAT_E2E_HARD_CLEANUP_CONTRACT = "approved_isolated_account_purge";
export const CHAT_E2E_ISOLATED_ACCOUNT_SCOPE = "isolated_sb04_account";

/**
 * Data-free preflight: a live Chat mutation cannot reach authentication merely because a shell
 * has credentials. A manager must authorize this exact process after checking the purge plan.
 */
export function assertChatTwoAccountLivePreflight(environment = process.env) {
  const missing = REQUIRED.filter(name => !environment[name]?.trim());
  if (missing.length) throw new Error(`missing_environment:${missing.join(",")}`);
  if (environment.QUATA_E2E_CHAT_MANAGER_AUTHORIZATION !== CHAT_E2E_MANAGER_AUTHORIZATION) {
    throw new Error("manager_authorization_required");
  }
  if (environment.QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP !== CHAT_E2E_HARD_CLEANUP_CONTRACT) {
    throw new Error("safe_cleanup_contract_missing");
  }
  for (const label of ["A", "B"]) {
    if (environment[`QUATA_E2E_CHAT_${label}_E2E_SCOPE`] !== CHAT_E2E_ISOLATED_ACCOUNT_SCOPE) {
      throw new Error("isolated_e2e_account_scope_missing");
    }
  }
  const a = `${environment.QUATA_E2E_CHAT_A_COUNTRY_CODE}|${environment.QUATA_E2E_CHAT_A_PHONE}`;
  const b = `${environment.QUATA_E2E_CHAT_B_COUNTRY_CODE}|${environment.QUATA_E2E_CHAT_B_PHONE}`;
  if (a === b) throw new Error("isolated_e2e_accounts_must_differ");
}

/** A journey with no independently verified hard purge is evidence, never a pass. */
export function requireVerifiedHardPurge(report, verification) {
  report.status = "failed";
  report.error ??= "external_hard_purge_unverified";
  report.cleanup = {
    state: "hard_purge_unverified",
    required: "authorized purge and independent verification of both isolated accounts and Chat rows",
  };
  return report;
}

/** Reject hand-written success flags: verified E2E requires the redacted exact-ID gate evidence. */
export function requireExactPurgeEvidence(evidence) {
  // Gate 001 used a forgeable local JSON success flag. Gate 002 is inspection-only.
  // Neither can prove destructive cleanup until a separately deployed service verifies an
  // Actions artifact signature, run id, candidate SHA and one-time nonce server-side.
  void evidence;
  throw new Error("external_hard_purge_evidence_unavailable_by_construction");
}
