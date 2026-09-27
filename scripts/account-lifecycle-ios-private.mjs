import { execFileSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { runAccountLifecycleTrial } from "./account-lifecycle-trial.mjs";
import { createAccountLifecycleIosTrial } from "./e2e-fixtures/account-lifecycle-ios.mjs";

const require = createRequire(import.meta.url);
let client;
try {
  let size = 0;
  const chunks = [];
  for await (const chunk of process.stdin) {
    size += chunk.length;
    if (size > 1024 * 1024) throw new Error("input_too_large");
    chunks.push(chunk);
  }
  const clear = Buffer.concat(chunks);
  const input = JSON.parse(clear.toString("utf8"));
  clear.fill(0);
  for (const chunk of chunks) chunk.fill(0);
  const { Client } = require(input.pgModule);
  const databaseUrl = new URL((await readFile(input.databaseUrlFile, "utf8")).trim());
  for (const key of ["sslmode", "sslrootcert", "sslcert", "sslkey"]) databaseUrl.searchParams.delete(key);
  client = new Client({ connectionString: databaseUrl.toString(),
    ssl: { ca: await readFile(input.databaseCaFile, "utf8"), rejectUnauthorized: true, servername: databaseUrl.hostname },
    connectionTimeoutMillis: 10_000, statement_timeout: 15_000 });
  await client.connect();
  const preflight = async () => {
    if (!/^[0-9a-f]{40}$/.test(input.productSha) ||
        execFileSync("git", ["rev-parse", "HEAD"], { cwd: input.root, encoding: "utf8", windowsHide: true }).trim() !== input.productSha ||
        execFileSync("git", ["status", "--porcelain"], { cwd: input.root, encoding: "utf8", windowsHide: true }).trim()) return false;
    const remoteHead = execFileSync("ssh", [input.host, "git", "-C", input.project, "rev-parse", "HEAD"],
      { encoding: "utf8", windowsHide: true, timeout: 20_000 }).trim();
    const remoteDirty = execFileSync("ssh", [input.host, "git", "-C", input.project, "status", "--porcelain"],
      { encoding: "utf8", windowsHide: true, timeout: 20_000 }).trim();
    if (remoteHead !== input.productSha || remoteDirty) return false;
    const devices = JSON.parse(execFileSync("ssh", [input.host, "xcrun", "simctl", "list", "devices", "available", "-j"],
      { encoding: "utf8", windowsHide: true, timeout: 30_000 }));
    const simulator = Object.values(devices.devices ?? {}).flat().find((entry) => entry.udid === input.simulatorUdid);
    if (!simulator || !simulator.isAvailable) return false;
    const edge = JSON.parse(execFileSync(input.supabaseCli,
      ["functions", "list", "--project-ref", "yrrlankpwmhluexshxnw", "--output", "json"],
      { cwd: input.root, encoding: "utf8", windowsHide: true, timeout: 30_000 }))
      .find((entry) => entry.slug === "quata-account-lifecycle");
    if (!edge || edge.status !== "ACTIVE" || edge.version !== input.expectedEdge?.version ||
        edge.ezbr_sha256 !== input.expectedEdge?.ezbr_sha256) return false;
    const database = await client.query(`select
      to_regclass('public.web_client_sessions') is not null as web_sessions,
      to_regclass('public.web_push_subscriptions') is not null as web_subscriptions,
      to_regclass('storage.objects') is not null as storage_objects,
      exists(select 1 from information_schema.columns where table_schema='public' and table_name='community_profiles'
        and column_name='deactivated_auth_user_id') as deactivated_auth_link,
      has_function_privilege('service_role','public.quata_account_deactivate(uuid,uuid)','execute') as deactivate_service_only,
      not has_function_privilege('authenticated','public.quata_account_deactivate(uuid,uuid)','execute') as deactivate_not_client`);
    return Object.values(database.rows?.[0] ?? {}).every((value) => value === true) &&
      path.isAbsolute(input.privateDirectory) && path.isAbsolute(input.outputDirectory);
  };
  const report = await runAccountLifecycleTrial({ platform: "ios", client, serviceKey: input.serviceKey,
    privateDirectory: input.privateDirectory, backendUrl: "https://yrrlankpwmhluexshxnw.supabase.co",
    publicKey: input.publicKey, preflight,
    createUi: () => createAccountLifecycleIosTrial({ root: input.root, outputDirectory: input.outputDirectory,
      privateDirectory: input.privateDirectory, host: input.host, project: input.project,
      simulatorUdid: input.simulatorUdid }) });
  await mkdir(path.dirname(input.reportFile), { recursive: true });
  await writeFile(input.reportFile, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  process.stdout.write(`${JSON.stringify(report)}\n`);
  process.exitCode = report.status === "passed" && report.cleanupComplete ? 0 : 1;
} catch {
  process.stdout.write(`${JSON.stringify({ status: "runner_failed_before_report" })}\n`);
  process.exitCode = 1;
} finally {
  await client?.end().catch(() => {});
}
