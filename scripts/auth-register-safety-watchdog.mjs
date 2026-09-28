#!/usr/bin/env node
import { access, rm } from "node:fs/promises";
import { execFile } from "node:child_process";
import { processIsAlive, recoverRegistrationActivation } from "./e2e-fixtures/auth-register-activation.mjs";

const [projectRef, cancellationFile, journalPath, delayText, ownerPidText] = process.argv.slice(2);
const delayMs = Number(delayText);
const ownerPid = Number(ownerPidText);
if (!/^[a-z0-9]{20}$/.test(projectRef || "") || !cancellationFile || !journalPath || !Number.isInteger(delayMs) || delayMs < 60_000 || !Number.isInteger(ownerPid) || ownerPid <= 0) {
  process.exit(2);
}

await new Promise((resolve) => setTimeout(resolve, delayMs));
while (true) {
  if (await cancellationRequested()) {
    await rm(cancellationFile, { force: true });
    process.exit(0);
  }
  if (!processIsAlive(ownerPid)) break;
  await new Promise((resolve) => setTimeout(resolve, 5_000));
}

// The verified owner process disappeared before cancelling the watchdog. Only
// now may the watchdog take cleanup custody and mutate the server or database.

const executable = process.platform === "win32" ? "npx.cmd" : "npx";
const command = (args) => run(["--yes", "supabase@2.109.1", ...args]);
const disableSucceeded = await command([
  "secrets", "set", "QUATA_WEB_REGISTRATION_ENABLED=false", "--project-ref", projectRef,
]).then(() => true, () => false);
const unsetSucceeded = await command([
  "secrets", "unset", "QUATA_WEB_REGISTRATION_TURNSTILE_SECRET", "QUATA_REGISTRATION_TURNSTILE_TEST_MODE", "--project-ref", projectRef,
]).then(() => true, () => false);
const cleanup = await recoverRegistrationActivation(
  { journalPath, serverAlreadyClosed: disableSucceeded && unsetSucceeded },
  { cli: command },
).catch(() => ({ verified: false }));
await rm(cancellationFile, { force: true }).catch(() => {});
if (!cleanup.verified) process.exitCode = 1;

async function cancellationRequested() {
  try {
    await access(cancellationFile);
    return true;
  } catch {
    return false;
  }
}

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
