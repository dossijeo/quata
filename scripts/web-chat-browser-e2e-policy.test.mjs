import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  CHAT_E2E_HARD_CLEANUP_CONTRACT,
  CHAT_E2E_ISOLATED_ACCOUNT_SCOPE,
  CHAT_E2E_MANAGER_AUTHORIZATION,
  assertChatTwoAccountLivePreflight,
  requireVerifiedHardPurge,
} from "./web-chat-browser-e2e-policy.mjs";

function environment() {
  return {
    QUATA_SUPABASE_URL: "https://example.supabase.co",
    QUATA_SUPABASE_PUBLISHABLE_KEY: "sb_publishable_fixture-key",
    QUATA_E2E_CHAT_A_COUNTRY_CODE: "34", QUATA_E2E_CHAT_A_PHONE: "600000001", QUATA_E2E_CHAT_A_PASSWORD: "a",
    QUATA_E2E_CHAT_B_COUNTRY_CODE: "34", QUATA_E2E_CHAT_B_PHONE: "600000002", QUATA_E2E_CHAT_B_PASSWORD: "b",
    QUATA_E2E_CHAT_A_E2E_SCOPE: CHAT_E2E_ISOLATED_ACCOUNT_SCOPE,
    QUATA_E2E_CHAT_B_E2E_SCOPE: CHAT_E2E_ISOLATED_ACCOUNT_SCOPE,
    QUATA_E2E_CHAT_EXTERNAL_HARD_CLEANUP: CHAT_E2E_HARD_CLEANUP_CONTRACT,
    QUATA_E2E_CHAT_MANAGER_AUTHORIZATION: CHAT_E2E_MANAGER_AUTHORIZATION,
  };
}

test("two-account Chat preflight is explicit, scoped and has no network side effects", () => {
  assert.doesNotThrow(() => assertChatTwoAccountLivePreflight(environment()));
  const noAuthorization = environment(); delete noAuthorization.QUATA_E2E_CHAT_MANAGER_AUTHORIZATION;
  assert.throws(() => assertChatTwoAccountLivePreflight(noAuthorization), { message: /missing_environment/ });
  const wrongAuthorization = environment(); wrongAuthorization.QUATA_E2E_CHAT_MANAGER_AUTHORIZATION = "yes";
  assert.throws(() => assertChatTwoAccountLivePreflight(wrongAuthorization), { message: "manager_authorization_required" });
  const sharedAccount = environment(); sharedAccount.QUATA_E2E_CHAT_B_PHONE = sharedAccount.QUATA_E2E_CHAT_A_PHONE;
  assert.throws(() => assertChatTwoAccountLivePreflight(sharedAccount), { message: "isolated_e2e_accounts_must_differ" });
});

test("a pending external purge never produces a passing Chat E2E report", () => {
  const report = { status: "passed", cleanup: { state: "logical_cleanup_complete" } };
  requireVerifiedHardPurge(report, { state: "pending" });
  assert.equal(report.status, "failed");
  assert.equal(report.error, "external_hard_purge_unverified");
  assert.equal(report.cleanup.state, "hard_purge_unverified");
});

test("only independently verified hard-purge evidence retains a successful report", () => {
  const report = { status: "passed", cleanup: { state: "verified" } };
  assert.equal(requireVerifiedHardPurge(report, { state: "verified" }), report);
  assert.equal(report.status, "passed");
});

test("both entry points wire the fail-closed preflight before remote Chat work", async () => {
  const runner = await readFile(new URL("./web-chat-browser-e2e.mjs", import.meta.url), "utf8");
  const wrapper = await readFile(new URL("./run-web-chat-browser-e2e.ps1", import.meta.url), "utf8");
  assert.match(runner, /assertChatTwoAccountLivePreflight\(\)/);
  assert.match(runner, /requireVerifiedHardPurge\(report, \{ state: "pending" \}\)/);
  assert.match(wrapper, /QUATA_E2E_CHAT_MANAGER_AUTHORIZATION/);
  assert.match(wrapper, /MANAGER_APPROVED_ISOLATED_CHAT_E2E/);
});
