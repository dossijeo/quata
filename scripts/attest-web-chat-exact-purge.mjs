#!/usr/bin/env node
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { requireExactPurgeEvidence, requireVerifiedHardPurge } from "./web-chat-browser-e2e-policy.mjs";

if (process.argv.length !== 8 || process.argv[2] !== "--chat-report" || process.argv[4] !== "--purge-evidence" || process.argv[6] !== "--out") {
  throw new Error("attestation_arguments_invalid");
}
const [reportPath, evidencePath, output] = [process.argv[3], process.argv[5], process.argv[7]].map(resolve);
const report = JSON.parse(await readFile(reportPath, "utf8"));
const evidence = requireExactPurgeEvidence(JSON.parse(await readFile(evidencePath, "utf8")));
if (report.check !== "WEB-CHAT-BROWSER-02" || report.status !== "passed") throw new Error("chat_report_not_passed");
requireVerifiedHardPurge(report, { state: "verified", evidence });
report.cleanup = { state: "hard_purge_verified_exact_id", evidence: { check: evidence.check, manifestSha256: evidence.manifestSha256, databaseFingerprint: evidence.databaseFingerprint } };
await mkdir(dirname(output), { recursive: true }); await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
