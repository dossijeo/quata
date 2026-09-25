#!/usr/bin/env node
import { access, rm } from "node:fs/promises";
import { execFile } from "node:child_process";
import { recoverRegistrationActivation } from "./e2e-fixtures/auth-register-activation.mjs";

const [projectRef, cancellationFile, journalPath, delayText] = process.argv.slice(2);
const delayMs = Number(delayText);
if (!/^[a-z0-9]{20}$/.test(projectRef || "") || !cancellationFile || !journalPath || !Number.isInteger(delayMs) || delayMs < 60_000) {
  process.exit(2);
}

await new Promise((resolve) => setTimeout(resolve, delayMs));
try {
  await access(cancellationFile);
  await rm(cancellationFile, { force: true });
  process.exit(0);
} catch {
  // The owner disappeared before cancelling the watchdog. Close the public
  // registration window and remove the challenge secret without printing it.
}

const executable = process.platform === "win32" ? "npx.cmd" : "npx";
const command = (args) => run(["--yes", "supabase@2.109.1", ...args]);
const disableSucceeded = await command([
  "secrets", "set", "QUATA_WEB_REGISTRATION_ENABLED=false", "--project-ref", projectRef,
]).then(() => true, () => false);
const unsetSucceeded = await command([
  "secrets", "unset", "QUATA_WEB_REGISTRATION_TURNSTILE_SECRET", "--project-ref", projectRef,
]).then(() => true, () => false);
const cleanup = await recoverRegistrationActivation(
  { journalPath, serverAlreadyClosed: disableSucceeded && unsetSucceeded },
  { cli: command },
).catch(() => ({ verified: false }));
await rm(cancellationFile, { force: true }).catch(() => {});
if (!cleanup.verified) process.exitCode = 1;

function run(args) {
  return new Promise((resolve, reject) => {
    execFile(executable, args, {
      windowsHide: true,
      maxBuffer: 4 * 1024 * 1024,
      timeout: 60_000,
      killSignal: "SIGKILL",
    }, (error, stdout) => error ? reject(new Error("watchdog_command_failed")) : resolve(stdout));
  });
}
