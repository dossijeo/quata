#!/usr/bin/env node
import { createRequire } from "node:module";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { buildSb01TlsConnection, loadSb01CertificateAuthority } from "./supabase-e2e-sb01-tls.mjs";

const require = createRequire(import.meta.url);
const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, "..");
const manifestPath = resolve(scriptDirectory, "backend-compatibility-manifest.json");
const androidApiPath = resolve(repositoryRoot, "app/src/main/java/com/quata/data/supabase/SupabaseCommunityApi.kt");
const blockedSql = /\b(?:alter|analyze|call|copy|create|delete|drop|grant|insert|listen|lock|notify|reassign|refresh|reindex|revoke|select\s+.*\bfor\s+(?:share|update)|truncate|update|vacuum)\b/i;

function parseArguments(argv) {
  const options = { mode: "all", output: null, baseline: null };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (["--mode", "--out", "--baseline"].includes(argument)) {
      const value = argv[index + 1];
      if (!value || value.startsWith("--")) throw new Error(`missing_argument:${argument}`);
      options[argument === "--out" ? "output" : argument.slice(2)] = value;
      index += 1;
    } else if (argument === "--help" || argument === "-h") {
      console.log("Usage: node scripts/backend-compatibility-contracts.mjs [--mode public|catalog|all] [--out FILE] [--baseline FILE]");
      process.exit(0);
    } else throw new Error(`unknown_argument:${argument}`);
  }
  if (!["public", "catalog", "all"].includes(options.mode)) throw new Error("invalid_mode");
  return options;
}

function safePublicConfiguration() {
  const baseUrl = process.env.QUATA_SUPABASE_URL?.trim().replace(/\/+$/, "");
  const publishableKey = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY?.trim();
  if (!baseUrl || !publishableKey) throw new Error("missing_public_supabase_configuration");
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(baseUrl)) throw new Error("invalid_public_supabase_url");
  return { baseUrl, publishableKey };
}

function readOnlyQuery(text, values = []) {
  if (blockedSql.test(text)) throw new Error("blocked_sql");
  return { text, values };
}

function androidRpcNames(source, extras) {
  const names = new Set(extras);
  const direct = /client\.rpc(?:Unit)?(?:<[^(\n]+)?\s*\(\s*"([^"]+)"/g;
  for (const match of source.matchAll(direct)) names.add(match[1]);
  // Nested Kotlin generic return types are awkward for a regex. This bounded scan deliberately
  // stops at the call's first string literal and is covered by the minimum-count guard below.
  const nested = /client\.rpc(?:Unit)?[\s\S]{0,220}?\(\s*"([^"]+)"/g;
  for (const match of source.matchAll(nested)) names.add(match[1]);
  if (names.size < 40) throw new Error(`android_rpc_inventory_too_small:${names.size}`);
  return [...names].sort();
}

function androidTableNames(source) {
  const names = new Set();
  const tableCall = /client\.(?:getList|getSingleOrNull|postList|post|patchMinimal|patch|delete|observeList)[\s\S]{0,260}?\(\s*"([a-z0-9_]+)"/g;
  for (const match of source.matchAll(tableCall)) names.add(match[1]);
  if (names.size < 18) throw new Error(`android_table_inventory_too_small:${names.size}`);
  return [...names].sort();
}

async function runPublicReads(manifest) {
  const config = safePublicConfiguration();
  const checks = [];
  for (const contract of manifest.publicReads) {
    const url = new URL(`${config.baseUrl}/rest/v1/${contract.table}`);
    url.searchParams.set("select", contract.select.join(","));
    for (const [key, value] of Object.entries(contract.query ?? {})) url.searchParams.set(key, value);
    const response = await fetch(url, {
      headers: {
        apikey: config.publishableKey,
        accept: "application/json",
        "x-client-info": "quata-backend-compatibility-gate",
      },
      signal: AbortSignal.timeout(15_000),
    });
    let rows;
    try { rows = await response.json(); } catch { rows = null; }
    const arrayResponse = Array.isArray(rows);
    const sample = arrayResponse ? rows[0] : null;
    const missingSampleFields = sample
      ? contract.select.filter((column) => !Object.prototype.hasOwnProperty.call(sample, column))
      : [];
    checks.push({
      id: contract.id,
      table: contract.table,
      httpStatus: response.status,
      arrayResponse,
      rowCount: arrayResponse ? rows.length : null,
      missingSampleFields,
      passed: response.ok && arrayResponse && missingSampleFields.length === 0,
    });
  }
  return checks;
}

async function runCatalog(manifest, androidRpcContracts) {
  let Client;
  try { ({ Client } = require("pg")); } catch { throw new Error("missing_pg_dependency"); }
  const connectionString = process.env.SUPABASE_DB_URL;
  if (!connectionString) throw new Error("missing_database_url");
  const certificateAuthority = await loadSb01CertificateAuthority();
  const client = new Client({
    ...buildSb01TlsConnection(connectionString, certificateAuthority),
    application_name: "quata-backend-compatibility-readonly",
    connectionTimeoutMillis: 15_000,
    query_timeout: 15_000,
    options: "-c default_transaction_read_only=on -c statement_timeout=15000",
  });
  try {
    await client.connect();
    await client.query(readOnlyQuery("BEGIN TRANSACTION READ ONLY"));
    await client.query(readOnlyQuery("SET LOCAL TRANSACTION READ ONLY"));
    const [columnsResult, functionsResult] = await Promise.all([
      client.query(readOnlyQuery(
        "SELECT table_name, column_name, data_type, is_nullable FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ANY($1::text[]) ORDER BY table_name, ordinal_position",
        [manifest.catalogTables],
      )),
      client.query(readOnlyQuery(
        "SELECT p.proname AS name, pg_get_function_identity_arguments(p.oid) AS identity_arguments, pg_get_function_result(p.oid) AS result_type FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'public' AND p.proname = ANY($1::text[]) ORDER BY p.proname, identity_arguments",
        [androidRpcContracts],
      )),
    ]);
    await client.query(readOnlyQuery("ROLLBACK"));
    const presentTables = new Set(columnsResult.rows.map((row) => row.table_name));
    const presentRpcs = new Set(functionsResult.rows.map((row) => row.name));
    return {
      missingTables: manifest.catalogTables.filter((name) => !presentTables.has(name)),
      missingRpcs: androidRpcContracts.filter((name) => !presentRpcs.has(name)),
      tableColumns: columnsResult.rows,
      rpcSignatures: functionsResult.rows,
    };
  } finally {
    await client.end().catch(() => undefined);
  }
}

function stableCatalogSnapshot(catalog) {
  return {
    tableColumns: catalog?.tableColumns ?? [],
    rpcSignatures: catalog?.rpcSignatures ?? [],
  };
}

async function compareBaseline(baselinePath, currentReport) {
  const baseline = JSON.parse(await readFile(resolve(baselinePath), "utf8"));
  const expected = JSON.stringify(stableCatalogSnapshot(baseline.catalog));
  const actual = JSON.stringify(stableCatalogSnapshot(currentReport.catalog));
  return {
    sourceCheck: baseline.check ?? "unknown",
    catalogUnchanged: expected === actual,
  };
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const startedAt = new Date().toISOString();
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  const androidSource = await readFile(androidApiPath, "utf8");
  const androidRpcContracts = androidRpcNames(androidSource, manifest.extraAndroidRpcs);
  const androidTableContracts = androidTableNames(androidSource);
  const missingManifestTables = androidTableContracts.filter((table) => !manifest.catalogTables.includes(table));
  if (missingManifestTables.length > 0) {
    throw new Error(`android_tables_missing_from_manifest:${missingManifestTables.join(",")}`);
  }
  const report = {
    check: "BACKEND-COMPATIBILITY",
    schemaVersion: 1,
    startedAt,
    finishedAt: null,
    mode: options.mode,
    publicReads: null,
    catalog: null,
    androidInventory: {
      source: "SupabaseCommunityApi.kt",
      tableCount: androidTableContracts.length,
      tableNames: androidTableContracts,
      rpcCount: androidRpcContracts.length,
      rpcNames: androidRpcContracts,
    },
    comparison: null,
    mutationPolicy: "Public checks issue GET requests only. Catalogue checks run in a PostgreSQL READ ONLY transaction. No fixture or business data is created.",
  };
  if (options.mode !== "catalog") report.publicReads = await runPublicReads(manifest);
  if (options.mode !== "public") report.catalog = await runCatalog(manifest, androidRpcContracts);
  if (options.baseline) report.comparison = await compareBaseline(options.baseline, report);
  report.finishedAt = new Date().toISOString();
  const publicPassed = !report.publicReads || report.publicReads.every((check) => check.passed);
  const catalogPassed = !report.catalog || (report.catalog.missingTables.length === 0 && report.catalog.missingRpcs.length === 0);
  const comparisonPassed = !report.comparison || report.comparison.catalogUnchanged;
  report.status = publicPassed && catalogPassed && comparisonPassed ? "passed" : "failed";
  const serialized = `${JSON.stringify(report, null, 2)}\n`;
  if (options.output) {
    const output = resolve(options.output);
    await mkdir(dirname(output), { recursive: true });
    await writeFile(output, serialized, { encoding: "utf8", mode: 0o600 });
    console.log(`Backend compatibility report written: ${output}`);
  } else console.log(serialized);
  process.exitCode = report.status === "passed" ? 0 : 1;
}

main().catch((error) => {
  const known = [
    "missing_public_supabase_configuration", "invalid_public_supabase_url", "missing_pg_dependency",
    "missing_database_url", "tls_ca_", "android_rpc_inventory_too_small", "android_table_inventory_too_small",
    "android_tables_missing_from_manifest", "blocked_sql",
  ];
  const message = typeof error?.message === "string" ? error.message : "unknown";
  const safeCode = known.some((prefix) => message.startsWith(prefix)) ? message : "unexpected_compatibility_gate_failure";
  console.error(JSON.stringify({ check: "BACKEND-COMPATIBILITY", status: "failed", error: safeCode }));
  process.exitCode = 1;
});
