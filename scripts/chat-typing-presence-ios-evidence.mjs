#!/usr/bin/env node

import { createHash, randomUUID } from "node:crypto";
import { execFileSync, spawn } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { chromium } from "playwright-core";
import { createChatTypingPeer } from "./e2e-fixtures/chat-typing-peer.mjs";

const delay = (milliseconds) => new Promise((done) => setTimeout(done, milliseconds));
const hash = (value) => createHash("sha256").update(String(value)).digest("hex");
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function options(argv) {
  const value = {
    credentialsFile: process.env.QUATA_CHAT_GROUP_CREDENTIALS_FILE,
    host: process.env.QUATA_IOS_SSH_HOST || "gabriel@192.168.1.109",
    project: process.env.QUATA_IOS_MAC_PROJECT || "/Users/gabriel/.codex/worktrees/chat-remote-typing-presence/quata",
    simulator: process.env.QUATA_IOS_SIMULATOR_UDID || "8AC9FBA4-1D33-4803-B632-1157B0750A2F",
    derivedData: process.env.QUATA_IOS_DERIVED_DATA_PATH || "build/ios-chat-typing-presence-derived-data",
    out: "build-reports/ios/chat-typing-presence-evidence.json",
    evidenceDirectory: "build-reports/ios/chat-typing-presence-evidence",
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    buildFirst: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--build-first") value.buildFirst = true;
    else if (["--credentials-file", "--host", "--project", "--simulator", "--derived-data", "--out", "--evidence-dir", "--chrome"].includes(arg)) {
      const next = argv[++index];
      if (!next) throw new Error(`missing_value:${arg}`);
      const key = { "--credentials-file": "credentialsFile", "--host": "host", "--project": "project", "--simulator": "simulator", "--derived-data": "derivedData", "--out": "out", "--evidence-dir": "evidenceDirectory", "--chrome": "chrome" }[arg];
      value[key] = next;
    } else throw new Error(`unknown_argument:${arg}`);
  }
  if (!value.credentialsFile) throw new Error("missing_chat_typing_credentials_file");
  value.credentialsFile = resolve(value.credentialsFile);
  value.out = resolve(value.out);
  value.evidenceDirectory = resolve(value.evidenceDirectory);
  value.chrome = resolve(value.chrome);
  return value;
}

function backendConfig(source) {
  const baseUrl = source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const publishableKey = source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !publishableKey || publishableKey.startsWith("sb_secret_")) throw new Error("missing_public_backend_configuration");
  return { baseUrl, publishableKey };
}

function headers(config, token) {
  return { apikey: config.publishableKey, "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) };
}

async function jsonRequest(url, init, label) {
  const response = await fetch(url, { ...init, signal: AbortSignal.timeout(20_000) });
  const text = await response.text();
  if (!response.ok) throw new Error(`${label}:http_${response.status}`);
  try { return text ? JSON.parse(text) : {}; } catch { throw new Error(`${label}:invalid_json`); }
}

async function login(config, user, label) {
  const body = await jsonRequest(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST", headers: headers(config), body: JSON.stringify({
      action: "web_login", country_code: user.countryCode, phone_local: user.phone, password: user.password,
      client_instance_id: `chat-typing-ios-${label}-${randomUUID()}`,
    }),
  }, `login_${label}`);
  const profileId = body?.profile?.id;
  const accessToken = body?.session?.access_token;
  if (!uuid.test(profileId ?? "") || !accessToken) throw new Error(`login_${label}:invalid_response`);
  return { profileId, accessToken };
}

async function rpc(config, session, name, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST", headers: headers(config, session.accessToken), body: JSON.stringify(body),
  }, `rpc_${name}`);
}

function threadId(payload) {
  const value = payload?.thread_id ?? payload?.id ?? payload?.thread?.id ?? payload?.conversation?.id;
  if (!Number.isSafeInteger(Number(value))) throw new Error("chat_typing_thread_missing");
  return Number(value);
}

function messageIds(payload) {
  const rows = payload?.messages ?? payload?.thread?.messages ?? payload?.conversation?.messages ?? [];
  return rows.map((row) => String(row?.id ?? row?.message_id ?? "")).filter(Boolean).sort();
}

async function snapshot(config, session, thread) {
  return messageIds(await rpc(config, session, "quata_chat_get_thread", {
    p_actor_profile_id: session.profileId, p_thread_id: thread, p_known_message_ids: [], p_limit: 250,
  }));
}

function shellQuote(value) { return `'${String(value).replaceAll("'", "'\\''")}'`; }

function run(command, args, { capture = false, input = null } = {}) {
  return new Promise((resolveRun, reject) => {
    let output = "";
    const child = spawn(command, args, { stdio: [input == null ? "ignore" : "pipe", capture ? "pipe" : "inherit", capture ? "pipe" : "inherit"], shell: false, windowsHide: true });
    if (input != null) { child.stdin.end(input); }
    if (capture) {
      child.stdout.on("data", (chunk) => { output += chunk.toString(); });
      child.stderr.on("data", (chunk) => { output += chunk.toString(); });
    }
    child.once("error", reject);
    child.once("exit", (code) => code === 0 ? resolveRun(output) : reject(new Error(`command_failed:${command}:${code}:${output.slice(-1200)}`)));
  });
}

async function ssh(host, script, capture = true) {
  return run("ssh", [host, "bash", "-s"], { capture, input: script });
}

async function waitForPhase(host, coordinator, phase, timeout = 40_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const status = await run("ssh", [host, "test", "-f", `${coordinator}/${phase}.phase`], { capture: true }).then(() => true, () => false);
    if (status) return;
    await delay(400);
  }
  throw new Error(`ios_typing_stage_timeout:${phase}`);
}

async function main() {
  const value = options(process.argv.slice(2));
  const report = {
    version: 1, status: "failed", platform: "ios-simulator",
    evidenceKind: "AUTHENTICATED_REALTIME_REMOTE_TYPING_PRODUCT_UI",
    git: { head: execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim(), workingTreeDirty: true },
    steps: [], assertions: {}, cleanup: { state: "pending", databaseMutation: false },
  };
  let browser, peer, localCredential, remoteCredential, coordinator;
  try {
    report.git.workingTreeDirty = execFileSync("git", ["status", "--porcelain"], { encoding: "utf8" }).trim().length > 0;
    const parsed = JSON.parse((await readFile(value.credentialsFile, "utf8")).replace(/^\uFEFF/, ""));
    const user = (entry) => ({ countryCode: String(entry?.country_code ?? "").trim(), phone: String(entry?.phone ?? "").trim(), password: String(entry?.password ?? "") });
    const actorUser = user(parsed.a);
    const remoteUser = user(parsed.b);
    if ([actorUser, remoteUser].some((entry) => !entry.countryCode || !entry.phone || !entry.password)) throw new Error("invalid_chat_typing_credentials");
    const config = backendConfig(await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8"));
    const [actor, remote] = await Promise.all([login(config, actorUser, "actor"), login(config, remoteUser, "remote")]);
    const thread = threadId(await rpc(config, actor, "quata_chat_get_or_create_private_thread", { p_actor_profile_id: actor.profileId, p_peer_profile_id: remote.profileId }));
    const conversationId = `sb:${thread}`;
    const before = await snapshot(config, actor, thread);
    browser = await chromium.launch({ headless: true, executablePath: value.chrome, args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"] });
    peer = await createChatTypingPeer({ browser, baseUrl: config.baseUrl, publishableKey: config.publishableKey, accessToken: remote.accessToken, profileId: remote.profileId, conversationId });

    const localDir = await mkdtemp(join(tmpdir(), "quata-ios-chat-typing-"));
    localCredential = join(localDir, "credentials.json");
    const phone = `+${actorUser.countryCode.replace(/\D/g, "")}${actorUser.phone.replace(/\D/g, "").replace(new RegExp(`^${actorUser.countryCode.replace(/\D/g, "")}`), "")}`;
    await writeFile(localCredential, `${JSON.stringify({ country_code: actorUser.countryCode, phone, password: actorUser.password })}\n`, { mode: 0o600 });
    remoteCredential = (await run("ssh", [value.host, "mktemp", "/tmp/quata-ios-chat-typing-credentials.XXXXXX.json"], { capture: true })).trim();
    coordinator = (await run("ssh", [value.host, "mktemp", "-d", "/tmp/quata-ios-chat-typing-XXXXXXXX"], { capture: true })).trim();
    await run("scp", [localCredential, `${value.host}:${remoteCredential}`], { capture: true });
    if (value.buildFirst) {
      await ssh(value.host, `set -euo pipefail\ncd ${shellQuote(value.project)}\nexport QUATA_IOS_SIGNED_DERIVED_DATA_PATH=${shellQuote(value.derivedData)}\nexport QUATA_IOS_SIGNED_RESULT_BUNDLE_PATH=${shellQuote(`${value.derivedData}-build.xcresult`)}\nbash scripts/build-ios-intel-simulator-signed.sh\n`, false);
      report.steps.push("ios_simulator_signed_build_succeeded");
    }

    const draft = `qadata-typing-${randomUUID().slice(0, 12)}`;
    const remoteLogDir = "build/reports/ios/chat-typing-presence";
    const remoteResults = `${remoteLogDir}/xcresults`;
    const remoteScreenshot = `${remoteLogDir}/ios-chat-remote-typing-visible.png`;
    const command = `set -euo pipefail\ncd ${shellQuote(value.project)}\nrm -rf ${shellQuote(remoteLogDir)}\nmkdir -p ${shellQuote(remoteLogDir)}\nexport QUATA_IOS_AUTH_E2E_FILE=${shellQuote(remoteCredential)}\nexport QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(value.derivedData)}\nexport QUATA_IOS_SIMULATOR_UDID=${shellQuote(value.simulator)}\nexport QUATA_IOS_CHAT_E2E_CONVERSATION_ID=${shellQuote(conversationId)}\nexport QUATA_IOS_CHAT_TYPING_DRAFT=${shellQuote(draft)}\nexport QUATA_IOS_CHAT_TYPING_COORDINATOR_DIRECTORY=${shellQuote(coordinator)}\nexport QUATA_IOS_CHAT_TYPING_LOG_DIR=${shellQuote(remoteLogDir)}\nexport QUATA_IOS_CHAT_TYPING_RESULT_BUNDLE_DIR=${shellQuote(remoteResults)}\nbash scripts/run-ios-chat-typing-presence-ui-test.sh\n`;
    const testRun = ssh(value.host, command, true).then((output) => ({ output }), (error) => ({ error }));
    const sendRemoteTypingUntil = async (phase) => {
      while (true) {
        await peer.sendTyping(true);
        try {
          await Promise.race([
            waitForPhase(value.host, coordinator, phase, 1_000),
            testRun.then((result) => {
              if (result.error) throw result.error;
              throw new Error(`ios_typing_test_ended_before_${phase}`);
            }),
          ]);
          return;
        } catch (error) {
          if (error?.message === `ios_typing_stage_timeout:${phase}`) continue;
          throw error;
        }
      }
    };

    await Promise.race([
      waitForPhase(value.host, coordinator, "ready-expiry", 90_000),
      testRun.then((result) => {
        if (result.error) throw result.error;
        throw new Error("ios_typing_test_ended_before_ready_expiry");
      }),
    ]);
    await sendRemoteTypingUntil("expiry-visible");
    await ssh(value.host, `set -euo pipefail\ncd ${shellQuote(value.project)}\nxcrun simctl io ${shellQuote(value.simulator)} screenshot ${shellQuote(remoteScreenshot)}\n`);
    await ssh(value.host, `set -euo pipefail\nprintf '%s' 'expiry-signals-stopped' > ${shellQuote(`${coordinator}/expiry-signals-stopped.phase`)}\n`);
    await waitForPhase(value.host, coordinator, "expiry-complete");

    await waitForPhase(value.host, coordinator, "ready-stop");
    await sendRemoteTypingUntil("stop-visible");
    await peer.sendTyping(false);
    await waitForPhase(value.host, coordinator, "stop-complete");
    await waitForPhase(value.host, coordinator, "local-typed");
    await peer.waitForTyping({ expectedProfileId: actor.profileId, isTyping: true });
    await peer.waitForTyping({ expectedProfileId: actor.profileId, isTyping: false });
    const testResult = await testRun;
    if (testResult.error) throw testResult.error;

    await rm(value.evidenceDirectory, { recursive: true, force: true });
    await mkdir(value.evidenceDirectory, { recursive: true });
    const screenshot = join(value.evidenceDirectory, "ios-chat-remote-typing-visible.png");
    await run("scp", [`${value.host}:${value.project}/${remoteScreenshot}`, screenshot], { capture: true });
    await run("scp", ["-r", `${value.host}:${value.project}/${remoteLogDir}`, join(value.evidenceDirectory, "mac-ui-report")], { capture: true });
    const screenshotBytes = await readFile(screenshot);
    const after = await snapshot(config, actor, thread);
    const peerState = await peer.snapshot();
    if (JSON.stringify(before) !== JSON.stringify(after)) throw new Error("chat_typing_message_set_changed");
    if (peerState.receivedTyping < 1 || peerState.receivedStopped < 1) throw new Error("chat_typing_product_broadcast_missing");
    report.steps.push(
      "authenticated_exact_private_chat_opened",
      "remote_authenticated_typing_visible_then_stopped",
      "product_composer_broadcast_typing_and_stop_observed_by_peer",
      "remote_typing_expired_without_stop",
    );
    report.assertions = {
      actorProfileHash: hash(actor.profileId), remoteProfileHash: hash(remote.profileId), conversationHash: hash(conversationId),
      remoteIndicatorVisible: true, remoteStopRemovedIndicator: true, remotePresenceExpiredWithoutStop: true,
      productTypingObservedByPeer: true, productStopObservedByPeer: true, messageSetUnchanged: true,
      screenshot: { file: "ios-chat-remote-typing-visible.png", sha256: createHash("sha256").update(screenshotBytes).digest("hex") },
    };
    report.cleanup = { state: "complete", databaseMutation: false };
    report.status = "passed";
  } finally {
    await peer?.close().catch(() => {});
    await browser?.close().catch(() => {});
    if (remoteCredential) await run("ssh", [value.host, "rm", "-f", remoteCredential], { capture: true }).catch(() => {});
    if (coordinator) await run("ssh", [value.host, "rm", "-rf", coordinator], { capture: true }).catch(() => {});
    if (localCredential) await rm(dirname(localCredential), { recursive: true, force: true }).catch(() => {});
    await mkdir(dirname(value.out), { recursive: true });
    await writeFile(value.out, `${JSON.stringify(report, null, 2)}\n`);
  }
  process.stdout.write(`${JSON.stringify({ status: report.status, platform: report.platform, steps: report.steps })}\n`);
}

main().catch((error) => { process.stderr.write(`${error?.stack ?? error}\n`); process.exitCode = 1; });
