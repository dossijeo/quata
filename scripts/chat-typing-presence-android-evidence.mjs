#!/usr/bin/env node

import { createHash, randomUUID } from "node:crypto";
import { spawn, execFileSync } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { chromium } from "playwright-core";
import { createChatTypingPeer } from "./e2e-fixtures/chat-typing-peer.mjs";

const delay = (milliseconds) => new Promise((done) => setTimeout(done, milliseconds));
const hash = (value) => createHash("sha256").update(String(value)).digest("hex");
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const deviceCredential = "app-internal:chat-typing-presence-credentials.json";
const deviceTempCredential = "/data/local/tmp/chat-typing-presence-credentials.json";
const deviceEvidence = "files/chat-typing-presence-evidence";

function options(argv) {
  const value = {
    credentialsFile: process.env.QUATA_CHAT_GROUP_CREDENTIALS_FILE,
    out: "build-reports/android/chat-typing-presence-evidence.json",
    evidenceDirectory: "build-reports/android/chat-typing-presence-evidence",
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--credentials-file") value.credentialsFile = argv[++index];
    else if (arg === "--out") value.out = argv[++index];
    else if (arg === "--evidence-dir") value.evidenceDirectory = argv[++index];
    else if (arg === "--chrome") value.chrome = argv[++index];
    else throw new Error(`unknown_argument:${arg}`);
  }
  if (!value.credentialsFile) throw new Error("missing_chat_typing_credentials_file");
  return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, resolve(entry)]));
}

function adb() {
  return process.env.ADB || resolve(process.env.LOCALAPPDATA ?? "", "Android/Sdk/platform-tools/adb.exe");
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
    method: "POST",
    headers: headers(config),
    body: JSON.stringify({
      action: "web_login",
      country_code: user.countryCode,
      phone_local: user.phone,
      password: user.password,
      client_instance_id: `chat-typing-android-${label}-${randomUUID()}`,
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

function run(command, args, { capture = false } = {}) {
  return new Promise((resolveRun, reject) => {
    let output = "";
    const child = spawn(command, args, { stdio: capture ? ["ignore", "pipe", "pipe"] : "inherit", shell: false });
    if (capture) {
      child.stdout.on("data", (chunk) => { output += chunk.toString(); });
      child.stderr.on("data", (chunk) => { output += chunk.toString(); });
    }
    child.once("error", reject);
    child.once("exit", (code) => code === 0 ? resolveRun(output) : reject(new Error(`command_failed:${command}:${code}:${output.slice(-800)}`)));
  });
}

async function waitForStage(adbCommand, stage, timeout = 35_000, instrumentation = null) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const logs = await run(adbCommand, ["logcat", "-d", "-s", "QuataTypingEvidence:I"], { capture: true });
    if (logs.includes(stage)) return;
    const ended = instrumentation
      ? await Promise.race([instrumentation.then((result) => ({ result })), delay(250).then(() => null)])
      : await delay(250).then(() => null);
    if (ended) {
      if (ended.result.error) throw ended.result.error;
      throw new Error(`android_typing_test_ended_before_${stage}:${ended.result.output.slice(-1_600)}`);
    }
  }
  throw new Error(`android_typing_stage_timeout:${stage}`);
}

async function pullEvidence(adbCommand, source, target) {
  const bytes = await new Promise((resolvePull, reject) => {
    const chunks = [];
    let stderr = "";
    const child = spawn(adbCommand, ["exec-out", "run-as", "com.quata", "cat", source], { stdio: ["ignore", "pipe", "pipe"], shell: false });
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.once("error", reject);
    child.once("exit", (code) => code === 0 ? resolvePull(Buffer.concat(chunks)) : reject(new Error(`android_evidence_pull_failed:${code}:${stderr.trim()}`)));
  });
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, bytes, "binary");
}

async function main() {
  const value = options(process.argv.slice(2));
  const adbCommand = adb();
  const report = {
    version: 1, status: "failed", platform: "android",
    evidenceKind: "AUTHENTICATED_REALTIME_REMOTE_TYPING_PRODUCT_UI",
    git: { head: execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim(), workingTreeDirty: true },
    steps: [], assertions: {}, cleanup: { state: "pending", databaseMutation: false },
  };
  let browser;
  let peer;
  let localCredential;
  try {
    report.git.workingTreeDirty = execFileSync("git", ["status", "--porcelain"], { encoding: "utf8" }).trim().length > 0;
    const parsed = JSON.parse((await readFile(value.credentialsFile, "utf8")).replace(/^\uFEFF/, ""));
    const user = (entry) => ({ countryCode: String(entry?.country_code ?? "").trim(), phone: String(entry?.phone ?? "").trim(), password: String(entry?.password ?? "") });
    const actorUser = user(parsed.a);
    const remoteUser = user(parsed.b);
    if ([actorUser, remoteUser].some((entry) => !entry.countryCode || !entry.phone || !entry.password)) throw new Error("invalid_chat_typing_credentials");
    const config = backendConfig(await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8"));
    const [actor, remote] = await Promise.all([login(config, actorUser, "actor"), login(config, remoteUser, "remote")]);
    const thread = threadId(await rpc(config, actor, "quata_chat_get_or_create_private_thread", {
      p_actor_profile_id: actor.profileId, p_peer_profile_id: remote.profileId,
    }));
    const conversationId = `sb:${thread}`;
    const before = await snapshot(config, actor, thread);
    browser = await chromium.launch({ headless: true, executablePath: value.chrome, args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"] });
    peer = await createChatTypingPeer({ browser, baseUrl: config.baseUrl, publishableKey: config.publishableKey, accessToken: remote.accessToken, profileId: remote.profileId, conversationId });

    const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
    await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain"]);
    await run(adbCommand, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
    await run(adbCommand, ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
    await run(adbCommand, ["shell", "pm", "clear", "com.quata"]);
    await run(adbCommand, ["shell", "pm", "grant", "com.quata", "android.permission.POST_NOTIFICATIONS"]);
    localCredential = resolve("build-reports/android/.chat-typing-presence-credentials.json");
    await mkdir(dirname(localCredential), { recursive: true });
    await writeFile(localCredential, `${JSON.stringify({ country_code: actorUser.countryCode, phone: actorUser.phone, password: actorUser.password })}\n`, { mode: 0o600 });
    await run(adbCommand, ["push", localCredential, deviceTempCredential]);
    await run(adbCommand, ["shell", "chmod", "644", deviceTempCredential]);
    await run(adbCommand, ["shell", "run-as", "com.quata", "mkdir", "-p", "files"]);
    await run(adbCommand, ["shell", "run-as", "com.quata", "cp", deviceTempCredential, `files/${deviceCredential.replace("app-internal:", "")}`]);
    await run(adbCommand, ["shell", "rm", "-f", deviceTempCredential]);
    await run(adbCommand, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidence]);
    await run(adbCommand, ["logcat", "-c"]);

    const draft = `typing-${randomUUID()}`;
    const instrumentation = run(adbCommand, ["shell", "am", "instrument", "-w", "-r",
      "-e", "class", "com.quata.feature.chat.presentation.chat.ChatRemoteTypingPresenceInstrumentedTest",
      "-e", "quataTypingCredentialsFile", deviceCredential,
      "-e", "quataTypingChatUrl", `https://egquata.com/#chat-${encodeURIComponent(conversationId)}`,
      "-e", "quataTypingDraft", draft,
      "com.quata.test/androidx.test.runner.AndroidJUnitRunner"], { capture: true })
      .then((output) => ({ output }), (error) => ({ error }));

    await waitForStage(adbCommand, "READY_REMOTE", 35_000, instrumentation);
    while (true) {
      await peer.sendTyping(true);
      try {
        await waitForStage(adbCommand, "REMOTE_VISIBLE", 900, instrumentation);
        break;
      } catch (error) {
        if (error?.message !== "android_typing_stage_timeout:REMOTE_VISIBLE") throw error;
      }
    }
    await peer.sendTyping(false);
    await waitForStage(adbCommand, "REMOTE_STOPPED", 35_000, instrumentation);
    await waitForStage(adbCommand, "LOCAL_READY", 35_000, instrumentation);
    await peer.waitForTyping({ expectedProfileId: actor.profileId, isTyping: true });
    await peer.waitForTyping({ expectedProfileId: actor.profileId, isTyping: false });
    await waitForStage(adbCommand, "EXPIRY_READY", 35_000, instrumentation);
    await peer.sendTyping(true);
    await waitForStage(adbCommand, "EXPIRY_VISIBLE", 35_000, instrumentation);
    await waitForStage(adbCommand, "EXPIRY_COMPLETE", 35_000, instrumentation);
    const instrumentationResult = await instrumentation;
    if (instrumentationResult.error) throw instrumentationResult.error;

    await mkdir(value.evidenceDirectory, { recursive: true });
    const screenshot = resolve(value.evidenceDirectory, "android-chat-remote-typing-visible.png");
    const productReport = resolve(value.evidenceDirectory, "android-chat-typing-presence-evidence.json");
    await pullEvidence(adbCommand, `${deviceEvidence}/android-chat-remote-typing-visible.png`, screenshot);
    await pullEvidence(adbCommand, `${deviceEvidence}/android-chat-typing-presence-evidence.json`, productReport);
    const screenshotBytes = await readFile(screenshot);
    const after = await snapshot(config, actor, thread);
    const peerState = await peer.snapshot();
    if (JSON.stringify(before) !== JSON.stringify(after)) throw new Error("chat_typing_message_set_changed");
    if (peerState.receivedTyping < 1 || peerState.receivedStopped < 1) throw new Error("chat_typing_product_broadcast_missing");
    report.steps = [
      "authenticated_exact_private_chat_opened",
      "remote_authenticated_typing_visible_then_stopped",
      "product_composer_broadcast_typing_and_stop_observed_by_peer",
      "remote_typing_expired_without_stop",
    ];
    report.assertions = {
      actorProfileHash: hash(actor.profileId), remoteProfileHash: hash(remote.profileId), conversationHash: hash(conversationId),
      remoteIndicatorVisible: true, remoteStopRemovedIndicator: true, remotePresenceExpiredWithoutStop: true,
      productTypingObservedByPeer: true, productStopObservedByPeer: true, messageSetUnchanged: true,
      screenshot: { file: "android-chat-remote-typing-visible.png", sha256: createHash("sha256").update(screenshotBytes).digest("hex") },
    };
    report.cleanup = { state: "complete", databaseMutation: false };
    report.status = "passed";
  } finally {
    await peer?.close().catch(() => {});
    await browser?.close().catch(() => {});
    await run(adbCommand, ["shell", "rm", "-f", deviceTempCredential], { capture: true }).catch(() => {});
    await run(adbCommand, ["shell", "run-as", "com.quata", "rm", "-f", `files/${deviceCredential.replace("app-internal:", "")}`], { capture: true }).catch(() => {});
    await rm(localCredential ?? "", { force: true }).catch(() => {});
    await mkdir(dirname(value.out), { recursive: true });
    await writeFile(value.out, `${JSON.stringify(report, null, 2)}\n`);
  }
  process.stdout.write(`${JSON.stringify({ status: report.status, platform: report.platform, steps: report.steps })}\n`);
}

main().catch((error) => { process.stderr.write(`${error?.stack ?? error}\n`); process.exitCode = 1; });
