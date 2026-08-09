#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import pg from "pg";

const RECOVERY_OPT_IN = "I_ACCEPT_PASSWORD_RESET_ROUNDTRIP";
const ACCOUNT_OPT_IN = "I_ACCEPT_AUTHORIZED_RECOVERY_ACCOUNT_MUTATION";
const DB_PREP_OPT_IN = "I_ACCEPT_DB_RECOVERY_SECRET_ROUNDTRIP";

const options = parseArguments(process.argv.slice(2));
const report = {
  check: "AUTH-RECOVERY-REAL-BRIDGE-001",
  mode: "real_authorized_account_roundtrip",
  status: "failed",
  revision: await repositoryRevision(),
  steps: [],
  cleanup: { passwordRestored: false, recoverySecretRestored: false },
};
let dbPrep;
let configForCleanup;

try {
  const config = await loadConfiguration(process.env);
  configForCleanup = config;
  const original = config.password;
  const temporary = config.temporaryPassword;
  if (temporary === original) throw new Error("temporary_password_must_differ");

  const originalLogin = await authBridge(config, {
    action: "login",
    country_code: config.countryCode,
    phone_local: config.phone,
    password: original,
  }, "original_login_failed");
  const bearer = stringAt(originalLogin, ["session", "access_token"], "original_login_missing_access_token");
  report.steps.push("original_password_login_succeeded");

  if (config.dbPrep) {
    dbPrep = await prepareRecoverySecretWithDatabase(config);
    report.steps.push("recovery_secret_prepared_with_authorized_db_roundtrip");
  } else {
    await authBridge(config, {
      action: "update_recovery_secret",
      version: 1,
      secret_question: config.secretQuestion,
      secret_answer: config.secretAnswer,
    }, "recovery_secret_update_failed", bearer);
    report.steps.push("recovery_secret_updated_for_authorized_account");
  }

  const missing = await authBridge(config, {
    action: "recovery_question",
    country_code: config.countryCode,
    phone_local: config.missingPhone,
  }, "missing_recovery_question_probe_failed", undefined, [404]);
  if (missing.status !== 404) throw new Error("missing_account_unexpectedly_resolved");
  report.steps.push("missing_account_returns_404");

  const question = await authBridge(config, {
    action: "recovery_question",
    country_code: config.countryCode,
    phone_local: config.phone,
  }, "registered_recovery_question_failed");
  if (question.body?.secret_question !== config.secretQuestion) throw new Error("registered_question_mismatch");
  report.steps.push("registered_account_question_returned");

  await authBridge(config, {
    action: "reset_password",
    country_code: config.countryCode,
    phone_local: config.phone,
    secret_answer: config.secretAnswer,
    new_password: temporary,
  }, "temporary_reset_failed");
  report.steps.push("temporary_password_reset_succeeded");

  await authBridge(config, {
    action: "login",
    country_code: config.countryCode,
    phone_local: config.phone,
    password: temporary,
  }, "temporary_login_failed");
  report.steps.push("temporary_password_login_succeeded");

  await authBridge(config, {
    action: "reset_password",
    country_code: config.countryCode,
    phone_local: config.phone,
    secret_answer: config.secretAnswer,
    new_password: original,
  }, "original_password_restore_failed");
  report.cleanup.passwordRestored = true;
  report.steps.push("original_password_restored");

  await authBridge(config, {
    action: "login",
    country_code: config.countryCode,
    phone_local: config.phone,
    password: original,
  }, "restored_login_failed");
  report.steps.push("restored_password_login_succeeded");

  report.account = {
    countryCode: config.countryCode,
    phoneSuffix: config.phone.slice(-4),
    missingPhoneSuffix: config.missingPhone.slice(-4),
  };
  report.bridgeEffects = {
    accepted: true,
    passwordRoundTrip: true,
    recoverySecretUpdated: true,
    dataCleanup: "pending_final_cleanup_check",
  };
  report.status = "passed";
} catch (error) {
  report.error = safeError(error);
} finally {
  if (configForCleanup && !report.cleanup.passwordRestored && report.steps.includes("temporary_password_reset_succeeded")) {
    report.cleanup.passwordRestored = await restoreOriginalPassword(configForCleanup).catch(() => false);
  }
  if (dbPrep) {
    report.cleanup.recoverySecretRestored = await restoreRecoverySecret(dbPrep).catch(() => false);
  }
  if (report.status === "passed") {
    if (!report.cleanup.passwordRestored) {
      report.status = "failed";
      report.error = "password_cleanup_not_verified";
    } else if (dbPrep && !report.cleanup.recoverySecretRestored) {
      report.status = "failed";
      report.error = "recovery_secret_cleanup_not_verified";
    } else if (report.bridgeEffects) {
      report.bridgeEffects.dataCleanup = dbPrep
        ? "password_restored; recovery_secret_restored"
        : "password_restored; authorized profile recovery secret retained";
    }
  }
  report.finishedAt = new Date().toISOString();
  await writeReport(options.output, report);
}

if (report.status !== "passed") {
  console.error(`Auth recovery real bridge evidence failed: ${report.error ?? "unknown_failure"}.`);
  process.exitCode = 1;
} else {
  console.log("Auth recovery real bridge evidence passed.");
}

function parseArguments(args) {
  const parsed = { output: resolve("build-reports/auth/auth-recovery-real-bridge-evidence.json") };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--out") {
      const value = args[++index];
      if (!value || value.startsWith("--")) throw new Error("invalid_arguments");
      parsed.output = resolve(value);
    } else if (argument === "--help" || argument === "-h") {
      console.log("Usage: node scripts/auth-recovery-real-bridge-evidence.mjs [--out REPORT]");
      process.exit(0);
    } else {
      throw new Error("invalid_arguments");
    }
  }
  return parsed;
}

async function loadConfiguration(environment) {
  if (environment.QUATA_AUTH_RECOVERY_REAL_OPT_IN !== RECOVERY_OPT_IN) {
    throw new Error("recovery_roundtrip_opt_in_required");
  }
  if (environment.QUATA_AUTH_RECOVERY_ACCOUNT_MUTATION_OPT_IN !== ACCOUNT_OPT_IN) {
    throw new Error("authorized_account_mutation_opt_in_required");
  }
  const dbPrep = environment.QUATA_AUTH_RECOVERY_DB_PREP_OPT_IN === DB_PREP_OPT_IN;
  for (const forbidden of [
    "SUPABASE_DB_URL",
    "SUPABASE_DB_TLS_CA_FILE",
    "SUPABASE_DB_TLS_CA_PEM",
    "SUPABASE_SERVICE_ROLE_KEY",
    "QUATA_SUPABASE_SERVICE_ROLE_KEY",
    "SUPABASE_ACCESS_TOKEN",
  ]) {
    if (environment[forbidden]?.trim()) throw new Error("privileged_environment_forbidden");
  }
  const source = await readFile(resolve("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt"), "utf8");
  const baseUrl = (environment.QUATA_SUPABASE_URL?.trim() || source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1] || "")
    .replace(/\/+$/, "");
  const publishableKey = environment.QUATA_SUPABASE_PUBLISHABLE_KEY?.trim() ||
    source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1] ||
    "";
  const config = {
    baseUrl,
    publishableKey,
    countryCode: required(environment, "QUATA_AUTH_RECOVERY_COUNTRY_CODE"),
    phone: digits(required(environment, "QUATA_AUTH_RECOVERY_PHONE")),
    missingPhone: digits(required(environment, "QUATA_AUTH_RECOVERY_MISSING_PHONE")),
    password: required(environment, "QUATA_AUTH_RECOVERY_PASSWORD"),
    temporaryPassword: required(environment, "QUATA_AUTH_RECOVERY_TEMP_PASSWORD"),
    secretQuestion: required(environment, "QUATA_AUTH_RECOVERY_SECRET_QUESTION"),
    secretAnswer: required(environment, "QUATA_AUTH_RECOVERY_SECRET_ANSWER"),
    dbPrep,
    dbUrlFile: environment.SUPABASE_DB_URL_FILE?.trim(),
    dbTlsCaFile: environment.SUPABASE_DB_TLS_CA_FILE_PATH?.trim(),
  };
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!/^sb_publishable_[A-Za-z0-9_-]+$/.test(config.publishableKey)) throw new Error("invalid_publishable_key");
  if (config.phone === config.missingPhone) throw new Error("missing_phone_must_differ");
  if (config.temporaryPassword.length < 6 || config.password.length < 6) throw new Error("password_too_short");
  if (!config.secretQuestion.trim() || !config.secretAnswer.trim()) throw new Error("recovery_secret_required");
  if (config.dbPrep && (!config.dbUrlFile || !config.dbTlsCaFile)) throw new Error("db_prep_secure_file_paths_required");
  return config;
}

async function authBridge(config, payload, failureCode, bearer, acceptedStatuses = [200]) {
  const response = await fetch(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: {
      apikey: config.publishableKey,
      authorization: `Bearer ${bearer || config.publishableKey}`,
      "content-type": "application/json",
      "x-client-info": "quata-auth-recovery-real-evidence",
    },
    body: JSON.stringify(payload),
  });
  const body = await response.json().catch(() => ({}));
  if (!acceptedStatuses.includes(response.status)) {
    throw new Error(`${failureCode}:${response.status}:${safeBackendCode(body?.error)}`);
  }
  return { status: response.status, body };
}

function stringAt(value, path, failureCode) {
  let current = value?.body ?? value;
  for (const key of path) current = current?.[key];
  if (typeof current !== "string" || !current) throw new Error(failureCode);
  return current;
}

function required(environment, name) {
  const value = environment[name]?.trim();
  if (!value) throw new Error(`missing_${name.toLowerCase()}`);
  return value;
}

async function prepareRecoverySecretWithDatabase(config) {
  const { Client } = pg;
  const client = new Client({
    connectionString: databaseUrlWithoutSslConflict((await readFile(config.dbUrlFile, "utf8")).trim()),
    ssl: { ca: await readFile(config.dbTlsCaFile, "utf8"), rejectUnauthorized: true },
    application_name: "quata-auth-recovery-real-evidence",
  });
  await client.connect();
  try {
    const hasAnswerHash = await hasColumn(client, "community_profiles", "secret_answer_hash");
    const selectColumns = hasAnswerHash
      ? "id, secret_question, secret_answer, secret_answer_hash"
      : "id, secret_question, secret_answer";
    const original = await client.query(
      `select ${selectColumns}
         from public.community_profiles
        where phone_local = $1 and coalesce(country_code, code, '') = $2
        limit 1`,
      [config.phone, config.countryCode],
    );
    if (original.rowCount !== 1) throw new Error("db_prep_profile_not_found");
    const row = original.rows[0];
    const updateSql = hasAnswerHash
      ? `update public.community_profiles
            set secret_question = $1,
                secret_answer = $2,
                secret_answer_hash = null
          where id = $3`
      : `update public.community_profiles
            set secret_question = $1,
                secret_answer = $2
          where id = $3`;
    await client.query(updateSql, [config.secretQuestion, config.secretAnswer, row.id]);
    return { client, row, hasAnswerHash };
  } catch (error) {
    await client.end().catch(() => {});
    throw error;
  }
}

async function restoreRecoverySecret(prep) {
  try {
    if (prep.hasAnswerHash) {
      await prep.client.query(
        `update public.community_profiles
            set secret_question = $1,
                secret_answer = $2,
                secret_answer_hash = $3
          where id = $4`,
        [
          prep.row.secret_question,
          prep.row.secret_answer,
          prep.row.secret_answer_hash,
          prep.row.id,
        ],
      );
    } else {
      await prep.client.query(
        `update public.community_profiles
            set secret_question = $1,
                secret_answer = $2
          where id = $3`,
        [
          prep.row.secret_question,
          prep.row.secret_answer,
          prep.row.id,
        ],
      );
    }
    return true;
  } finally {
    await prep.client.end().catch(() => {});
  }
}

async function restoreOriginalPassword(config) {
  await authBridge(config, {
    action: "reset_password",
    country_code: config.countryCode,
    phone_local: config.phone,
    secret_answer: config.secretAnswer,
    new_password: config.password,
  }, "original_password_cleanup_failed");
  return true;
}

async function hasColumn(client, table, column) {
  const result = await client.query(
    `select 1
       from information_schema.columns
      where table_schema = 'public' and table_name = $1 and column_name = $2`,
    [table, column],
  );
  return result.rowCount > 0;
}

function digits(value) {
  const normalized = value.replace(/\D+/g, "");
  if (!normalized) throw new Error("phone_digits_required");
  return normalized;
}

function databaseUrlWithoutSslConflict(value) {
  const url = new URL(value);
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) url.searchParams.delete(key);
  return url.toString();
}

async function writeReport(path, value) {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  console.log(`Auth recovery real bridge report written: ${path}`);
}

async function repositoryRevision() {
  const result = spawnSync("git", ["rev-parse", "HEAD"], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
  return result.status === 0 ? result.stdout.trim() : null;
}

function safeBackendCode(value) {
  return typeof value === "string" && /^[a-z0-9_:-]+$/i.test(value) ? value : "backend_error";
}

function safeError(error) {
  const message = typeof error?.message === "string" ? error.message : String(error ?? "");
  return message.replace(/[^a-zA-Z0-9_:.-]/g, "_").slice(0, 160) || "auth_recovery_real_bridge_failure";
}
