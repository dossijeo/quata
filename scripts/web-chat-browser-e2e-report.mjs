/**
 * A failed logical cleanup invalidates the browser journey even when all UI assertions passed.
 * The external hard purge remains mandatory, but the runner must never report success while its
 * own compensating RPC operations are incomplete.
 */
export function recordLogicalCleanupFailure(report) {
  report.status = "failed";
  report.error ??= "chat_logical_cleanup_failed";
  report.cleanup = {
    state: "rollback_pending",
    required: "authorized hard purge of both isolated accounts and related Chat rows",
  };
  return report;
}
