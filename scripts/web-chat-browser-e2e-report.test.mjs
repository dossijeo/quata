import assert from "node:assert/strict";
import test from "node:test";

import { recordLogicalCleanupFailure } from "./web-chat-browser-e2e-report.mjs";

test("logical cleanup failure converts a successful journey into a failure", () => {
  const report = {
    status: "passed_with_external_hard_cleanup_pending",
    cleanup: { state: "ui_sessions_ended_external_hard_purge_required" },
  };

  recordLogicalCleanupFailure(report);

  assert.equal(report.status, "failed");
  assert.equal(report.error, "chat_logical_cleanup_failed");
  assert.equal(report.cleanup.state, "rollback_pending");
  assert.equal(report.status.startsWith("passed"), false);
});

test("logical cleanup failure preserves an earlier sanitized failure", () => {
  const report = {
    status: "failed",
    error: "browser_runtime_fault",
    cleanup: { state: "not_started" },
  };

  recordLogicalCleanupFailure(report);

  assert.equal(report.status, "failed");
  assert.equal(report.error, "browser_runtime_fault");
  assert.equal(report.cleanup.state, "rollback_pending");
});
