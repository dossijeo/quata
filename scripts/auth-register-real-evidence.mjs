#!/usr/bin/env node
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { runRegistrationActivationEvidence } from "./e2e-fixtures/auth-register-activation.mjs";

const OPT_IN = "I_ACCEPT_TEMPORARY_REAL_REGISTRATION_AND_EXACT_CLEANUP";
const TURNSTILE_TEST_MODE = "cloudflare-test";
const TURNSTILE_TEST_SITE_KEY = "1x00000000000000000000AA";
const TURNSTILE_TEST_SECRET = "1x0000000000000000000000000000000AA";
// Supabase CLI on Windows cannot reliably consume --env-file paths containing
// non-ASCII characters. Keep the durable credentials at their protected path,
// but stage short-lived journals and activation files under an ASCII path.
const DEFAULT_PRIVATE_DIRECTORY = process.env.LOCALAPPDATA
  ? resolve(process.env.LOCALAPPDATA, "QuataEvidence", "Registration")
  : "C:/Users/PC/.quata-registration-evidence";
const DEFAULT_ENV_FILE = `${DEFAULT_PRIVATE_DIRECTORY}/supabase-registration.env`;
const DEFAULT_DB_URL_FILE = "C:/Users/PC/.quata-supabase-db-url-verify-full.txt";
const DEFAULT_DB_TLS_CA_FILE = "C:/Users/PC/.quata-supabase-pooler-ca.pem";

const output = parseArgs(process.argv.slice(2));
let report = {
  check: "AUTH-REGISTER-REAL-001",
  status: "failed",
  failureCode: "runner_failed_before_report",
  cleanup: { verified: false },
};
try {
  const config = await configuration();
  report = await runRegistrationActivationEvidence(config);
} catch (error) {
  report = error?.evidenceReport ?? { ...report, failureCode: safeCode(error) };
} finally {
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, "utf8");
}
if (report.status !== "passed" || report.cleanup?.verified !== true) process.exitCode = 1;
else console.log("AUTH_REGISTER_REAL_EVIDENCE_PASSED");

function parseArgs(args) {
  if (args.length === 2 && args[0] === "--out" && args[1]?.trim()) return resolve(args[1]);
  throw new Error("usage: node scripts/auth-register-real-evidence.mjs --out <ignored-report.json>");
}

async function configuration() {
  if (process.env.QUATA_AUTH_REGISTER_REAL_OPT_IN !== OPT_IN) throw new Error("registration_mutation_opt_in_required");
  const privateDirectory = process.env.QUATA_REGISTRATION_PRIVATE_DIRECTORY?.trim() || DEFAULT_PRIVATE_DIRECTORY;
  const values = parseEnv(await readFile(process.env.QUATA_REGISTRATION_ENV_FILE?.trim() || DEFAULT_ENV_FILE, "utf8"));
  const publicSource = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const supabaseUrl = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(publicSource)?.[1]?.replace(/\/+$/, "");
  const publishableKey = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(publicSource)?.[1];
  const required = (name) => {
    const value = values[name]?.trim();
    if (!value) throw new Error(`registration_private_value_missing:${name}`);
    return value;
  };
  const registrationOrigin = process.env.QUATA_REGISTRATION_EVIDENCE_ORIGIN?.trim() || "https://egquata.com";
  const allowedOrigins = required("QUATA_WEB_REGISTRATION_ALLOWED_ORIGINS").split(",").map((value) => value.trim());
  const allowedHostnames = required("QUATA_TURNSTILE_ALLOWED_HOSTNAMES").split(",").map((value) => value.trim());
  if (!allowedOrigins.includes(registrationOrigin) || !allowedHostnames.includes(new URL(registrationOrigin).hostname)) {
    throw new Error("registration_evidence_origin_not_allowed");
  }
  if (!supabaseUrl || !publishableKey) throw new Error("registration_public_configuration_missing");
  const productSha = await gitHead();
  const turnstileTestMode = process.env.QUATA_REGISTRATION_TURNSTILE_MODE?.trim() === TURNSTILE_TEST_MODE;
  return {
    productSha,
    projectRef: new URL(supabaseUrl).hostname.split(".")[0],
    supabaseUrl,
    publishableKey,
    registrationApiKey: required("QUATA_WEB_REGISTRATION_API_KEY"),
    pepper: required("QUATA_WEB_REGISTRATION_PEPPER"),
    turnstileSiteKey: turnstileTestMode ? TURNSTILE_TEST_SITE_KEY : required("QUATA_TURNSTILE_SITE_KEY"),
    turnstileSecret: turnstileTestMode ? TURNSTILE_TEST_SECRET : required("QUATA_WEB_REGISTRATION_TURNSTILE_SECRET"),
    turnstileTestMode,
    registrationOrigin,
    turnstilePageUrl: process.env.QUATA_TURNSTILE_EVIDENCE_PAGE_URL?.trim() || `${registrationOrigin}/`,
    browserExecutablePath: process.env.QUATA_BROWSER_EXECUTABLE_PATH?.trim() || undefined,
    countryCode: process.env.QUATA_REGISTRATION_EVIDENCE_COUNTRY_CODE?.trim() || "34",
    dbUrlFile: process.env.SUPABASE_DB_URL_FILE?.trim() || DEFAULT_DB_URL_FILE,
    dbTlsCaFile: process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || DEFAULT_DB_TLS_CA_FILE,
    privateDirectory,
    watchdogDelayMs: 10 * 60_000,
  };
}

function parseEnv(source) {
  const values = {};
  for (const line of source.split(/\r?\n/)) {
    const match = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/.exec(line);
    if (!match || line.trimStart().startsWith("#")) continue;
    let value = match[2];
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    values[match[1]] = value;
  }
  return values;
}

async function gitHead() {
  const { execFile } = await import("node:child_process");
  return new Promise((resolvePromise, reject) => execFile("git", ["rev-parse", "HEAD"], { windowsHide: true }, (error, stdout) => {
    if (error || !/^[0-9a-f]{40}\s*$/.test(stdout)) reject(new Error("registration_product_sha_unavailable"));
    else resolvePromise(stdout.trim());
  }));
}

function safeCode(error) {
  const raw = typeof error?.message === "string" ? error.message : "runner_failed_before_report";
  return /^[a-z0-9_:.-]{1,120}$/i.test(raw) ? raw : "sanitized_failure";
}
