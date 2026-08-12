#!/usr/bin/env node
import { spawn } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import pg from "pg";

const check = "CHAT-ACTIONS-NOTIFICATIONS-IOS-001";
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_IOS_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_IOS_HARD_CLEANUP";
const tempProfileHashAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH_AUTHORIZATION";
const tempProfileHashAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH";
const credentialsFileEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_CREDENTIALS_FILE";
const useAdjacentAuthorizedProfile = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_IOS_USE_ADJACENT_AUTHORIZED_PROFILE === "1";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const options = parseArgs(process.argv.slice(2));
const translationOnly = options.translationOnly;
const profileOnly = options.profileOnly;
const profileFollowOnly = options.profileFollowOnly;
const profileListsOnly = options.profileListsOnly;
const profileEvidenceOnly = profileOnly || profileFollowOnly || profileListsOnly;
const report = {
  check,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  cleanup: { state: "not_started" },
  evidence: {},
};

let localCredentials;
let remoteCredentials;
let config;
let profileHashWindow = { state: "not_started", restored: true, restore: async () => {} };
const state = {
  a: null,
  b: null,
  thread: null,
  seedMessage: null,
  peerMessage: null,
  editableMessage: null,
  seedMarker: null,
  peerMarker: null,
  editableMarker: null,
  composerMarker: null,
  replyMarker: null,
  editMarker: null,
  uniqueKey: null,
  composerMessage: null,
  replyMessage: null,
  forwardProfile: null,
  forwardThread: null,
  forwardedMessage: null,
  profileFollow: null,
  profileListEdges: null,
};

try {
  config = await publicBackendConfig();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");

  const users = await authorizedUsers();
  profileHashWindow = await openTemporaryProfileHashWindow(users);
  if (profileHashWindow.state === "opened") {
    report.steps.push("temporary_profile_hash_window_opened");
  }
  state.a = await login(config, users[0]);
  state.b = await login(config, users[1]);
  report.steps.push("two_authorized_profiles_logged_in");

  const runId = randomUUID();
  state.uniqueKey = `qadata-chat-actions-notifications-ios-${runId}`;
  state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
    p_actor_profile_id: state.a.profileId,
    p_recipient_profile_ids: [state.b.profileId],
    p_subject: `QADATA chat actions iOS ${runId}`,
    p_type: "group",
    p_message: "",
    p_unique_key: state.uniqueKey,
    p_community_id: null,
  }));
  report.steps.push("isolated_group_thread_ready");
  if (!translationOnly && !profileEvidenceOnly) {
    state.forwardProfile = await createTemporaryForwardProfile(runId);
    report.steps.push("temporary_forward_destination_profile_created");
  }

  state.seedMarker = translationOnly ? "Mbolo" : `chat-actions-ios-seed-${randomUUID()}`;
  state.peerMarker = translationOnly ? null : `chat-profile-ios-peer-${randomUUID()}`;
  state.editableMarker = translationOnly || profileEvidenceOnly ? null : `chat-actions-ios-editable-${randomUUID()}`;
  state.composerMarker = translationOnly || profileEvidenceOnly ? null : `chat-actions-ios-send-${randomUUID()}`;
  state.replyMarker = translationOnly || profileEvidenceOnly ? null : `chat-actions-ios-reply-${randomUUID()}`;
  state.editMarker = translationOnly || profileEvidenceOnly ? null : `chat-actions-ios-edit-${randomUUID()}`;
  state.seedMessage = messageId(await rpc(config, state.a, "quata_chat_send_message", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message: state.seedMarker,
    p_file_ids: [],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-actions-ios-seed-${randomUUID()}`,
  }));
  await pollMessage(config, state.b, state.thread, (message) => Number(message?.id) === state.seedMessage && messageText(message) === state.seedMarker);
  if (!translationOnly) {
    state.peerMessage = messageId(await rpc(config, state.b, "quata_chat_send_message", {
      p_actor_profile_id: state.b.profileId,
      p_thread_id: state.thread,
      p_message: state.peerMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-profile-ios-peer-${randomUUID()}`,
    }));
    await pollMessage(config, state.a, state.thread, (message) => Number(message?.id) === state.peerMessage && messageText(message) === state.peerMarker);
    if (!profileEvidenceOnly) {
      state.editableMessage = messageId(await rpc(config, state.a, "quata_chat_send_message", {
        p_actor_profile_id: state.a.profileId,
        p_thread_id: state.thread,
        p_message: state.editableMarker,
        p_file_ids: [],
        p_reply_to_message_id: null,
        p_client_message_id: `chat-actions-ios-editable-${randomUUID()}`,
      }));
      await pollMessage(config, state.b, state.thread, (message) => Number(message?.id) === state.editableMessage && messageText(message) === state.editableMarker);
      report.steps.push("unique_seed_peer_and_editable_messages_ready");
    } else {
      report.steps.push("unique_seed_and_peer_messages_ready");
    }
  } else {
    report.steps.push("translation_seed_message_visible_to_peer");
  }

  localCredentials = join(await mkdtemp(join(tmpdir(), "quata-ios-chat-actions-")), "credentials.json");
  await writeFile(localCredentials, `${JSON.stringify({
    country_code: users[0].countryCode,
    phone: e164Phone(users[0].countryCode, users[0].phone),
    password: users[0].password,
  })}\n`, { mode: 0o600 });
  remoteCredentials = (await runCapture("ssh", [
    options.host,
    "mktemp /tmp/quata-ios-chat-actions-credentials.XXXXXX.json",
  ])).trim();
  await run("scp", [localCredentials, `${options.host}:${remoteCredentials}`]);
  report.steps.push("ios_real_credentials_copied_to_mac_tempfile_without_logging_contents");

  const remoteHead = (await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
git rev-parse HEAD
`)).trim();
  report.mac = { host: options.host, project: options.project, head: remoteHead };
  if (remoteHead !== report.git.head) throw new Error(`mac_checkout_sha_mismatch:${remoteHead}:${report.git.head}`);
  report.steps.push("mac_checkout_sha_matches_local_candidate");

  if (options.buildFirst) {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
python3 scripts/ios-public-client-config.py \\
  --source core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt \\
  --output iosApp/Configuration/QuataPublicRuntime.local.xcconfig
`, 2 * 60 * 1000);
    report.steps.push("ios_public_runtime_local_xcconfig_generated_on_mac");
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
scripts/build-ios-intel-simulator-signed.sh
`, 60 * 60 * 1000);
    report.steps.push("ios_simulator_signed_build_succeeded_on_mac");
  }

  const markerProbe = state.seedMarker.slice(0, 28);
  if (translationOnly) {
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
export QUATA_IOS_AUTH_E2E_FILE=${shellQuote(remoteCredentials)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.simulatorUdid)}
export QUATA_IOS_CHAT_E2E_CONVERSATION_ID=${shellQuote(`sb:${state.thread}`)}
export QUATA_IOS_CHAT_E2E_MESSAGE_ID=${shellQuote(String(state.seedMessage))}
export QUATA_IOS_CHAT_E2E_MARKER_PROBE=${shellQuote(markerProbe)}
export QUATA_IOS_CHAT_TRANSLATION_LOG_DIR=${shellQuote(options.remoteLogDir)}
export QUATA_IOS_CHAT_TRANSLATION_RESULT_BUNDLE_DIR=${shellQuote(options.remoteResultBundleDir)}
bash scripts/run-ios-chat-translation-ui-test.sh
`, 30 * 60 * 1000);
    report.steps.push("ios_xctest_chat_translation_common_overlay_verified");
    await copyRemoteEvidence(options);
    report.status = "passed";
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      translatedMessageId: state.seedMessage,
      seedMarkerSha256: sha256(state.seedMarker),
    };
  } else {
    const peerMarkerProbe = state.peerMarker.slice(0, 28);
    if (profileFollowOnly) {
      state.profileFollow = await prepareProfileFollowAbsent(state.a.profileId, state.b.profileId);
      report.steps.push("profile_follow_initial_state_snapshot_and_absent_prepared");
    }
    if (profileListsOnly) {
      state.profileListEdges = await prepareProfileListEdges(state.a.profileId, state.b.profileId);
      report.steps.push("profile_follow_list_edges_prepared_reversibly");
    }
    await runSshScript(options.host, `
set -euo pipefail
cd ${shellQuote(options.project)}
export QUATA_IOS_AUTH_E2E_FILE=${shellQuote(remoteCredentials)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(options.derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(options.simulatorUdid)}
export QUATA_IOS_CHAT_E2E_CONVERSATION_ID=${shellQuote(`sb:${state.thread}`)}
export QUATA_IOS_CHAT_E2E_MESSAGE_ID=${shellQuote(String(state.seedMessage))}
export QUATA_IOS_CHAT_E2E_MARKER_PROBE=${shellQuote(markerProbe)}
export QUATA_IOS_CHAT_PROFILE_E2E_MARKER_PROBE=${shellQuote(peerMarkerProbe)}
export QUATA_IOS_CHAT_PROFILE_E2E_PROFILE_ID=${shellQuote(state.b.profileId)}
export QUATA_IOS_CHAT_PROFILE_ONLY=${profileEvidenceOnly ? "1" : "0"}
export QUATA_IOS_CHAT_PROFILE_FOLLOW_UI_E2E=${profileFollowOnly ? "1" : "0"}
export QUATA_IOS_CHAT_PROFILE_LISTS_UI_E2E=${profileListsOnly ? "1" : "0"}
export QUATA_IOS_CHAT_E2E_EDITABLE_MESSAGE_ID=${shellQuote(String(state.editableMessage ?? "profile-only"))}
export QUATA_IOS_CHAT_E2E_EDITABLE_MARKER=${shellQuote(state.editableMarker ?? "profile-only")}
export QUATA_IOS_CHAT_E2E_COMPOSER_MARKER=${shellQuote(state.composerMarker ?? "profile-only")}
export QUATA_IOS_CHAT_E2E_REPLY_MARKER=${shellQuote(state.replyMarker ?? "profile-only")}
export QUATA_IOS_CHAT_E2E_EDIT_MARKER=${shellQuote(state.editMarker ?? "profile-only")}
export QUATA_IOS_CHAT_E2E_FORWARD_QUERY=${shellQuote(state.forwardProfile?.phoneLocal ?? "profile-only")}
export QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR=${shellQuote(options.remoteLogDir)}
export QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_RESULT_BUNDLE_DIR=${shellQuote(options.remoteResultBundleDir)}
bash scripts/run-ios-chat-actions-notifications-ui-test.sh
`, 30 * 60 * 1000);
    report.steps.push(profileListsOnly
      ? "ios_xctest_profile_followers_and_following_lists_verified"
      : "ios_xctest_profile_entry_composer_reply_edit_and_action_bar_verified");

    if (profileFollowOnly) {
      await pollProfileFollowEdge(state.a.profileId, state.b.profileId, true);
      report.steps.push("profile_follow_toggled_and_verified_by_db");
    }

    if (!profileEvidenceOnly) {
      const backendContract = await pollBackendContract(config, state);
      state.composerMessage = backendContract.composerMessageId;
      state.replyMessage = backendContract.replyMessageId;
      report.steps.push("backend_verified_send_reply_edit_and_favorite");
      const forwardDestination = await pollForwardDestinationThread(config, state.a, state.forwardProfile.id);
      state.forwardThread = forwardDestination.threadId;
      const forwardedMessage = await pollMessage(config, state.a, state.forwardThread, (message) =>
        messageText(message) === state.editMarker &&
          Number(message?.forwarded_from_message_id) === Number(state.editableMessage),
        "forwarded_message",
      );
      state.forwardedMessage = messageId(forwardedMessage);
      report.steps.push("message_forwarded_by_shared_ui_and_verified_by_rpc");
    }

    await copyRemoteEvidence(options);
    report.status = "passed";
    report.fixture = profileEvidenceOnly
      ? {
        threadId: state.thread,
        conversationId: `sb:${state.thread}`,
        seedMessageId: state.seedMessage,
        peerMessageId: state.peerMessage,
        peerProfileIdSha256: sha256(state.b.profileId),
        seedMarkerSha256: sha256(state.seedMarker),
        peerMarkerSha256: sha256(state.peerMarker),
        profileFollowInitialState: state.profileFollow?.initiallyFollowing ?? null,
        profileListInitialEdges: state.profileListEdges?.map((edge) => ({ label: edge.label, existed: edge.existed })),
      }
      : {
        threadId: state.thread,
        conversationId: `sb:${state.thread}`,
        seedMessageId: state.seedMessage,
        peerMessageId: state.peerMessage,
        editableMessageId: state.editableMessage,
        composerMessageId: state.composerMessage,
        replyMessageId: state.replyMessage,
        forwardThreadId: state.forwardThread,
        forwardedMessageId: state.forwardedMessage,
        forwardProfileIdSha256: sha256(state.forwardProfile.id),
        peerProfileIdSha256: sha256(state.b.profileId),
        seedMarkerSha256: sha256(state.seedMarker),
        peerMarkerSha256: sha256(state.peerMarker),
        editableMarkerSha256: sha256(state.editableMarker),
        composerMarkerSha256: sha256(state.composerMarker),
        replyMarkerSha256: sha256(state.replyMarker),
        editMarkerSha256: sha256(state.editMarker),
      };
  }
} catch (error) {
  report.error = safeFailure(error);
} finally {
  let profileHashRestoreFailed = false;
  try {
    await profileHashWindow.restore();
    if (profileHashWindow.state === "opened") {
      report.profileHashWindow = { state: "restored", count: profileHashWindow.count };
    }
  } catch (error) {
    profileHashRestoreFailed = true;
    report.profileHashWindow = { state: "restore_failed", error: safeFailure(error) };
  }
  if (config && state.thread) {
    const cleanup = { state: "completed", actions: [] };
    let cleanupFailed = false;
    try {
      if (state.profileListEdges) {
        cleanup.actions.push(...await restoreProfileListEdges(state.profileListEdges));
      }
      cleanup.actions.push(...await logicalCleanup(config, state));
    } catch (error) {
      cleanupFailed = true;
      cleanup.error = safeFailure(error);
    }
    if (state.profileFollow && state.a && state.b) {
      try {
        await restoreProfileFollowEdge(state.a.profileId, state.b.profileId, state.profileFollow.initiallyFollowing);
        cleanup.actions.push("profile_follow_edge_restored_to_initial_state");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (state.uniqueKey) {
      try {
        cleanup.hardCleanup = await hardDeleteTemporaryThread(state.thread, state.uniqueKey);
        cleanup.actions.push("hard_deleted_temporary_thread");
        cleanup.actions.push("cleanup_verified_physical_residue_absent");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (state.forwardProfile) {
      try {
        cleanup.forwardDestination = await hardDeleteTemporaryForwardDestination(state.forwardProfile, state.forwardThread);
        cleanup.actions.push("temporary_forward_destination_deleted");
        cleanup.actions.push("forward_destination_cleanup_verified_physical_residue_absent");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (cleanupFailed) {
      cleanup.state = "failed_or_incomplete";
      if (report.status === "passed") {
        report.status = "failed";
        report.error = cleanup.error ?? "cleanup_residue_detected";
      }
    }
    report.cleanup = cleanup;
  }
  if (profileHashRestoreFailed && report.status === "passed") {
    report.status = "failed";
    report.error = "temporary_profile_hash_window_restore_failed";
  }
  if (remoteCredentials) await run("ssh", [options.host, "rm", "-f", remoteCredentials]).catch(() => {});
  if (localCredentials) await rm(dirname(localCredentials), { recursive: true, force: true }).catch(() => {});
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Chat actions/notifications iOS evidence written: ${options.output}`);
}

if (report.status !== "passed") {
  console.error(`Chat actions/notifications iOS evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Chat actions/notifications iOS evidence passed.");
}

function parseArgs(argv) {
  const result = {
    host: process.env.QUATA_IOS_SSH_HOST?.trim() || "quata-mac",
    project: process.env.QUATA_IOS_MAC_PROJECT?.trim() || "/Users/gabriel/StudioProjects/quata-auth-recovery-parity-v1",
    derivedDataPath: process.env.QUATA_IOS_DERIVED_DATA_PATH?.trim() || "build/ios-intel-simulator-signed-derived-data",
    simulatorUdid: process.env.QUATA_IOS_SIMULATOR_UDID?.trim(),
    remoteLogDir: process.env.QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_LOG_DIR?.trim() || "build/reports/ios/chat-actions-notifications",
    remoteResultBundleDir: process.env.QUATA_IOS_CHAT_ACTIONS_NOTIFICATIONS_RESULT_BUNDLE_DIR?.trim() || "build/reports/ios/chat-actions-notifications/xcresults",
    output: resolve("build-reports/ios/chat-actions-notifications-evidence.json"),
    evidenceDir: resolve("build-reports/ios/chat-actions-notifications-evidence"),
    buildFirst: process.env.QUATA_IOS_BUILD_FIRST === "1",
    translationOnly: process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_IOS_TRANSLATION_ONLY === "1",
    profileOnly: process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_IOS_PROFILE_ONLY === "1",
    profileFollowOnly: process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_IOS_PROFILE_FOLLOW_ONLY === "1",
    profileListsOnly: process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_IOS_PROFILE_LISTS_ONLY === "1",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    const value = argv[index + 1];
    if (key === "--build-first") {
      result.buildFirst = true;
      continue;
    }
    if (key === "--translation-only") {
      result.translationOnly = true;
      result.output = resolve("build-reports/ios/chat-translation-evidence.json");
      result.evidenceDir = resolve("build-reports/ios/chat-translation-evidence");
      result.remoteLogDir = "build/reports/ios/chat-translation";
      result.remoteResultBundleDir = "build/reports/ios/chat-translation/xcresults";
      continue;
    }
    if (key === "--profile-only") {
      result.profileOnly = true;
      continue;
    }
    if (key === "--profile-follow-only") {
      result.profileFollowOnly = true;
      continue;
    }
    if (key === "--profile-lists-only") {
      result.profileListsOnly = true;
      continue;
    }
    if (!["--host", "--project", "--derived-data", "--simulator", "--remote-log-dir", "--remote-result-bundle-dir", "--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) {
      throw new Error("invalid_arguments");
    }
    index += 1;
    if (key === "--host") result.host = value;
    if (key === "--project") result.project = value;
    if (key === "--derived-data") result.derivedDataPath = value;
    if (key === "--simulator") result.simulatorUdid = value;
    if (key === "--remote-log-dir") result.remoteLogDir = value;
    if (key === "--remote-result-bundle-dir") result.remoteResultBundleDir = value;
    if (key === "--out") result.output = resolve(value);
    if (key === "--evidence-dir") result.evidenceDir = resolve(value);
  }
  if (!result.simulatorUdid) throw new Error("missing_environment:QUATA_IOS_SIMULATOR_UDID");
  return result;
}

async function authorizedUsers() {
  if (useAdjacentAuthorizedProfile) return authorizedUsersFromAdjacentSshCredential();
  const file = process.env[credentialsFileEnvironment]?.trim();
  if (file) {
    const parsed = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
    if (process.env.QUATA_CHAT_EVIDENCE_PAIR_FROM_SINGLE_FILE === "1") {
      const users = authorizedPairFromSingleCredential(parsed);
      validateAuthorizedUsers(users);
      return users;
    }
    const user = (entry, label) => ({
      label,
      countryCode: String(entry?.country_code ?? entry?.countryCode ?? "").trim(),
      phone: String(entry?.phone ?? "").trim(),
      password: String(entry?.password ?? ""),
    });
    const users = [user(parsed.a, "A"), user(parsed.b, "B")];
    validateAuthorizedUsers(users);
    return users;
  }
  const users = ["A", "B"].map((label) => ({
    label,
    countryCode: env(`QUATA_CHAT_EVIDENCE_${label}_COUNTRY_CODE`),
    phone: env(`QUATA_CHAT_EVIDENCE_${label}_PHONE`),
    password: process.env[`QUATA_CHAT_EVIDENCE_${label}_PASSWORD`],
  }));
  validateAuthorizedUsers(users);
  return users;
}

async function authorizedUsersFromAdjacentSshCredential() {
  const host = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_IOS_SSH_HOST?.trim()
    || process.env.QUATA_CHAT_EVIDENCE_SSH_HOST?.trim();
  const file = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_IOS_SSH_CREDENTIALS_FILE?.trim()
    || process.env.QUATA_CHAT_EVIDENCE_SSH_CREDENTIALS_FILE?.trim();
  if (!host || !file) throw new Error("missing_ios_adjacent_profile_credentials_source");
  const users = authorizedPairFromSingleCredential(JSON.parse(await runSilent("ssh", [host, `cat ${shellQuote(file)}`])));
  validateAuthorizedUsers(users);
  return users;
}

function authorizedPairFromSingleCredential(entry) {
  const countryCode = String(entry?.country_code ?? entry?.countryCode ?? "240").replace(/\D/g, "");
  const phone = String(entry?.phone ?? "").trim();
  const password = String(entry?.password ?? "");
  const digits = phone.replace(/\D/g, "");
  const local = digits.startsWith(countryCode) ? digits.slice(countryCode.length) : digits;
  if (!countryCode || !local || !/^\d+$/.test(local)) throw new Error("single_chat_evidence_credential_phone_required");
  const previousLocal = (BigInt(local) - 1n).toString().padStart(local.length, "0");
  if (previousLocal.length !== local.length) throw new Error("single_chat_evidence_pair_underflow");
  return [
    { label: "A", countryCode, phone: previousLocal, password },
    { label: "B", countryCode, phone: local, password },
  ];
}

function validateAuthorizedUsers(users) {
  if (users.some((user) => !user.password)) throw new Error("missing_chat_evidence_credentials");
  if (`${users[0].countryCode}|${users[0].phone}` === `${users[1].countryCode}|${users[1].phone}`) {
    throw new Error("chat_evidence_users_must_differ");
  }
}

function env(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`missing_environment:${name}`);
  return value;
}

function e164Phone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  if (!country || !digits) throw new Error("ios_e164_credentials_required");
  const local = digits.startsWith(country) ? digits.slice(country.length) : digits;
  if (!local) throw new Error("ios_e164_local_phone_required");
  return `+${country}${local}`;
}

async function publicBackendConfig() {
  const configuredUrl = process.env.QUATA_SUPABASE_URL?.trim();
  const configuredKey = process.env.QUATA_SUPABASE_PUBLISHABLE_KEY?.trim();
  if (configuredUrl && configuredKey) return { baseUrl: configuredUrl.replace(/\/+$/, ""), key: configuredKey };
  const source = await readFile("core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", "utf8");
  const baseUrl = source.match(/SUPABASE_URL\s*=\s*"([^"]+)"/)?.[1]?.replace(/\/+$/, "");
  const key = source.match(/SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/)?.[1];
  if (!baseUrl || !key) throw new Error("missing_public_supabase_configuration");
  return { baseUrl, key };
}

function isPublicKey(value) {
  if (!value || value.startsWith("sb_secret_") || value.toLowerCase().includes("service_role")) return false;
  if (value.startsWith("sb_publishable_")) return true;
  const parts = value.split(".");
  if (parts.length !== 3) return false;
  try { return JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"))?.role === "anon"; } catch { return false; }
}

function headers(config, token) {
  return {
    apikey: config.key,
    "content-type": "application/json",
    "x-client-info": "quata-chat-actions-notifications-ios-evidence",
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  };
}

async function jsonRequest(url, requestOptions, prefix) {
  let response;
  try { response = await fetch(url, { ...requestOptions, signal: AbortSignal.timeout(20_000) }); }
  catch { throw new Error(`${prefix}:network`); }
  const text = await response.text();
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  try { return text ? JSON.parse(text) : {}; } catch { throw new Error(`${prefix}:invalid_json`); }
}

async function login(config, user) {
  const payload = await jsonRequest(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: headers(config),
    body: JSON.stringify({
      action: "web_login",
      country_code: user.countryCode,
      phone_local: user.phone,
      password: user.password,
      client_instance_id: `chat-actions-notifications-ios-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const session = payload?.session;
  const profileId = payload?.profile?.id;
  if (!uuid.test(profileId ?? "") || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at)) {
    throw new Error(`invalid_auth_response:${user.label}`);
  }
  return { label: user.label, profileId, accessToken: session.access_token };
}

function rpc(config, session, name, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `chat_rpc_failed:${name}`);
}

function rows(payload, key) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.[key])) return payload[key];
  if (Array.isArray(payload?.data?.[key])) return payload.data[key];
  if (Array.isArray(payload?.messages)) return payload.messages;
  if (Array.isArray(payload?.favorites)) return payload.favorites;
  if (Array.isArray(payload?.data?.messages)) return payload.data.messages;
  if (Array.isArray(payload?.data?.favorites)) return payload.data.favorites;
  return [];
}

function threadId(payload) {
  const raw = payload?.thread_id ?? payload?.id ?? payload?.thread?.id ?? rows(payload, "threads")[0]?.id;
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error("chat_contract_invalid:thread_id");
  return value;
}

function messageId(payload) {
  const raw = payload?.message_id ?? payload?.id ?? payload?.message?.id ?? rows(payload, "messages")[0]?.id;
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error("chat_contract_invalid:message_id");
  return value;
}

function messageText(row) {
  return String(row?.body ?? row?.text ?? row?.message ?? "");
}

function messageNumericId(row) {
  const raw = row?.id ?? row?.message_id ?? row?.messageId ?? row?.message?.id;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value > 0 ? value : null;
}

function messageReplyToId(row) {
  const raw = row?.reply_to_message_id ?? row?.replyToMessageId ?? row?.reply?.id;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value > 0 ? value : null;
}

function favoriteMessageId(row) {
  const raw = row?.id ?? row?.message_id ?? row?.messageId ?? row?.message?.id ?? row?.favorite?.message_id;
  const value = Number(raw);
  return Number.isSafeInteger(value) && value > 0 ? value : null;
}

async function pollMessage(config, session, thread, predicate, label = "message", timeout = 180_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const detail = await rpc(config, session, "quata_chat_get_thread", {
      p_actor_profile_id: session.profileId,
      p_thread_id: thread,
      p_known_message_ids: [],
      p_limit: 250,
    });
    const match = rows(detail, "messages").find(predicate);
    if (match) return match;
    await new Promise((resolve) => setTimeout(resolve, 1_500));
  }
  throw new Error(`chat_backend_poll_timeout:${label}`);
}

async function pollBackendContract(config, state, timeout = 180_000) {
  const deadline = Date.now() + timeout;
  let lastState = null;
  while (Date.now() < deadline) {
    lastState = await backendContractState(config, state);
    if (
      lastState.editedExact &&
      lastState.composerMessageId &&
      lastState.replyMessageId &&
      lastState.seedFavoritePresent
    ) {
      return lastState;
    }
    await new Promise((resolve) => setTimeout(resolve, 1_500));
  }
  throw new Error(`chat_backend_contract_incomplete:${JSON.stringify(lastState)}`);
}

async function backendContractState(config, state) {
  const detail = await rpc(config, state.a, "quata_chat_get_thread", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_known_message_ids: [],
    p_limit: 250,
  });
  const messages = rows(detail, "messages");
  const editedMessage = messages.find((message) => messageNumericId(message) === state.editableMessage);
  const composerMessage = messages.find((message) => messageText(message) === state.composerMarker);
  const replyMessage = messages.find((message) =>
    messageText(message) === state.replyMarker && messageReplyToId(message) === state.seedMessage);
  const favoriteRows = await favorites(config, state.a);
  return {
    editedExact: messageText(editedMessage) === state.editMarker,
    editedMessagePresent: Boolean(editedMessage),
    editedTextSha256: editedMessage ? sha256(messageText(editedMessage)) : null,
    editMarkerMessageIds: messages
      .filter((message) => messageText(message) === state.editMarker)
      .map(messageNumericId)
      .filter(Boolean),
    originalEditableStillPresent: messages.some((message) => messageText(message) === state.editableMarker),
    composerMessageId: messageNumericId(composerMessage),
    replyMessageId: messageNumericId(replyMessage),
    seedFavoritePresent: favoriteRows.some((message) => favoriteMessageId(message) === state.seedMessage),
  };
}

async function favorites(config, session) {
  return rows(await rpc(config, session, "quata_chat_get_favorites", {
    p_actor_profile_id: session.profileId,
    p_limit: 250,
  }), "messages");
}

async function pollForwardDestinationThread(config, session, profileId, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const inbox = await rpc(config, session, "quata_chat_get_inbox", {
      p_actor_profile_id: session.profileId,
      p_limit: 100,
    });
    const threads = [
      inbox?.thread,
      inbox?.conversation,
      ...(Array.isArray(inbox?.threads) ? inbox.threads : []),
      ...(Array.isArray(inbox?.conversations) ? inbox.conversations : []),
      ...(Array.isArray(inbox?.update?.threads) ? inbox.update.threads : []),
      ...(Array.isArray(inbox?.update?.conversations) ? inbox.update.conversations : []),
    ].filter(Boolean);
    const match = threads.find((row) => JSON.stringify(row).includes(profileId));
    if (match) return { threadId: threadId(match), row: match };
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  throw new Error("chat_backend_contract_incomplete:forward_destination_thread");
}

async function threadContainsAnyMarker(config, session, thread, markers) {
  const detail = await rpc(config, session, "quata_chat_get_thread", {
    p_actor_profile_id: session.profileId,
    p_thread_id: thread,
    p_known_message_ids: [],
    p_limit: 250,
  });
  const markerSet = new Set(markers.filter(Boolean));
  return rows(detail, "messages").some((message) => markerSet.has(messageText(message)));
}

async function inboxContainsThread(config, session, thread) {
  const inbox = await rpc(config, session, "quata_chat_get_inbox", {
    p_actor_profile_id: session.profileId,
    p_limit: 100,
  });
  const threads = [
    ...(Array.isArray(inbox?.threads) ? inbox.threads : []),
    ...(Array.isArray(inbox?.conversations) ? inbox.conversations : []),
    ...(Array.isArray(inbox?.update?.threads) ? inbox.update.threads : []),
    ...(Array.isArray(inbox?.update?.conversations) ? inbox.update.conversations : []),
  ];
  return threads.some((row) => Number(row?.thread_id ?? row?.id) === thread);
}

async function logicalCleanup(config, state) {
  const actions = [];
  if (state.thread && state.seedMessage && state.a) {
    await rpc(config, state.a, "quata_chat_set_favorite", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_id: state.seedMessage,
      p_favorite: false,
    });
    actions.push("seed_favorite_removed");
  }
  const messageIds = [state.seedMessage, state.peerMessage, state.editableMessage, state.composerMessage, state.replyMessage].filter((value) => Number.isSafeInteger(value));
  if (state.thread && messageIds.length && state.a) {
    await rpc(config, state.a, "quata_chat_delete_messages", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_ids: messageIds,
    });
    actions.push("test_messages_deleted");
  }
  const markers = [state.seedMarker, state.peerMarker, state.editableMarker, state.composerMarker, state.replyMarker, state.editMarker];
  if (state.thread && state.a && await threadContainsAnyMarker(config, state.a, state.thread, markers)) {
    throw new Error("cleanup_residue_detected:message_a");
  }
  actions.push("cleanup_verified_messages_absent_for_a");
  if (state.thread && state.b && await threadContainsAnyMarker(config, state.b, state.thread, markers)) {
    throw new Error("cleanup_residue_detected:message_b");
  }
  actions.push("cleanup_verified_messages_absent_for_b");
  if (state.thread && state.a) {
    await rpc(config, state.a, "quata_chat_delete_thread", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread });
    actions.push("thread_removed_from_a_inbox");
  }
  if (state.thread && state.b) {
    await rpc(config, state.b, "quata_chat_delete_thread", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread });
    actions.push("thread_removed_from_b_inbox");
  }
  if (state.thread && state.seedMessage && state.a) {
    const remaining = await favorites(config, state.a);
    if (remaining.some((message) => favoriteMessageId(message) === state.seedMessage)) throw new Error("cleanup_residue_detected:favorite");
    actions.push("cleanup_verified_favorite_absent");
  }
  if (state.thread && state.a && await inboxContainsThread(config, state.a, state.thread)) throw new Error("cleanup_residue_detected:thread_a");
  actions.push("cleanup_verified_thread_absent_for_a");
  if (state.thread && state.b && await inboxContainsThread(config, state.b, state.thread)) throw new Error("cleanup_residue_detected:thread_b");
  actions.push("cleanup_verified_thread_absent_for_b");
  return actions;
}

async function hardDeleteTemporaryThread(thread, uniqueKey) {
  if (process.env[hardCleanupAuthorizationEnvironment]?.trim() !== hardCleanupAuthorizationValue) {
    throw new Error("missing_hard_cleanup_authorization");
  }
  if (!uniqueKey.startsWith("qadata-chat-actions-notifications-ios-")) throw new Error("cleanup_residue_detected:unsafe_unique_key");
  const dbUrlPath = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaPath = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([readFile(dbUrlPath, "utf8"), readFile(tlsCaPath, "utf8")]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const client = new pg.Client({
    connectionString: parsedConnection.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  });
  await client.connect();
  try {
    await client.query("begin");
    const owned = await client.query(
      "select id from public.chat_threads where id = $1 and unique_key = $2 and unique_key like 'qadata-chat-actions-notifications-ios-%' for update",
      [thread, uniqueKey],
    );
    if (owned.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_not_owned");
    const deleted = await client.query("delete from public.chat_threads where id = $1 and unique_key = $2 returning id", [thread, uniqueKey]);
    if (deleted.rowCount !== 1) throw new Error("cleanup_residue_detected:thread_delete_failed");
    const residue = await client.query(
      `select
        (select count(*)::int from public.chat_threads where id = $1 or unique_key = $2) as chat_threads,
        (select count(*)::int from public.chat_messages where thread_id = $1) as chat_messages,
        (select count(*)::int from public.chat_participants where thread_id = $1) as chat_participants,
        (select count(*)::int from public.chat_attachments where thread_id = $1) as chat_attachments,
        (select count(*)::int from public.chat_message_states where thread_id = $1) as chat_message_states,
        (select count(*)::int from public.chat_events where thread_id = $1) as chat_events,
        (select count(*)::int from public.conversation_user_state where conversation_id = $1) as conversation_user_state`,
      [thread, uniqueKey],
    );
    const counts = residue.rows[0] ?? {};
    if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:physical_rows");
    await client.query("commit");
    return { threadId: thread, uniqueKeySha256: sha256(uniqueKey), residueCounts: counts };
  } catch (error) {
    await client.query("rollback").catch(() => {});
    throw error;
  } finally {
    await client.end().catch(() => {});
  }
}

async function createTemporaryForwardProfile(runId) {
  const id = randomUUID();
  const phoneLocal = `999${Date.now().toString().slice(-6)}`;
  const displayName = `QADATA Forward ${phoneLocal}`;
  await withDatabase(async (client) => {
    await client.query("begin");
    try {
      await client.query(
        `insert into public.community_profiles
          (id, display_name, phone, pass_hash, phone_normalized, country_code, phone_local, phone_e164, neighborhood, barrio, barrio_normalized, account_status)
         values ($1, $2, $3, $4, $5, '240', $6, $7, 'QADATA', 'QADATA', 'qadata', 'active')`,
        [id, displayName, `+240 ${phoneLocal}`, `qadata-chat-forward-no-login-${runId}`, `240${phoneLocal}`, phoneLocal, `+240${phoneLocal}`],
      );
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  return { id, phoneLocal, displayName };
}

async function withPoolerClient(callback) {
  const dbUrlPath = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaPath = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([readFile(dbUrlPath, "utf8"), readFile(tlsCaPath, "utf8")]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const client = new pg.Client({
    connectionString: parsedConnection.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  });
  await client.connect();
  try {
    return await callback(client);
  } finally {
    await client.end().catch(() => {});
  }
}

async function profileFollowExists(actorProfileId, targetProfileId) {
  return await withPoolerClient(async (client) => {
    const result = await client.query(
      `select exists (
         select 1 from public.community_profile_follows
         where follower_profile_id = $1 and followed_profile_id = $2
       ) as exists`,
      [actorProfileId, targetProfileId],
    );
    return result.rows[0]?.exists === true;
  });
}

async function prepareProfileFollowAbsent(actorProfileId, targetProfileId) {
  return await withPoolerClient(async (client) => {
    await client.query("begin");
    try {
      const existing = await client.query(
        `select id from public.community_profile_follows
         where follower_profile_id = $1 and followed_profile_id = $2
         for update`,
        [actorProfileId, targetProfileId],
      );
      if (existing.rowCount > 0) {
        await client.query(
          `delete from public.community_profile_follows
           where follower_profile_id = $1 and followed_profile_id = $2`,
          [actorProfileId, targetProfileId],
        );
      }
      await client.query("commit");
      return { initiallyFollowing: existing.rowCount > 0 };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function restoreProfileFollowEdge(actorProfileId, targetProfileId, initiallyFollowing) {
  await withPoolerClient(async (client) => {
    await client.query("begin");
    try {
      if (initiallyFollowing) {
        await client.query(
          `insert into public.community_profile_follows (follower_profile_id, followed_profile_id)
           values ($1, $2)
           on conflict do nothing`,
          [actorProfileId, targetProfileId],
        );
      } else {
        await client.query(
          `delete from public.community_profile_follows
           where follower_profile_id = $1 and followed_profile_id = $2`,
          [actorProfileId, targetProfileId],
        );
      }
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  const restored = await profileFollowExists(actorProfileId, targetProfileId);
  if (restored !== initiallyFollowing) throw new Error("cleanup_residue_detected:profile_follow_edge");
}

async function pollProfileFollowEdge(actorProfileId, targetProfileId, expected, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await profileFollowExists(actorProfileId, targetProfileId) === expected) return;
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 750));
  }
  throw new Error(`profile_follow_backend_poll_timeout:${expected ? "created" : "removed"}`);
}

async function hardDeleteTemporaryForwardDestination(profile, threadId) {
  return await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const owned = await client.query(
        "select id from public.community_profiles where id = $1 and display_name = $2 and phone_local = $3 for update",
        [profile.id, profile.displayName, profile.phoneLocal],
      );
      if (owned.rowCount !== 1) throw new Error("cleanup_residue_detected:forward_profile_not_owned");
      if (threadId) {
        const participant = await client.query(
          "select 1 from public.chat_participants where thread_id = $1 and profile_id = $2 for update",
          [threadId, profile.id],
        );
        if (participant.rowCount !== 1) throw new Error("cleanup_residue_detected:forward_thread_not_owned");
        await client.query("delete from public.chat_threads where id = $1", [threadId]);
      }
      const deleted = await client.query(
        "delete from public.community_profiles where id = $1 and display_name = $2 and phone_local = $3 returning id",
        [profile.id, profile.displayName, profile.phoneLocal],
      );
      if (deleted.rowCount !== 1) throw new Error("cleanup_residue_detected:forward_profile_delete_failed");
      const residue = await client.query(
        `select
          (select count(*)::int from public.community_profiles where id = $1) as community_profiles,
          (select count(*)::int from public.chat_threads where id = $2) as chat_threads,
          (select count(*)::int from public.chat_messages where thread_id = $2) as chat_messages,
          (select count(*)::int from public.chat_participants where profile_id = $1 or thread_id = $2) as chat_participants,
          (select count(*)::int from public.chat_private_threads where thread_id = $2 or profile_low_id = $1 or profile_high_id = $1) as chat_private_threads`,
        [profile.id, threadId ?? -1],
      );
      const counts = residue.rows[0] ?? {};
      if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:forward_physical_rows");
      await client.query("commit");
      return { profileIdSha256: sha256(profile.id), threadId, residueCounts: counts };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function withDatabase(callback) {
  const dbUrlPath = process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile;
  const tlsCaPath = process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile;
  const [connectionString, ca] = await Promise.all([readFile(dbUrlPath, "utf8"), readFile(tlsCaPath, "utf8")]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const client = new pg.Client({
    connectionString: parsedConnection.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  });
  await client.connect();
  try {
    return await callback(client);
  } finally {
    await client.end().catch(() => {});
  }
}

async function prepareProfileListEdges(followerId, followedId) {
  return await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const pairs = [
        { label: "a_follows_b", followerId, followedId },
        { label: "b_follows_a", followerId: followedId, followedId: followerId },
      ];
      const initial = [];
      for (const pair of pairs) {
        const existing = await client.query(
          `select id
             from public.community_profile_follows
            where follower_profile_id = $1 and followed_profile_id = $2
            limit 1
            for update`,
          [pair.followerId, pair.followedId],
        );
        const existed = existing.rowCount > 0;
        initial.push({ ...pair, existed });
        if (!existed) {
          await client.query(
            `insert into public.community_profile_follows (follower_profile_id, followed_profile_id)
             values ($1, $2)
             on conflict do nothing`,
            [pair.followerId, pair.followedId],
          );
        }
      }
      await client.query("commit");
      return initial;
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function restoreProfileListEdges(edges) {
  if (!Array.isArray(edges) || edges.length === 0) return [];
  await withDatabase(async (client) => {
    await client.query("begin");
    try {
      for (const edge of edges) {
        if (edge.existed) {
          await client.query(
            `insert into public.community_profile_follows (follower_profile_id, followed_profile_id)
             values ($1, $2)
             on conflict do nothing`,
            [edge.followerId, edge.followedId],
          );
        } else {
          await client.query(
            "delete from public.community_profile_follows where follower_profile_id = $1 and followed_profile_id = $2",
            [edge.followerId, edge.followedId],
          );
        }
      }
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  return ["profile_follow_list_edges_restored_to_initial_state"];
}

async function openTemporaryProfileHashWindow(users) {
  if (process.env[tempProfileHashAuthorizationEnvironment]?.trim() !== tempProfileHashAuthorizationValue) {
    return { state: "not_requested", restored: true, restore: async () => {} };
  }
  const opened = await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const rowsToRestore = [];
      for (const user of users) {
        const found = await client.query(
          `select id, pass_hash, pass_plain
             from public.community_profiles
            where (
              regexp_replace(coalesce(country_code, ''), '\\D', '', 'g') = $1
              and regexp_replace(coalesce(phone_local, ''), '\\D', '', 'g') = $2
            ) or regexp_replace(coalesce(phone, ''), '\\D', '', 'g') = any($3::text[])
            order by created_at desc nulls last, id
            limit 1
            for update`,
          [user.countryCode, user.phone, [`${user.countryCode}${user.phone}`, user.phone]],
        );
        if (found.rowCount !== 1) throw new Error("temporary_profile_hash_window:profile_not_found");
        const row = found.rows[0];
        rowsToRestore.push({ id: row.id, pass_hash: row.pass_hash, pass_plain: row.pass_plain });
        await client.query(
          "update public.community_profiles set pass_hash = $1, pass_plain = null where id = $2",
          [sha256(user.password), row.id],
        );
      }
      await client.query("commit");
      return rowsToRestore;
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  let restored = false;
  return {
    state: "opened",
    restored,
    count: opened.length,
    restore: async () => {
      if (restored) return;
      await withDatabase(async (client) => {
        await client.query("begin");
        try {
          for (const row of opened) {
            await client.query(
              "update public.community_profiles set pass_hash = $1, pass_plain = $2 where id = $3",
              [row.pass_hash, row.pass_plain, row.id],
            );
          }
          await client.query("commit");
          restored = true;
        } catch (error) {
          await client.query("rollback").catch(() => {});
          throw error;
        }
      });
    },
  };
}

async function copyRemoteEvidence(values) {
  const target = join(values.evidenceDir, "mac-ui-report");
  const resultTarget = join(values.evidenceDir, "xcresults");
  await rm(values.evidenceDir, { recursive: true, force: true });
  await mkdir(values.evidenceDir, { recursive: true });
  await run("scp", ["-r", `${values.host}:${values.project}/${values.remoteLogDir}`, target]);
  await run("scp", ["-r", `${values.host}:${values.project}/${values.remoteResultBundleDir}`, resultTarget]).catch((error) => {
    report.evidence.resultBundleCopyWarning = safeFailure(error);
  });
  report.evidence.uiReportDirectory = target;
  report.evidence.resultBundleDirectory = resultTarget;
}

async function gitMetadata() {
  const head = (await runSilent("git", ["rev-parse", "HEAD"])).trim();
  const status = await runSilent("git", ["status", "--porcelain"]);
  return { head, workingTreeDirty: status.trim().length > 0 };
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function shellQuote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
}

async function runSshScript(host, script, timeoutMs = 15 * 60 * 1000) {
  return run("ssh", [host, "bash", "-s"], { input: script, timeoutMs });
}

async function run(command, args, options = {}) {
  const output = await runCapture(command, args, options);
  if (output.trim()) report.lastCommandOutputTail = redactedTail(output);
  return output;
}

function runSilent(command, args, options = {}) {
  return runCapture(command, args, { ...options, silent: true });
}

function runCapture(command, args, options = {}) {
  if (!existsSync(process.cwd())) throw new Error("working_directory_missing");
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, args, {
      cwd: process.cwd(),
      env: process.env,
      stdio: ["pipe", "pipe", "pipe"],
      windowsHide: true,
    });
    let stdout = "";
    let stderr = "";
    const timeout = setTimeout(() => {
      child.kill("SIGTERM");
      rejectPromise(new Error(`command_timeout:${command}`));
    }, options.timeoutMs ?? 15 * 60 * 1000);
    child.stdout.on("data", (chunk) => { stdout += chunk; });
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.on("error", (error) => {
      clearTimeout(timeout);
      rejectPromise(error);
    });
    child.on("close", (code) => {
      clearTimeout(timeout);
      const combined = `${stdout}${stderr}`;
      if (code === 0) resolvePromise(combined);
      else rejectPromise(new Error(`command_failed:${command}:${code}:${redactedTail(combined)}`));
    });
    if (options.input) child.stdin.end(options.input);
    else child.stdin.end();
  });
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}

function redactedTail(text) {
  return safeFailure(String(text).split(/\r?\n/).slice(-40).join("\n"));
}
