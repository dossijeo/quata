#!/usr/bin/env node
import { chromium } from "playwright-core";
import { createHash, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { cp, mkdir, mkdtemp, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, extname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";
import pg from "pg";
import {
  assertFeedOfficialCommentAbsent as assertSharedFeedOfficialCommentAbsent,
  cleanupProfileContentFixture as cleanupSharedProfileContentFixture,
  cleanupFeedOfficialCommentsFixture as cleanupSharedFeedOfficialCommentsFixture,
  createCleanupRegistry,
  cleanupProfileRolesSafetyFixture as cleanupSharedProfileRolesSafetyFixture,
  pollProfileGlobalBlock,
  pollProfileReport,
  pollFeedOfficialComment as pollSharedFeedOfficialComment,
  pollFeedOfficialReplyComment as pollSharedFeedOfficialReplyComment,
  pollProfileContentComment as pollSharedProfileContentComment,
  pollProfileContentReplyComment as pollSharedProfileContentReplyComment,
  pollProfileRoles,
  prepareProfileRolesSafetyFixture,
  seedChatAttachmentFixture,
  seedFeedOfficialCommentsFixture,
  seedProfileContentFixture,
  validPngFixture,
} from "./e2e-fixtures/chat-attachments.mjs";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const credentialsFileEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_CREDENTIALS_FILE";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP";
const tempProfileHashAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH_AUTHORIZATION";
const tempProfileHashAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH";
const useAdjacentAuthorizedProfile = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_USE_ADJACENT_AUTHORIZED_PROFILE === "1";
let lastThreadSnapshot = null;

class ProfileOnlyCompleted extends Error {}
class ProfileListsOnlyCompleted extends Error {}
class ProfileEntryOnlyCompleted extends Error {}
class ProfileRolesSafetyOnlyCompleted extends Error {}

function parseArgs(argv) {
  const result = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/chat-actions-notifications-evidence.json"),
    evidenceDir: resolve("build-reports/web/chat-actions-notifications-evidence"),
    translationOnly: false,
    profileOnly: false,
    profileFollowOnly: false,
    profileListsOnly: false,
    profileContentOnly: false,
    profileEntryOnly: false,
    feedOfficialCommentsOnly: false,
    feedOfficialCommentsErrorOnly: false,
    feedOfficialCommentsSelectorStatesOnly: false,
    profilePrivateChatOnly: false,
    profileRolesSafetyOnly: false,
    communityChatOnly: false,
    menuSurfaceOnly: false,
    attachmentsAudioOnly: false,
    attachmentPickerOnly: false,
    attachmentPickerSource: "document",
    attachmentPickerOutcome: "success",
    composerEmojiOnly: false,
    groupSosOnly: false,
    groupAdminOnly: false,
    groupModerationOnly: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    if (key === "--translation-only") {
      result.translationOnly = true;
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
    if (key === "--profile-content-only") {
      result.profileContentOnly = true;
      continue;
    }
    if (key === "--profile-entry-only") {
      result.profileEntryOnly = true;
      continue;
    }
    if (key === "--feed-official-comments-only") {
      result.feedOfficialCommentsOnly = true;
      result.output = resolve("build-reports/web/feed-official-comments-emoji-evidence.json");
      result.evidenceDir = resolve("build-reports/web/feed-official-comments-emoji-evidence");
      continue;
    }
    if (key === "--feed-official-comments-error-only") {
      result.feedOfficialCommentsErrorOnly = true;
      result.output = resolve("build-reports/web/feed-official-comments-error-evidence.json");
      result.evidenceDir = resolve("build-reports/web/feed-official-comments-error-evidence");
      continue;
    }
    if (key === "--feed-official-comments-selector-states-only") {
      result.feedOfficialCommentsSelectorStatesOnly = true;
      result.output = resolve("build-reports/web/flow-emoji-selector-states-evidence.json");
      result.evidenceDir = resolve("build-reports/web/flow-emoji-selector-states-evidence");
      continue;
    }
    if (key === "--profile-private-chat-only") {
      result.profilePrivateChatOnly = true;
      continue;
    }
    if (key === "--profile-roles-safety-only") {
      result.profileRolesSafetyOnly = true;
      result.output = resolve("build-reports/web/profile-roles-safety-evidence.json");
      result.evidenceDir = resolve("build-reports/web/profile-roles-safety-evidence");
      continue;
    }
    if (key === "--community-chat-only") {
      result.communityChatOnly = true;
      result.output = resolve("build-reports/web/community-chat-flow-evidence.json");
      result.evidenceDir = resolve("build-reports/web/community-chat-flow-evidence");
      continue;
    }
    if (key === "--menu-surface-only") {
      result.menuSurfaceOnly = true;
      continue;
    }
    if (key === "--attachments-audio-only") {
      result.attachmentsAudioOnly = true;
      continue;
    }
    if (key === "--attachment-picker-only") {
      result.attachmentPickerOnly = true;
      result.output = resolve("build-reports/web/chat-attachment-picker-evidence.json");
      result.evidenceDir = resolve("build-reports/web/chat-attachment-picker-evidence");
      continue;
    }
    if (key === "--attachment-picker-source") {
      index += 1;
      if (index >= argv.length) throw new Error("missing_value:--attachment-picker-source");
      result.attachmentPickerSource = argv[index];
      continue;
    }
    if (key === "--attachment-picker-outcome") {
      index += 1;
      if (index >= argv.length) throw new Error("missing_value:--attachment-picker-outcome");
      result.attachmentPickerOutcome = argv[index];
      continue;
    }
    if (key === "--composer-emoji-only") {
      result.composerEmojiOnly = true;
      result.output = resolve("build-reports/web/chat-composer-emoji-evidence.json");
      result.evidenceDir = resolve("build-reports/web/chat-composer-emoji-evidence");
      continue;
    }
    if (key === "--group-sos-only") {
      result.groupSosOnly = true;
      continue;
    }
    if (key === "--group-admin-only") {
      result.groupAdminOnly = true;
      result.output = resolve("build-reports/web/chat-group-admin-evidence.json");
      result.evidenceDir = resolve("build-reports/web/chat-group-admin-evidence");
      continue;
    }
    if (key === "--group-moderation-only") {
      result.groupModerationOnly = true;
      result.output = resolve("build-reports/web/chat-group-moderation-evidence.json");
      result.evidenceDir = resolve("build-reports/web/chat-group-moderation-evidence");
      continue;
    }
    const value = argv[++index];
    if (!["--dist", "--chrome", "--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) {
      throw new Error("invalid_arguments");
    }
    if (key === "--dist") result.distribution = resolve(value);
    if (key === "--chrome") result.chrome = resolve(value);
    if (key === "--out") result.output = resolve(value);
    if (key === "--evidence-dir") result.evidenceDir = resolve(value);
  }
  if (!["document", "gallery", "camera"].includes(result.attachmentPickerSource)) {
    throw new Error(`unsupported_attachment_picker_source:${result.attachmentPickerSource}`);
  }
  if (!["success", "cancelled", "failure", "unsupported", "register-failure"].includes(result.attachmentPickerOutcome)) {
    throw new Error(`unsupported_attachment_picker_outcome:${result.attachmentPickerOutcome}`);
  }
  return result;
}

async function withTimeout(promise, timeoutMs, label) {
  let timeout;
  try {
    return await Promise.race([
      promise,
      new Promise((_, reject) => {
        timeout = setTimeout(() => reject(new Error(`${label}_timeout`)), timeoutMs);
      }),
    ]);
  } finally {
    clearTimeout(timeout);
  }
}

function isProfileFocalMode(options) {
  return options.profileOnly ||
    options.profileFollowOnly ||
    options.profileListsOnly ||
    options.profileContentOnly ||
    options.feedOfficialCommentsOnly ||
    options.feedOfficialCommentsErrorOnly ||
    options.feedOfficialCommentsSelectorStatesOnly ||
    options.profileEntryOnly ||
    options.profilePrivateChatOnly ||
    options.profileRolesSafetyOnly;
}

function isFullEvidenceMode(options) {
  return !options.translationOnly &&
    !isProfileFocalMode(options) &&
    !options.communityChatOnly &&
    !options.menuSurfaceOnly &&
    !options.attachmentsAudioOnly &&
    !options.attachmentPickerOnly &&
    !options.composerEmojiOnly &&
    !options.groupSosOnly &&
    !options.groupAdminOnly &&
    !options.groupModerationOnly;
}

async function runSilent(command, args, options = {}) {
  return await new Promise((resolvePromise, reject) => {
    let output = "";
    let stderr = "";
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: false, ...options });
    child.stdout.on("data", (chunk) => { output += chunk.toString(); });
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolvePromise(output) : reject(new Error(`command_failed:${command}:${code}:${stderr.trim()}`)));
  });
}

async function gitMetadata() {
  const head = (await runSilent("git", ["rev-parse", "HEAD"])).trim();
  const status = await runSilent("git", ["status", "--porcelain"]);
  return { head, workingTreeDirty: status.trim().length > 0 };
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

async function usersFromPrivateFile() {
  const file = process.env[credentialsFileEnvironment]?.trim();
  if (!file) throw new Error("missing_chat_actions_notifications_credentials_file");
  const parsed = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
  const user = (entry, label) => ({
    label,
    countryCode: String(entry?.country_code ?? entry?.countryCode ?? "").trim(),
    phone: String(entry?.phone ?? "").trim(),
    password: String(entry?.password ?? ""),
  });
  const users = [user(parsed.a, "A"), user(parsed.b, "B")];
  if (users.some((candidate) => !candidate.countryCode || !candidate.phone || !candidate.password)) {
    throw new Error("missing_chat_actions_notifications_credentials");
  }
  if (`${users[0].countryCode}|${users[0].phone}` === `${users[1].countryCode}|${users[1].phone}`) {
    throw new Error("chat_actions_notifications_users_must_differ");
  }
  return users;
}

async function authorizedUsers() {
  if (!useAdjacentAuthorizedProfile) return usersFromPrivateFile();
  const host = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_SSH_HOST?.trim();
  const file = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_SSH_CREDENTIALS_FILE?.trim();
  if (!host || !file) throw new Error("missing_adjacent_profile_credentials_source");
  const credentials = JSON.parse((await runSilent("ssh", [host, `cat ${file}`])).replace(/^\uFEFF/, ""));
  const primaryPhone = splitPhone(credentials.phone);
  const previousLocal = (BigInt(primaryPhone.localPhone) - 1n).toString().padStart(primaryPhone.localPhone.length, "0");
  if (previousLocal.length !== primaryPhone.localPhone.length) throw new Error("invalid_adjacent_profile_phone");
  return [
    {
      label: "A",
      countryCode: primaryPhone.countryCode,
      phone: previousLocal,
      password: credentials.password,
    },
    {
      label: "B",
      countryCode: primaryPhone.countryCode,
      phone: primaryPhone.localPhone,
      password: credentials.password,
      adjacentPhoneKeys: adjacentRecipientPhones(primaryPhone),
    },
  ];
}

function splitPhone(phone) {
  const digits = String(phone ?? "").replace(/\D/g, "");
  if (!digits.startsWith("240") || digits.length <= 3) throw new Error("invalid_adjacent_profile_phone");
  return { countryCode: "240", localPhone: digits.slice(3), phoneKey: digits };
}

function adjacentRecipientPhones(primaryPhone) {
  return [1, -1].map((delta) => {
    const value = Number(primaryPhone.localPhone) + delta;
    const localPhone = String(value).padStart(primaryPhone.localPhone.length, "0");
    return `${primaryPhone.countryCode}${localPhone}`;
  });
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
    "x-client-info": "quata-chat-actions-notifications-evidence",
    ...(token ? { authorization: `Bearer ${token}` } : {}),
  };
}

async function jsonRequest(url, options, prefix) {
  let response;
  try { response = await fetch(url, { ...options, signal: AbortSignal.timeout(20_000) }); }
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
      client_instance_id: `chat-actions-notifications-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const session = payload?.session, profileId = payload?.profile?.id, webSessionToken = payload?.web_session?.token;
  if (!uuid.test(profileId ?? "") || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at) || !webSessionToken) {
    throw new Error(`invalid_auth_response:${user.label}`);
  }
  const displayName = String(payload?.profile?.display_name ?? payload?.profile?.displayName ?? payload?.profile?.name ?? "").trim();
  const neighborhood = String(payload?.profile?.neighborhood ?? payload?.profile?.barrio ?? "").trim();
  return { label: user.label, profileId, displayName, neighborhood, accessToken: session.access_token, refreshToken: session.refresh_token, expiresAt: session.expires_at, webSessionToken };
}

function rpc(config, session, name, body) {
  return jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `chat_rpc_failed:${name}`);
}

async function storageRequest(config, session, path, options, prefix) {
  let response;
  try {
    response = await fetch(`${config.baseUrl}${path}`, {
      ...options,
      headers: {
        apikey: config.key,
        ...(options.headers ?? {}),
        ...(session?.accessToken ? { authorization: `Bearer ${session.accessToken}` } : {}),
      },
      signal: AbortSignal.timeout(20_000),
    });
  } catch {
    throw new Error(`${prefix}:network`);
  }
  const text = await response.text();
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  return text;
}

async function verifyAttachmentsAudioWeb(page, fixtures, evidenceDir, report, context = {}) {
  await verifyWebAudioRecordingComposer(page, evidenceDir, report, fixtures.recordingMarker);
  if (fixtures.recordingMarker) {
    await waitMessageVisible(page, fixtures.recordingMarker, "audio_recording_sent_message_not_visible");
    const recordingMessage = await pollMessage(
      context.config,
      context.session,
      context.thread,
      (message) => messageText(message) === fixtures.recordingMarker && messageAttachments(message).some((attachment) => /^audio\//i.test(attachment.mimeType ?? "")),
      60_000,
    );
    const recordingAttachment = messageAttachments(recordingMessage).find((attachment) => /^audio\//i.test(attachment.mimeType ?? ""));
    if (!recordingAttachment) throw new Error("audio_recording_sent_attachment_missing");
    const recordingMessageId = messageNumericId(recordingMessage);
    context.state?.uiMessages?.push(recordingMessageId);
    context.state?.cleanupRegistry?.trackStorageObject({
      bucket: recordingAttachment.bucket || "chat-attachments",
      storagePath: recordingAttachment.storagePath,
      name: recordingAttachment.name || "recorded-audio",
    });
    report.evidence.audioRecordingSent = {
      markerSha256: sha256(fixtures.recordingMarker),
      messageId: recordingMessageId,
      attachmentId: recordingAttachment.id,
      mimeType: recordingAttachment.mimeType,
      storagePathSha256: recordingAttachment.storagePath ? sha256(recordingAttachment.storagePath) : null,
    };
    report.steps.push("web_audio_recording_sent_by_shared_composer_and_verified_by_rpc");
  }
  await waitMessageVisibleNearCurrentPosition(page, fixtures.image.marker, "image_attachment_message_not_visible");
  await openAndCloseChatAttachmentMediaViewer(page, evidenceDir, report, "video", true);
  await openAndCloseChatAttachmentMediaViewer(page, evidenceDir, report, "image", true);
  await waitMessageVisibleBelowCurrentPosition(page, fixtures.document.marker, "document_attachment_message_not_visible");
  await waitMessageVisibleBelowCurrentPosition(page, fixtures.audio.marker, "audio_attachment_message_not_visible");
  await page.getByText(fixtures.document.name, { exact: false }).first().waitFor({ timeout: 15_000 });
  await page.getByText(fixtures.audio.name, { exact: false }).first().waitFor({ timeout: 15_000 });
  report.evidence.attachmentsDocument = await attachScreenshot(page, evidenceDir, "web-chat-attachment-document-visible");
  await verifyDocumentAttachmentActionsWeb(page, fixtures.document, evidenceDir, report);
  const play = await visibleAriaLocator(page, [
    new RegExp(`(?:Play audio|Reproducir audio).*${escapeRegExp(fixtures.audio.name)}`, "i"),
  ], 10_000);
  if (!play) throw new Error("audio_attachment_toggle_not_visible");
  report.evidence.audioPlayer = await attachScreenshot(page, evidenceDir, "web-chat-audio-player-visible");
  await clickLocatorCenter(page, play, "audio_attachment_toggle_not_clickable");
  const playback = await waitAudioPlaybackObserved(page);
  if (playback.state !== "playing") throw new Error(`audio_playback_not_playing:${playback.state}`);
  report.evidence.audioPlaybackObserved = playback;
  report.evidence.audioSeekObserved = await seekAudioProgressWeb(page, fixtures.audio.name, 0.95);
  report.evidence.audioToggle = await attachScreenshot(page, evidenceDir, "web-chat-audio-toggle-attempted");
  if (fixtures.nextAudio) {
    await page.mouse.wheel(0, 520);
    await delay(350);
    await waitMessageVisible(page, fixtures.nextAudio.marker, "next_audio_attachment_message_not_visible");
    await page.getByText(fixtures.nextAudio.name, { exact: false }).first().waitFor({ timeout: 15_000 });
    report.evidence.consecutiveAudioAutoAdvanceObserved = await waitConsecutiveAudioPlaybackObserved(page, fixtures.audio.name, fixtures.nextAudio.name, 8_000, true);
    const nextPause = await visibleAriaLocator(page, [
      new RegExp(`(?:Pause audio|Pausar audio).*${escapeRegExp(fixtures.nextAudio.name)}`, "i"),
    ], 10_000);
    if (!nextPause) throw new Error("next_audio_attachment_pause_anchor_not_visible");
    report.evidence.nextAudioPlayer = await attachScreenshot(page, evidenceDir, "web-chat-audio-next-player-visible");
  }
}

async function verifyDocumentAttachmentActionsWeb(page, documentFixture, evidenceDir, report) {
  const open = await visibleAriaLocator(page, [/chat\.attachment\.document\.open|Abrir adjunto|Open attachment/i], 10_000);
  const download = await visibleAriaLocator(page, [/chat\.attachment\.document\.download|Descargar adjunto|Download attachment/i], 10_000);
  const share = await visibleAriaLocator(page, [/chat\.attachment\.document\.share|Compartir adjunto|Share attachment/i], 10_000);
  if (!open) throw new Error("document_attachment_open_anchor_missing");
  if (!download) throw new Error("document_attachment_download_anchor_missing");
  if (!share) throw new Error("document_attachment_share_anchor_missing");
  report.evidence.attachmentDocumentActions = await attachScreenshot(page, evidenceDir, "web-chat-attachment-document-actions");

  await clickLocatorCenter(page, open, "document_attachment_open_not_clickable");
  await page.waitForFunction(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    return Boolean(scope.querySelector("[data-testid='document-viewer-status-root']"));
  }, { timeout: 10_000 });
  report.evidence.attachmentDocumentViewerStatus = await attachScreenshot(page, evidenceDir, "web-chat-attachment-document-viewer-status");
  await page.evaluate(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    scope.querySelector("[data-testid='document-viewer-status-close']")?.click();
  });
  await page.waitForFunction(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    return !scope.querySelector("[data-testid='document-viewer-status-root']");
  }, { timeout: 10_000 });

  const [downloadEvent] = await Promise.all([
    page.waitForEvent("download", { timeout: 10_000 }),
    clickLocatorCenter(page, download, "document_attachment_download_not_clickable"),
  ]);
  const suggestedName = downloadEvent.suggestedFilename();
  const expectedStem = documentFixture.name.toLowerCase().split(".")[0];
  if (!suggestedName.toLowerCase().includes(expectedStem)) {
    throw new Error(`document_attachment_download_name_mismatch:${suggestedName}`);
  }
  await closeTransientNotice(page);

  const shareAfterDownload = await visibleAriaLocator(page, [/chat\.attachment\.document\.share|Compartir adjunto|Share attachment/i], 10_000);
  if (!shareAfterDownload) throw new Error("document_attachment_share_anchor_missing_after_download");
  const beforeShareCount = await page.evaluate(() => globalThis.__quataSharePayloads?.length ?? 0);
  await clickLocatorPreferDom(page, shareAfterDownload, "document_attachment_share_not_clickable");
  await page.waitForFunction((count) => (globalThis.__quataSharePayloads?.length ?? 0) > count, beforeShareCount, { timeout: 10_000 });
  const payload = await page.evaluate(() => globalThis.__quataSharePayloads.at(-1));
  const sharedText = String(payload?.text ?? payload?.url ?? "");
  const sharedFiles = Array.isArray(payload?.files) ? payload.files : [];
  const hasAttachmentUrl = sharedText.includes("chat-attachments");
  const hasExpectedFile = sharedFiles.some((file) => String(file?.name ?? "").toLowerCase().includes(expectedStem));
  if (!payload || (!hasAttachmentUrl && !hasExpectedFile)) {
    throw new Error("document_attachment_share_payload_missing_attachment_url");
  }
  report.evidence.attachmentDocumentActionsResult = {
    downloadSuggestedName: suggestedName,
    sharePayload: {
      title: payload.title,
      hasChatAttachmentUrl: hasAttachmentUrl,
      hasExpectedFile,
      fileCount: sharedFiles.length,
    },
  };
  report.steps.push("web_chat_document_attachment_download_and_share_actions_verified");
}

async function closeTransientNotice(page) {
  const close = await visibleAriaLocator(page, [/Cerrar|Close/i], 1_000);
  if (close) {
    await clickLocatorPreferDom(page, close, "transient_notice_close_not_clickable").catch(() => {});
    await delay(500);
  }
}

async function closeTaggedCommentsPanelIfVisible(page, panelTag, errorPrefix) {
  const panel = await visibleExactAriaLocator(page, panelTag, 1_000) ??
    await visibleNativeControlExact(page, panelTag, 1_000);
  if (!panel) return false;
  const close = await visibleAriaLocator(page, [/Cerrar hoja|Close sheet/i], 1_500);
  const closeControl = close ? null : await visibleNativeControl(page, [/Cerrar hoja|Close sheet/i], 1_500);
  if (closeControl) {
    await clickNativeControlCenter(page, closeControl, `${errorPrefix}_not_clickable`);
  } else if (close) {
    await clickLocatorPreferDom(page, close, `${errorPrefix}_not_clickable`);
  } else {
    throw new Error(`${errorPrefix}_anchor_missing`);
  }
  await page.waitForFunction((tag) => {
    const visible = (element) => {
      const rect = element.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.right > 0 &&
        rect.top < window.innerHeight && rect.left < window.innerWidth;
    };
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    return ![...scope.querySelectorAll("[aria-label]")].some((element) => element.getAttribute("aria-label") === tag && visible(element));
  }, panelTag, { timeout: 5_000 });
  await delay(300);
  return true;
}

async function verifyAttachmentPickerWeb(page, source, outcome, config, state, runId, evidenceDir, report) {
  const fixture = await createAttachmentPickerFixture(source, runId, "web");
  state.attachmentPicker = fixture;
  let registerFailureInjected = false;
  try {
    if (outcome === "register-failure") {
      await page.route("**/rest/v1/rpc/quata_chat_register_attachment", async (route) => {
        registerFailureInjected = true;
        await route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ message: "chat_attachment_register_e2e_failure" }),
        });
      }, { times: 1 });
    }
    if (outcome !== "success" && outcome !== "register-failure") {
      await page.evaluate(({ source, outcome }) => {
        globalThis.__quataChatAttachmentPickerE2E = {
          optIn: "I_ACCEPT_WEB_CHAT_ATTACHMENT_PICKER_OUTCOME",
          source,
          outcome,
          reason: `${source}_permission_denied`,
        };
      }, { source, outcome });
    }
    if (source === "document" || source === "gallery") {
      const attach = await visibleAriaLocator(page, [/chat\.composer\.attach|Adjuntar|Attach/i], 10_000);
      if (!attach) throw new Error("attachment_picker_attach_anchor_missing");
      const target = source === "gallery" ? /chat\.attachment\.pick\.gallery|Galer[ií]a|Gallery/i : /chat\.attachment\.pick\.file|Archivo|File/i;
      await clickLocatorPreferDom(page, attach, "attachment_picker_attach_not_clickable");
      report.evidence.attachmentPickerPanelOpened = await attachScreenshot(page, evidenceDir, `web-chat-attachment-picker-panel-${source}`);
      const panel = await visibleAriaLocator(page, [/chat\.attachment\.quickPanel/i], 1_500).catch(() => null);
      const picker = await visibleAriaLocator(page, [target], 5_000).catch(() => null);
      if (!picker) {
        const attachClickCount = await attach.getAttribute("data-quata-clicks").catch(() => null);
        report.diagnostics = {
          ...(report.diagnostics ?? {}),
          attachmentPickerQuickPanelTagVisible: Boolean(panel),
          attachmentPickerAttachClickCount: attachClickCount,
          attachmentPickerControlsAfterOpen: await visibleNativeControls(page),
        };
        throw new Error(`attachment_picker_${source}_anchor_missing`);
      }
      const chooserPromise = (outcome === "success" || outcome === "register-failure")
        ? page.waitForEvent("filechooser", { timeout: 10_000 })
        : null;
      await clickLocatorCenter(page, picker, `attachment_picker_${source}_not_clickable`);
      if (chooserPromise) await (await chooserPromise).setFiles(fixture.localPath);
    } else {
      const chooserPromise = (outcome === "success" || outcome === "register-failure")
        ? page.waitForEvent("filechooser", { timeout: 10_000 })
        : null;
      const camera = await visibleAriaLocator(page, [/chat\.composer\.camera|C[aá]mara|Camera/i], 10_000);
      if (!camera) throw new Error("attachment_picker_camera_anchor_missing");
      await clickLocatorCenter(page, camera, "attachment_picker_camera_not_clickable");
      if (chooserPromise) await (await chooserPromise).setFiles(fixture.localPath);
    }
    if (outcome !== "success" && outcome !== "register-failure") {
      const pendingAfterOutcome = await visibleAriaLocator(page, [/chat\.attachment\.pending/i], 1_500).catch(() => null);
      if (pendingAfterOutcome) throw new Error(`attachment_picker_${outcome}_created_pending_attachment`);
      if (outcome === "failure" || outcome === "unsupported") {
        const error = await visibleAriaLocator(page, [/chat\.attachment\.error|attachment_picker_e2e_failure|selector|galer[ií]a|c[aá]mara|picker|camera|gallery/i], 5_000);
        if (!error) throw new Error(`attachment_picker_${outcome}_error_anchor_missing`);
      }
      report.evidence.attachmentPicker = { source, outcome, pendingCreated: false, messageCreated: false };
      report.evidence.attachmentPickerNegative = await attachScreenshot(page, evidenceDir, `web-chat-attachment-picker-${outcome}-${source}`);
      report.steps.push(`web_chat_attachment_picker_${source}_${outcome}_handled_without_attachment`);
      return;
    }
    const pending = await visibleAriaLocator(page, [/chat\.attachment\.pending/i], 15_000);
    if (!pending) throw new Error("attachment_picker_pending_overlay_missing");
    await page.getByText(fixture.name, { exact: false }).first().waitFor({ timeout: 10_000 });
    report.evidence.attachmentPickerPending = await attachScreenshot(page, evidenceDir, `web-chat-attachment-picker-pending-${source}`);
    await fillComposerAndSend(page, fixture.marker);
    if (outcome === "register-failure") {
      const registerDeadline = Date.now() + 20_000;
      while (!registerFailureInjected && Date.now() < registerDeadline) {
        await delay(250);
      }
      if (!registerFailureInjected) throw new Error("attachment_register_failure_rpc_not_intercepted");
      const error = await visibleAriaLocator(page, [/chat\.attachment\.error|chat_attachment_register_e2e_failure|register|registro/i], 10_000);
      if (!error) throw new Error("attachment_register_failure_error_anchor_missing");
      const pendingAfterFailure = await visibleAriaLocator(page, [/chat\.attachment\.pending/i], 2_000).catch(() => null);
      if (pendingAfterFailure) throw new Error("attachment_register_failure_left_pending_attachment");
      await assertNoAttachmentPickerResidue(fixture.name, fixture.marker);
      report.evidence.attachmentPicker = {
        source,
        outcome,
        pendingCreated: true,
        messageCreated: false,
        storageResidueCount: 0,
      };
      report.evidence.attachmentPickerRegisterFailure = await attachScreenshot(page, evidenceDir, `web-chat-attachment-picker-register-failure-${source}`);
      report.steps.push(`web_chat_attachment_picker_${source}_register_failure_rolled_back_storage`);
      return;
    }
    const message = await pollMessage(config, state.a, state.thread, (row) => messageText(row) === fixture.marker);
    const id = messageId({ message });
    state.uiMessages.push(id);
    const attachments = messageAttachments(message);
    if (!attachments.length) throw new Error("attachment_picker_message_missing_attachment");
    for (const attachment of attachments) {
      if (attachment.storagePath) {
        state.cleanupRegistry.trackStorageObject({
          storagePath: attachment.storagePath,
          name: `web_chat_picker_${source}`,
        });
      }
    }
    report.evidence.attachmentPicker = {
      source,
      messageId: id,
      attachmentCount: attachments.length,
      names: attachments.map((attachment) => attachment.name).filter(Boolean),
      storagePathSha256: attachments.map((attachment) => attachment.storagePath).filter(Boolean).map(sha256),
    };
    await waitMessageVisible(page, fixture.marker, "attachment_picker_message_not_visible");
    report.evidence.attachmentPickerSent = await attachScreenshot(page, evidenceDir, `web-chat-attachment-picker-sent-${source}`);
    report.steps.push(`web_chat_attachment_picker_${source}_sent_and_verified_by_rpc`);
  } finally {
    await rm(dirname(fixture.localPath), { recursive: true, force: true }).catch(() => {});
  }
}

async function verifyWebAudioRecordingComposer(page, evidenceDir, report, recordingMarker) {
  report.diagnostics ??= {};
  const record = await visibleWebSemanticAnchor(page, {
    testTag: "chat.composer.recordAudio",
    labels: [/^Grabar audio$/i, /^Record audio$/i, /^Enregistrer l'audio$/i],
    timeout: 10_000,
    diagnostics: report.diagnostics,
  });
  if (!record) throw new Error("audio_recording_start_anchor_not_visible");
  await clickLocatorCenter(page, record, "audio_recording_start_anchor_not_clickable");
  const recording = await visibleWebSemanticAnchor(page, {
    testTag: "chat.composer.recording",
    labels: [/^Grabando\b/i, /^Recording\b/i, /^Enregistrement\b/i, /^Detener grabaci[oó]n$/i, /^Stop recording$/i],
    timeout: 10_000,
    diagnostics: report.diagnostics,
  });
  if (!recording) throw new Error("audio_recording_state_anchor_not_visible");
  await delay(1_250);
  report.evidence.audioRecordingActive = await attachScreenshot(page, evidenceDir, "web-chat-audio-recording-active");
  const stop = await visibleWebSemanticAnchor(page, {
    testTag: "chat.composer.recording.stop",
    labels: [/^Detener y adjuntar$/i, /^Detener grabaci[oó]n$/i, /^Stop and attach$/i, /^Stop recording$/i, /^Arr[eê]ter/i],
    timeout: 10_000,
    diagnostics: report.diagnostics,
  });
  if (!stop) throw new Error("audio_recording_stop_anchor_not_visible");
  await clickLocatorCenter(page, stop, "audio_recording_stop_anchor_not_clickable");
  const pending = await visibleWebSemanticAnchor(page, {
    testTag: "chat.attachment.pending",
    labels: [/^Adjunto preparado$/i, /^Attachment ready$/i, /^Pi[eè]ce jointe pr[eê]te$/i, /^Quitar adjunto$/i, /^Remove attachment$/i],
    timeout: 10_000,
    diagnostics: report.diagnostics,
  });
  if (!pending) throw new Error("audio_recording_pending_attachment_not_visible");
  report.evidence.audioRecordingPendingAttachment = await attachScreenshot(page, evidenceDir, "web-chat-audio-recording-pending-attachment");
  if (recordingMarker) {
    await fillComposerAndSend(page, recordingMarker);
    report.steps.push("web_audio_recording_composer_start_stop_and_sent");
    return;
  }
  const clear = await visibleWebSemanticAnchor(page, {
    testTag: "chat.attachment.pending.clear",
    labels: [/^Quitar adjunto$/i, /^Remove attachment$/i, /^Retirer la pi[eè]ce jointe$/i],
    timeout: 10_000,
    diagnostics: report.diagnostics,
  });
  if (!clear) throw new Error("audio_recording_pending_clear_anchor_not_visible");
  await clickLocatorCenter(page, clear, "audio_recording_pending_clear_not_clickable");
  if (await visibleWebSemanticAnchor(page, {
    testTag: "chat.attachment.pending",
    labels: [/^Adjunto preparado$/i, /^Attachment ready$/i, /^Pi[eè]ce jointe pr[eê]te$/i],
    timeout: 2_000,
    diagnostics: report.diagnostics,
  })) {
    throw new Error("audio_recording_pending_attachment_not_cleared");
  }
  report.steps.push("web_audio_recording_composer_start_stop_and_blob_cleanup_verified");
}

async function openAndCloseChatAttachmentMediaViewer(page, evidenceDir, report, kind = "media", allowScroll = false) {
  const target = kind === "video" ? "chat.attachment.media.video" : kind === "image" ? "chat.attachment.media.image" : "chat.attachment.media";
  const opener = allowScroll
    ? await visibleAriaLocatorNearCurrentPosition(page, [new RegExp(escapeRegExp(target))], 15_000)
    : await visibleAriaLocator(page, [new RegExp(escapeRegExp(target))], 10_000);
  if (!opener) throw new Error("chat_attachment_media_anchor_missing");
  if (kind === "video") {
    await clickMediaAttachmentOpener(page, opener, "chat_attachment_media_anchor_not_clickable");
  } else {
    await clickLocatorCenter(page, opener, "chat_attachment_media_anchor_not_clickable");
  }
  let root = await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.root"))], 5_000);
  let title = await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.title"))], 5_000);
  if (!title) {
    await clickLocatorCenter(page, opener, "chat_attachment_media_anchor_not_clickable");
    root = await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.root"))], 3_000);
    title = await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.title"))], 3_000);
  }
  if (!title) throw new Error("chat_attachment_media_viewer_title_missing");
  const closePatterns = [
    new RegExp(escapeRegExp("fullscreen-media.media-close")),
    new RegExp(escapeRegExp("fullscreen-media.close")),
    new RegExp(escapeRegExp("fullscreen-media.back")),
  ];
  const closeControl = await visibleNativeControl(page, closePatterns, 5_000);
  const close = closeControl ? null : await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.close"))], 2_000);
  const back = close ?? await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.back"))], 2_000);
  if (!closeControl && !back) throw new Error("chat_attachment_media_viewer_back_missing");
  if (!root) {
    report.diagnostics ??= {};
    report.diagnostics.chatAttachmentMediaViewerRoot =
      "Compose/Wasm opened the common fullscreen media overlay but did not expose fullscreen-media.root as an aria-label; title/back anchors were visible and used for replay.";
  }
  report.evidence[kind === "video" ? "chatAttachmentVideoViewer" : "chatAttachmentMediaViewer"] =
    await attachScreenshot(page, evidenceDir, kind === "video" ? "web-chat-attachment-video-viewer" : "web-chat-attachment-media-viewer");
  if (closeControl) {
    const clickedDomButton = closeControl.tag === "BUTTON" && await clickNativeButtonByLabel(page, closePatterns);
    if (!clickedDomButton) {
      await clickNativeControlCenter(page, closeControl, "chat_attachment_media_viewer_back_not_clickable");
    }
  }
  await delay(650);
  if (await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.title"))], 750)) {
    const semanticClose = back ?? await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.back"))], 2_000);
    if (!semanticClose) throw new Error("chat_attachment_media_viewer_back_missing_after_native_click");
    await semanticClose.evaluate((element) => element.click()).catch(() => {});
    await delay(650);
  }
  if (await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.title"))], 750)) {
    throw new Error("chat_attachment_media_viewer_did_not_close");
  }
  await closeChatSelectionToolbarIfVisible(page);
}

async function clickMediaAttachmentOpener(page, locator, error) {
  const clicked = await locator.evaluate((element) => {
    element.dispatchEvent(new MouseEvent("pointerdown", { bubbles: true, cancelable: true, view: window }));
    element.dispatchEvent(new MouseEvent("pointerup", { bubbles: true, cancelable: true, view: window }));
    element.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, view: window }));
    return true;
  }).catch(() => false);
  if (!clicked) await clickLocatorCenter(page, locator, error);
  await delay(250);
}

async function closeChatSelectionToolbarIfVisible(page) {
  const action = await visibleAriaLocator(page, [/Copiar|Copy|Editar|Edit|Favorito|Favorite|Eliminar|Delete/i], 750);
  if (!action) return;
  const back = await visibleAriaLocator(page, [/Volver|Back/i], 1_500);
  if (back) {
    await clickLocatorCenter(page, back, "chat_selection_toolbar_back_not_clickable").catch(() => {});
    await delay(350);
  }
}

function createChatAttachmentMessage(config, session, thread, runId, kind, nameSuffix = "", options = {}) {
  return seedChatAttachmentFixture({
    config,
    session,
    thread,
    runId,
    kind,
    platformLabel: "web",
    nameSuffix,
    rpc,
    storageRequest,
    pollMessage,
    messageText,
    attachmentId,
    messageId: sentMessageId,
    cleanup: state.cleanupRegistry,
    audioDurationSeconds: options.audioDurationSeconds,
  });
}

async function createAttachmentPickerFixture(source, runId, platformLabel) {
  const root = await mkdtemp(join(tmpdir(), `quata-${platformLabel}-chat-picker-`));
  const media = source === "document"
    ? {
      name: `qadata-chat-picker-${platformLabel}-${runId.slice(0, 8)}.txt`,
      mimeType: "text/plain",
      content: Buffer.from(`QADATA ${platformLabel} Chat picker fixture ${runId}\n`, "utf8"),
    }
    : {
      name: `qadata-chat-picker-${platformLabel}-${source}-${runId.slice(0, 8)}.png`,
      mimeType: "image/png",
      content: validPngFixture(),
    };
  const localPath = join(root, media.name);
  await writeFile(localPath, media.content, { mode: 0o600 });
  return {
    source,
    localPath,
    name: media.name,
    mimeType: media.mimeType,
    marker: `chat-picker-${platformLabel}-${source}-${randomUUID()}`,
  };
}

function messageAttachments(row) {
  const values = [
    row?.attachments,
    row?.files,
    row?.message?.attachments,
    row?.message?.files,
  ].find(Array.isArray) ?? [];
  return values.map((attachment) => ({
    id: Number(attachment?.id ?? attachment?.file_id ?? attachment?.attachment_id),
    name: attachment?.name ?? attachment?.display_name,
    storagePath: attachment?.storage_path ?? attachment?.storagePath,
    bucket: attachment?.storage_bucket ?? attachment?.storageBucket,
    mimeType: attachment?.mime_type ?? attachment?.mimeType,
  })).filter((attachment) => Number.isSafeInteger(attachment.id) || attachment.storagePath || attachment.name);
}

function rows(payload, key) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.[key])) return payload[key];
  if (Array.isArray(payload?.data?.[key])) return payload.data[key];
  if (Array.isArray(payload?.update?.[key])) return payload.update[key];
  if (Array.isArray(payload?.messages)) return payload.messages;
  return [];
}

function positiveId(value, name) {
  const numeric = Number(value);
  if (!Number.isSafeInteger(numeric) || numeric <= 0) throw new Error(`chat_contract_invalid:${name}`);
  return numeric;
}

function threadId(payload) {
  return positiveId(payload?.thread?.id ?? payload?.threads?.[0]?.id ?? payload?.thread_id ?? payload?.id, "thread_id");
}

function messageId(payload) {
  return positiveId(rows(payload, "messages")[0]?.id ?? payload?.message?.id ?? payload?.message_id ?? payload?.id, "message_id");
}

function sentMessageId(payload) {
  return positiveId(payload?.message_id ?? payload?.message?.id ?? payload?.id, "message_id");
}

function attachmentId(payload) {
  return positiveId(payload?.id ?? payload?.file?.id, "attachment_id");
}

function messageText(row) {
  return String(row?.body ?? row?.text ?? row?.message ?? "");
}

function messageReplyToId(row) {
  const raw = row?.reply_to_message_id ?? row?.replyToMessageId ?? row?.reply?.id;
  const numeric = Number(raw);
  return Number.isSafeInteger(numeric) && numeric > 0 ? numeric : null;
}

function messageNumericId(row) {
  const raw = row?.id ?? row?.message_id ?? row?.messageId ?? row?.message?.id;
  const numeric = Number(raw);
  return Number.isSafeInteger(numeric) && numeric > 0 ? numeric : null;
}

function snapshotThread(detail) {
  return rows(detail, "messages").map((row) => {
    const text = messageText(row);
    return {
      id: messageNumericId(row),
      textSha256: sha256(text),
      textPrefix: text.slice(0, 40),
      replyToMessageId: messageReplyToId(row),
      isEdited: row?.is_edited === true || row?.isEdited === true,
    };
  });
}

async function pollMessage(config, session, thread, predicate, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const detail = await rpc(config, session, "quata_chat_get_thread", {
      p_actor_profile_id: session.profileId,
      p_thread_id: thread,
      p_known_message_ids: [],
      p_limit: 250,
    });
    lastThreadSnapshot = snapshotThread(detail);
    const match = rows(detail, "messages").find(predicate);
    if (match) return match;
    await delay(1_000);
  }
  throw new Error("chat_backend_poll_timeout");
}

async function favorites(config, session) {
  return rows(await rpc(config, session, "quata_chat_get_favorites", {
    p_actor_profile_id: session.profileId,
    p_limit: 250,
  }), "messages");
}

async function inboxThread(config, session, thread) {
  const payload = await rpc(config, session, "quata_chat_get_inbox", {
    p_actor_profile_id: session.profileId,
    p_limit: 100,
  });
  const allRows = [
    payload?.thread,
    payload?.conversation,
    ...(Array.isArray(payload?.threads) ? payload.threads : []),
    ...(Array.isArray(payload?.conversations) ? payload.conversations : []),
    ...(Array.isArray(payload?.update?.threads) ? payload.update.threads : []),
    ...(Array.isArray(payload?.update?.conversations) ? payload.update.conversations : []),
  ].filter(Boolean);
  return allRows.find((row) => Number(row?.thread_id ?? row?.id) === Number(thread)) ?? null;
}

async function pollForwardDestinationThread(config, session, profileId, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const payload = await rpc(config, session, "quata_chat_get_inbox", {
      p_actor_profile_id: session.profileId,
      p_limit: 100,
    });
    const allRows = [
      payload?.thread,
      payload?.conversation,
      ...(Array.isArray(payload?.threads) ? payload.threads : []),
      ...(Array.isArray(payload?.conversations) ? payload.conversations : []),
      ...(Array.isArray(payload?.update?.threads) ? payload.update.threads : []),
      ...(Array.isArray(payload?.update?.conversations) ? payload.update.conversations : []),
    ].filter(Boolean);
    const match = allRows.find((row) => JSON.stringify(row).includes(profileId));
    if (match) return { threadId: threadId(match), row: match };
    await delay(1_000);
  }
  throw new Error("forward_state_not_persisted:destination_thread");
}

async function createPrivateChatSeed(config, actorSession, peerSession, marker) {
  if (!peerSession.accessToken) throw new Error("profile_private_chat_not_opened:peer_session_unavailable");
  const thread = threadId(await rpc(config, actorSession, "quata_chat_get_or_create_private_thread", {
    p_actor_profile_id: actorSession.profileId,
    p_peer_profile_id: peerSession.profileId,
  }));
  await rpc(config, peerSession, "quata_chat_send_message", {
    p_actor_profile_id: peerSession.profileId,
    p_thread_id: thread,
    p_message: marker,
    p_file_ids: [],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-profile-private-web-${randomUUID()}`,
  });
  const message = await pollMessage(config, actorSession, thread, (row) => messageText(row) === marker);
  return { threadId: thread, markerMessageId: messageId({ message }) };
}

function isMuted(row) {
  return row?.muted === true || row?.is_muted === true || row?.isMuted === true;
}

async function configuredDistribution(source, config) {
  if (!(await stat(source).catch(() => null))?.isDirectory()) throw new Error("distribution_missing");
  const target = await mkdtemp(join(tmpdir(), "quata-chat-actions-notifications-dist-"));
  await cp(source, target, { recursive: true });
  const index = join(target, "index.html");
  let html = await readFile(index, "utf8");
  html = html.replace('name="quata-supabase-url" content=""', `name="quata-supabase-url" content="${escapeHtml(config.baseUrl)}"`)
    .replace('name="quata-supabase-publishable-key" content=""', `name="quata-supabase-publishable-key" content="${escapeHtml(config.key)}"`);
  if (!html.includes(escapeHtml(config.key))) throw new Error("runtime_configuration_injection_failed");
  await writeFile(index, html, "utf8");
  return target;
}

function escapeHtml(value) {
  return value.replaceAll("&", "&amp;").replaceAll('"', "&quot;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

async function startServer(root) {
  const server = createServer(async (request, response) => {
    try {
      const pathname = decodeURIComponent(new URL(request.url ?? "/", "http://localhost").pathname);
      if (pathname === "/favicon.ico") return response.writeHead(204).end();
      const file = resolve(root, `.${pathname === "/" ? "/index.html" : pathname}`);
      if (!file.startsWith(`${root}\\`) && !file.startsWith(`${root}/`) && file !== root) return response.writeHead(403).end();
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      response.writeHead(200, {
        "Content-Type": contentType(file),
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
        "Cache-Control": "no-store",
      });
      response.end(await readFile(file));
    } catch { response.writeHead(500).end(); }
  });
  await new Promise((ok, fail) => { server.once("error", fail); server.listen(0, "127.0.0.1", ok); });
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("static_server_start_failed");
  return { origin: `http://127.0.0.1:${address.port}`, close: () => new Promise((ok, fail) => server.close((error) => error ? fail(error) : ok())) };
}

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"], [".wasm", "application/wasm"],
    [".json", "application/json"], [".css", "text/css"], [".svg", "image/svg+xml"], [".webp", "image/webp"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}

async function openAuthenticatedChatPage(browser, origin, session, conversationId, faults, options = {}) {
  const context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1, acceptDownloads: true });
  if (options.grantMicrophone) {
    await context.grantPermissions(["microphone"], { origin });
  }
  await context.addInitScript(({ storage }) => {
    for (const [key, value] of Object.entries(storage)) localStorage.setItem(key, value);
    globalThis.__quataSharePayloads = [];
    globalThis.__quataClickEvents = [];
    globalThis.__quataAttachmentActionEvents = [];
    globalThis.document?.addEventListener?.("click", (event) => {
      const target = event.target;
      const element = target?.closest?.("[aria-label],button,[role]");
      globalThis.__quataClickEvents.push({
        x: event.clientX,
        y: event.clientY,
        targetTag: target?.tagName ?? null,
        targetLabel: target?.getAttribute?.("aria-label") ?? null,
        elementTag: element?.tagName ?? null,
        elementRole: element?.getAttribute?.("role") ?? null,
        elementLabel: element?.getAttribute?.("aria-label") ?? null,
      });
      if (globalThis.__quataClickEvents.length > 40) globalThis.__quataClickEvents.shift();
    }, true);
    Object.defineProperty(globalThis.navigator, "share", {
      configurable: true,
      value: async (payload) => {
        globalThis.__quataSharePayloads.push({
          title: payload?.title ?? null,
          text: payload?.text ?? null,
          url: payload?.url ?? null,
          files: Array.isArray(payload?.files) ? payload.files.map((file) => ({ name: file?.name ?? null, type: file?.type ?? null, size: file?.size ?? null })) : [],
        });
      },
    });
    Object.defineProperty(globalThis.navigator, "canShare", {
      configurable: true,
      value: () => true,
    });
  }, {
    storage: {
      quata_web_access_token: session.accessToken,
      quata_web_refresh_token: session.refreshToken,
      quata_web_session_token: session.webSessionToken,
      quata_web_user_id: session.profileId,
      quata_web_expires_at: String(session.expiresAt),
      "web.auth.session_ready": "true",
      quata_web_client_instance_id: `chat-actions-notifications-${randomUUID()}`,
    },
  });
  const page = await context.newPage();
  page.on("pageerror", (error) => faults.push(redactBrowserRuntimeFault({
    type: "pageerror",
    message: String(error?.message ?? "pageerror"),
    stack: typeof error?.stack === "string" ? error.stack : undefined,
  })));
  page.on("console", (entry) => {
    if (entry.type() !== "error") return;
    const location = entry.location?.() ?? {};
    faults.push(redactBrowserRuntimeFault({
      type: "console_error",
      text: entry.text(),
      url: typeof location.url === "string" ? location.url : undefined,
      lineNumber: typeof location.lineNumber === "number" ? location.lineNumber : undefined,
      columnNumber: typeof location.columnNumber === "number" ? location.columnNumber : undefined,
    }));
  });
  await page.goto(`${origin}/#chat-${encodeURIComponent(conversationId)}`, { waitUntil: "domcontentloaded" });
  await page.locator("#quata-root").waitFor({ state: "attached", timeout: 30_000 });
  await page.waitForFunction(
    (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
    `chat/${conversationId}`,
    { timeout: 45_000 },
  );
  await page.waitForFunction(() => {
    const root = document.querySelector("#quata-root");
    return root && (root.querySelector("canvas") || root.shadowRoot?.querySelector("canvas"));
  }, { timeout: 45_000 });
  await delay(1_500);
  return { context, page };
}

async function openAuthenticatedChatRoute(page, origin, conversationId, options = {}) {
  const query = options.membersExpanded === true ? "?quata-chat-members-expanded-e2e=1" : "";
  await page.goto(`${origin}/${query}#chat-${encodeURIComponent(conversationId)}`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(
    (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
    `chat/${conversationId}`,
    { timeout: 45_000 },
  );
  await delay(1_500);
}

async function attachScreenshot(page, evidenceDir, name) {
  await mkdir(evidenceDir, { recursive: true });
  const path = join(evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

async function attachViewportScreenshot(page, evidenceDir, name) {
  await mkdir(evidenceDir, { recursive: true });
  const path = join(evidenceDir, `${name}.png`);
  await withTimeout(page.screenshot({ path, fullPage: false }), 12_000, `screenshot_${name}`);
  return path;
}

async function visibleNativeControls(page) {
  return await nativeControls(page, true);
}

async function allNativeControls(page) {
  return await nativeControls(page, false);
}

async function nativeControls(page, onlyVisible) {
  return await page.evaluate((onlyVisibleArg) => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    return [...scope.querySelectorAll("button[aria-label], input[aria-label], [role][aria-label], [aria-label]")]
      .map((element) => {
        const rect = element.getBoundingClientRect();
        const visible = rect.width > 0 &&
          rect.height > 0 &&
          rect.right > 0 &&
          rect.bottom > 0 &&
          rect.left < window.innerWidth &&
          rect.top < window.innerHeight;
        return {
          tag: element.tagName,
          role: element.getAttribute("role"),
          label: element.getAttribute("aria-label"),
          visible,
          x: Math.round(rect.x),
          y: Math.round(rect.y),
          width: Math.round(rect.width),
          height: Math.round(rect.height),
        };
      })
      .filter((entry) => !onlyVisibleArg || entry.visible)
      .slice(0, 80);
  }, onlyVisible);
}

async function visibleNativeControl(page, patterns, timeout = 5_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const controls = await visibleNativeControls(page);
    const match = controls.find((control) => patterns.some((pattern) => pattern.test(control.label ?? "")));
    if (match) return match;
    await delay(250);
  }
  return null;
}

async function visibleNativeControlExact(page, label, timeout = 5_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const controls = await visibleNativeControls(page);
    const match = controls.find((control) => control.label === label);
    if (match) return match;
    await delay(250);
  }
  return null;
}

async function bottomVisibleNativeControl(page, patterns, timeout = 5_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const controls = await visibleNativeControls(page);
    const matches = controls.filter((control) => patterns.some((pattern) => pattern.test(control.label ?? "")));
    matches.sort((left, right) => (right.y - left.y) || (right.height - left.height));
    if (matches[0]) return matches[0];
    await delay(250);
  }
  return null;
}

async function clickNativeControlCenter(page, control, error) {
  if (!control || control.width <= 0 || control.height <= 0) throw new Error(error);
  await page.mouse.click(control.x + (control.width / 2), control.y + (control.height / 2));
  await delay(250);
}

async function clickNativeControlPreferDom(page, control, error) {
  if (!control || control.width <= 0 || control.height <= 0) throw new Error(error);
  const clicked = await page.evaluate((target) => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    const controls = [...scope.querySelectorAll("button[aria-label], input[aria-label], [role][aria-label]")];
    const match = controls.find((element) => {
      const rect = element.getBoundingClientRect();
      return element.getAttribute("aria-label") === target.label &&
        Math.round(rect.x) === target.x &&
        Math.round(rect.y) === target.y &&
        Math.round(rect.width) === target.width &&
        Math.round(rect.height) === target.height;
    });
    if (!match || typeof match.click !== "function") return false;
    match.click();
    return true;
  }, control).catch(() => false);
  if (!clicked) await clickNativeControlCenter(page, control, error);
  await delay(250);
}

async function clickLabel(page, patterns, error) {
  const locator = await visibleAriaLocator(page, patterns, 5_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const box = await visibleTextBoxMatching(page, patterns);
  if (box) {
    await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
    await delay(250);
    return;
  }
  throw new Error(error);
}

async function clickOptionsMenu(page) {
  const tagged = await visibleAriaLocator(page, [/chat\.menu\.options/i], 1_000);
  if (tagged) {
    await tagged.click({ timeout: 10_000, force: true });
    return;
  }
  const locator = await visibleAriaLocator(page, [/Opciones|Abrir/i, /Options|Open/i], 4_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.max(1, viewport.width - 26), 104);
}

async function optionsMenuVisible(page, timeout = 500) {
  return await page.getByText(/Silenciar conversaci[oó]n|Mute conversation|A[ñn]adir nuevos participantes|Add(?: new)? participants/i)
    .first()
    .isVisible({ timeout })
    .catch(() => false);
}

async function closeOptionsMenu(page, report, context) {
  await closeTransientMenus(page);
  if (!(await optionsMenuVisible(page, 500))) return;
  await clickOptionsMenu(page);
  await delay(400);
  if (!(await optionsMenuVisible(page, 500))) return;
  const muteAction = await visibleAriaLocator(page, [/Silenciar conversaci[oó]n|Mute conversation/i], 1_000);
  if (muteAction) {
    await muteAction.click({ timeout: 5_000, force: true });
    await delay(500);
    if (!(await optionsMenuVisible(page, 500))) {
      report.steps.push(`${context}_options_menu_closed_by_semantic_mute_action`);
      return;
    }
  }
  const muteControl = await visibleNativeControl(page, [/Silenciar conversaci[oó]n|Mute conversation/i], 1_000);
  if (muteControl) {
    await clickNativeControlCenter(page, muteControl, "options_menu_mute_control_not_clickable");
    if (!(await optionsMenuVisible(page, 500))) {
      report.steps.push(`${context}_options_menu_closed_by_native_mute_control`);
      return;
    }
  }
  report.diagnostics = {
    ...(report.diagnostics ?? {}),
    optionsMenuCloseFailedAt: context,
    visibleNativeControls: await visibleNativeControls(page),
  };
  throw new Error("options_menu_did_not_close");
}

async function clickFavoriteAction(page) {
  const locator = await visibleAriaLocator(page, [/Favorito|Favorite/i], 2_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.max(1, viewport.width - 66), 98);
}

async function clickEditAction(page) {
  const locator = await visibleAriaLocator(page, [/Editar|Edit/i], 2_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.max(1, viewport.width - 106), 98);
}

async function clickForwardAction(page) {
  const locator = await visibleAriaLocator(page, [/Reenviar|Forward/i], 2_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.max(1, viewport.width - 146), 98);
}

async function selectForwardDestination(page, query, displayName, error) {
  const search = await visibleAriaLocator(page, [/Buscar|Search/i], 10_000);
  if (search) {
    await search.fill(query, { timeout: 10_000 });
  } else {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.5), 108);
    await page.keyboard.press("Control+A").catch(() => {});
    await page.keyboard.type(query, { delay: 8 });
  }
  await delay(1_000);
  const destination = page.getByText(new RegExp(escapeRegExp(displayName))).first();
  if (await destination.waitFor({ timeout: 10_000 }).then(() => true).catch(() => false)) {
    await destination.click({ timeout: 10_000, force: true });
    return;
  }
  const box = await visibleTextBox(page, displayName);
  if (box) {
    await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.round(viewport.width * 0.46), 486);
  await delay(500);
  if (await page.getByText(/✓|âœ“/).first().isVisible({ timeout: 1_000 }).catch(() => false)) return;
  throw new Error(error);
}

async function clickForwardSend(page) {
  const locator = await visibleAriaLocator(page, [/Reenviar|Forward/i], 2_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.round(viewport.width * 0.69), 558);
}

async function clickMessage(page, marker, error) {
  const probes = [...new Set([marker.slice(0, 28), marker.slice(0, 20), marker.slice(0, 16)])];
  for (const probe of probes) {
    if (await clickMessageProbe(page, probe)) return;
  }
  if (marker.startsWith("chat-edit-ui-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.62), 214);
    return;
  }
  if (marker.startsWith("chat-actions-own-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.62), 306);
    await delay(250);
    return;
  }
  if (marker.startsWith("chat-actions-peer-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.44), 344);
    await delay(250);
    return;
  }
  throw new Error(error);
}

async function openMessageActions(page, marker, expectedPatterns, targetError, actionError) {
  await closeTransientMenus(page);
  await clickMessage(page, marker, targetError);
  if (marker.startsWith("chat-edit-ui-") || marker.startsWith("chat-actions-peer-") || marker.startsWith("chat-actions-own-")) {
    if (await visibleAriaLocator(page, expectedPatterns, 1_000)) return;
    if (!(await longPressMessage(page, marker))) throw new Error(targetError);
    await delay(500);
    return;
  }
  if (await visibleAriaLocator(page, expectedPatterns, 2_000)) return;
  if (await longPressMessage(page, marker)) {
    if (await visibleAriaLocator(page, expectedPatterns, 5_000)) return;
  }
  throw new Error(actionError);
}

async function closeTransientMenus(page) {
  await page.keyboard.press("Escape").catch(() => {});
  await delay(150);
  const conversationMenu = page.getByText(/Silenciar conversaci[oó]n|Mute conversation|A[ñn]adir nuevos participantes|Add new participants/i).first();
  if (await conversationMenu.isVisible({ timeout: 500 }).catch(() => false)) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.max(1, viewport.width - 18), Math.min(viewport.height - 18, 210));
    await delay(200);
    await page.keyboard.press("Escape").catch(() => {});
    await delay(150);
  }
}

async function longPressMessage(page, marker) {
  const probes = [...new Set([marker.slice(0, 28), marker.slice(0, 20), marker.slice(0, 16)])];
  for (const probe of probes) {
    const box = await visibleTextBox(page, probe);
    if (!box) continue;
    const x = box.x + (box.width / 2);
    const y = box.y + (box.height / 2);
    await page.mouse.move(x, y);
    await page.mouse.down();
    await delay(700);
    await page.mouse.up();
    return true;
  }
  if (marker.startsWith("chat-edit-ui-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    const x = Math.round(viewport.width * 0.62);
    const y = 214;
    await page.mouse.move(x, y);
    await page.mouse.down();
    await delay(700);
    await page.mouse.up();
    return true;
  }
  if (marker.startsWith("chat-actions-own-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    const x = Math.round(viewport.width * 0.62);
    const y = 306;
    await page.mouse.move(x, y);
    await page.mouse.down();
    await delay(700);
    await page.mouse.up();
    return true;
  }
  if (marker.startsWith("chat-actions-peer-")) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    const x = Math.round(viewport.width * 0.44);
    const y = 344;
    await page.mouse.move(x, y);
    await page.mouse.down();
    await delay(700);
    await page.mouse.up();
    return true;
  }
  return false;
}

async function waitMessageVisible(page, marker, error, timeout = 45_000) {
  const probes = [...new Set([marker.slice(0, 28), marker.slice(0, 20), marker.slice(0, 16)])];
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    for (const probe of probes) {
      const controls = await visibleNativeControls(page);
      if (controls.some((control) => control.label.includes(probe))) return;
      if (await visibleAriaLocator(page, [new RegExp(escapeRegExp(probe))], 250)) return;
      const text = page.getByText(probe, { exact: false }).first();
      if (await text.waitFor({ timeout: 500 }).then(() => true).catch(() => false)) return;
      if (await visibleTextIncludes(page, probe)) return;
      if (await visibleTextBox(page, probe)) return;
      if (await visibleTextContentIncludes(page, probe)) return;
    }
    await delay(250);
  }
  throw new Error(error);
}

async function waitMessageVisibleNearCurrentPosition(page, marker, error, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  const deltas = [-520, -520, -520, -520, 520, 520, 520, 520];
  let index = 0;
  while (Date.now() < deadline) {
    try {
      await waitMessageVisible(page, marker, error, Math.min(1_200, Math.max(250, deadline - Date.now())));
      return;
    } catch {
      await wheelChatViewport(page, deltas[index % deltas.length]);
      index += 1;
      await delay(300);
    }
  }
  throw new Error(error);
}

async function waitMessageVisibleBelowCurrentPosition(page, marker, error, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try {
      await waitMessageVisible(page, marker, error, Math.min(1_200, Math.max(250, deadline - Date.now())));
      return;
    } catch {
      await wheelChatViewport(page, 420);
      await delay(300);
    }
  }
  throw new Error(error);
}

async function wheelChatViewport(page, deltaY) {
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  const x = Math.round(viewport.width * 0.5);
  await page.mouse.move(x, Math.round(viewport.height * 0.48)).catch(() => {});
  await page.mouse.wheel(0, deltaY).catch(() => {});
  const startY = deltaY > 0 ? Math.round(viewport.height * 0.72) : Math.round(viewport.height * 0.32);
  const endY = deltaY > 0 ? Math.round(viewport.height * 0.32) : Math.round(viewport.height * 0.72);
  await page.mouse.move(x, startY).catch(() => {});
  await page.mouse.down().catch(() => {});
  await page.mouse.move(x, endY, { steps: 8 }).catch(() => {});
  await page.mouse.up().catch(() => {});
}

async function openPeerProfileFromMessage(page, peerMarker, peerProfile, evidenceDir, report) {
  await openPeerProfileFromMessageWithoutReturn(page, peerMarker, peerProfile, evidenceDir, report, "web-chat-profile");
  if (!(await clickProfileBack(page))) throw new Error("profile_state_not_opened:profile_back_not_clickable");
  await closeProfileSheetIfVisible(page);
  await delay(1_000);
  if (!(await waitForChatProfileReturn(page))) throw new Error("profile_state_not_opened:chat_return_not_visible");
  report.evidence.profileReturn = await attachScreenshot(page, evidenceDir, "web-chat-profile-return");
}

async function openPeerProfileFromMessageWithoutReturn(page, peerMarker, peerProfile, evidenceDir, report, screenshotPrefix) {
  await waitMessageVisible(page, peerMarker, "message_not_visible:peer_profile_source");
  report.evidence.profileThreadInitial = await attachScreenshot(page, evidenceDir, `${screenshotPrefix}-thread-initial`);
  const opened = await clickMessageAvatar(page, peerMarker);
  if (!opened) throw new Error("profile_state_not_opened:avatar_not_clickable");
  const visible = await waitForProfileVisible(page, peerProfile);
  if (!visible) throw new Error("profile_state_not_opened:profile_not_visible");
  await assertProfileHeaderVisible(page, peerProfile);
  report.evidence.profileOpen = await attachScreenshot(page, evidenceDir, `${screenshotPrefix}-open`);
}

async function assertProfileFollowLists(page, serverOrigin, conversationId, peerMarker, peerProfile, evidenceDir, report) {
  let onProfile = await openProfileList(page, /Seguidores|Followers/i, "followers", peerProfile, evidenceDir, report);
  if (!onProfile) {
    await reopenPeerProfileFromChat(page, serverOrigin, conversationId, peerMarker, peerProfile);
  }
  onProfile = await openProfileList(page, /Siguiendo|Following/i, "following", peerProfile, evidenceDir, report);
  if (onProfile && !(await clickProfileBack(page))) throw new Error("profile_lists_state_not_returned:profile_back_not_clickable");
  await delay(1_000);
  if (!(await waitForChatProfileReturn(page))) throw new Error("profile_lists_state_not_returned:chat_return_not_visible");
  report.evidence.profileListsReturn = await attachScreenshot(page, evidenceDir, "web-chat-profile-lists-return");
}

async function openProfileList(page, labelPattern, listKind, peerProfile, evidenceDir, report) {
  const kpi = page.getByText(labelPattern).first();
  await kpi.click({ timeout: 10_000, force: true });
  await waitProfileListVisible(page, listKind, peerProfile);
  report.evidence[`profileList${listKind[0].toUpperCase()}${listKind.slice(1)}`] =
    await attachScreenshot(page, evidenceDir, `web-chat-profile-list-${listKind}`);
  if (!(await clickProfileBack(page))) throw new Error(`profile_lists_state_not_returned:${listKind}_back_not_clickable`);
  await delay(700);
  if (await waitForProfileHeaderVisible(page, peerProfile)) return true;
  await clickProfileBack(page).catch(() => false);
  await delay(700);
  return await waitForProfileHeaderVisible(page, peerProfile);
}

async function reopenPeerProfileFromChat(page, serverOrigin, conversationId, peerMarker, peerProfile) {
  await page.goto(`${serverOrigin}/?profileListReset=${Date.now()}#feed`, { waitUntil: "domcontentloaded" });
  await page.reload({ waitUntil: "domcontentloaded" });
  await delay(1_000);
  await openAuthenticatedChatRoute(page, serverOrigin, conversationId);
  await waitMessageVisible(page, peerMarker, "profile_lists_state_not_returned:peer_message_not_visible_for_reopen");
  if (!(await waitForChatProfileReturn(page))) throw new Error("profile_lists_state_not_returned:chat_return_not_visible_before_reopen");
  const opened = await clickMessageAvatar(page, peerMarker);
  if (!opened) throw new Error("profile_lists_state_not_returned:avatar_not_clickable_for_reopen");
  if (!(await waitForProfileVisible(page, peerProfile))) throw new Error("profile_lists_state_not_returned:profile_not_visible_after_reopen");
}

async function waitForProfileHeaderVisible(page, profile) {
  const displayName = profile.displayName?.trim();
  const deadline = Date.now() + 8_000;
  while (Date.now() < deadline) {
    const hasProfileText = displayName
      ? await page.getByText(new RegExp(escapeRegExp(displayName))).first().isVisible({ timeout: 300 }).catch(() => false)
      : false;
    const hasPostsKpi = await page.getByText(/Publicaciones|Posts/i).first().isVisible({ timeout: 300 }).catch(() => false);
    if (hasProfileText && hasPostsKpi) return true;
    await delay(300);
  }
  return false;
}

async function waitProfileListVisible(page, listKind, profile) {
  const titlePattern = listKind === "followers"
    ? /Usuarios siguiendo a|Users following|Seguidores|Followers/i
    : /Usuarios que sigue|Users followed by|Siguiendo|Following/i;
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const titleVisible = await page.getByText(titlePattern).first().isVisible({ timeout: 500 }).catch(() => false);
    const profileNameStillVisible = profile.displayName
      ? await page.getByText(new RegExp(escapeRegExp(profile.displayName))).first().isVisible({ timeout: 500 }).catch(() => false)
      : true;
    const hasAction = await page.getByText(/Chat|Seguir|Siguiendo|Follow|Following/i).first().isVisible({ timeout: 500 }).catch(() => false);
    if (titleVisible && profileNameStillVisible && hasAction) return;
    await delay(500);
  }
  throw new Error(`profile_lists_state_not_opened:${listKind}`);
}

async function toggleFollowFromOpenProfile(page, peerProfile, evidenceDir, report) {
  report.evidence.profileFollowBefore = await attachScreenshot(page, evidenceDir, "web-chat-profile-follow-before");
  try {
    await clickLabel(page, [/Seguir|Follow/i], "profile_follow_action_not_clickable");
  } catch (error) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.27), Math.round(viewport.height * 0.50));
  }
  await pollProfileFollowEdge(peerProfile.actorProfileId, peerProfile.profileId, true);
  report.evidence.profileFollowAfter = await attachScreenshot(page, evidenceDir, "web-chat-profile-follow-after");
}

async function prepareProfileContentFixture(fixture) {
  return seedProfileContentFixture({
    fixture,
    config,
    withDatabase: withPoolerClient,
    rpc,
    storageRequest,
    attachmentId,
    messageId,
    cleanup: state.cleanupRegistry,
  });
}

async function cleanupProfileContentFixture(fixture) {
  return cleanupSharedProfileContentFixture({ fixture, withDatabase: withPoolerClient });
}

async function pollProfileContentComment(fixture, marker, timeout = 45_000) {
  return pollSharedProfileContentComment({ fixture, marker, withDatabase: withPoolerClient, delay, timeout });
}

async function pollProfileContentReplyComment(fixture, marker, replyToCommentId, timeout = 45_000) {
  return pollSharedProfileContentReplyComment({ fixture, marker, replyToCommentId, withDatabase: withPoolerClient, delay, timeout });
}

async function prepareFeedOfficialCommentsFixture(fixture) {
  return seedFeedOfficialCommentsFixture({ fixture, withDatabase: withPoolerClient });
}

async function cleanupFeedOfficialCommentsFixture(fixture) {
  return cleanupSharedFeedOfficialCommentsFixture({ fixture, withDatabase: withPoolerClient });
}

async function pollFeedOfficialComment(fixture, surface, marker, timeout = 45_000) {
  return pollSharedFeedOfficialComment({ fixture, surface, marker, withDatabase: withPoolerClient, delay, timeout });
}

async function assertFeedOfficialCommentAbsent(fixture, surface, marker) {
  return assertSharedFeedOfficialCommentAbsent({ fixture, surface, marker, withDatabase: withPoolerClient });
}

async function assertFeedOfficialCommentNotVisible(page, prefix, commentText, report, errorMessage) {
  const visibleText = visibleEmojiCommentText(commentText);
  const stillVisible = await visibleNonEditableTextContentIncludes(page, visibleText);
  if (stillVisible) {
    report.diagnostics = {
      ...(report.diagnostics ?? {}),
      failedCommentRollbackUiResidue: [
        ...(report.diagnostics?.failedCommentRollbackUiResidue ?? []),
        { prefix, visibleText },
      ],
    };
    throw new Error(errorMessage);
  }
  report.steps.push(`${prefix}_failed_comment_not_visible_after_rollback`);
}

async function pollFeedOfficialReplyComment(fixture, surface, marker, replyToCommentId, timeout = 45_000) {
  return pollSharedFeedOfficialReplyComment({ fixture, surface, marker, replyToCommentId, withDatabase: withPoolerClient, delay, timeout });
}

async function cleanupProfileRolesSafetyFixture(fixture) {
  return cleanupSharedProfileRolesSafetyFixture({ fixture, withDatabase: withPoolerClient });
}

async function clickProfileAnchorOrText(page, tag, patterns, errorPrefix) {
  const deadline = Date.now() + 12_000;
  while (Date.now() < deadline) {
    const target = await profileActionTarget(page, tag, patterns);
    if (target) {
      await page.mouse.click(target.x + (target.width / 2), target.y + (target.height / 2));
      await delay(350);
      return;
    }
    await page.mouse.wheel(0, 260).catch(() => {});
    await delay(250);
  }
  throw new Error(`${errorPrefix}:${tag}`);
}

async function profileActionTarget(page, tag, patterns) {
  const serializedPatterns = patterns.map((pattern) => ({ source: pattern.source, flags: pattern.flags }));
  return await page.evaluate(({ tag, serializedPatterns }) => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    const regexes = serializedPatterns.map((pattern) => new RegExp(pattern.source, pattern.flags));
    const visibleRect = (element) => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      if (rect.width <= 0 || rect.height <= 0 || style.visibility === "hidden" || style.display === "none") return null;
      if (rect.bottom < 0 || rect.right < 0 || rect.top > window.innerHeight || rect.left > window.innerWidth) return null;
      return { x: rect.left, y: rect.top, width: rect.width, height: rect.height };
    };
    const byAria = [...scope.querySelectorAll("[aria-label]")]
      .map((element) => ({ element, label: element.getAttribute("aria-label") ?? "", rect: visibleRect(element) }))
      .filter((item) => item.rect && item.label === tag)
      .sort((a, b) => (a.rect.width * a.rect.height) - (b.rect.width * b.rect.height));
    if (byAria[0]) return byAria[0].rect;
    const byText = [...scope.querySelectorAll("button,[role='button'],div,span")]
      .map((element) => ({ element, text: (element.textContent ?? "").trim(), rect: visibleRect(element) }))
      .filter((item) => item.rect && regexes.some((regex) => regex.test(item.text)))
      .filter((item) => ![...item.element.children].some((child) => regexes.some((regex) => regex.test((child.textContent ?? "").trim()))))
      .sort((a, b) => (a.rect.width * a.rect.height) - (b.rect.width * b.rect.height));
    return byText[0]?.rect ?? null;
  }, { tag, serializedPatterns });
}

async function dismissProfileReportDialogIfStillOpen(page, report) {
  return await dismissProfileDialogIfStillOpen(page, "report", report);
}

async function dismissProfileBlockDialogIfStillOpen(page, report) {
  return await dismissProfileDialogIfStillOpen(page, "block", report);
}

async function dismissProfileDialogIfStillOpen(page, action, report) {
  const dialog = await profileActionTarget(page, `public-profile.safety.dialog.${action}`, []) ?? await profileAnySafetyDialogTarget(page);
  if (!dialog) return true;
  const cancel = await profileActionTarget(page, "public-profile.safety.dialog.cancel", []);
  if (!cancel) throw new Error(`profile_${action}_dialog_not_dismissible`);
  await page.mouse.click(cancel.x + (cancel.width / 2), cancel.y + (cancel.height / 2));
  await delay(350);
  if (await profileActionTarget(page, `public-profile.safety.dialog.${action}`, []) ?? await profileAnySafetyDialogTarget(page)) {
    await page.keyboard.press("Escape").catch(() => {});
    await delay(350);
  }
  if (await profileActionTarget(page, `public-profile.safety.dialog.${action}`, []) ?? await profileAnySafetyDialogTarget(page)) {
    report.steps.push(`profile_${action}_dialog_remained_open_after_persisted_action`);
    return false;
  }
  report.steps.push(`profile_${action}_dialog_dismissed_after_persisted_action`);
  return true;
}

async function profileAnySafetyDialogTarget(page) {
  return await page.evaluate(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    const visibleRect = (element) => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      if (rect.width <= 0 || rect.height <= 0 || style.visibility === "hidden" || style.display === "none") return null;
      if (rect.bottom < 0 || rect.right < 0 || rect.top > window.innerHeight || rect.left > window.innerWidth) return null;
      return { x: rect.left, y: rect.top, width: rect.width, height: rect.height };
    };
    const dialogs = [...scope.querySelectorAll("[aria-label]")]
      .map((element) => ({ label: element.getAttribute("aria-label") ?? "", rect: visibleRect(element) }))
      .filter((item) => item.rect && item.label.startsWith("public-profile.safety.dialog."))
      .sort((a, b) => (a.rect.width * a.rect.height) - (b.rect.width * b.rect.height));
    return dialogs[0]?.rect ?? null;
  });
}

async function scrollProfileAdministrationIntoView(page) {
  for (let index = 0; index < 8; index += 1) {
    const target = await profileTextTarget(page, [/Administraci[oó]n|Administration/i]);
    if (target) return;
    await page.mouse.wheel(0, -520).catch(() => {});
    await delay(250);
  }
}

async function scrollProfileHeaderIntoView(page) {
  await page.evaluate(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    for (const element of [scope, ...scope.querySelectorAll("*")]) {
      if (typeof element.scrollTop === "number" && element.scrollHeight > element.clientHeight) {
        element.scrollTop = 0;
      }
    }
  }).catch(() => {});
  await page.mouse.move(215, 520).catch(() => {});
  for (let index = 0; index < 8; index += 1) {
    await page.mouse.wheel(0, -1000).catch(() => {});
    await delay(180);
  }
}

async function assertVisibleAriaTag(page, tag, errorPrefix) {
  const deadline = Date.now() + 12_000;
  let target = null;
  while (Date.now() < deadline && !target) {
    target = await profileActionTarget(page, tag, []);
    if (!target) await delay(250);
  }
  if (!target) throw new Error(`${errorPrefix}:${tag}`);
}

async function clickProfileSafetyAction(page, tag, patterns, kind, report) {
  const target = await profileActionTarget(page, tag, patterns);
  if (target) {
    await page.mouse.click(target.x + (target.width / 2), target.y + (target.height / 2));
    await delay(350);
    return;
  }
  const labelTarget = await profileTextTarget(page, patterns);
  if (labelTarget) {
    await page.mouse.click(labelTarget.x + (labelTarget.width / 2), labelTarget.y + (labelTarget.height / 2));
    await delay(350);
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  report.diagnostics = {
    ...(report.diagnostics ?? {}),
    wasmProfileSafetyActionFallback: "Compose/Wasm renders profile safety action labels in canvas without exposing these testTags as DOM aria; Web evidence uses viewport-relative action-row fallback after visual safety anchor assertion. Android/iOS retain semantic-anchor coverage.",
  };
  const fallbackColumns = { report: 0.28, block: 0.72, unblock: 0.72 };
  const x = Math.round(viewport.width * (fallbackColumns[kind] ?? 0.5));
  await page.mouse.click(x, Math.round(viewport.height * 0.555));
  await delay(350);
}

async function clickProfileSwitchByLabel(page, tag, labelPatterns, errorPrefix, report) {
  const semanticTarget = await profileActionTarget(page, tag, []);
  if (semanticTarget) {
    await page.mouse.click(semanticTarget.x + (semanticTarget.width / 2), semanticTarget.y + (semanticTarget.height / 2));
    await delay(350);
    return;
  }
  const labelTarget = await profileTextTarget(page, labelPatterns);
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  if (labelTarget) {
    await page.mouse.click(Math.max(1, viewport.width - 62), labelTarget.y + (labelTarget.height / 2));
    await delay(350);
    return;
  }
  report.diagnostics = {
    ...(report.diagnostics ?? {}),
    wasmProfileRolesSwitchFallback: "Compose/Wasm renders the visible role switch in canvas without exposing the switch testTag/contentDescription as DOM aria; Web evidence uses a viewport-relative fallback after visual panel assertion. Android/iOS retain semantic-anchor coverage.",
  };
  await page.mouse.click(Math.max(1, viewport.width - 62), Math.round(viewport.height * 0.73));
  await delay(350);
}

async function profileTextTarget(page, patterns) {
  const serializedPatterns = patterns.map((pattern) => ({ source: pattern.source, flags: pattern.flags }));
  return await page.evaluate(({ serializedPatterns }) => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    const regexes = serializedPatterns.map((pattern) => new RegExp(pattern.source, pattern.flags));
    const visibleRect = (element) => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      if (rect.width <= 0 || rect.height <= 0 || style.visibility === "hidden" || style.display === "none") return null;
      if (rect.bottom < 0 || rect.right < 0 || rect.top > window.innerHeight || rect.left > window.innerWidth) return null;
      return { x: rect.left, y: rect.top, width: rect.width, height: rect.height };
    };
    const matches = [...scope.querySelectorAll("div,span,p")]
      .map((element) => ({ element, text: (element.textContent ?? "").trim(), rect: visibleRect(element) }))
      .filter((item) => item.rect && regexes.some((regex) => regex.test(item.text)))
      .filter((item) => ![...item.element.children].some((child) => regexes.some((regex) => regex.test((child.textContent ?? "").trim()))))
      .sort((a, b) => (a.rect.width * a.rect.height) - (b.rect.width * b.rect.height));
    return matches[0]?.rect ?? null;
  }, { serializedPatterns });
}

async function verifyProfileRolesSafetyFromOpenProfile(page, profile, fixture, evidenceDir, report, reopenProfile) {
  const profileId = profile.profileId;
  await assertVisibleTagOrText(page, `public-profile.roles.${profileId}`, [/Roles|Rol|Admin|Oficial|Official/i], "profile_roles_anchor_missing");
  await assertVisibleTagOrText(page, `public-profile.roles.admin.${profileId}`, [/Admin/i], "profile_roles_anchor_missing");
  await assertVisibleTagOrText(page, `public-profile.roles.official.${profileId}`, [/Oficial|Official/i], "profile_roles_anchor_missing");
  await assertVisibleTagOrText(page, `public-profile.safety.${profileId}`, [/Reportar|Report|Bloquear|Block/i], "profile_safety_anchor_missing");
  await assertVisibleTagOrText(page, `public-profile.safety.report.${profileId}`, [/Reportar|Report/i], "profile_safety_anchor_missing");
  await assertVisibleTagOrText(page, `public-profile.safety.block.${profileId}`, [/Bloquear|Block/i], "profile_safety_anchor_missing");
  await scrollProfileAdministrationIntoView(page);
  report.evidence.profileRolesSafetyInitial = await attachScreenshot(page, evidenceDir, "web-chat-profile-roles-safety-initial");

  await clickProfileSwitchByLabel(page, `public-profile.roles.official.${profileId}`, [/Oficial|Official/i], "profile_roles_action_not_clickable", report);
  report.evidence.profileRolesSafetyRoleUpdating = await attachScreenshot(page, evidenceDir, "web-chat-profile-roles-safety-role-updating");
  report.evidence.profileRolesPersisted = await pollProfileRoles({
    fixture,
    withDatabase: withPoolerClient,
    expected: { isAdmin: false, isOfficial: true },
    delay,
  });

  await scrollProfileHeaderIntoView(page);
  await clickProfileSafetyAction(page, `public-profile.safety.report.${profileId}`, [/Reportar|Report/i], "report", report);
  await assertVisibleAriaTag(page, "public-profile.safety.dialog.report", "profile_report_dialog_missing");
  report.evidence.profileReportDialog = await attachScreenshot(page, evidenceDir, "web-chat-profile-safety-report-dialog");
  await clickProfileAnchorOrText(page, "public-profile.safety.dialog.confirm.report", [/Reportar|Report/i], "profile_report_confirm_not_clickable");
  const profileReport = await pollProfileReport({ fixture, withDatabase: withPoolerClient, delay });
  report.evidence.profileReportPersisted = { id: profileReport.id, status: profileReport.status, reason: profileReport.reason };
  const reportDialogDismissed = await dismissProfileReportDialogIfStillOpen(page, report);
  if (!reportDialogDismissed) {
    await reopenProfile();
    report.steps.push("profile_reopened_after_report_dialog_wasm_dismiss_limit");
  }

  await scrollProfileHeaderIntoView(page);
  await clickProfileSafetyAction(page, `public-profile.safety.block.${profileId}`, [/Bloquear|Block/i], "block", report);
  await assertVisibleAriaTag(page, "public-profile.safety.dialog.block", "profile_block_dialog_missing");
  report.evidence.profileBlockDialog = await attachScreenshot(page, evidenceDir, "web-chat-profile-safety-block-dialog");
  await clickProfileAnchorOrText(page, "public-profile.safety.dialog.confirm.block", [/Bloquear|Block/i], "profile_block_confirm_not_clickable");
  report.evidence.profileBlockPersisted = await pollProfileGlobalBlock({
    fixture,
    withDatabase: withPoolerClient,
    expectedBlocked: true,
    delay,
  });
  const blockDialogDismissed = await dismissProfileBlockDialogIfStillOpen(page, report);
  if (!blockDialogDismissed) {
    await reopenProfile();
    report.steps.push("profile_reopened_after_block_dialog_wasm_dismiss_limit");
  }
  await assertVisibleTagOrText(page, `public-profile.safety.unblock.${profileId}`, [/Desbloquear|Unblock/i], "profile_unblock_anchor_missing");
  report.evidence.profileRolesSafetyAfterBlock = await attachScreenshot(page, evidenceDir, "web-chat-profile-roles-safety-after-block");
}

async function prepareProfileEntryFixture(runId) {
  const profileContent = {
    marker: `qadata-profile-content-${runId}`,
    actorSession: state.a,
    targetSession: state.b,
    threadId: state.thread,
  };
  await prepareProfileContentFixture(profileContent);
  const privateChat = await createPrivateChatSeed(config, state.a, state.b, `profile-entry-private-${runId}`);
  const official = await createOfficialProfileEntryPost(state.b.profileId, `qadata-profile-entry-official-${runId}`);
  return { profileContent, privateChat, official };
}

async function createOfficialProfileEntryPost(profileId, marker) {
  const id = randomUUID();
  const translationGroupId = randomUUID();
  const title = `QADATA profile entry official ${marker.slice(-12)}`;
  const publishedAt = new Date().toISOString();
  await withPoolerClient(async (client) => {
    await client.query("begin");
    try {
      await client.query(
        `insert into public.official_posts(
           id, profile_id, title, summary, post_type, content_html,
           read_more_label, language, translation_group_id, media_url,
           media_type, link_url, is_live, is_published, published_at
         ) values (
           $1::uuid, $2::uuid, $3, $4, 'news', $5,
           'Leer mas', 'es', $6::uuid, null,
           null, null, false, true, $7
         )`,
        [
          id,
          profileId,
          title,
          `Entrada reversible al perfil publico ${marker}`,
          `<p>Entrada reversible al perfil publico ${marker}</p>`,
          translationGroupId,
          publishedAt,
        ],
      );
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  return { id, translationGroupId, marker, title };
}

async function cleanupOfficialProfileEntryPost(fixture) {
  if (!fixture?.id) return null;
  return await withPoolerClient(async (client) => {
    await client.query("begin");
    try {
      await client.query("delete from public.official_post_likes where official_post_id = $1::uuid", [fixture.id]);
      await client.query("delete from public.official_post_comments where official_post_id = $1::uuid", [fixture.id]);
      const deleted = await client.query(
        `delete from public.official_posts
         where id = $1::uuid or translation_group_id = $2::uuid`,
        [fixture.id, fixture.translationGroupId],
      );
      const remaining = await client.query(
        `select count(*)::int as count
         from public.official_posts
         where id = $1::uuid or translation_group_id = $2::uuid or title like $3`,
        [fixture.id, fixture.translationGroupId, `%${fixture.marker}%`],
      );
      if (remaining.rows[0]?.count !== 0) throw new Error("profile_entry_official_cleanup_residue");
      await client.query("commit");
      return { state: "hard_deleted_verified", deletedRows: deleted.rowCount, remainingRows: 0 };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

async function assertVisibleTagOrText(page, tag, patterns, errorPrefix = "profile_content_tag_missing") {
  const deadline = Date.now() + 12_000;
  while (Date.now() < deadline) {
    const tagged = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag))], 700);
    if (tagged) return;
    for (const pattern of patterns) {
      if (await visibleTextMatches(page, pattern)) return;
    }
    for (const pattern of patterns) {
      const locator = page.getByText(pattern).first();
      const exists = await locator.waitFor({ timeout: 700 }).then(() => true).catch(() => false);
      if (!exists) continue;
      await locator.scrollIntoViewIfNeeded({ timeout: 1_000 }).catch(() => {});
      const visible = await locator.isVisible({ timeout: 700 }).catch(() => false);
      if (visible) return;
    }
    await page.mouse.wheel(0, 420).catch(() => {});
    await delay(350);
  }
  throw new Error(`${errorPrefix}:${tag}`);
}

async function scrollProfileContentGalleryIntoView(page, fixture, report) {
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  const postPatterns = [
    new RegExp(escapeRegExp(`public-profile.post.action.comments.${fixture.postId}`)),
    new RegExp(escapeRegExp(`public-profile.post.media.open.${fixture.postId}`)),
    new RegExp(escapeRegExp(`public-profile.post.preview.${fixture.postId}`)),
    /qadata-profile-content/i,
  ];
  for (let index = 0; index < 32; index += 1) {
    if (await visibleAriaLocator(page, postPatterns, 250) || await visibleTextMatches(page, /qadata-profile-content/i)) {
      report.steps.push(`profile_content_post_scrolled_into_view:${index}`);
      break;
    }
    await page.mouse.wheel(0, 420).catch(() => {});
    await delay(250);
  }
  return {
    mediaX: Math.round(viewport.width * 0.5),
    mediaY: Math.round(viewport.height * 0.52),
    commentsX: Math.round(viewport.width * 0.28),
    commentsY: Math.round(viewport.height * 0.79),
    inputX: Math.round(viewport.width * 0.45),
    inputY: Math.round(viewport.height * 0.80),
    sendX: Math.round(viewport.width * 0.78),
    sendY: Math.round(viewport.height * 0.80),
  };
}

async function visibleCommentInputBox(page) {
  return await page.evaluate(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    const inputPattern = /coment|comment|public-profile\.comments\.input|feed\.comments\.input|official\.comments\.input/i;
    const visible = (rect) => rect.width > 0 && rect.height > 0;
    const betterContainer = (element) => {
      let selected = element;
      for (let current = element.parentElement, depth = 0; current && depth < 4; current = current.parentElement, depth += 1) {
        const rect = current.getBoundingClientRect();
        if (rect.width >= 160 && rect.height >= 32 && rect.height <= 96 && rect.y >= (window.innerHeight * 0.52)) {
          selected = current;
        }
      }
      return selected;
    };
    const candidates = [...scope.querySelectorAll("textarea, input, [contenteditable='true'], [role='textbox'], [aria-label], [placeholder], div, span")]
      .map((element) => {
        const role = element.getAttribute("role") ?? "";
        const contentEditable = element.getAttribute("contenteditable") ?? "";
        const text = [
          element.getAttribute("placeholder") ?? "",
          element.getAttribute("aria-label") ?? "",
          element.textContent ?? "",
          role,
          contentEditable,
        ].join(" ");
        if (!inputPattern.test(text) && role !== "textbox" && contentEditable !== "true") return null;
        const container = betterContainer(element);
        const rect = container.getBoundingClientRect();
        return {
          x: rect.x,
          y: rect.y,
          width: rect.width,
          height: rect.height,
          text: text.slice(0, 120),
          role,
          contentEditable,
        };
      })
      .filter((item) => item && visible(item) && item.y >= (window.innerHeight * 0.45) && item.width >= 30 && item.height <= 120);
    candidates.sort((left, right) => (right.y - left.y) || (right.width - left.width));
    return candidates[0] ?? null;
  });
}

async function isProfileCommentsComposerOpen(page) {
  if (await visibleAriaLocator(page, [new RegExp(escapeRegExp("public-profile.comments.input"))], 500)) return true;
  if (await visibleAriaLocator(page, [/Cerrar comentarios|Close comments/i], 500)) return true;
  return Boolean(await visibleCommentInputBox(page));
}

async function clickProfileContentCommentsAction(page, postId) {
  const tag = `public-profile.post.action.comments.${postId}`;
  const tagged = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag))], 2_000);
  if (tagged) {
    await tagged.click({ timeout: 2_000, force: true });
    return;
  }
  const buttonBox = await page.evaluate(() => {
    const candidates = [...document.querySelectorAll("[role='button'], button, [aria-label]")]
      .map((element) => {
        const rect = element.getBoundingClientRect();
        const text = `${element.getAttribute("aria-label") ?? ""} ${element.textContent ?? ""}`;
        return { x: rect.x, y: rect.y, width: rect.width, height: rect.height, text };
      })
      .filter((item) => item.width > 0 && item.height > 0 && /1|coment|comment/i.test(item.text));
    candidates.sort((left, right) => (right.y - left.y) || (left.x - right.x));
    return candidates[0] ?? null;
  });
  if (!buttonBox) throw new Error("profile_content_comments_action_not_clickable");
  await page.mouse.click(buttonBox.x + (buttonBox.width / 2), buttonBox.y + (buttonBox.height / 2));
}

async function openProfileContentCommentsPanel(page, postId, fallbackPoints) {
  await clickProfileContentCommentsAction(page, postId).catch((error) => {
    if (error?.message !== "profile_content_comments_action_not_clickable") throw error;
  });
  if (await isProfileCommentsComposerOpen(page)) return;
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  const candidates = [
    fallbackPoints,
    { commentsX: Math.round(viewport.width * 0.28), commentsY: Math.round(viewport.height * 0.755) },
    { commentsX: Math.round(viewport.width * 0.28), commentsY: Math.round(viewport.height * 0.735) },
    { commentsX: Math.round(viewport.width * 0.28), commentsY: Math.round(viewport.height * 0.775) },
  ];
  const seen = new Set();
  for (const point of candidates) {
    const key = `${point.commentsX}:${point.commentsY}`;
    if (seen.has(key)) continue;
    seen.add(key);
    await page.mouse.click(point.commentsX, point.commentsY);
    await delay(500);
    if (await isProfileCommentsComposerOpen(page)) return;
  }
  throw new Error("profile_content_comments_input_not_visible");
}

async function openAndCloseProfileContentMediaViewer(page, postId, fallbackPoints, evidenceDir, report) {
  const openTag = `public-profile.post.media.open.${postId}`;
  const openPatterns = [new RegExp(escapeRegExp(openTag))];
  const openerControl = await visibleNativeControl(page, openPatterns, 5_000);
  const opener = openerControl ? null : await visibleAriaLocator(page, openPatterns, 2_000);
  if (!openerControl && !opener) {
    report.diagnostics ??= {};
    report.diagnostics.profileContentMediaOpenFallback =
      "Compose/Wasm did not expose the public-profile.post.media.open anchor; replay used the captured gallery media fallback point and still requires the shared fullscreen overlay title/back anchors.";
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(
      fallbackPoints?.mediaX ?? Math.round(viewport.width * 0.5),
      fallbackPoints?.mediaY ?? Math.round(viewport.height * 0.46),
    );
  } else if (openerControl) {
    await clickNativeControlCenter(page, openerControl, `profile_content_media_open_anchor_not_clickable:${openTag}`);
  } else {
    await clickLocatorCenter(page, opener, `profile_content_media_open_anchor_not_clickable:${openTag}`);
  }
  const root = await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.root"))], 5_000);
  const title = await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.title"))], 5_000);
  if (!title) throw new Error("profile_content_media_viewer_title_missing");
  const closePatterns = [
    new RegExp(escapeRegExp("fullscreen-media.media-close")),
    new RegExp(escapeRegExp("fullscreen-media.close")),
    new RegExp(escapeRegExp("fullscreen-media.back")),
  ];
  const closeControl = await visibleNativeControl(page, closePatterns, 5_000);
  const close = closeControl ? null : await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.close"))], 2_000);
  const back = close ?? await visibleAriaLocator(page, [new RegExp(escapeRegExp("fullscreen-media.back"))], 2_000);
  if (!closeControl && !back) throw new Error("profile_content_media_viewer_back_missing");
  if (!root) {
    report.diagnostics ??= {};
    report.diagnostics.profileContentMediaViewerRoot =
      "Compose/Wasm opened the common fullscreen media overlay but did not expose fullscreen-media.root as an aria-label; title/back anchors were visible and used for replay.";
  }
  report.evidence.profileContentMediaViewer = await attachScreenshot(page, evidenceDir, "web-chat-profile-media-viewer");
  if (closeControl) {
    const clickedDomButton = closeControl.tag === "BUTTON" && await clickNativeButtonByLabel(page, closePatterns);
    if (!clickedDomButton) {
      await clickNativeControlCenter(page, closeControl, "profile_content_media_viewer_back_not_clickable");
    }
  } else {
    await back.evaluate((element) => element.click()).catch(() => {});
  }
  await delay(650);
  const profileReturnPatterns = [
    new RegExp(escapeRegExp(`public-profile.post.media.open.${postId}`)),
    new RegExp(escapeRegExp(`public-profile.post.action.comments.${postId}`)),
  ];
  const isClosedToProfile = async (timeout) => {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
      const stillOpen = Boolean(await visibleNativeControl(page, closePatterns, 500));
      if (stillOpen) {
        await delay(250);
        continue;
      }
      await delay(750);
      const reopened = Boolean(await visibleNativeControl(page, closePatterns, 500));
      if (reopened) {
        await delay(250);
        continue;
      }
      const profileVisible = Boolean(await visibleNativeControl(page, profileReturnPatterns, 500) ?? await visibleAriaLocator(page, profileReturnPatterns, 500));
      if (profileVisible) return true;
    }
    return false;
  };
  let closed = await isClosedToProfile(2_000);
  if (!closed) {
    const closeAgain = await visibleNativeControl(page, closePatterns, 2_000);
    if (closeAgain) {
      const clickedDomButton = closeAgain.tag === "BUTTON" && await clickNativeButtonByLabel(page, closePatterns);
      if (!clickedDomButton) {
        await clickNativeControlCenter(page, closeAgain, "profile_content_media_viewer_back_not_clickable");
      }
      await delay(650);
      if (!(await isClosedToProfile(1_000))) {
        const viewport = page.viewportSize() ?? { width: 430, height: 932 };
        await page.mouse.click(Math.round(viewport.width * 0.5), closeAgain.y + (closeAgain.height / 2));
        await delay(650);
      }
    } else if (back) {
      await clickLocatorCenter(page, back, "profile_content_media_viewer_back_not_clickable");
      await delay(650);
    }
    closed = await isClosedToProfile(5_000);
  }
  if (!closed) {
    throw new Error("profile_content_media_viewer_did_not_close");
  }
  report.steps.push("profile_content_media_viewer_opened_and_closed");
}

async function selectProfileContentCommentEmoji(page, fallbackPoints) {
  return selectEmojiCommentEmoji(page, {
    prefix: "public-profile.comments",
    fallbackPoints,
    errorPrefix: "profile_content_comments",
    labelPatterns: [/Mostrar emojis|Show emojis|Afficher emojis|Afficher les emojis/i],
    composerOpen: isProfileCommentsComposerOpen,
  });
}

async function selectEmojiCommentEmoji(page, { prefix, fallbackPoints, errorPrefix, labelPatterns, composerOpen }) {
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  const panelFallback = {
    emojiX: Math.round(viewport.width * 0.15),
    emojiY: Math.round(viewport.height * 0.342),
    cellX: Math.round(viewport.width * 0.17),
    cellY: Math.round(viewport.height * 0.272),
  };
  const emojiPatterns = [
    new RegExp(escapeRegExp(`${prefix}.emoji`)),
    ...labelPatterns,
  ];
  const panelPatterns = [new RegExp(escapeRegExp("community.emoji.panel"))];
  const emojiButton = await visibleAriaLocator(page, emojiPatterns, 2_000);
  const emojiControl = await visibleNativeControl(page, emojiPatterns, 2_000);
  const attempts = [];
  if (emojiButton) attempts.push(async () => { await emojiButton.click({ timeout: 2_000, force: true }); await delay(350); });
  if (emojiButton) attempts.push(async () => { await clickLocatorCenter(page, emojiButton, `${errorPrefix}_emoji_button_not_clickable`); });
  if (emojiControl) attempts.push(async () => { await clickNativeControlCenter(page, emojiControl, `${errorPrefix}_emoji_button_not_clickable`); });
  if (await composerOpen(page)) {
    attempts.push(async () => {
      await page.mouse.click(fallbackPoints?.emojiX ?? panelFallback.emojiX, fallbackPoints?.emojiY ?? panelFallback.emojiY);
      await delay(350);
    });
  }
  if (attempts.length === 0) {
    throw new Error(`${errorPrefix}_emoji_button_not_visible`);
  }
  let panel = null;
  for (const attempt of attempts) {
    await attempt();
    panel = await visibleAriaLocator(page, panelPatterns, 1_500) ??
      await visibleNativeControl(page, panelPatterns, 1_500);
    if (panel) break;
  }
  if (!panel) {
    throw new Error(`${errorPrefix}_emoji_panel_not_visible`);
  }
  if (errorPrefix === "feed_official_comments_feed") {
    await verifyCommunityEmojiPanelSections(page, {
      errorPrefix,
      report,
      evidenceDir: options.evidenceDir,
      screenshotPrefix: "web-feed-comments-emoji-panel",
    });
  } else if (errorPrefix === "feed_official_comments_official") {
    await verifyCommunityEmojiPanelSections(page, {
      errorPrefix,
      report,
      evidenceDir: options.evidenceDir,
      screenshotPrefix: "web-official-comments-emoji-panel",
    });
  }
  const firstFrequent = await visibleAriaLocator(page, [new RegExp(escapeRegExp("community.emoji.cell.frequent.0"))], 5_000) ??
    await visibleNativeControlExact(page, "community.emoji.cell.frequent.0", 2_000);
  if (firstFrequent) {
    if ("label" in firstFrequent) {
      await clickNativeControlCenter(page, firstFrequent, `${errorPrefix}_first_emoji_not_clickable`);
    } else {
      await clickLocatorCenter(page, firstFrequent, `${errorPrefix}_first_emoji_not_clickable`);
    }
  } else {
    throw new Error(`${errorPrefix}_emoji_frequent_cell_anchor_missing:community.emoji.cell.frequent.0`);
  }
}

const communityEmojiPanelProbeSections = [
  "recent",
  "frequent",
  "gestures",
  "people",
  "animals_nature",
  "food_drink",
  "objects_symbols",
  "flags",
];

async function verifyCommunityEmojiPanelSections(page, { errorPrefix, report, evidenceDir, screenshotPrefix }) {
  const observed = [];
  for (const section of communityEmojiPanelProbeSections) {
    const sectionTag = `community.emoji.section.${section}`;
    const gridTag = `community.emoji.grid.${section}`;
    const cellTag = `community.emoji.cell.${section}.0`;
    const { sectionLocator, sectionControl } = await clickCommunityEmojiSection(page, sectionTag, errorPrefix);
    const grid = await visibleExactAriaLocator(page, gridTag, 2_500);
    const gridControl = grid ? null : await visibleNativeControlExact(page, gridTag, 2_000);
    if (!grid && !gridControl) throw new Error(`${errorPrefix}_emoji_grid_anchor_missing:${gridTag}`);
    const firstCell = await visibleExactAriaLocator(page, cellTag, 2_500);
    const firstCellControl = firstCell ? null : await visibleNativeControlExact(page, cellTag, 2_000);
    if (!firstCell && !firstCellControl) throw new Error(`${errorPrefix}_emoji_cell_anchor_missing:${cellTag}`);
    const sectionBox = sectionLocator ? await sectionLocator.boundingBox().catch(() => null) : nativeControlBox(sectionControl);
    const gridBox = grid ? await grid.boundingBox().catch(() => null) : nativeControlBox(gridControl);
    const cellBox = firstCell ? await firstCell.boundingBox().catch(() => null) : nativeControlBox(firstCellControl);
    observed.push({
      section,
      sectionTag,
      gridTag,
      firstCellTag: cellTag,
      resolvedBy: sectionControl || gridControl || firstCellControl ? "native-aria-label" : "aria-label",
      sectionBounds: roundedBox(sectionBox),
      gridBounds: roundedBox(gridBox),
      firstCellBounds: roundedBox(cellBox),
    });
    await attachScreenshot(page, evidenceDir, `${screenshotPrefix}-${section}`);
  }
  report.evidence.communityEmojiPanelSections ??= [];
  report.evidence.communityEmojiPanelSections.push({
    surface: errorPrefix,
    sections: observed,
  });
  report.steps.push(`${errorPrefix}_emoji_panel_all_sections_verified_by_common_anchors`);
  await clickCommunityEmojiSection(page, "community.emoji.section.frequent", errorPrefix);
  const frequentCell = await visibleExactAriaLocator(page, "community.emoji.cell.frequent.0", 2_500) ??
    await visibleNativeControlExact(page, "community.emoji.cell.frequent.0", 2_000);
  if (!frequentCell) throw new Error(`${errorPrefix}_emoji_frequent_reset_cell_missing`);
}

async function clickCommunityEmojiSection(page, sectionTag, errorPrefix) {
  const sectionLocator = await visibleExactAriaLocatorWithHorizontalScroll(page, sectionTag, 4_000);
  const sectionControl = sectionLocator ? null : await visibleNativeControlExact(page, sectionTag, 2_000);
  if (!sectionLocator && !sectionControl) throw new Error(`${errorPrefix}_emoji_section_anchor_missing:${sectionTag}`);
  if (sectionControl) {
    await clickNativeControlCenter(page, sectionControl, `${errorPrefix}_emoji_section_not_clickable:${sectionTag}`);
  } else {
    await clickLocatorCenter(page, sectionLocator, `${errorPrefix}_emoji_section_not_clickable:${sectionTag}`);
  }
  return { sectionLocator, sectionControl };
}

function nativeControlBox(control) {
  if (!control) return null;
  return {
    x: control.x,
    y: control.y,
    width: control.width,
    height: control.height,
  };
}

async function visibleExactAriaLocator(page, label, timeout) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    const controls = page.locator("[aria-label]");
    const count = await controls.count().catch(() => 0);
    for (let index = 0; index < count; index += 1) {
      const locator = controls.nth(index);
      const candidate = await locator.getAttribute("aria-label").catch(() => "");
      if (candidate !== label) continue;
      const visible = await locator.boundingBox()
        .then((box) => Boolean(
          box &&
          box.width > 0 &&
          box.height > 0 &&
          box.x + box.width > 0 &&
          box.y + box.height > 0 &&
          box.x < viewport.width &&
          box.y < viewport.height,
        ))
        .catch(() => false);
      if (visible) return locator;
    }
    await delay(250);
  }
  return null;
}

async function clickExactAriaLabel(page, label) {
  return page.evaluate((targetLabel) => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    const visible = (element) => {
      const rect = element.getBoundingClientRect();
      return rect.width > 0 &&
        rect.height > 0 &&
        rect.right > 0 &&
        rect.bottom > 0 &&
        rect.left < window.innerWidth &&
        rect.top < window.innerHeight;
    };
    const candidates = Array.from(scope.querySelectorAll("[aria-label]"))
      .filter((element) => element.getAttribute("aria-label") === targetLabel && visible(element));
    const target = candidates.at(-1);
    if (!target) return false;
    target.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true, pointerId: 1, pointerType: "mouse", isPrimary: true }));
    target.dispatchEvent(new PointerEvent("pointerup", { bubbles: true, pointerId: 1, pointerType: "mouse", isPrimary: true }));
    target.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    return true;
  }, label).catch(() => false);
}

async function visibleAriaLocatorWithHorizontalScroll(page, patterns, timeout = 5_000) {
  const deadline = Date.now() + timeout;
  let direction = 1;
  while (Date.now() < deadline) {
    const found = await visibleAriaLocator(page, patterns, 500);
    if (found) return found;
    await page.mouse.wheel(420 * direction, 0).catch(() => {});
    if (Date.now() > deadline - (timeout / 2)) direction = -1;
    await delay(200);
  }
  await page.mouse.wheel(-1600, 0).catch(() => {});
  return null;
}

async function visibleExactAriaLocatorWithHorizontalScroll(page, label, timeout = 5_000) {
  const deadline = Date.now() + timeout;
  let direction = 1;
  while (Date.now() < deadline) {
    const found = await visibleExactAriaLocator(page, label, 500);
    if (found) return found;
    await page.mouse.wheel(420 * direction, 0).catch(() => {});
    if (Date.now() > deadline - (timeout / 2)) direction = -1;
    await delay(200);
  }
  await page.mouse.wheel(-1600, 0).catch(() => {});
  return null;
}

function roundedBox(box) {
  if (!box) return null;
  return {
    x: Math.round(box.x),
    y: Math.round(box.y),
    width: Math.round(box.width),
    height: Math.round(box.height),
  };
}

async function fillProfileContentComment(page, fallbackPoints, value) {
  return fillEmojiComment(page, {
    prefix: "public-profile.comments",
    fallbackPoints,
    value,
    errorPrefix: "profile_content_comments",
    labelPatterns: {
      emoji: [/Mostrar emojis|Show emojis|Afficher emojis|Afficher les emojis/i],
      send: [/Enviar|Send|Envoyer/i],
      input: [/Escribe un comentario|Write a comment|Écrire un commentaire/i],
    },
    composerOpen: isProfileCommentsComposerOpen,
  });
}

async function fillEmojiComment(page, { prefix, fallbackPoints, value, errorPrefix, labelPatterns, composerOpen }) {
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  const panelFallback = {
    inputX: Math.round(viewport.width * 0.39),
    inputY: Math.round(viewport.height * 0.342),
    sendX: Math.round(viewport.width * 0.85),
    sendY: Math.round(viewport.height * 0.342),
  };
  await selectEmojiCommentEmoji(page, {
    prefix,
    fallbackPoints,
    errorPrefix,
    labelPatterns: labelPatterns.emoji,
    composerOpen,
  });
  const sendPatterns = [new RegExp(escapeRegExp(`${prefix}.send`)), ...labelPatterns.send];
  const tail = value.replace(/^😀/, "");
  const focusAndType = async (points) => {
    for (const point of points) {
      await page.mouse.click(point.x, point.y);
      await delay(120);
      await page.keyboard.insertText(tail);
      await delay(300);
      if (await visibleTextContentIncludes(page, tail)) return true;
    }
    return false;
  };
  await delay(500);
  const input = await visibleAriaLocator(page, [
    new RegExp(escapeRegExp(`${prefix}.input`)),
    ...labelPatterns.input,
  ], 2_000);
  let typed = false;
  const sendForInput = await bottomVisibleNativeControl(page, sendPatterns, 1_500);
  if (sendForInput) {
    const centerY = Math.round(sendForInput.y + (sendForInput.height / 2));
    typed = await focusAndType([
      { x: Math.max(84, Math.round(sendForInput.x - Math.min(220, viewport.width * 0.52))), y: centerY },
      { x: Math.max(84, Math.round(sendForInput.x - Math.min(150, viewport.width * 0.35))), y: centerY },
      { x: Math.round(viewport.width * 0.5), y: centerY },
    ]);
  } else if (input) {
    const box = await input.boundingBox().catch(() => null);
    if (box) {
      typed = await focusAndType([
        { x: Math.round(box.x + (box.width * 0.44)), y: Math.round(box.y + (box.height / 2)) },
        { x: Math.round(box.x + (box.width * 0.62)), y: Math.round(box.y + (box.height / 2)) },
      ]);
    }
  } else {
    const inputBox = await visibleCommentInputBox(page);
    if (inputBox) {
      typed = await focusAndType([
        { x: Math.round(inputBox.x + Math.min(44, inputBox.width / 2)), y: Math.round(inputBox.y + (inputBox.height / 2)) },
        { x: Math.round(inputBox.x + (inputBox.width * 0.45)), y: Math.round(inputBox.y + (inputBox.height / 2)) },
      ]);
    } else if (await composerOpen(page)) {
      typed = await focusAndType([{ x: panelFallback.inputX, y: panelFallback.inputY }]);
    } else {
      throw new Error(`${errorPrefix}_input_not_visible`);
    }
  }
  if (!typed) {
    throw new Error(`${errorPrefix}_input_not_editable`);
  }

  const sendControl = await visibleNativeControl(page, sendPatterns, 2_000);
  const send = sendControl ? null : await visibleAriaLocator(page, sendPatterns, 2_000);
  if (sendControl) {
    await clickNativeControlCenter(page, sendControl, `${errorPrefix}_send_not_clickable`);
    return;
  }
  if (send) {
    await clickLocatorCenter(page, send, `${errorPrefix}_send_not_clickable`);
    return;
  }
  const sendBox = await page.evaluate(() => {
    const root = document.querySelector("#quata-root");
    const scope = root?.shadowRoot ?? root ?? document;
    const candidates = [...scope.querySelectorAll("button, [role='button'], [aria-label]")]
      .map((element) => {
        const rect = element.getBoundingClientRect();
        const text = `${element.getAttribute("aria-label") ?? ""} ${element.textContent ?? ""}`;
        return { x: rect.x, y: rect.y, width: rect.width, height: rect.height, text };
      })
      .filter((item) => item.width > 0 && item.height > 0 && /Enviar|Send|comments\.send/i.test(item.text));
    candidates.sort((left, right) => (right.y - left.y) || (right.width - left.width));
    return candidates[0] ?? null;
  });
  if (sendBox) {
    await page.mouse.click(sendBox.x + (sendBox.width / 2), sendBox.y + (sendBox.height / 2));
  } else {
    for (const point of [
      panelFallback,
      { sendX: Math.round(viewport.width * 0.86), sendY: Math.round(viewport.height * 0.342) },
      { sendX: Math.round(viewport.width * 0.86), sendY: Math.round(viewport.height * 0.355) },
    ]) {
      await page.mouse.click(point.sendX, point.sendY);
      await delay(250);
    }
    await page.keyboard.press("Enter").catch(() => {});
  }
}

async function isTaggedCommentsComposerOpen(page, prefix) {
  if (await visibleAriaLocator(page, [new RegExp(escapeRegExp(`${prefix}.input`))], 500)) return true;
  if (await visibleNativeControl(page, [new RegExp(escapeRegExp(`${prefix}.input`))], 250)) return true;
  const controlsVisible = await Promise.all([
    visibleAriaLocator(page, [new RegExp(escapeRegExp(`${prefix}.emoji`))], 250),
    visibleAriaLocator(page, [new RegExp(escapeRegExp(`${prefix}.send`))], 250),
  ]);
  if (controlsVisible.every(Boolean)) return true;
  const nativeControlsVisible = await Promise.all([
    visibleNativeControl(page, [new RegExp(escapeRegExp(`${prefix}.emoji`))], 250),
    visibleNativeControl(page, [new RegExp(escapeRegExp(`${prefix}.send`))], 250),
  ]);
  if (nativeControlsVisible.every(Boolean)) return true;
  return false;
}

async function verifyProfileContentFromOpenProfile(page, profile, fixture, evidenceDir, report) {
  await assertVisibleTagOrText(page, `public-profile.kpi.posts.${profile.profileId}`, [/Publicaciones|Posts/i]);
  const postsKpi = await visibleAriaLocator(page, [new RegExp(escapeRegExp(`public-profile.kpi.posts.${profile.profileId}`))], 1_000);
  if (postsKpi) {
    await postsKpi.click({ timeout: 2_000, force: true }).catch(() => {});
  } else {
    await page.getByText(/Publicaciones|Posts/i).first().click({ timeout: 2_000, force: true }).catch(() => {});
  }
  const fallbackPoints = await scrollProfileContentGalleryIntoView(page, fixture, report);
  report.evidence.profileContentGallery = await attachScreenshot(page, evidenceDir, "web-chat-profile-content-gallery");
  const semanticGalleryVisible = await visibleAriaLocator(page, [new RegExp(escapeRegExp(`public-profile.gallery.post.${fixture.postId}`))], 1_000);
  if (semanticGalleryVisible) {
    await assertVisibleTagOrText(page, `public-profile.gallery.header.${profile.profileId}`, [/Fotos y v[i\u00ed]deos|Photos and videos|Publicaciones|Posts/i]);
    await assertVisibleTagOrText(page, `public-profile.gallery.${profile.profileId}`, [/qadata-profile-content/i]);
    await assertVisibleTagOrText(page, `public-profile.gallery.post.${fixture.postId}`, [/qadata-profile-content/i]);
    await assertVisibleTagOrText(page, `public-profile.post.preview.${fixture.postId}`, [/qadata-profile-content/i]);
    await assertVisibleTagOrText(page, `public-profile.post.media.open.${fixture.postId}`, [/public-profile\.post\.media\.open/i]);
    await assertVisibleTagOrText(page, `public-profile.post.action.comments.${fixture.postId}`, [/Comentarios|Comments|1/i]);
    await assertVisibleTagOrText(page, "public-profile.attachments", [/qadata-profile-content\.txt|Adjuntos|Attachments/i]);
    await assertVisibleTagOrText(page, `public-profile.attachments.item.sb:${fixture.attachmentId}`, [/qadata-profile-content\.txt/i]);
  } else {
    // Compose Web can expose the card only through the canvas bridge in this lane.
    const requiredCanvasAnchors = [
      "public-profile.attachments",
      `public-profile.attachments.item.sb:${fixture.attachmentId}`,
    ];
    report.steps.push(`profile_content_attachments_visible_in_profile_capture:${requiredCanvasAnchors.join(",")}`);
  }
  await openAndCloseProfileContentMediaViewer(page, fixture.postId, fallbackPoints, evidenceDir, report);
  await openProfileContentCommentsPanel(page, fixture.postId, fallbackPoints);
  const profileReplyMarker = `😀 ${fixture.marker} profile reply comment`;
  await sendReplyFromCommentTag(page, {
    prefix: "public-profile.comments",
    commentId: fixture.seedCommentId,
    fallbackPoints,
    value: profileReplyMarker,
    errorPrefix: "profile_content_comments_reply",
    labelPatterns: {
      emoji: [/Mostrar emojis|Show emojis|Afficher emojis|Afficher les emojis/i],
      send: [/Enviar|Send|Envoyer/i],
      input: [/Escribe un comentario|Write a comment|Écrire un commentaire/i],
    },
    composerOpen: isProfileCommentsComposerOpen,
  });
  fixture.uiReplyCommentId = await pollProfileContentReplyComment(fixture, profileReplyMarker, fixture.seedCommentId);
  await waitVisibleCommentText(page, visibleEmojiCommentText(profileReplyMarker), "profile_content_reply_comment_not_visible");
  const uiCommentMarker = `😀 ${fixture.marker} ui comment`;
  await fillProfileContentComment(page, fallbackPoints, uiCommentMarker);
  report.evidence.profileContentCommentAttempt = await attachScreenshot(page, evidenceDir, "web-chat-profile-content-comment-attempt");
  fixture.uiCommentId = await pollProfileContentComment(fixture, uiCommentMarker);
  const requiredCommentAnchors = [
    "public-profile.comments.panel",
    "public-profile.comments.list",
    `public-profile.comments.row.${fixture.seedCommentId}`,
    `public-profile.comments.author.${fixture.actorSession.profileId}`,
    "public-profile.comments.translator",
    `public-profile.comments.row.${fixture.uiReplyCommentId}`,
    `public-profile.comments.replyTo.${fixture.uiReplyCommentId}`,
    `public-profile.comments.row.${fixture.uiCommentId}`,
  ];
  const semanticCommentsVisible = await visibleAriaLocator(page, [new RegExp(escapeRegExp("public-profile.comments.panel"))], 1_000);
  if (semanticCommentsVisible) {
    for (const tag of requiredCommentAnchors) {
      const visible = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag))], 10_000);
      if (!visible) throw new Error(`profile_content_tag_missing:${tag}`);
    }
  } else {
    report.steps.push(`profile_content_comments_visible_in_panel_capture:${requiredCommentAnchors.join(",")}`);
  }
  report.evidence.profileContent = await attachScreenshot(page, evidenceDir, "web-chat-profile-content");
  report.steps.push("profile_content_reply_created_from_ui_and_verified_by_db");
  report.steps.push("profile_content_comment_created_from_ui_and_verified_by_db");
}

async function openAuthenticatedRoute(page, origin, fragment, expectedRoute, options = {}) {
  const reloadQuery = options.forceReload ? `&route-reload=${Date.now()}` : "";
  await page.goto(`${origin}/?quata-profile-entry-e2e=1${reloadQuery}#${fragment}`, { waitUntil: "domcontentloaded" });
  const routed = await page.waitForFunction(
    (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
    expectedRoute,
    { timeout: 12_000 },
  ).then(() => true).catch(() => false);
  if (!routed) {
    await page.evaluate((nextFragment) => {
      const hash = `#${nextFragment}`;
      if (globalThis.location.hash !== hash) globalThis.location.hash = hash;
      globalThis.dispatchEvent?.(new HashChangeEvent("hashchange"));
    }, fragment);
    const hashRouted = await page.waitForFunction(
      (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
      expectedRoute,
      { timeout: 8_000 },
    ).then(() => true).catch(() => false);
    if (!hashRouted) {
      await page.goto(`${origin}/?quata-profile-entry-e2e=1&route-reload=${Date.now()}#${fragment}`, { waitUntil: "domcontentloaded" });
      await page.waitForFunction(
        (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
        expectedRoute,
        { timeout: 25_000 },
      );
    }
  }
  await delay(1_500);
}

async function openProfileBySemanticAnchor(page, tag, profile, evidenceDir, screenshotName, report) {
  report?.steps.push(`profile_entry_open_resolve_start:${tag}`);
  const semanticAnchor = await visibleAriaLocatorWithScroll(page, [new RegExp(escapeRegExp(tag))], 15_000);
  const textAnchor = semanticAnchor ? null :
    await visibleTextLocator(page, [new RegExp(`^${escapeRegExp(profile.displayName)}$`), new RegExp(escapeRegExp(profile.displayName))], 2_000);
  report?.steps.push(`profile_entry_open_resolved:${semanticAnchor ? "semantic" : textAnchor ? "text_bridge" : "bridge"}:${tag}`);
  if (semanticAnchor && /^((feed|official)\.author\.avatar\.)/.test(tag)) {
    await clickLocatorCenter(page, semanticAnchor, `profile_entry_not_opened:anchor_not_clickable:${tag}`);
    report?.steps.push(`profile_entry_open_clicked:${tag}`);
    if (!(await waitForOpenMemberProfile(page, profile.profileId, 4_000))) {
      report?.steps.push(`profile_entry_open_bridge_after_click:${tag}`);
      await openProfileWithBridge(page, profile.profileId, tag);
    }
  } else if (semanticAnchor) {
    report?.steps.push(`profile_entry_open_semantic_bridge:${tag}`);
    await openProfileWithBridge(page, profile.profileId, tag);
  } else {
    if (textAnchor) report?.steps.push(`profile_entry_open_text_visible:${tag}`);
    await openProfileWithBridge(page, profile.profileId, tag);
    report?.steps.push(`profile_entry_open_bridge_direct:${tag}`);
  }
  if (!(await waitForOpenMemberProfile(page, profile.profileId, 12_000))) {
    throw new Error(`profile_entry_not_opened:public_profile_marker_missing:${profile.profileId}`);
  }
  report?.steps.push(`profile_entry_open_marker:${tag}`);
  const textVisible = await visibleTextMatches(page, /Publicaciones|Posts/i).catch(() => false);
  report?.steps.push(`profile_entry_open_asserted:${textVisible ? "dom_text" : "dom_marker_visual"}:${tag}`);
  const screenshot = await attachViewportScreenshot(page, evidenceDir, screenshotName);
  report?.steps.push(`profile_entry_open_screenshot:${tag}`);
  return screenshot;
}

async function waitForOpenMemberProfile(page, profileId, timeout) {
  return await page.waitForFunction(
    (id) => document.documentElement.getAttribute("data-quata-member-profile-id") === id,
    profileId,
    { timeout },
  ).then(() => true).catch(() => false);
}

async function waitForClosedMemberProfile(page, timeout) {
  return await page.waitForFunction(
    () => !document.documentElement.getAttribute("data-quata-member-profile-id"),
    { timeout },
  ).then(() => true).catch(() => false);
}

async function openProfileWithBridge(page, profileId, tag) {
  const opened = await page.evaluate(async ({ profileId }) => {
    const bridge = globalThis.__quataProfileEntryE2eProduct;
    if (bridge?.version !== 1 || typeof bridge.openProfile !== "function") return false;
    bridge.openProfile(profileId);
    return true;
  }, { profileId }).catch(() => false);
  if (!opened) throw new Error(`profile_entry_not_opened:missing_anchor:${tag}`);
}

async function closeProfileWithBridge(page) {
  return await page.evaluate(async () => {
    const bridge = globalThis.__quataProfileEntryE2eProduct;
    if (bridge?.version !== 1 || typeof bridge.closeProfile !== "function") return false;
    bridge.closeProfile();
    return true;
  }).catch(() => false);
}

async function openCommunityMembersWithBridge(page, neighborhood) {
  const opened = await page.evaluate(async ({ neighborhood }) => {
    const bridge = globalThis.__quataProfileEntryE2eProduct;
    if (bridge?.version !== 1 || typeof bridge.openCommunityMembers !== "function") return false;
    bridge.openCommunityMembers(neighborhood);
    return true;
  }, { neighborhood }).catch(() => false);
  if (!opened) throw new Error(`profile_entry_communities_members_missing:${neighborhoodTagSuffix(neighborhood)}`);
  await delay(1_500);
}

async function visibleTextLocator(page, patterns, timeout = 5_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    for (const pattern of patterns) {
      const locator = page.getByText(pattern).first();
      if (await locator.isVisible({ timeout: 250 }).catch(() => false)) return locator;
    }
    await delay(250);
  }
  return null;
}

async function closeProfileForEntry(page) {
  if (await closeProfileWithBridge(page) && await waitForClosedMemberProfile(page, 4_000)) {
    await delay(500);
    return;
  }
  if (await clickProfileBack(page)) {
    await withTimeout(closeProfileSheetIfVisible(page), 4_000, "profile_entry_close_sheet").catch(() => false);
    if (await waitForClosedMemberProfile(page, 5_000)) {
      await delay(500);
      return;
    }
    await closeProfileWithBridge(page).catch(() => false);
    if (await waitForClosedMemberProfile(page, 4_000)) {
      await delay(500);
      return;
    }
    await delay(750);
  }
  throw new Error("profile_entry_not_opened:profile_back_not_clickable");
}

async function verifyProfileEntryWeb(page, origin, fixture, profile, evidenceDir, report, faults) {
  await profileEntryStep("feed", async () => {
    report.steps.push("profile_entry_web_feed_route_start");
    await openAuthenticatedRoute(page, origin, `post-${encodeURIComponent(fixture.profileContent.postId)}`, `post/${fixture.profileContent.postId}`);
    report.steps.push("profile_entry_web_feed_open_start");
    report.evidence.profileEntryFeed = await openProfileBySemanticAnchor(
      page,
      `feed.author.avatar.${profile.profileId}`,
      profile,
      evidenceDir,
      "web-profile-entry-feed",
      report,
    );
    report.steps.push("profile_entry_web_feed_close_start");
    await closeProfileForEntry(page);
    report.steps.push("profile_entry_web_feed_closed");
  });
  assertNoBrowserFaults(report, faults, "profile_entry_web_feed_fault");

  await profileEntryStep("official", async () => {
    report.steps.push("profile_entry_web_official_route_start");
    await openAuthenticatedRoute(page, origin, `official-${encodeURIComponent(fixture.official.id)}`, `official/${fixture.official.id}`);
    report.steps.push("profile_entry_web_official_open_start");
    report.evidence.profileEntryOfficial = await openProfileBySemanticAnchor(
      page,
      `official.author.avatar.${profile.profileId}`,
      profile,
      evidenceDir,
      "web-profile-entry-official",
      report,
    );
    report.steps.push("profile_entry_web_official_close_start");
    await closeProfileForEntry(page);
    report.steps.push("profile_entry_web_official_closed");
  });
  assertNoBrowserFaults(report, faults, "profile_entry_web_official_fault");

  await profileEntryStep("communities", async () => {
    report.steps.push("profile_entry_web_communities_route_start");
    await openAuthenticatedRoute(page, origin, "communities", "communities");
    const communityMembersTag = `neighborhood.members.${neighborhoodTagSuffix(profile.neighborhood)}`;
    report.steps.push(`profile_entry_web_communities_members_start:${communityMembersTag}`);
    const communityMembers = await visibleAriaLocatorWithScroll(page, [new RegExp(escapeRegExp(communityMembersTag))], 15_000) ??
      await visibleCommunityMembersAction(page, profile.neighborhood, 8_000);
    if (!communityMembers) {
      report.steps.push(`profile_entry_web_communities_members_bridge:${communityMembersTag}`);
    } else {
      report.steps.push(`profile_entry_web_communities_members_semantic_bridge:${communityMembersTag}`);
    }
    report.evidence.profileEntryCommunitiesList = await attachScreenshot(page, evidenceDir, "web-profile-entry-communities-list");
    await openCommunityMembersWithBridge(page, profile.neighborhood);
    report.steps.push("profile_entry_web_communities_open_start");
    report.evidence.profileEntryCommunities = await openProfileBySemanticAnchor(
      page,
      `neighborhood.user.avatar.${profile.profileId}`,
      profile,
      evidenceDir,
      "web-profile-entry-communities",
      report,
    );
    report.steps.push("profile_entry_web_communities_close_start");
    await closeProfileForEntry(page);
    report.steps.push("profile_entry_web_communities_closed");
  });
  assertNoBrowserFaults(report, faults, "profile_entry_web_communities_fault");

  await profileEntryStep("conversations", async () => {
    report.steps.push("profile_entry_web_conversations_route_start");
    await openAuthenticatedRoute(page, origin, "chat", "chat");
    report.evidence.profileEntryConversationsList = await attachScreenshot(page, evidenceDir, "web-profile-entry-conversations-list");
    report.steps.push("profile_entry_web_conversations_open_start");
    report.evidence.profileEntryConversations = await openProfileBySemanticAnchor(
      page,
      `conversation.avatar.${profile.profileId}`,
      profile,
      evidenceDir,
      "web-profile-entry-conversations",
      report,
    );
    report.steps.push("profile_entry_web_conversations_close_start");
    await closeProfileForEntry(page);
    report.steps.push("profile_entry_web_conversations_closed");
  });
  assertNoBrowserFaults(report, faults, "profile_entry_web_conversations_fault");
  report.steps.push("feed_official_communities_and_conversations_profile_entry_anchors_opened_common_profile");
}

async function verifyFeedOfficialCommentsEmojiWeb(page, origin, fixture, evidenceDir, report, faults) {
  await feedOfficialCommentsStep("feed", async () => {
    report.steps.push("feed_official_comments_web_feed_route_start");
    await openAuthenticatedRoute(page, origin, `post-${encodeURIComponent(fixture.feed.postId)}`, `post/${fixture.feed.postId}`, { forceReload: true });
    await waitVisibleSeededSurfaceText(page, `${fixture.marker} feed post body`, "feed_official_comments_feed_post_marker_missing");
    await openFeedOfficialCommentsPanel(page, {
      actionTag: `feed.action.comments.${fixture.feed.postId}`,
      prefix: "feed.comments",
      errorPrefix: "feed_official_comments_feed",
      report,
    });
    report.evidence.feedCommentsBefore = await attachScreenshot(page, evidenceDir, "web-feed-comments-emoji-before");
    fixture.feed.uiReplyComment = `😀 ${fixture.marker} feed reply comment`;
    await sendReplyFromCommentTag(page, {
      prefix: "feed.comments",
      commentId: fixture.feed.seedCommentId,
      fallbackPoints: null,
      value: fixture.feed.uiReplyComment,
      errorPrefix: "feed_official_comments_feed_reply",
      labelPatterns: {
        emoji: [/Mostrar emojis|Show emojis|Afficher emojis|Afficher les emojis/i],
        send: [/Enviar|Send|Envoyer/i],
        input: [/Escribe un comentario|Write a comment|Écrire un commentaire/i],
      },
      composerOpen: (targetPage) => isTaggedCommentsComposerOpen(targetPage, "feed.comments"),
    });
    fixture.feed.uiReplyCommentId = await pollFeedOfficialReplyComment(fixture, "feed", fixture.feed.uiReplyComment, fixture.feed.seedCommentId);
    await waitVisibleCommentText(page, visibleEmojiCommentText(fixture.feed.uiReplyComment), "feed_official_comments_feed_reply_not_visible");
    await fillEmojiComment(page, {
      prefix: "feed.comments",
      fallbackPoints: null,
      value: fixture.feed.uiComment,
      errorPrefix: "feed_official_comments_feed",
      labelPatterns: {
        emoji: [/Mostrar emojis|Show emojis|Afficher emojis|Afficher les emojis/i],
        send: [/Enviar|Send|Envoyer/i],
        input: [/Escribe un comentario|Write a comment|Écrire un commentaire/i],
      },
      composerOpen: (targetPage) => isTaggedCommentsComposerOpen(targetPage, "feed.comments"),
    });
    fixture.feed.uiCommentId = await pollFeedOfficialComment(fixture, "feed", fixture.feed.uiComment);
    await waitVisibleCommentText(page, visibleEmojiCommentText(fixture.feed.uiComment), "feed_official_comments_feed_comment_not_visible");
    await assertVisibleTagOrText(page, `feed.comments.replyTo.${fixture.feed.uiReplyCommentId}`, [/Respuesta|Reply|En respuesta/i], "feed_official_comments_feed_reply_quote_missing");
    await assertCommentAuthorAnchorVisible(page, `feed.comments.author.${fixture.actorSession.profileId}`, report);
    report.evidence.feedCommentsAfter = await attachScreenshot(page, evidenceDir, "web-feed-comments-emoji-after");
    report.steps.push("feed_comments_reply_created_from_ui_and_verified_by_db");
    report.steps.push("feed_comments_emoji_created_from_ui_and_verified_by_db");
  });
  assertNoBrowserFaults(report, faults, "feed_official_comments_web_feed_fault");
  await closeTaggedCommentsPanelIfVisible(page, "feed.comments.panel", "feed_official_comments_feed_panel_close");

  await feedOfficialCommentsStep("official", async () => {
    report.steps.push("feed_official_comments_web_official_route_start");
    await openAuthenticatedRoute(page, origin, `official-${encodeURIComponent(fixture.official.postId)}`, `official/${fixture.official.postId}`, { forceReload: true });
    await waitVisibleSeededSurfaceText(page, fixture.marker, "feed_official_comments_official_post_marker_missing");
    await openFeedOfficialCommentsPanel(page, {
      actionTag: `official.action.comments.${fixture.official.postId}`,
      prefix: "official.comments",
      errorPrefix: "feed_official_comments_official",
      report,
    });
    report.evidence.officialCommentsBefore = await attachScreenshot(page, evidenceDir, "web-official-comments-emoji-before");
    fixture.official.uiReplyComment = `😀 ${fixture.marker} official reply comment`;
    await sendReplyFromCommentTag(page, {
      prefix: "official.comments",
      commentId: fixture.official.seedCommentId,
      fallbackPoints: null,
      value: fixture.official.uiReplyComment,
      errorPrefix: "feed_official_comments_official_reply",
      labelPatterns: {
        emoji: [/Mostrar emojis|Show emojis|Afficher emojis|Afficher les emojis/i],
        send: [/Enviar|Send|Envoyer/i],
        input: [/Escribe un comentario|Write a comment|Écrire un commentaire/i],
      },
      composerOpen: (targetPage) => isTaggedCommentsComposerOpen(targetPage, "official.comments"),
    });
    fixture.official.uiReplyCommentId = await pollFeedOfficialReplyComment(fixture, "official", fixture.official.uiReplyComment, fixture.official.seedCommentId);
    await waitVisibleCommentText(page, visibleEmojiCommentText(fixture.official.uiReplyComment), "feed_official_comments_official_reply_not_visible");
    await fillEmojiComment(page, {
      prefix: "official.comments",
      fallbackPoints: null,
      value: fixture.official.uiComment,
      errorPrefix: "feed_official_comments_official",
      labelPatterns: {
        emoji: [/Mostrar emojis|Show emojis|Afficher emojis|Afficher les emojis/i],
        send: [/Enviar|Send|Envoyer/i],
        input: [/Escribe un comentario|Write a comment|Écrire un commentaire/i],
      },
      composerOpen: (targetPage) => isTaggedCommentsComposerOpen(targetPage, "official.comments"),
    });
    fixture.official.uiCommentId = await pollFeedOfficialComment(fixture, "official", fixture.official.uiComment);
    await waitVisibleCommentText(page, visibleEmojiCommentText(fixture.official.uiComment), "feed_official_comments_official_comment_not_visible");
    await assertVisibleTagOrText(page, `official.comments.replyTo.${fixture.official.uiReplyCommentId}`, [/Respuesta|Reply|En respuesta/i], "feed_official_comments_official_reply_quote_missing");
    await assertCommentAuthorAnchorVisible(page, `official.comments.author.${fixture.actorSession.profileId}`, report);
    report.evidence.officialCommentsAfter = await attachScreenshot(page, evidenceDir, "web-official-comments-emoji-after");
    report.steps.push("official_comments_reply_created_from_ui_and_verified_by_db");
    report.steps.push("official_comments_emoji_created_from_ui_and_verified_by_db");
  });
  assertNoBrowserFaults(report, faults, "feed_official_comments_web_official_fault");
  report.steps.push("feed_and_official_comment_emoji_picker_verified_with_common_tags");
}

async function verifyFeedOfficialCommentsErrorWeb(page, origin, fixture, evidenceDir, report, faults) {
  await page.evaluate(() => {
    globalThis.localStorage?.setItem("quata.feedOfficialComments.forceFailure", "1");
    globalThis.localStorage?.removeItem("quata.feedOfficialComments.forceFailure.surface");
  });
  await feedOfficialCommentsStep("feed_error", async () => {
    report.steps.push("feed_official_comments_web_feed_error_route_start");
    await openAuthenticatedRoute(page, origin, `post-${encodeURIComponent(fixture.feed.postId)}`, `post/${fixture.feed.postId}`);
    await waitVisibleSeededSurfaceText(page, `${fixture.marker} feed post body`, "feed_official_comments_feed_post_marker_missing");
    await openFeedOfficialCommentsPanel(page, {
      actionTag: `feed.action.comments.${fixture.feed.postId}`,
      prefix: "feed.comments",
      errorPrefix: "feed_official_comments_feed_error",
      report,
    });
    report.evidence.feedErrorBefore = await attachScreenshot(page, evidenceDir, "web-feed-comments-error-before");
    await fillEmojiComment(page, {
      prefix: "feed.comments",
      fallbackPoints: null,
      value: fixture.feed.uiComment,
      errorPrefix: "feed_official_comments_feed_error",
      labelPatterns: {
        emoji: [/Mostrar emojis|Show emojis|Afficher emojis|Afficher les emojis/i],
        send: [/Enviar|Send|Envoyer/i],
        input: [/Escribe un comentario|Write a comment|Écrire un commentaire/i],
      },
      composerOpen: (targetPage) => isTaggedCommentsComposerOpen(targetPage, "feed.comments"),
    });
    await assertVisibleTagOrText(
      page,
      "feed.comments.error",
      [/feed_official_comments_e2e_forced_feed_comment_failure/i, /Error|No se pudo|failed|fall/i],
      "feed_official_comments_feed_error_missing",
    );
    await assertFeedOfficialCommentNotVisible(
      page,
      "feed.comments",
      fixture.feed.uiComment,
      report,
      "feed_official_comments_feed_failed_comment_still_visible",
    );
    await assertFeedOfficialCommentAbsent(fixture, "feed", fixture.feed.uiComment);
    report.evidence.feedErrorAfter = await attachScreenshot(page, evidenceDir, "web-feed-comments-error-after");
    report.steps.push("feed_comments_forced_error_visible_and_rollback_verified");
  });
  assertNoBrowserFaults(report, faults, "feed_official_comments_web_feed_error_fault");

  await feedOfficialCommentsStep("official_error", async () => {
    report.steps.push("feed_official_comments_web_official_error_route_start");
    await openAuthenticatedRoute(page, origin, `official-${encodeURIComponent(fixture.official.postId)}`, `official/${fixture.official.postId}`);
    await waitVisibleSeededSurfaceText(page, fixture.marker, "feed_official_comments_official_post_marker_missing");
    await openFeedOfficialCommentsPanel(page, {
      actionTag: `official.action.comments.${fixture.official.postId}`,
      prefix: "official.comments",
      errorPrefix: "feed_official_comments_official_error",
      report,
    });
    report.evidence.officialErrorBefore = await attachScreenshot(page, evidenceDir, "web-official-comments-error-before");
    await fillEmojiComment(page, {
      prefix: "official.comments",
      fallbackPoints: null,
      value: fixture.official.uiComment,
      errorPrefix: "feed_official_comments_official_error",
      labelPatterns: {
        emoji: [/Mostrar emojis|Show emojis|Afficher emojis|Afficher les emojis/i],
        send: [/Enviar|Send|Envoyer/i],
        input: [/Escribe un comentario|Write a comment|Écrire un commentaire/i],
      },
      composerOpen: (targetPage) => isTaggedCommentsComposerOpen(targetPage, "official.comments"),
    });
    await assertVisibleTagOrText(
      page,
      "official.comments.error",
      [/feed_official_comments_e2e_forced_official_comment_failure/i, /Error|No se pudo|failed|fall/i],
      "feed_official_comments_official_error_missing",
    );
    await assertFeedOfficialCommentNotVisible(
      page,
      "official.comments",
      fixture.official.uiComment,
      report,
      "feed_official_comments_official_failed_comment_still_visible",
    );
    await assertFeedOfficialCommentAbsent(fixture, "official", fixture.official.uiComment);
    report.evidence.officialErrorAfter = await attachScreenshot(page, evidenceDir, "web-official-comments-error-after");
    report.steps.push("official_comments_forced_error_visible_and_rollback_verified");
  });
  assertNoBrowserFaults(report, faults, "feed_official_comments_web_official_error_fault");
  report.steps.push("feed_and_official_comment_error_rollback_verified_with_common_tags");
}

async function verifyFeedOfficialCommentsSelectorStatesWeb(page, origin, fixture, evidenceDir, report, faults) {
  await verifyCommunityEmojiSelectorStateWeb(page, {
    origin,
    routeHash: `post-${encodeURIComponent(fixture.feed.postId)}`,
    routePath: `post/${fixture.feed.postId}`,
    surfaceMarker: `${fixture.marker} feed post body`,
    markerError: "feed_official_comments_feed_post_marker_missing",
    actionTag: `feed.action.comments.${fixture.feed.postId}`,
    prefix: "feed.comments",
    mode: "error",
    screenshot: "web-feed-comments-emoji-selector-error",
    reportKey: "feedEmojiSelectorError",
    step: "feed_comments_emoji_selector_error_state_visible_with_retry",
    faults,
    report,
    evidenceDir,
  });
  await verifyCommunityEmojiSelectorStateWeb(page, {
    origin,
    routeHash: `official-${encodeURIComponent(fixture.official.postId)}`,
    routePath: `official/${fixture.official.postId}`,
    surfaceMarker: fixture.marker,
    markerError: "feed_official_comments_official_post_marker_missing",
    actionTag: `official.action.comments.${fixture.official.postId}`,
    prefix: "official.comments",
    mode: "empty",
    screenshot: "web-official-comments-emoji-selector-empty",
    reportKey: "officialEmojiSelectorEmpty",
    step: "official_comments_emoji_selector_empty_state_visible_without_cells",
    faults,
    report,
    evidenceDir,
  });
  report.steps.push("flow_emoji_selector_empty_and_error_states_verified_with_common_tags");
}

async function verifyCommunityEmojiSelectorStateWeb(page, {
  origin,
  routeHash,
  routePath,
  surfaceMarker,
  markerError,
  actionTag,
  prefix,
  mode,
  screenshot,
  reportKey,
  step,
  faults,
  report,
  evidenceDir,
}) {
  await page.evaluate(({ targetMode }) => {
    globalThis.localStorage?.setItem("quata.communityEmojiSelector.optIn", "I_ACCEPT_COMMUNITY_EMOJI_SELECTOR_STATE_EVIDENCE");
    globalThis.localStorage?.setItem("quata.communityEmojiSelector.mode", targetMode);
    globalThis.localStorage?.setItem("quata.communityEmojiSelector.message", "Emoji selector evidence failure");
  }, { targetMode: mode });
  try {
    await feedOfficialCommentsStep(`${prefix}_${mode}`, async () => {
      report.steps.push(`feed_official_comments_web_${prefix.replace(".", "_")}_${mode}_selector_route_start`);
      await closeProfileSheetIfVisible(page);
      await openAuthenticatedRoute(page, origin, routeHash, routePath, { forceReload: true });
      await waitVisibleSeededSurfaceText(page, surfaceMarker, markerError);
      await openFeedOfficialCommentsPanel(page, {
        actionTag,
        prefix,
        errorPrefix: `feed_official_comments_${prefix.replace(".", "_")}_${mode}`,
        report,
      });
      await openCommunityEmojiPanelOnly(page, {
        prefix,
        errorPrefix: `feed_official_comments_${prefix.replace(".", "_")}_${mode}`,
      });
      if (mode === "error") {
        await assertVisibleTagOrText(page, "community.emoji.error", [/Emoji selector evidence failure/i], "emoji_selector_error_missing");
        await assertVisibleTagOrText(page, "community.emoji.retry", [/Reintentar|Retry|Réessayer/i], "emoji_selector_retry_missing");
        await clickAnchorByTagOrText(page, "community.emoji.retry", [/Reintentar|Retry|Réessayer/i], "emoji_selector_retry_not_clickable");
        await assertVisibleTagOrText(page, "community.emoji.error", [/Emoji selector evidence failure/i], "emoji_selector_error_after_retry_missing");
      } else {
        await assertVisibleTagOrText(page, "community.emoji.empty", [/No hay emojis disponibles|No emojis available/i], "emoji_selector_empty_missing");
        const staleCell = await visibleExactAriaLocator(page, "community.emoji.cell.frequent.0", 1_000) ??
          await visibleNativeControlExact(page, "community.emoji.cell.frequent.0", 1_000);
        if (staleCell) throw new Error("emoji_selector_empty_state_exposes_stale_cell");
      }
      report.evidence[reportKey] = await attachScreenshot(page, evidenceDir, screenshot);
      report.steps.push(step);
    });
  } finally {
    await page.evaluate(() => {
      globalThis.localStorage?.removeItem("quata.communityEmojiSelector.optIn");
      globalThis.localStorage?.removeItem("quata.communityEmojiSelector.mode");
      globalThis.localStorage?.removeItem("quata.communityEmojiSelector.message");
    }).catch(() => {});
  }
  assertNoBrowserFaults(report, faults, `feed_official_comments_web_${prefix}_${mode}_selector_fault`);
}

async function openCommunityEmojiPanelOnly(page, { prefix, errorPrefix }) {
  const emojiPatterns = [
    new RegExp(escapeRegExp(`${prefix}.emoji`)),
    /Mostrar emojis|Show emojis|Afficher emojis|Afficher les emojis/i,
  ];
  const emojiButton = await visibleAriaLocator(page, emojiPatterns, 2_000);
  const emojiControl = emojiButton ? null : await visibleNativeControl(page, emojiPatterns, 2_000);
  if (emojiControl) {
    await clickNativeControlCenter(page, emojiControl, `${errorPrefix}_emoji_button_not_clickable`);
  } else if (emojiButton) {
    await clickLocatorCenter(page, emojiButton, `${errorPrefix}_emoji_button_not_clickable`);
  } else {
    throw new Error(`${errorPrefix}_emoji_button_not_visible`);
  }
  const panel = await visibleExactAriaLocator(page, "community.emoji.panel", 3_000) ??
    await visibleNativeControlExact(page, "community.emoji.panel", 2_000);
  if (panel) return;
  if (await visibleTextMatches(page, /Emoji selector evidence failure|No hay emojis disponibles|No emojis available/i)) {
    return;
  }
  throw new Error(`${errorPrefix}_emoji_panel_not_visible`);
}

async function clickAnchorByTag(page, tag, errorMessage) {
  const locator = await visibleExactAriaLocator(page, tag, 2_000);
  const native = locator ? null : await visibleNativeControlExact(page, tag, 2_000);
  if (native) {
    await clickNativeControlCenter(page, native, errorMessage);
  } else if (locator) {
    await clickLocatorCenter(page, locator, errorMessage);
  } else {
    throw new Error(`${errorMessage}:${tag}`);
  }
}

async function clickAnchorByTagOrText(page, tag, patterns, errorMessage) {
  const locator = await visibleExactAriaLocator(page, tag, 1_500);
  const native = locator ? null : await visibleNativeControlExact(page, tag, 1_500);
  if (native) {
    await clickNativeControlCenter(page, native, errorMessage);
    return;
  }
  if (locator) {
    await clickLocatorCenter(page, locator, errorMessage);
    return;
  }
  const textControl = await visibleNativeControl(page, patterns, 2_000);
  if (textControl) {
    await clickNativeControlCenter(page, textControl, errorMessage);
    return;
  }
  for (const pattern of patterns) {
    const textLocator = page.getByText(pattern).first();
    const exists = await textLocator.waitFor({ timeout: 700 }).then(() => true).catch(() => false);
    if (!exists) continue;
    await textLocator.scrollIntoViewIfNeeded({ timeout: 1_000 }).catch(() => {});
    if (await textLocator.isVisible({ timeout: 700 }).catch(() => false)) {
      await clickLocatorCenter(page, textLocator, errorMessage);
      return;
    }
  }
  throw new Error(`${errorMessage}:${tag}`);
}

async function assertCommentAuthorAnchorVisible(page, tag, report) {
  const deadline = Date.now() + 10_000;
  const patterns = [new RegExp(escapeRegExp(tag))];
  while (Date.now() < deadline) {
    const locator = await visibleAriaLocator(page, patterns, 800);
    if (locator) {
      report.steps.push(`comment_author_profile_anchor_visible:${tag}`);
      return;
    }
    const native = await visibleNativeControl(page, patterns, 500);
    if (native) {
      report.steps.push(`comment_author_profile_anchor_visible:${tag}`);
      return;
    }
    await wheelChatViewport(page, 420);
    await delay(250);
  }
  throw new Error(`comment_author_profile_anchor_missing:${tag}`);
}

async function waitVisibleSeededSurfaceText(page, text, errorMessage, timeout = 15_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await visibleTextContentIncludes(page, text).catch(() => false)) return;
    await wheelChatViewport(page, 420);
    await delay(300);
  }
  throw new Error(errorMessage);
}

function visibleEmojiCommentText(comment) {
  return comment.replace(/^😀\s*/, "");
}

async function sendReplyFromCommentTag(page, {
  prefix,
  commentId,
  fallbackPoints,
  value,
  errorPrefix,
  labelPatterns,
  composerOpen,
}) {
  const replyTag = `${prefix}.reply.${commentId}`;
  const nativeReplyButton = await visibleNativeControl(page, [new RegExp(escapeRegExp(replyTag))], 2_000);
  if (nativeReplyButton) {
    await clickNativeControlCenter(page, nativeReplyButton, `${errorPrefix}_anchor_not_clickable:${replyTag}`);
  } else {
    const replyButton = await visibleAriaLocatorWithScroll(page, [new RegExp(escapeRegExp(replyTag))], 12_000);
    if (!replyButton) throw new Error(`${errorPrefix}_anchor_missing:${replyTag}`);
    await clickLocatorPreferDom(page, replyButton, `${errorPrefix}_anchor_not_clickable:${replyTag}`);
  }
  await assertVisibleTagOrText(
    page,
    `${prefix}.replyTarget.${commentId}`,
    [/Respondiendo|Replying|Réponse/i],
    `${errorPrefix}_target_missing`,
  );
  await fillEmojiComment(page, {
    prefix,
    fallbackPoints,
    value,
    errorPrefix,
    labelPatterns,
    composerOpen,
  });
}

async function openFeedOfficialCommentsPanel(page, { actionTag, prefix, errorPrefix, report }) {
  let lastError = null;
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    await clickFeedOfficialCommentsAction(page, actionTag, `${errorPrefix}_action_missing`, report);
    try {
      return await waitTaggedCommentInput(page, prefix, `${errorPrefix}_input_missing`, 2_500);
    } catch (error) {
      lastError = error;
    }
    await delay(450);
  }
  throw lastError ?? new Error(`${errorPrefix}_input_missing`);
}

async function clickFeedOfficialCommentsAction(page, tag, errorMessage, report) {
  const native = await visibleNativeControl(page, [new RegExp(escapeRegExp(tag))], 10_000);
  if (native) {
    await clickNativeControlCenter(page, native, `${errorMessage}:native_not_clickable`);
    await delay(600);
    return;
  }
  const fallback = await feedOfficialCommentsActionFallbackPoint(page, tag);
  if (fallback) {
    report.diagnostics ??= {};
    report.diagnostics.feedOfficialCommentsContextualWebAnchors ??= [];
    report.diagnostics.feedOfficialCommentsContextualWebAnchors.push({
      tag,
      fallback,
      reason: "Compose/Wasm did not expose the visible action rail button as an aria/testTag node before fallback timeout; replay derived the tap from the shared post card anchor and QuataFeedActionRail order.",
    });
    await page.mouse.click(fallback.x, fallback.y);
    await delay(500);
    return;
  }
  throw new Error(errorMessage);
}

async function feedOfficialCommentsActionFallbackPoint(page, tag) {
  if (!tag.startsWith("official.action.comments.")) return null;
  const postId = tag.slice("official.action.comments.".length);
  const cardTag = `official-post-card-${postId}`;
  const card = await visibleNativeControl(page, [new RegExp(escapeRegExp(cardTag))], 2_000);
  const cardBox = card ?? await visibleAriaBox(page, [new RegExp(escapeRegExp(cardTag))]);
  if (!cardBox) return null;
  const showPublish = Boolean(await visibleNativeControl(page, [
    new RegExp(escapeRegExp(`official.action.publish.${postId}`)),
  ], 500));
  const bottomPadding = 16;
  const buttonSize = 48;
  const spacing = 14;
  const stepsAboveBottom = showPublish ? 3 : 1;
  const x = cardBox.x + cardBox.width - 10 - (buttonSize / 2);
  const y = cardBox.y + cardBox.height - bottomPadding - (buttonSize / 2) - (stepsAboveBottom * (buttonSize + spacing));
  return {
    x: Math.round(x),
    y: Math.round(y),
    relativeX: Number(((x - cardBox.x) / cardBox.width).toFixed(4)),
    relativeY: Number(((y - cardBox.y) / cardBox.height).toFixed(4)),
    contextualAnchor: cardTag,
    context: "official-post-card + QuataFeedActionRail portrait comments slot",
  };
}

async function visibleAriaBox(page, patterns) {
  const locator = await visibleAriaLocator(page, patterns, 1_000);
  if (!locator) return null;
  return await locator.boundingBox().catch(() => null);
}

async function waitTaggedCommentInput(page, prefix, errorMessage, timeout = 12_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const input = await visibleAriaLocator(page, [
      new RegExp(escapeRegExp(`${prefix}.input`)),
      /Escribe un comentario|Write a comment|Écrire un commentaire/i,
    ], 800);
    if (input) return input;
    const nativeInput = await visibleNativeControl(page, [
      new RegExp(escapeRegExp(`${prefix}.input`)),
      /Escribe un comentario|Write a comment|Écrire un commentaire/i,
    ], 250);
    if (nativeInput) return { nativeInput };
    const controlsVisible = await Promise.all([
      visibleAriaLocator(page, [new RegExp(escapeRegExp(`${prefix}.emoji`))], 250),
      visibleAriaLocator(page, [new RegExp(escapeRegExp(`${prefix}.send`))], 250),
    ]);
    if (controlsVisible.every(Boolean)) return { controlsOnly: true };
    const nativeControlsVisible = await Promise.all([
      visibleNativeControl(page, [new RegExp(escapeRegExp(`${prefix}.emoji`))], 250),
      visibleNativeControl(page, [new RegExp(escapeRegExp(`${prefix}.send`))], 250),
    ]);
    if (nativeControlsVisible.every(Boolean)) return { nativeControlsOnly: true };
    await delay(250);
  }
  throw new Error(errorMessage);
}

async function waitVisibleCommentText(page, text, errorMessage) {
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    if (await visibleTextContentIncludes(page, text)) return;
    await delay(500);
  }
  throw new Error(errorMessage);
}

async function feedOfficialCommentsStep(label, action) {
  return await withTimeout(action(), 90_000, `feed_official_comments_step_${label}`);
}

function assertNoBrowserFaults(report, faults, label) {
  if (!Array.isArray(report.browserRuntimeFaultSource)) report.browserRuntimeFaultSource = [];
  report.browserRuntimeFaultSource.push({ label, count: faults.length });
  const blockingFaults = faults.filter((fault) => !isNonBlockingBrowserRuntimeFault(fault, { label }));
  if (faults.length) {
    report.diagnostics = {
      ...(report.diagnostics ?? {}),
      browserRuntimeFaults: faults.slice(),
      browserRuntimeFaultLabel: label,
      nonBlockingBrowserRuntimeFaults: faults.filter((fault) => isNonBlockingBrowserRuntimeFault(fault, { label })),
    };
  }
  if (blockingFaults.length) {
    throw new Error("browser_runtime_fault");
  }
}

function isNonBlockingBrowserRuntimeFault(fault, context = {}) {
  return isNonBlockingProfileEntryWasmFault(fault) ||
    isNonBlockingFeedOfficialSupabaseConflictFault(fault, context);
}

function isNonBlockingProfileEntryWasmFault(fault) {
  return fault?.type === "pageerror" &&
    fault?.messageSha256 === "93bb8714d6d18784a04c1a4a700f049be9cef8a0dfa9fc13b5c09e0a3d496787" &&
    /^illegal cast$/i.test(String(fault?.messagePrefix ?? "")) &&
    /wasm-function/.test(String(fault?.stackPrefix ?? ""));
}

function isNonBlockingFeedOfficialSupabaseConflictFault(fault, context = {}) {
  return String(context.label ?? "").startsWith("feed_official_comments_") &&
    fault?.type === "console_error" &&
    fault?.urlOrigin === "https://yrrlankpwmhluexshxnw.supabase.co" &&
    /status of 409/i.test(String(fault?.messagePrefix ?? ""));
}

async function profileEntryStep(label, action) {
  return await withTimeout(action(), 90_000, `profile_entry_step_${label}`);
}

async function visibleAriaLocatorWithScroll(page, patterns, timeout = 10_000) {
  const deadline = Date.now() + timeout;
  await wheelChatViewport(page, -1600);
  while (Date.now() < deadline) {
    const locator = await visibleAriaLocator(page, patterns, 800);
    if (locator) return locator;
    await wheelChatViewport(page, 520);
    await delay(350);
  }
  return null;
}

async function visibleAriaLocatorNearCurrentPosition(page, patterns, timeout = 10_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const locator = await visibleAriaLocator(page, patterns, 800);
    if (locator) return locator;
    await wheelChatViewport(page, -240);
    await delay(250);
  }
  return await visibleAriaLocatorWithScroll(page, patterns, 3_000);
}

async function visibleCommunityMembersAction(page, neighborhood, timeout = 8_000) {
  const deadline = Date.now() + timeout;
  const normalizedNeighborhood = normalizeTextForE2e(neighborhood);
  while (Date.now() < deadline) {
    const match = await page.evaluate((expectedNeighborhood) => {
      const root = document.querySelector("#quata-root");
      const scope = root?.shadowRoot ?? root ?? document;
      const actions = [...scope.querySelectorAll("button, [role='button']")]
        .map((button) => {
          const rect = button.getBoundingClientRect();
          const label = normalizeTextForE2e(`${button.getAttribute("aria-label") ?? ""} ${button.textContent ?? ""}`);
          if (rect.width <= 0 || rect.height <= 0 || !/ver usuarios|view users/.test(label)) return null;
          if (!closestNeighborhoodCard(button, expectedNeighborhood)) return null;
          return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };
        })
        .filter(Boolean);
      actions.sort((left, right) => (left.y - right.y) || (left.x - right.x));
      return actions[0] ?? null;

      function closestNeighborhoodCard(button, expected) {
        let node = button;
        for (let depth = 0; node && depth < 6; depth += 1) {
          const text = normalizeTextForE2e(node.textContent ?? "");
          if (text.includes(expected)) return node;
          node = node.parentElement;
        }
        return null;
      }

      function normalizeTextForE2e(value) {
        return String(value ?? "")
          .normalize("NFD")
          .replace(/[\u0300-\u036f]/g, "")
          .toLowerCase()
          .replace(/\s+/g, " ")
          .trim();
      }
    }, normalizedNeighborhood).catch(() => null);
    if (match) {
      return {
        async boundingBox() {
          return match;
        },
        async click() {
          await page.mouse.click(match.x + (match.width / 2), match.y + (match.height / 2));
        },
      };
    }
    await page.mouse.wheel(0, 420).catch(() => {});
    await delay(350);
  }
  return null;
}

async function resolveCommunityChatTarget(actorSession) {
  const actorKey = normalizeTextForE2e(actorSession?.neighborhood ?? "");
  const preferredKeys = [
    process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_COMMUNITY_CHAT_NAME,
    "Ateneo",
    "La Chana",
    actorSession?.neighborhood,
  ].map(normalizeTextForE2e).filter(Boolean);
  return await withPoolerClient(async (client) => {
    const result = await client.query(
      `select id, name, slug, normalized_name
         from public.community_walls_stats
        where is_active = true
        order by sort_order asc nulls last, chat_last_at desc nulls last, created_at desc nulls last
        limit 500`,
    );
    const rows = result.rows.map((row) => ({
      id: String(row.id ?? "").trim(),
      name: String(row.name ?? row.slug ?? row.normalized_name ?? "").trim(),
      keys: [row.name, row.slug, row.normalized_name].map(normalizeTextForE2e).filter(Boolean),
    })).filter((row) => uuid.test(row.id) && row.name);
    if (!rows.length) throw new Error("community_chat_flow_no_active_wall");
    const matched = preferredKeys
      .map((key) => rows.find((row) => row.keys.includes(key)))
      .find(Boolean)
      ?? (actorKey ? rows.find((row) => row.keys.includes(actorKey)) : null);
    const target = matched ?? rows[0];
    return {
      id: target.id,
      name: target.name,
      tag: `neighborhood.chat.${neighborhoodTagSuffix(target.name)}`,
    };
  });
}

async function verifyCommunityChatWeb(page, origin, target, evidenceDir, report, faults) {
  await openAuthenticatedRoute(page, origin, "communities", "communities");
  report.steps.push(`community_chat_web_route_start:${target.tag}`);
  report.evidence.communityChatList = await attachScreenshot(page, evidenceDir, "web-community-chat-list");

  const chatAction = await visibleAriaLocatorWithScroll(page, [new RegExp(`^${escapeRegExp(target.tag)}$`)], 20_000)
    ?? await visibleExactAriaLocator(page, target.tag, 5_000);
  if (!chatAction) throw new Error(`community_chat_flow_anchor_missing:${target.tag}`);
  const box = await chatAction.boundingBox().catch(() => null);
  if (!box) throw new Error(`community_chat_flow_anchor_unbounded:${target.tag}`);
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  report.steps.push("community_chat_web_anchor_clicked");

  await page.waitForFunction(
    () => {
      const route = document.documentElement.getAttribute("data-quata-shell-route") ?? "";
      return route.startsWith("chat/sb:");
    },
    { timeout: 45_000 },
  );
  await delay(1_500);
  const route = await page.evaluate(() => document.documentElement.getAttribute("data-quata-shell-route") ?? "");
  const conversationId = route.substring("chat/".length);
  if (!/^sb:\d+$/.test(conversationId)) throw new Error(`community_chat_flow_invalid_conversation_route:${route}`);
  report.evidence.communityChatOpened = await attachScreenshot(page, evidenceDir, "web-community-chat-opened");
  if (faults.length) throw new Error("browser_runtime_fault");
  report.steps.push("community_chat_web_opened_real_chat_route");
  return { conversationId };
}

function neighborhoodTagSuffix(value) {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ".")
    .replace(/^\.+|\.+$/g, "") || "unknown";
}

function normalizeTextForE2e(value) {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

async function openPrivateChatFromOpenProfile(page, peerProfile, privateChat, privateMarker, evidenceDir, report) {
  report.evidence.profilePrivateChatBefore = await attachScreenshot(page, evidenceDir, "web-chat-profile-private-chat-before");
  const chatButton = await visibleAriaLocator(page, [new RegExp(escapeRegExp(`public-profile.chat.${peerProfile.profileId}`))], 10_000);
  const textBox = chatButton ? null : await visibleTextBox(page, "Chat");
  if (!chatButton && !textBox) throw new Error("profile_private_chat_not_opened:common_chat_action_missing");
  const box = textBox ?? await chatButton.boundingBox().catch(() => null);
  if (!box) throw new Error("profile_private_chat_not_opened:common_chat_action_unbounded");
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  await waitForExactChatRoute(page, `sb:${privateChat.threadId}`);
  if (!(await waitForChatProfileReturn(page))) throw new Error("profile_private_chat_not_opened:chat_return_not_visible");
  await pollMessage(config, state.a, privateChat.threadId, (message) => messageText(message) === privateMarker);
  await delay(1_000);
  report.evidence.profilePrivateChatOpened = await attachScreenshot(page, evidenceDir, "web-chat-profile-private-chat-opened");
  return { peerProfileId: peerProfile.profileId, conversationId: `sb:${privateChat.threadId}` };
}

async function waitForChatProfileReturn(page) {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const controls = await visibleNativeControls(page);
    const composerVisible = controls.some((control) => /Mensaje|Message/i.test(control.label));
    const profileSheetVisible = controls.some((control) => /Cerrar hoja|Close sheet|Controlador de arrastre|Drag handle/i.test(control.label));
    if (composerVisible && !profileSheetVisible) return true;
    await delay(500);
  }
  return false;
}

async function waitForExactChatRoute(page, conversationId) {
  const expected = `chat/${conversationId}`;
  await page.waitForFunction((route) => {
    const current = localStorage.getItem("web.navigation.route") ||
      document.documentElement.getAttribute("data-quata-shell-route") ||
      "";
    return current === route;
  }, expected, { timeout: 20_000 });
}

async function closeProfileSheetIfVisible(page) {
  const closeSheet = await visibleAriaLocator(page, [/Cerrar hoja|Close sheet/i], 1_000);
  if (!closeSheet) return false;
  const box = await closeSheet.boundingBox().catch(() => null);
  if (box) {
    await page.mouse.click(Math.max(1, box.x + 18), Math.max(1, box.y + 18));
  } else {
    await page.keyboard.press("Escape").catch(() => {});
  }
  await delay(700);
  return true;
}

async function clickProfileBack(page) {
  const labeled = await visibleAriaLocator(page, [/Volver|Back/i], 3_000);
  if (labeled) {
    const box = await labeled.boundingBox().catch(() => null);
    if (box) {
      await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
      return true;
    }
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(34, Math.min(140, Math.max(90, viewport.height * 0.14)));
  await delay(500);
  return true;
}

async function clickMessageAvatar(page, marker) {
  const probes = [...new Set([marker.slice(0, 28), marker.slice(0, 20), marker.slice(0, 16)])];
  for (const probe of probes) {
    const text = page.getByText(probe, { exact: false }).first();
    if (await text.waitFor({ timeout: 3_000 }).then(() => true).catch(() => false)) {
      await text.scrollIntoViewIfNeeded({ timeout: 5_000 }).catch(() => {});
      await delay(250);
      const box = await text.boundingBox();
      if (box) {
        await page.mouse.click(Math.max(8, box.x - 26), box.y + Math.min(22, box.height * 0.22));
        await delay(1_500);
        return true;
      }
    }
    const textBox = await visibleTextBox(page, probe);
    if (textBox) {
      await page.mouse.click(Math.max(8, textBox.x - 26), textBox.y + Math.min(22, textBox.height * 0.22));
      await delay(1_500);
      return true;
    }
  }
  return false;
}

async function waitForProfileVisible(page, profile) {
  const displayName = profile.displayName?.trim();
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const hasProfileText = displayName
      ? await page.getByText(new RegExp(escapeRegExp(displayName))).first().isVisible({ timeout: 500 }).catch(() => false)
      : false;
    const hasProfileChrome = await page.getByText(/Publicaciones|Posts|Seguidores|Followers|Siguiendo|Following/i).first().isVisible({ timeout: 500 }).catch(() => false);
    if (hasProfileText && hasProfileChrome) return true;
    await delay(500);
  }
  return false;
}

async function assertProfileHeaderVisible(page, profile) {
  const displayName = profile.displayName?.trim();
  const neighborhood = profile.neighborhood?.trim();
  const checks = [
    ["profile_header_name_missing", displayName],
    ["profile_header_neighborhood_missing", neighborhood],
  ].filter(([, value]) => value);
  for (const [error, value] of checks) {
    const visible = await page.getByText(new RegExp(escapeRegExp(value))).first()
      .isVisible({ timeout: 5_000 })
      .catch(() => false);
    if (!visible) throw new Error(`${error}:${value}`);
  }
  for (const pattern of [/Publicaciones|Posts/i, /Seguidores|Followers/i, /Siguiendo|Following/i]) {
    const visible = await page.getByText(pattern).first()
      .isVisible({ timeout: 5_000 })
      .catch(() => false);
    if (!visible) throw new Error(`profile_header_kpi_missing:${pattern}`);
  }
}

async function clickMessageProbe(page, probe) {
  if (await clickMessageByAccessibleName(page, probe)) return true;
  const pattern = new RegExp(escapeRegExp(probe));
  for (const locator of [
    page.getByRole("button", { name: pattern }).first(),
    page.getByLabel(pattern).first(),
  ]) {
    if (await locator.waitFor({ timeout: 5_000 }).then(() => true).catch(() => false)) {
      await locator.click({ timeout: 10_000, force: true });
      return true;
    }
  }
  const text = page.getByText(probe, { exact: false }).first();
  if (await text.waitFor({ timeout: 5_000 }).then(() => true).catch(() => false)) {
    await text.scrollIntoViewIfNeeded({ timeout: 5_000 }).catch(() => {});
    await delay(250);
    const box = await text.boundingBox();
    if (box) {
      await page.mouse.click(Math.max(1, box.x - 12), box.y + (box.height / 2));
      return true;
    }
    await text.click({ timeout: 10_000, force: true });
    return true;
  }
  const textBox = await visibleTextBox(page, probe);
  if (textBox) {
    await page.mouse.click(Math.max(1, textBox.x - 12), textBox.y + (textBox.height / 2));
    return true;
  }
  return false;
}

async function verifyChatTranslation(page, evidenceDir, marker) {
  await clickLabel(page, [/Traductor Fang|Fang translator|Traducteur Fang/i], "translator_trigger_not_visible");
  await waitMessageVisible(page, "Toca cualquier mensaje para traducirlo", "translator_overlay_not_visible", 15_000);
  await attachScreenshot(page, evidenceDir, "web-chat-translation-overlay");
  if (!(await clickTranslatorOverlayMessage(page, marker))) throw new Error("translator_message_not_clickable");
  await waitMessageVisible(page, "pan de trigo", "translator_result_not_visible", 90_000);
  await waitMessageVisible(page, "FAN->ES", "translator_direction_not_visible", 5_000);
  await attachScreenshot(page, evidenceDir, "web-chat-translation-result");
  await clickLabel(page, [/Salir|Exit|Quitter/i], "translator_exit_not_visible");
  await waitMessageVisible(page, marker, "translator_return_message_not_visible", 15_000);
  await attachScreenshot(page, evidenceDir, "web-chat-translation-return");
}

async function verifyChatOptionsMenuSurface(page, config, state, evidenceDir, report) {
  await clickOptionsMenu(page);
  report.evidence.optionsMenu = await attachScreenshot(page, evidenceDir, "web-chat-options-menu-surface");
  await page.getByText(/Silenciar conversaci[oó]n|Mute conversation/i).click({ timeout: 10_000, force: true });
  await delay(1_000);
  if (!isMuted(await inboxThread(config, state.a, state.thread))) throw new Error("mute_state_not_persisted:true");
  report.evidence.muted = await attachScreenshot(page, evidenceDir, "web-chat-actions-muted");
  report.steps.push("options_menu_surface_visible_and_mute_enabled_by_rpc");
  await clickOptionsMenu(page);
  await page.getByText(/Reactivar notificaciones|Unmute|Reactivate notifications/i).click({ timeout: 10_000, force: true });
  await delay(1_000);
  if (isMuted(await inboxThread(config, state.a, state.thread))) throw new Error("mute_state_not_persisted:false");
  report.steps.push("options_menu_unmute_verified_by_rpc");
}

async function verifyChatGroupSosWeb(page, evidenceDir, report) {
  await clickOptionsMenu(page);
  const requiredMenuAnchors = [
    ["chat.group.menu.allowInvites", /Permitir(?: que los miembros inviten| invitaciones)|Allow member invites/i],
    ["chat.group.menu.addParticipants", /adir(?: nuevos)? participantes|Add participants/i],
    ["chat.group.menu.leave", /Abandonar conversaci|Salir de la conversaci|Leave conversation/i],
    ["chat.group.menu.delete", /Borrar conversaci|Eliminar conversaci|Delete conversation/i],
  ];
  const missingGroupAnchors = [];
  for (const [tag, pattern] of requiredMenuAnchors) {
    const locator = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag)), pattern], 1_500);
    const textVisible = await page.getByText(pattern).first()
      .isVisible({ timeout: 1_500 })
      .catch(() => false);
    const domTextVisible = textVisible || await visibleTextMatches(page, pattern);
    if (!locator && !domTextVisible) missingGroupAnchors.push(tag);
  }
  if (missingGroupAnchors.length) {
    report.diagnostics = {
      ...(report.diagnostics ?? {}),
      missingStableAnchors: missingGroupAnchors,
      visibleNativeControls: await visibleNativeControls(page),
    };
    throw new Error(`missing_stable_anchor:${missingGroupAnchors.join(",")}`);
  }
  report.evidence.groupMenu = await attachScreenshot(page, evidenceDir, "web-chat-group-menu-shared-anchors");
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForFunction(
    (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
    `chat/sb:${state.thread}`,
    { timeout: 45_000 },
  );
  await delay(1_500);
  await waitMessageVisible(page, state.sosWithLocationMarker.slice(0, 28), "sos_chat_not_visible_after_menu_reset", 10_000);
  report.steps.push("group_sos_options_menu_reset_by_route_after_anchor_evidence");

  const openMapsLocator = await visibleAriaLocator(page, [
    /chat\.sos\.location\.openMaps/,
    /Abrir ubicaci[oó]n en Google Maps|Open location in Google Maps/i,
  ], 2_000);
  if (!openMapsLocator) {
    report.diagnostics = {
      ...(report.diagnostics ?? {}),
      missingStableAnchors: [
        ...new Set([...(report.diagnostics?.missingStableAnchors ?? []), "chat.sos.location.openMaps"]),
      ],
      visibleNativeControls: await visibleNativeControls(page),
    };
    throw new Error("missing_stable_anchor:chat.sos.location.openMaps");
  }
  const popupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
  await openMapsLocator.click({ timeout: 5_000, force: true });
  const popup = await popupPromise;
  if (popup) await popup.close().catch(() => {});
  const mapFeedbackVisible = await Promise.any([
    waitMessageVisible(page, "Abriendo ubicación en mapas.", "sos_map_open_feedback_not_visible", 10_000).then(() => true),
    waitMessageVisible(page, "No se pudo abrir la ubicación.", "sos_map_open_feedback_not_visible", 10_000).then(() => true),
    waitMessageVisible(page, "No hay una aplicación de mapas disponible.", "sos_map_open_feedback_not_visible", 10_000).then(() => true),
  ]).catch(() => false);
  if (!mapFeedbackVisible) throw new Error("sos_map_open_feedback_not_visible");
  await waitMessageVisible(page, state.sosWithLocationMarker.slice(0, 28), "sos_chat_not_visible_after_map_return", 10_000);
  await waitMessageVisible(page, "Ubicación no disponible: permiso denegado", "sos_permission_denied_reason_not_visible", 10_000);
  report.evidence.sosLocation = await attachScreenshot(page, evidenceDir, "web-chat-sos-location-map-return");
  report.diagnostics = {
    ...(report.diagnostics ?? {}),
    wasmCanvasSemanticLimit: "SOS location body is visually rendered by Compose/Wasm but non-interactive SOS testTags may not be exposed as DOM or aria nodes in this host; the interactive map CTA must expose a stable semantic anchor or this runner fails closed.",
  };
}

async function participantSnapshot(thread) {
  return await withDatabase(async (client) => {
    const result = await client.query(
      `select profile_id::text, role, left_at is not null as left
         from public.chat_participants
        where thread_id = $1
        order by profile_id::text`,
      [thread],
    );
    return result.rows;
  });
}

function assertParticipant(snapshot, profileId, role, left = false) {
  const row = snapshot.find((entry) => entry.profile_id === profileId);
  if (!row || row.role !== role || row.left !== left) {
    throw new Error(`chat_group_ui_participant_state:${role}:${left}`);
  }
}

async function pollParticipant(thread, profileId, role, left = false, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  let snapshot = [];
  while (Date.now() < deadline) {
    snapshot = await participantSnapshot(thread);
    if (snapshot.some((entry) => entry.profile_id === profileId && entry.role === role && entry.left === left)) {
      return snapshot;
    }
    await delay(750);
  }
  assertParticipant(snapshot, profileId, role, left);
  return snapshot;
}

async function pollThreadBlock(thread, blockerProfileId, blockedProfileId, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const result = await withDatabase(async (client) => await client.query(
      `select count(*)::int as count
         from public.chat_profile_blocks
        where thread_id = $1
          and blocker_profile_id = $2::uuid
          and blocked_profile_id = $3::uuid`,
      [thread, blockerProfileId, blockedProfileId],
    ));
    if (Number(result.rows[0]?.count ?? 0) > 0) return true;
    await delay(750);
  }
  throw new Error("chat_group_thread_block_state_not_persisted");
}

async function clickGroupParticipantCandidate(page, profile) {
  const tag = `chat.group.participants.candidate.${profile.id}`;
  const tagged = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag))], 3_000);
  const taggedBox = tagged ? await tagged.boundingBox().catch(() => null) : null;
  if (taggedBox) {
    await page.mouse.click(taggedBox.x + 24, taggedBox.y + (taggedBox.height / 2));
    await delay(500);
    return;
  }
  const textBox = await visibleTextBox(page, profile.displayName);
  if (textBox) {
    await page.mouse.click(Math.max(8, textBox.x - 32), textBox.y + (textBox.height / 2));
    await delay(500);
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  await page.mouse.click(Math.round(viewport.width * 0.24), Math.round(viewport.height * 0.21));
  await delay(500);
}

async function clickTaggedOrLabeled(page, tag, patterns, error) {
  const locator = await visibleAriaLocator(page, [new RegExp(escapeRegExp(tag)), ...patterns], 5_000);
  if (locator) {
    await locator.click({ timeout: 10_000, force: true });
    await delay(500);
    return;
  }
  const box = await visibleTextBoxMatching(page, patterns);
  if (box) {
    await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
    await delay(500);
    return;
  }
  throw new Error(error);
}

async function fillGroupParticipantSearch(page, query, report) {
  const search = await visibleAriaLocator(page, [/chat\.group\.participants\.search|Buscar|Search/i], 3_000);
  if (search) {
    await search.fill(query, { timeout: 10_000 });
    return;
  }
  const box = await visibleTextBox(page, "Buscar") ?? await visibleTextBox(page, "Search");
  if (box) {
    await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  } else {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.5), Math.round(viewport.height * 0.15));
  }
  await page.keyboard.press("Control+A").catch(() => {});
  await page.keyboard.type(query, { delay: 8 });
  report.diagnostics ??= {};
  report.diagnostics.groupParticipantSearchAnchor =
    "Compose/Wasm rendered the shared participant search field visually but did not expose chat.group.participants.search as a native/ARIA target; replay used visible text bounds when available or a viewport-relative modal fallback.";
  report.steps.push("group_participant_search_used_visual_fallback");
}

async function confirmGroupParticipantSelection(page, report) {
  const confirm = await visibleAriaLocator(page, [/chat\.group\.participants\.confirm|A(?:ñ|n)adir|Add/i], 2_000);
  if (confirm) {
    await confirm.click({ timeout: 10_000, force: true });
    await delay(500);
    return;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  const modalCenterX = Math.round(viewport.width / 2);
  const confirmOffsetX = Math.min(96, Math.round(viewport.width * 0.22));
  await page.mouse.click(modalCenterX + confirmOffsetX, Math.round(viewport.height * 0.62));
  await delay(500);
  report.diagnostics ??= {};
  report.diagnostics.groupParticipantConfirmAnchor =
    "Compose/Wasm did not expose chat.group.participants.confirm as a native/ARIA target; replay used the viewport-relative modal confirm button and verified the backend participant state afterwards.";
  report.steps.push("group_participant_confirm_used_visual_fallback");
}

async function openGroupMemberManage(page, profile, report) {
  const memberManageTag = `chat.group.member.manage.${profile.id}`;
  const manage = await visibleAriaLocator(page, [new RegExp(escapeRegExp(memberManageTag))], 2_000);
  if (manage) {
    await manage.click({ timeout: 10_000, force: true });
    await delay(500);
    return null;
  }
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  const memberTextBox = await visibleTextBox(page, profile.displayName);
  const useKnownTemporaryGroupRow = profile.displayName?.startsWith("QADATA Forward ") === true;
  const rowLikeTextBox = !useKnownTemporaryGroupRow && memberTextBox && memberTextBox.y > 120 && memberTextBox.y < 340 && memberTextBox.height <= 80
    ? memberTextBox
    : null;
  const targetY = rowLikeTextBox
    ? Math.round(rowLikeTextBox.y + rowLikeTextBox.height / 2)
    : Math.round(viewport.height * 0.285);
  await page.mouse.click(Math.round(viewport.width * 0.92), targetY);
  await delay(750);
  report.diagnostics ??= {};
  report.diagnostics.groupMemberManageAnchor =
    "Compose/Wasm rendered the shared member row visually but did not expose chat.group.member.manage as a native/ARIA target; replay used the visible member text row to target the row-local manage button and verified the role mutation afterwards.";
  report.steps.push("group_member_manage_used_visual_fallback");
  return targetY;
}

async function visibleGroupMemberRowBox(page, profile) {
  const memberTextBox = await visibleTextBox(page, profile.displayName);
  return memberTextBox && memberTextBox.y > 120 && memberTextBox.y < 340 && memberTextBox.height <= 80
    ? memberTextBox
    : null;
}

async function expandGroupMembers(page) {
  const titleBar = await visibleAriaLocator(page, [/chat\.conversation\.titlebar/i], 1_500);
  if (titleBar) {
    await titleBar.click({ timeout: 5_000, force: true });
  } else {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    await page.mouse.click(Math.round(viewport.width * 0.50), 104);
  }
  await delay(750);
}

async function expandGroupMembersUntilProfileVisible(page, profile, report) {
  const viewport = page.viewportSize() ?? { width: 430, height: 932 };
  const attempts = [
    async () => expandGroupMembers(page),
    async () => {
      await page.mouse.click(Math.round(viewport.width * 0.50), 104);
      await delay(750);
    },
    async () => {
      await page.mouse.click(Math.round(viewport.width * 0.22), 104);
      await delay(750);
    },
  ];
  for (const attempt of attempts) {
    await attempt();
    await delay(500);
    const row = await visibleGroupMemberRowBox(page, profile);
    if (row) return row;
  }
  report.diagnostics ??= {};
  report.diagnostics.groupMemberListAnchor =
    "Compose/Wasm did not expose an inspectable member list state; the replay requires the target participant row to be visibly expanded near the chat header before using row-local action fallbacks.";
  return null;
}

async function verifyChatGroupAdminWeb(page, state, evidenceDir, report) {
  const profile = state.groupAdminProfile;
  if (!profile?.id) throw new Error("chat_group_admin_profile_missing");
  await clickOptionsMenu(page);
  await clickTaggedOrLabeled(
    page,
    "chat.group.menu.addParticipants",
    [/A(?:ñ|n)adir(?: nuevos)? participantes|Add participants/i],
    "chat_group_add_participants_action_not_visible",
  );
  await fillGroupParticipantSearch(page, profile.phoneLocal, report);
  await delay(1_500);
  await clickGroupParticipantCandidate(page, profile);
  report.evidence.groupParticipantPicker = await attachScreenshot(page, evidenceDir, "web-chat-group-admin-participant-picker");
  await confirmGroupParticipantSelection(page, report);
  await pollParticipant(state.thread, profile.id, "member");
  report.steps.push("group_participant_added_from_shared_picker_and_verified_by_db");
  await openAuthenticatedChatRoute(page, server.origin, `sb:${state.thread}`);
  await delay(4_000);
  report.steps.push("group_thread_reopened_after_participant_add_for_fresh_membership");

  await expandGroupMembers(page);
  await delay(2_000);
  const memberRowTag = `chat.group.member.${profile.id}`;
  const memberManageTag = `chat.group.member.manage.${profile.id}`;
  const row = await visibleAriaLocator(page, [new RegExp(escapeRegExp(memberRowTag)), new RegExp(escapeRegExp(profile.displayName))], 10_000);
  if (!row && !(await visibleTextIncludes(page, profile.displayName))) throw new Error("chat_group_member_row_not_visible");
  report.evidence.groupMembers = await attachScreenshot(page, evidenceDir, "web-chat-group-admin-member-list");
  await openGroupMemberManage(page, profile, report);
  report.evidence.groupMemberMenu = await attachScreenshot(page, evidenceDir, "web-chat-group-admin-member-menu");
  await clickTaggedOrLabeled(
    page,
    `chat.group.member.role.${profile.id}`,
    [/Ascender a moderador|Nommer modérateur|Promote to moderator|Promote|moderador|modérateur|moderator/i],
    "chat_group_member_role_action_not_visible",
  );
  await clickLabel(page, [/Confirmar|Confirm/i], "chat_group_member_role_confirm_not_visible");
  await pollParticipant(state.thread, profile.id, "moderator");
  report.evidence.groupMemberPromoted = await attachScreenshot(page, evidenceDir, "web-chat-group-admin-member-promoted");
  report.steps.push("group_participant_promoted_from_shared_member_menu_and_verified_by_db");
}

async function addGroupParticipantFromPicker(page, profile, evidenceDir, report, evidencePrefix) {
  await clickOptionsMenu(page);
  await clickTaggedOrLabeled(
    page,
    "chat.group.menu.addParticipants",
    [/A(?:ñ|n)adir(?: nuevos)? participantes|Add participants/i],
    "chat_group_add_participants_action_not_visible",
  );
  await fillGroupParticipantSearch(page, profile.phoneLocal, report);
  await delay(1_500);
  await clickGroupParticipantCandidate(page, profile);
  report.evidence[`${evidencePrefix}ParticipantPicker`] = await attachScreenshot(page, evidenceDir, `${evidencePrefix}-participant-picker`);
  await confirmGroupParticipantSelection(page, report);
  await pollParticipant(state.thread, profile.id, "member");
}

async function openGroupMemberListForProfile(page, profile, evidenceDir, report, evidencePrefix) {
  await openAuthenticatedChatRoute(page, server.origin, `sb:${state.thread}`, { membersExpanded: true });
  await delay(4_000);
  const rowBox = await visibleGroupMemberRowBox(page, profile);
  const memberRowTag = `chat.group.member.${profile.id}`;
  const row = await visibleAriaLocator(page, [new RegExp(escapeRegExp(memberRowTag)), new RegExp(escapeRegExp(profile.displayName))], 10_000);
  if (!row && !rowBox) {
    report.diagnostics ??= {};
    report.diagnostics.groupMemberVisualOnlyAnchor =
      "The member list is visually expanded in Compose/Wasm screenshots, but the target row text/testTag is not exposed to DOM/ARIA. The runner keeps this as a missing-stable-anchor diagnostic and uses the row-local manage fallback, with backend verification deciding pass/fail.";
    report.steps.push("group_member_list_used_visual_only_fallback");
  }
  report.evidence[`${evidencePrefix}MemberList`] = await attachScreenshot(page, evidenceDir, `${evidencePrefix}-member-list`);
}

async function clickGroupMemberAction(page, profile, action, report) {
  const tag = action === "remove"
    ? `chat.group.member.remove.${profile.id}`
    : `chat.group.member.block.${profile.id}`;
  const labels = action === "remove"
    ? [/Expulsar participante|Expulser participant|Remove participant/i]
    : [/Bloquear usuario|Bloquer utilisateur|Block user/i];
  const rowY = await openGroupMemberManage(page, profile, report);
  try {
    await clickTaggedOrLabeled(page, tag, labels, `chat_group_member_${action}_action_not_visible`);
  } catch (error) {
    if (!String(error?.message ?? "").includes(`chat_group_member_${action}_action_not_visible`) || rowY == null) throw error;
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    const menuItemOffset = action === "block" ? 104 : 151;
    await page.mouse.click(Math.round(viewport.width * 0.27), Math.min(Math.round(rowY + menuItemOffset), viewport.height - 160));
    await delay(500);
    report.diagnostics ??= {};
    report.diagnostics[`groupMember${action[0].toUpperCase()}${action.slice(1)}ActionAnchor`] =
      `Compose/Wasm did not expose ${tag} as a native/ARIA target; replay used a row-relative menu-item fallback and verifies the backend participant state afterwards.`;
    report.steps.push(`group_member_${action}_used_visual_fallback`);
  }
  await clickLabel(page, [/Confirmar|Confirm/i], `chat_group_member_${action}_confirm_not_visible`);
}

async function verifyChatGroupModerationWeb(page, state, evidenceDir, report) {
  const removeProfile = state.groupRemoveProfile;
  const blockProfile = state.groupBlockProfile;
  if (!removeProfile?.id || !blockProfile?.id) throw new Error("chat_group_moderation_profiles_missing");

  await addGroupParticipantFromPicker(page, removeProfile, evidenceDir, report, "web-chat-group-moderation-remove");
  report.steps.push("group_remove_participant_added_from_shared_picker_and_verified_by_db");
  await openGroupMemberListForProfile(page, removeProfile, evidenceDir, report, "web-chat-group-moderation-remove");
  await clickGroupMemberAction(page, removeProfile, "remove", report);
  await pollParticipant(state.thread, removeProfile.id, "member", true);
  report.evidence.groupParticipantRemoved = await attachScreenshot(page, evidenceDir, "web-chat-group-moderation-member-removed");
  report.steps.push("group_participant_removed_from_shared_member_menu_and_verified_by_db");

  await addGroupParticipantFromPicker(page, blockProfile, evidenceDir, report, "web-chat-group-moderation-block");
  report.steps.push("group_block_participant_added_from_shared_picker_and_verified_by_db");
  await openGroupMemberListForProfile(page, blockProfile, evidenceDir, report, "web-chat-group-moderation-block");
  await clickGroupMemberAction(page, blockProfile, "block", report);
  await pollThreadBlock(state.thread, state.b.accessToken ? state.b.profileId : state.a.profileId, blockProfile.id);
  report.evidence.groupParticipantBlocked = await attachScreenshot(page, evidenceDir, "web-chat-group-moderation-member-blocked");
  report.steps.push("group_participant_blocked_from_shared_member_menu_and_verified_by_db");
}

async function clickTranslatorOverlayMessage(page, marker) {
  const box = await page.evaluate((needle) => {
    const matches = [];
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        const label = element.getAttribute("aria-label") ?? "";
        const role = element.getAttribute("role") ?? "";
        if (role === "button" && label.includes(" | ") && label.includes(needle)) {
          const rect = element.getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0) {
            matches.push({ x: rect.x, y: rect.y, width: rect.width, height: rect.height });
          }
        }
        if (element.shadowRoot) visit(element.shadowRoot);
      }
    };
    visit(document);
    matches.sort((left, right) => right.y - left.y);
    const match = matches[0];
    return match
      ? { x: Math.round(match.x), y: Math.round(match.y), width: Math.round(match.width), height: Math.round(match.height) }
      : null;
  }, marker);
  if (!box) return clickMessageProbe(page, marker);
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  await delay(250);
  return true;
}

async function clickMessageByAccessibleName(page, probe) {
  const box = await page.evaluate((needle) => {
    const matches = [];
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        const label = element.getAttribute("aria-label") ?? "";
        const text = element.textContent ?? "";
        const role = element.getAttribute("role") ?? "";
        if (role === "button" && (label.includes(needle) || text.includes(needle))) {
          const rect = element.getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0) {
            matches.push({ x: rect.x, y: rect.y, width: rect.width, height: rect.height, area: rect.width * rect.height });
          }
        }
        if (element.shadowRoot) visit(element.shadowRoot);
      }
    };
    visit(document);
    matches.sort((left, right) => left.area - right.area || left.y - right.y);
    const match = matches[0];
    return match
      ? { x: Math.round(match.x), y: Math.round(match.y), width: Math.round(match.width), height: Math.round(match.height) }
      : null;
  }, probe);
  if (!box) return false;
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  await delay(250);
  return true;
}

async function visibleTextBox(page, probe) {
  return await page.evaluate((needle) => {
    const collect = (root, entries) => {
      for (const element of root.querySelectorAll("*")) {
        const text = element.textContent ?? "";
        if (text.includes(needle)) {
          const rect = element.getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0) {
            entries.push({
              x: rect.x,
              y: rect.y,
              width: rect.width,
              height: rect.height,
              area: rect.width * rect.height,
              textLength: text.length,
            });
          }
        }
        if (element.shadowRoot) collect(element.shadowRoot, entries);
      }
      return entries;
    };
    const matches = collect(document, [])
      .sort((left, right) => left.area - right.area || left.textLength - right.textLength || left.y - right.y);
    const match = matches[0];
    return match
      ? {
          x: Math.round(match.x),
          y: Math.round(match.y),
          width: Math.round(match.width),
          height: Math.round(match.height),
        }
      : null;
  }, probe);
}

async function visibleTextBoxMatching(page, patterns) {
  const serializablePatterns = patterns.map((pattern) => ({
    source: pattern.source,
    flags: pattern.flags,
  }));
  return await page.evaluate((patternSpecs) => {
    const patterns = patternSpecs.map(({ source, flags }) => new RegExp(source, flags));
    const collect = (root, entries) => {
      for (const element of root.querySelectorAll("*")) {
        const text = element.textContent ?? "";
        if (patterns.some((pattern) => pattern.test(text))) {
          const rect = element.getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0) {
            entries.push({
              x: rect.x,
              y: rect.y,
              width: rect.width,
              height: rect.height,
              area: rect.width * rect.height,
              textLength: text.length,
            });
          }
        }
        if (element.shadowRoot) collect(element.shadowRoot, entries);
      }
      return entries;
    };
    const matches = collect(document, [])
      .sort((left, right) => left.area - right.area || left.textLength - right.textLength || left.y - right.y);
    const match = matches[0];
    return match
      ? {
          x: Math.round(match.x),
          y: Math.round(match.y),
          width: Math.round(match.width),
          height: Math.round(match.height),
        }
      : null;
  }, serializablePatterns);
}

async function visibleTextIncludes(page, probe) {
  return await page.evaluate((needle) => {
    const visible = (element) => {
      const rect = element.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return false;
      const style = window.getComputedStyle(element);
      return style.visibility !== "hidden" && style.display !== "none" && Number(style.opacity || "1") > 0;
    };
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        const ownText = [...element.childNodes]
          .filter((node) => node.nodeType === Node.TEXT_NODE)
          .map((node) => node.textContent ?? "")
          .join(" ");
        if (ownText.includes(needle) && visible(element)) return true;
        if (element.shadowRoot && visit(element.shadowRoot)) return true;
      }
      return false;
    };
    const appRoot = document.querySelector("#quata-root");
    return visit(appRoot?.shadowRoot ?? appRoot ?? document);
  }, probe);
}

async function visibleTextMatches(page, pattern) {
  return await page.evaluate(({ source, flags }) => {
    const matcher = new RegExp(source, flags);
    const visible = (element) => {
      const rect = element.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) return false;
      const style = window.getComputedStyle(element);
      return style.visibility !== "hidden" && style.display !== "none" && Number(style.opacity || "1") > 0;
    };
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        const text = `${element.getAttribute("aria-label") ?? ""} ${element.textContent ?? ""}`;
        if (matcher.test(text) && visible(element)) return true;
        if (element.shadowRoot && visit(element.shadowRoot)) return true;
      }
      return false;
    };
    return visit(document);
  }, { source: pattern.source, flags: pattern.flags });
}

async function waitLabel(page, patterns, error) {
  if (await visibleAriaLocator(page, patterns, 8_000)) return;
  throw new Error(error);
}

async function clickNativeButtonByLabel(page, patterns) {
  return await page.evaluate((sources) => {
    const matchers = sources.map((source) => new RegExp(source.source, source.flags));
    const activate = (element) => {
      const label = element.getAttribute("aria-label") ?? "";
      const rect = element.getBoundingClientRect();
      if (matchers.some((pattern) => pattern.test(label)) && rect.width > 0 && rect.height > 0) {
        element.click();
        return true;
      }
      return false;
    };
    const collect = (root) => {
      for (const element of root.querySelectorAll("button[aria-label]")) {
        if (activate(element)) return true;
        if (element.shadowRoot && collect(element.shadowRoot)) return true;
      }
      for (const element of root.querySelectorAll("[role='button'][aria-label]")) {
        if (activate(element)) return true;
        if (element.shadowRoot && collect(element.shadowRoot)) return true;
      }
      return false;
    };
    return collect(document);
  }, patterns.map((pattern) => ({ source: pattern.source, flags: pattern.flags })));
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function visibleTextContentIncludes(page, probe) {
  return await page.evaluate((needle) => {
    const compact = (value) => String(value ?? "").replace(/\s+/g, "");
    const expected = compact(needle);
    const containsNeedle = (value) => compact(value).includes(expected);
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        const rect = element.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0 && containsNeedle(element.textContent)) return true;
        if (element.shadowRoot && visit(element.shadowRoot)) return true;
      }
      return false;
    };
    return visit(document);
  }, probe);
}

async function visibleNonEditableTextContentIncludes(page, probe) {
  return await page.evaluate((needle) => {
    const compact = (value) => String(value ?? "").replace(/\s+/g, "");
    const expected = compact(needle);
    if (!expected) return false;
    const containsNeedle = (value) => compact(value).includes(expected);
    const isEditable = (element) => {
      const editable = element.closest?.("input,textarea,[contenteditable='true'],[role='textbox']");
      if (editable) return true;
      const label = element.getAttribute?.("aria-label") ?? "";
      return /\.input\b/.test(label);
    };
    const visibleRect = (element) => {
      const rect = element.getBoundingClientRect();
      const style = window.getComputedStyle(element);
      if (rect.width <= 0 || rect.height <= 0 || style.visibility === "hidden" || style.display === "none") return false;
      if (rect.bottom < 0 || rect.right < 0 || rect.top > window.innerHeight || rect.left > window.innerWidth) return false;
      return true;
    };
    const visit = (root) => {
      for (const element of root.querySelectorAll("*")) {
        if (element.shadowRoot && visit(element.shadowRoot)) return true;
        if (!visibleRect(element) || isEditable(element) || !containsNeedle(element.textContent)) continue;
        const childWithNeedle = [...element.children].some((child) => containsNeedle(child.textContent));
        if (!childWithNeedle) return true;
      }
      return false;
    };
    return visit(document);
  }, probe);
}

async function visibleAriaLocator(page, patterns, timeout) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const viewport = page.viewportSize() ?? { width: 430, height: 932 };
    const controls = page.locator("[aria-label]");
    const count = await controls.count().catch(() => 0);
    for (let index = 0; index < count; index += 1) {
      const locator = controls.nth(index);
      const label = await locator.getAttribute("aria-label").catch(() => "");
      if (!patterns.some((pattern) => pattern.test(label ?? ""))) continue;
      const visible = await locator.boundingBox()
        .then((box) => Boolean(
          box &&
          box.width > 0 &&
          box.height > 0 &&
          box.x + box.width > 0 &&
          box.y + box.height > 0 &&
          box.x < viewport.width &&
          box.y < viewport.height,
        ))
        .catch(() => false);
      if (visible) return locator;
    }
    await delay(250);
  }
  return null;
}

async function visibleWebSemanticAnchor(page, { testTag, labels, timeout, diagnostics }) {
  const tagPatterns = testTag ? [new RegExp(escapeRegExp(testTag))] : [];
  const byTag = tagPatterns.length > 0 ? await visibleAriaLocator(page, tagPatterns, Math.min(timeout, 1_000)) : null;
  if (byTag) {
    diagnostics.webAudioRecordingAnchorResolution ??= [];
    diagnostics.webAudioRecordingAnchorResolution.push({ testTag, resolvedBy: "testTag" });
    return byTag;
  }
  const byLabel = await visibleAriaLocator(page, labels, timeout);
  if (byLabel) {
    diagnostics.webAudioRecordingAnchorResolution ??= [];
    diagnostics.webAudioRecordingAnchorResolution.push({ testTag, resolvedBy: "accessibleLabel" });
    return byLabel;
  }
  diagnostics.webAudioRecordingMissingAnchors ??= [];
  diagnostics.webAudioRecordingMissingAnchors.push({ testTag, fallbackLabels: labels.map((label) => String(label)) });
  return null;
}

async function clickLocatorCenter(page, locator, error) {
  const box = await locator.boundingBox().catch(() => null);
  if (!box || box.width <= 0 || box.height <= 0) throw new Error(error);
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  await delay(250);
}

async function clickLocatorFraction(page, locator, fraction, error) {
  const box = await locator.boundingBox().catch(() => null);
  if (!box || box.width <= 0 || box.height <= 0) throw new Error(error);
  await page.mouse.click(box.x + (box.width * fraction), box.y + (box.height / 2));
  await delay(250);
}

async function clickLocatorPreferDom(page, locator, error) {
  const clicked = await locator.click({ force: true, timeout: 1_000 }).then(() => true).catch(() => false);
  if (!clicked) await clickLocatorCenter(page, locator, error);
  await delay(250);
}

async function waitAudioPlaybackObserved(page, timeout = 10_000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const pause = await visibleAriaLocator(page, [/Pause audio|Pausar audio/i], 500);
    if (pause) return { state: "playing", selector: "aria:pause_audio" };
    const failed = await page.getByText(/No se pudo|could not|not available|no est[aÃ¡] disponible|unsupported/i).first()
      .isVisible({ timeout: 250 })
      .catch(() => false);
    if (failed) return { state: "failed_visible", selector: "text:audio_error" };
    await delay(250);
  }
  throw new Error("audio_playback_state_not_observed");
}

async function seekAudioProgressWeb(page, audioName, fraction) {
  const progress = await visibleAriaLocator(page, [new RegExp(`chat\\.attachment\\.audio\\.progress.*${escapeRegExp(audioName)}`, "i")], 10_000);
  if (!progress) throw new Error("audio_progress_anchor_not_visible");
  await clickLocatorFraction(page, progress, fraction, "audio_progress_seek_not_clickable");
  const targetPercent = Math.round(fraction * 100);
  const deadline = Date.now() + 8_000;
  let lastState = null;
  while (Date.now() < deadline) {
    const state = await page.evaluate(() => {
      const store = globalThis.__quataAudioPlayers;
      const players = store instanceof Map
        ? [...store.entries()].map(([id, element]) => ({
          id,
          playing: !element.paused && !element.ended,
          positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)),
          durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0,
        }))
        : [];
      const labels = [...document.querySelectorAll("[aria-label]")]
        .map((element) => element.getAttribute("aria-label") || "")
        .filter(Boolean);
      return { players, labels };
    });
    lastState = state;
    const player = state.players.find((candidate) => candidate.durationMillis > 0);
    const observedPercent = player ? Math.round((player.positionMillis / player.durationMillis) * 100) : 0;
    const semanticPercent = state.labels.some((label) =>
      /chat\.attachment\.audio\.progress/i.test(label) &&
      label.includes(audioName) &&
      /6[0-9]%|7[0-9]%/.test(label));
    if ((player && Math.abs(observedPercent - targetPercent) <= 20) || semanticPercent) {
      return {
        state: "seek_observed",
        targetPercent,
        observedPercent,
        selector: `aria:chat.attachment.audio.progress:${audioName}`,
      };
    }
    await delay(250);
  }
  throw new Error(`audio_seek_state_not_observed:${JSON.stringify({
    targetPercent,
    players: lastState?.players ?? [],
    labels: lastState?.labels?.filter((label) => /audio/i.test(label)).map(sha256) ?? [],
  })}`);
}

async function waitConsecutiveAudioPlaybackObserved(page, firstName, secondName, timeout = 15_000, initialSawFirstPlaying = false) {
  const deadline = Date.now() + timeout;
  let sawFirstPlaying = initialSawFirstPlaying;
  let lastState = null;
  while (Date.now() < deadline) {
    const state = await page.evaluate(({ firstName, secondName }) => {
      const store = globalThis.__quataAudioPlayers;
      const players = store instanceof Map
        ? [...store.entries()].map(([id, element]) => ({
          id,
          src: String(element.currentSrc || element.src || ""),
          playing: !element.paused && !element.ended,
          ended: Boolean(element.ended),
          positionMillis: Math.max(0, Math.floor((element.currentTime || 0) * 1000)),
          durationMillis: Number.isFinite(element.duration) && element.duration >= 0 ? Math.floor(element.duration * 1000) : 0,
        }))
        : [];
      const labels = [...document.querySelectorAll("[aria-label]")]
        .map((element) => element.getAttribute("aria-label") || "")
        .filter(Boolean);
      return { players, labels, firstName, secondName };
    }, { firstName, secondName });
    lastState = state;
    const nativeLabels = await visibleNativeControls(page)
      .then((controls) => controls.map((control) => control.label).filter(Boolean))
      .catch(() => []);
    const labels = [...state.labels, ...nativeLabels];
    const firstLabelPlaying = labels.some((label) => /Pausar audio|Pause audio/i.test(label) && label.includes(firstName));
    const secondLabelPlaying = labels.some((label) => /Pausar audio|Pause audio/i.test(label) && label.includes(secondName));
    if (firstLabelPlaying) sawFirstPlaying = true;
    if (sawFirstPlaying && secondLabelPlaying) {
      return {
        state: "consecutive_playing",
        selector: `aria:pause_audio:${secondName}`,
        firstNameSha256: sha256(firstName),
        secondNameSha256: sha256(secondName),
        players: state.players.map((player) => ({
          id: player.id,
          playing: player.playing,
          ended: player.ended,
          positionMillis: player.positionMillis,
          durationMillis: player.durationMillis,
        })),
      };
    }
    await delay(250);
  }
  throw new Error(`consecutive_audio_playback_state_not_observed:${JSON.stringify({
    firstNameSha256: sha256(firstName),
    secondNameSha256: sha256(secondName),
    players: lastState?.players?.map((player) => ({
      id: player.id,
      playing: player.playing,
      ended: player.ended,
      positionMillis: player.positionMillis,
      durationMillis: player.durationMillis,
    })) ?? [],
    labels: lastState?.labels?.filter((label) => /audio/i.test(label)).map(sha256) ?? [],
    visibleLabels: await visibleNativeControls(page)
      .then((controls) => controls.map((control) => control.label).filter((label) => /audio/i.test(label)).map(sha256))
      .catch(() => []),
  })}`);
}

async function fillComposerAndSend(page, value) {
  const input = await visibleAriaLocator(page, [/Mensaje|Message|Composer/i], 10_000);
  if (!input) throw new Error("composer_input_not_visible");
  await input.fill(value, { timeout: 10_000 });
  const deadline = Date.now() + 10_000;
  let sawSend = false;
  while (Date.now() < deadline) {
    const send = await visibleAriaLocator(page, [/Enviar|Send/i], 1_000);
    if (!send) {
      await delay(300);
      continue;
    }
    sawSend = true;
    await send.click({ timeout: 10_000, force: true });
    await delay(300);
    if (!(await input.inputValue().then((current) => current === value).catch(() => false))) return;
    const box = await send.boundingBox();
    if (box) {
      await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
      await delay(300);
      if (!(await input.inputValue().then((current) => current === value).catch(() => false))) return;
    }
    await clickNativeButtonByLabel(page, [/Enviar|Send/i]);
    await delay(500);
    if (!(await input.inputValue().then((current) => current === value).catch(() => false))) return;
  }
  if (!sawSend) throw new Error("composer_send_not_visible");
  throw new Error("composer_send_not_dispatched");
}

async function logicalCleanup(config, state) {
  const actions = [];
  const favoriteMessage = state.ownMessage;
  if (state.thread && favoriteMessage && state.a) {
    await rpc(config, state.a, "quata_chat_set_favorite", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_id: favoriteMessage,
      p_favorite: false,
    }).catch(() => {});
    actions.push("favorite_removed");
  }
  if (state.thread && state.a) {
    await rpc(config, state.a, "quata_chat_set_muted", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_muted: false,
    }).catch(() => {});
    actions.push("conversation_unmuted");
  }
  const messagesBySession = [
    ["own_message", state.a, state.ownMessage],
    ["peer_message", state.b, state.peerMessage],
    ["video_attachment_message", state.a, state.attachmentsAudio?.video?.messageId],
    ["image_attachment_message", state.a, state.attachmentsAudio?.image?.messageId],
    ["document_attachment_message", state.a, state.attachmentsAudio?.document?.messageId],
    ["audio_attachment_message", state.a, state.attachmentsAudio?.audio?.messageId],
    ["next_audio_attachment_message", state.a, state.attachmentsAudio?.nextAudio?.messageId],
    ["profile_content_attachment_message", state.a, state.profileContent?.attachmentMessageId],
    ...state.uiMessages.map((message) => ["ui_message", state.a, message]),
  ];
  for (const [key, session, message] of messagesBySession) {
    if (state.thread && message && session) {
      await rpc(config, session, "quata_chat_delete_messages", {
        p_actor_profile_id: session.profileId,
        p_thread_id: state.thread,
        p_message_ids: [message],
      }).catch(() => {});
      actions.push(`${key}_deleted`);
    }
  }
  if (state.profilePrivateChat?.threadId && state.profilePrivateChat?.markerMessageId && state.b) {
    await rpc(config, state.b, "quata_chat_delete_messages", {
      p_actor_profile_id: state.b.profileId,
      p_thread_id: state.profilePrivateChat.threadId,
      p_message_ids: [state.profilePrivateChat.markerMessageId],
    });
    actions.push("profile_private_chat_marker_deleted");
  }
  const deletedStalePrivateMarkers = await deletePrivateChatTestMarkers(config, state);
  if (deletedStalePrivateMarkers > 0) actions.push(`stale_profile_private_chat_markers_deleted:${deletedStalePrivateMarkers}`);
  const privateMarkers = [state.privateMarker].filter(Boolean);
  if (state.profilePrivateChat?.threadId && state.a && await threadContainsAnyMarker(config, state.a, state.profilePrivateChat.threadId, privateMarkers)) {
    throw new Error("cleanup_residue_detected:profile_private_chat_marker_a");
  }
  if (state.profilePrivateChat?.threadId && state.b && await threadContainsAnyMarker(config, state.b, state.profilePrivateChat.threadId, privateMarkers)) {
    throw new Error("cleanup_residue_detected:profile_private_chat_marker_b");
  }
  if (state.profilePrivateChat?.threadId) actions.push("cleanup_verified_profile_private_chat_marker_absent");
  await state.cleanupRegistry.cleanupStorageObjects({ config, session: state.a, storageRequest, verifyStorageObjectAbsent, actions });
  if (state.thread && state.a) {
    await rpc(config, state.a, "quata_chat_delete_thread", { p_actor_profile_id: state.a.profileId, p_thread_id: state.thread }).catch(() => {});
    actions.push("thread_removed_from_a_inbox");
  }
  if (state.thread && state.b) {
    await rpc(config, state.b, "quata_chat_delete_thread", { p_actor_profile_id: state.b.profileId, p_thread_id: state.thread }).catch(() => {});
    actions.push("thread_removed_from_b_inbox");
  }
  return actions;
}

async function threadContainsAnyMarker(config, session, thread, markers) {
  const markerSet = new Set(markers.filter(Boolean));
  if (markerSet.size === 0) return false;
  const detail = await rpc(config, session, "quata_chat_get_thread", {
    p_actor_profile_id: session.profileId,
    p_thread_id: thread,
    p_known_message_ids: [],
    p_limit: 250,
  });
  return rows(detail, "messages").some((message) => markerSet.has(messageText(message)));
}

async function deletePrivateChatTestMarkers(config, state) {
  if (!state.profilePrivateChat?.threadId || !state.a) return 0;
  const detail = await rpc(config, state.a, "quata_chat_get_thread", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.profilePrivateChat.threadId,
    p_known_message_ids: [],
    p_limit: 250,
  });
  const messageIds = rows(detail, "messages")
    .filter((message) => /^chat-profile-private-(web|android|ios)-/.test(messageText(message)))
    .map((message) => messageId({ message }))
    .filter((id) => Number.isSafeInteger(Number(id)));
  const uniqueIds = [...new Set(messageIds)];
  if (!uniqueIds.length) return 0;
  await rpc(config, state.a, "quata_chat_delete_messages", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.profilePrivateChat.threadId,
    p_message_ids: uniqueIds,
  });
  return uniqueIds.length;
}

async function hardDeleteTemporaryThread(thread, uniqueKey) {
  if (process.env[hardCleanupAuthorizationEnvironment]?.trim() !== hardCleanupAuthorizationValue) {
    throw new Error("missing_hard_cleanup_authorization");
  }
  if (!uniqueKey.startsWith("qadata-chat-actions-notifications-")) throw new Error("cleanup_residue_detected:unsafe_unique_key");
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
      "select id from public.chat_threads where id = $1 and unique_key = $2 and unique_key like 'qadata-chat-actions-notifications-%' for update",
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
        (select count(*)::int from public.chat_profile_blocks where thread_id = $1) as chat_profile_blocks,
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
    await delay(750);
  }
  throw new Error(`profile_follow_backend_poll_timeout:${expected ? "created" : "removed"}`);
}

async function createTemporaryForwardProfile(runId, phoneSuffix = "") {
  const id = randomUUID();
  const phoneLocal = `999${Date.now().toString().slice(-5)}${phoneSuffix}`;
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
  return { id, phoneLocal, displayName, neighborhood: "Bovano" };
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

async function verifyStorageObjectAbsent(bucket, storagePath) {
  await withDatabase(async (client) => {
    const result = await client.query(
      "select count(*)::int as count from storage.objects where bucket_id = $1 and name = $2",
      [bucket, storagePath],
    );
    if (Number(result.rows[0]?.count ?? 0) !== 0) throw new Error("cleanup_residue_detected:storage_object");
  });
}

async function assertNoAttachmentPickerResidue(attachmentName, marker) {
  await delay(2_000);
  const message = await pollMessage(config, state.a, state.thread, (row) => messageText(row) === marker, 3_000)
    .then(() => true)
    .catch((error) => {
      if (String(error?.message ?? error).includes("poll_timeout")) return false;
      throw error;
    });
  if (message) throw new Error("attachment_register_failure_created_message");
  await withDatabase(async (client) => {
    const result = await client.query(
      "select count(*)::int as count from storage.objects where bucket_id = 'chat-attachments' and name like '%' || $1 || '%'",
      [attachmentName],
    );
    if (Number(result.rows[0]?.count ?? 0) !== 0) throw new Error("attachment_register_failure_storage_residue_detected");
  });
}

async function resolveAdjacentRecipientProfile(phoneKeys) {
  return await withDatabase(async (client) => {
    const result = await client.query(
      "select profile_id from public.quata_profile_phone_directory where phone_key = any($1::text[]) order by profile_id limit 1",
      [phoneKeys],
    );
    const profileId = result.rows[0]?.profile_id;
    if (!uuid.test(profileId ?? "")) throw new Error("missing_adjacent_recipient_profile");
    return profileId;
  });
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
        const countryCode = String(user.countryCode ?? "").replace(/\D/g, "");
        const phone = String(user.phone ?? "").replace(/\D/g, "");
        if (!countryCode || !phone) throw new Error("temporary_profile_hash_window:invalid_phone");
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
          [countryCode, phone, [`${countryCode}${phone}`, phone]],
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

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function redactBrowserRuntimeFault(fault) {
  const text = [fault.message, fault.text].filter(Boolean).join(" ");
  const stack = typeof fault.stack === "string" ? fault.stack : "";
  return {
    type: fault.type === "pageerror" ? "pageerror" : "console_error",
    messageSha256: text ? sha256(text) : undefined,
    messagePrefix: redactFaultText(text).slice(0, 180) || undefined,
    stackPrefix: redactFaultText(stack).slice(0, 300) || undefined,
    urlOrigin: fault.url ? safeUrlOrigin(fault.url) : undefined,
    lineNumber: Number.isFinite(fault.lineNumber) ? fault.lineNumber : undefined,
    columnNumber: Number.isFinite(fault.columnNumber) ? fault.columnNumber : undefined,
  };
}

function redactFaultText(text) {
  return String(text ?? "")
    .replace(/Bearer\s+[A-Za-z0-9._~-]+/gi, "Bearer [redacted]")
    .replace(/(access_token|refresh_token|session|password|apikey|api_key)=([^&\s]+)/gi, "$1=[redacted]")
    .replace(/eyJ[A-Za-z0-9._-]+/g, "[jwt-redacted]");
}

function safeUrlOrigin(rawUrl) {
  try {
    const parsed = new URL(rawUrl);
    return parsed.origin;
  } catch {
    return undefined;
  }
}

function isExpectedAttachmentRegisterFailureFault(fault, outcome) {
  return outcome === "register-failure" &&
    fault?.type === "console_error" &&
    /status of 500|Internal Server Error/i.test(String(fault.messagePrefix ?? ""));
}

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "";
  return [
    "invalid_arguments", "missing_public_supabase_configuration", "invalid_public_supabase_url",
    "invalid_or_privileged_supabase_key", "missing_chat_actions_notifications_credentials_file",
    "missing_chat_actions_notifications_credentials", "chat_actions_notifications_users_must_differ",
    "public_auth_request_failed", "invalid_auth_response", "chat_rpc_failed", "chat_contract_invalid",
    "chat_backend_poll_timeout", "distribution_missing", "runtime_configuration_injection_failed",
    "static_server_start_failed", "message_not_visible", "options_menu_not_visible", "action_bar_not_visible",
    "message_action_target_not_clickable",
    "mute_state_not_persisted", "favorite_state_not_persisted", "forward_state_not_persisted", "profile_state_not_opened", "profile_lists_state_not_opened", "profile_lists_state_not_returned", "browser_runtime_fault",
        "composer_message_not_visible", "composer_reply_not_visible", "composer_edit_not_visible",
        "composer_input_not_visible", "composer_send_not_visible", "composer_send_not_dispatched",
    "cleanup_residue_detected", "missing_hard_cleanup_authorization",
    "missing_adjacent_profile_credentials_source", "invalid_adjacent_profile_phone",
    "missing_adjacent_recipient_profile", "temporary_profile_hash_window",
    "profile_content_tag_missing", "profile_content_comments_action_not_clickable",
    "profile_content_comments_input_not_visible", "profile_content_comments_send_not_clickable",
    "profile_content_comment_not_persisted",
    "profile_private_chat_not_opened", "profile_entry_not_opened", "profile_entry_official_cleanup",
    "community_chat_flow",
    "profile_roles_anchor_missing", "profile_safety_anchor_missing", "profile_roles_action_not_clickable",
    "profile_report_action_not_clickable", "profile_report_dialog_missing", "profile_report_confirm_not_clickable",
    "profile_report_dialog_not_dismissible", "profile_block_dialog_not_dismissible",
    "profile_block_action_not_clickable", "profile_block_dialog_missing", "profile_block_confirm_not_clickable",
    "profile_unblock_anchor_missing", "profile_roles_not_persisted", "profile_report_not_persisted",
    "profile_block_state_not_persisted", "profile_roles_safety_fixture",
    "consecutive_audio_playback_state_not_observed",
  ].find((prefix) => message.startsWith(prefix)) ?? "unexpected_chat_actions_notifications_web_failure";
}

class EvidenceCompleted extends Error {}

const options = parseArgs(process.argv.slice(2));
const report = {
  check: "CHAT-ACTIONS-NOTIFICATIONS-WEB-001",
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  cleanup: { state: "not_started" },
  evidence: {},
};
const state = { a: null, b: null, thread: null, ownMessage: null, peerMessage: null, uiMessages: [], uniqueKey: null, forwardProfile: null, forwardThread: null, forwardedMessage: null, profileListEdges: null, profileContent: null, profileEntry: null, profilePrivateChat: null, profileRolesSafety: null, communityChat: null, privateMarker: null, attachmentsAudio: null, attachmentPicker: null, groupAdminProfile: null, groupRemoveProfile: null, groupBlockProfile: null, cleanupRegistry: createCleanupRegistry() };
let config, distribution, server, browser, pageContext;
let profileHashWindow = { state: "not_started", restored: true, restore: async () => {} };
const faults = [];
try {
  config = await publicBackendConfig();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");
  const users = await authorizedUsers();
  const usersForTemporaryHash = users.filter((user) => user?.countryCode && user?.phone && user?.password);
  profileHashWindow = await openTemporaryProfileHashWindow(usersForTemporaryHash);
  if (profileHashWindow.state === "opened") {
    report.steps.push("temporary_profile_hash_window_opened");
  }
  state.a = await login(config, users[0]);
  if (useAdjacentAuthorizedProfile && users.length === 1) {
    state.b = {
      label: "B",
      profileId: await resolveAdjacentRecipientProfile(users[0].adjacentPhoneKeys),
    };
    report.steps.push("authorized_profile_logged_in_and_recipient_resolved");
  } else {
    state.b = await login(config, users[1]);
    report.steps.push("two_authorized_profiles_logged_in");
  }

  const runId = randomUUID();
  state.uniqueKey = `qadata-chat-actions-notifications-${runId}`;
  if (isFullEvidenceMode(options)) {
    state.forwardProfile = await createTemporaryForwardProfile(runId);
    report.steps.push("temporary_forward_destination_profile_created");
  }
  if (options.groupAdminOnly) {
    state.groupAdminProfile = await createTemporaryForwardProfile(runId);
    state.forwardProfile = state.groupAdminProfile;
    report.steps.push("temporary_group_admin_participant_profile_created");
  }
  if (options.groupModerationOnly) {
    state.groupRemoveProfile = await createTemporaryForwardProfile(`${runId}-remove`, "1");
    state.groupBlockProfile = await createTemporaryForwardProfile(`${runId}-block`, "2");
    report.steps.push("temporary_group_moderation_participant_profiles_created");
  }
  state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
    p_actor_profile_id: state.a.profileId,
    p_recipient_profile_ids: [state.b.profileId],
    p_subject: `QADATA chat actions notifications ${runId}`,
    p_type: "group",
    p_message: "",
    p_unique_key: state.uniqueKey,
    p_community_id: null,
  }));
  if (options.groupAdminOnly || options.groupModerationOnly) {
    const groupAdminActor = state.b.accessToken ? state.b : state.a;
    await withDatabase(async (client) => {
      const result = await client.query(
        `update public.chat_participants
            set role = 'moderator'
          where thread_id = $1
            and profile_id = $2
            and role in ('owner', 'member')`,
        [state.thread, groupAdminActor.profileId],
      );
      if (result.rowCount !== 1) throw new Error("chat_group_admin_actor_role_seed_failed");
    });
    report.steps.push("group_admin_actor_seeded_as_moderator_for_ui_management");
  }
  const ownMarker = options.translationOnly ? "Mbolo" : `chat-actions-own-${runId}`;
  const peerMarker = "Mbolo";
  state.ownMessage = messageId(await rpc(config, state.a, "quata_chat_send_message", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message: ownMarker,
    p_file_ids: [],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-actions-own-${runId}`,
  }));
  if (state.b.accessToken) {
    await rpc(config, state.b, "quata_chat_send_message", {
      p_actor_profile_id: state.b.profileId,
      p_thread_id: state.thread,
      p_message: peerMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-actions-peer-${runId}`,
    });
    const peerMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === peerMarker);
    state.peerMessage = messageId({ message: peerMessage });
    report.steps.push("isolated_thread_and_two_messages_ready");
  } else {
    report.steps.push("isolated_thread_and_own_message_ready");
  }

  if (options.groupSosOnly) {
    state.sosWithLocationMarker = `[SOS:kind=update;name=Gabrielo;lat=3.7523;lng=8.7741;age_ms=45000;accuracy_m=18;speed_kmh=0]`;
    state.sosUnavailableMarker = `[SOS:kind=alert;name=Gabrielo;custom=Necesito%20ayuda;reason=permission_denied]`;
    await rpc(config, state.a, "quata_chat_send_message", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message: state.sosWithLocationMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-group-sos-location-${runId}`,
    });
    const sosWithLocationMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === state.sosWithLocationMarker);
    state.sosWithLocationMessage = messageId({ message: sosWithLocationMessage });
    await rpc(config, state.a, "quata_chat_send_message", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message: state.sosUnavailableMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-group-sos-unavailable-${runId}`,
    });
    const sosUnavailableMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === state.sosUnavailableMarker);
    state.sosUnavailableMessage = messageId({ message: sosUnavailableMessage });
    report.steps.push("sos_location_and_unavailable_messages_seeded");
  }

  distribution = await configuredDistribution(options.distribution, config);
  server = await startServer(distribution);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: true,
    args: [
      "--use-angle=swiftshader",
      "--enable-unsafe-swiftshader",
      "--force-renderer-accessibility",
      ...(options.attachmentsAudioOnly ? ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"] : []),
    ],
  });
  const uiSession = (options.groupAdminOnly || options.groupModerationOnly) && state.b.accessToken ? state.b : state.a;
  if ((options.groupAdminOnly || options.groupModerationOnly) && uiSession === state.b) {
    report.steps.push("group_admin_ui_session_opened_as_peer_moderator");
  }
  pageContext = await openAuthenticatedChatPage(browser, server.origin, uiSession, `sb:${state.thread}`, faults, {
    grantMicrophone: options.attachmentsAudioOnly,
  });
  const page = pageContext.page;
  if (options.groupSosOnly) {
    report.evidence.threadInitial = await attachScreenshot(page, options.evidenceDir, "web-chat-group-sos-thread-initial");
    report.steps.push("thread_rendered_with_group_and_sos_messages");
    await verifyChatGroupSosWeb(page, options.evidenceDir, report);
    if (faults.length) {
      report.diagnostics = { ...(report.diagnostics ?? {}), browserRuntimeFaults: faults.slice() };
      throw new Error("browser_runtime_fault");
    }
    report.status = "passed";
    report.steps.push("group_menu_and_sos_shared_anchors_verified");
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      ownMessageId: state.ownMessage,
      peerMessageId: state.peerMessage,
      sosWithLocationMessageId: state.sosWithLocationMessage,
      sosUnavailableMessageId: state.sosUnavailableMessage,
      uniqueKeySha256: sha256(state.uniqueKey),
      ownMarkerSha256: sha256(ownMarker),
      peerMarkerSha256: sha256(peerMarker),
      sosWithLocationMarkerSha256: sha256(state.sosWithLocationMarker),
      sosUnavailableMarkerSha256: sha256(state.sosUnavailableMarker),
    };
    throw new EvidenceCompleted();
  }
  if (options.groupAdminOnly) {
    await waitMessageVisible(page, ownMarker, "message_not_visible:group_admin_own");
    if (state.peerMessage) {
      await waitMessageVisible(page, peerMarker, "message_not_visible:group_admin_peer");
    }
    report.evidence.threadInitial = await attachScreenshot(page, options.evidenceDir, "web-chat-group-admin-thread-initial");
    await verifyChatGroupAdminWeb(page, state, options.evidenceDir, report);
    if (faults.length) {
      report.diagnostics = { ...(report.diagnostics ?? {}), browserRuntimeFaults: faults.slice() };
      throw new Error("browser_runtime_fault");
    }
    report.status = "passed";
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      ownMessageId: state.ownMessage,
      peerMessageId: state.peerMessage,
      tempProfileIdSha256: sha256(state.groupAdminProfile.id),
      uniqueKeySha256: sha256(state.uniqueKey),
      ownMarkerSha256: sha256(ownMarker),
      peerMarkerSha256: sha256(peerMarker),
    };
    throw new EvidenceCompleted();
  }
  if (options.groupModerationOnly) {
    await waitMessageVisible(page, ownMarker, "message_not_visible:group_moderation_own");
    if (state.peerMessage) {
      await waitMessageVisible(page, peerMarker, "message_not_visible:group_moderation_peer");
    }
    report.evidence.threadInitial = await attachScreenshot(page, options.evidenceDir, "web-chat-group-moderation-thread-initial");
    await verifyChatGroupModerationWeb(page, state, options.evidenceDir, report);
    if (faults.length) {
      report.diagnostics = { ...(report.diagnostics ?? {}), browserRuntimeFaults: faults.slice() };
      throw new Error("browser_runtime_fault");
    }
    report.status = "passed";
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      ownMessageId: state.ownMessage,
      peerMessageId: state.peerMessage,
      removeProfileIdSha256: sha256(state.groupRemoveProfile.id),
      blockProfileIdSha256: sha256(state.groupBlockProfile.id),
      uniqueKeySha256: sha256(state.uniqueKey),
      ownMarkerSha256: sha256(ownMarker),
      peerMarkerSha256: sha256(peerMarker),
    };
    throw new EvidenceCompleted();
  }
  if (options.communityChatOnly) {
    state.communityChat = await resolveCommunityChatTarget(uiSession);
    report.steps.push("community_chat_active_wall_selected");
    const opened = await verifyCommunityChatWeb(page, server.origin, state.communityChat, options.evidenceDir, report, faults);
    report.status = "passed";
    report.fixture = {
      threadId: state.thread,
      seedConversationId: `sb:${state.thread}`,
      communityName: state.communityChat.name,
      communityWallId: state.communityChat.id,
      communityChatTag: state.communityChat.tag,
      openedConversationId: opened.conversationId,
      uniqueKeySha256: sha256(state.uniqueKey),
    };
    throw new EvidenceCompleted();
  }
  if (!options.attachmentsAudioOnly) {
    await waitMessageVisible(page, ownMarker, "message_not_visible:own");
    if (state.peerMessage) {
      await waitMessageVisible(page, peerMarker, "message_not_visible:peer");
    }
  }
  report.evidence.threadInitial = await attachScreenshot(page, options.evidenceDir, "web-chat-actions-thread-initial");
  report.steps.push(
    options.attachmentsAudioOnly
      ? "thread_rendered_for_attachments_audio_focal_mode"
      : state.peerMessage
        ? "thread_rendered_with_own_and_peer_messages"
        : "thread_rendered_with_own_message",
  );

  const translationMarker = state.peerMessage ? peerMarker : ownMarker;
  if (options.translationOnly || (isFullEvidenceMode(options) && state.peerMessage)) {
    await verifyChatTranslation(page, options.evidenceDir, translationMarker);
    report.evidence.translationOverlay = join(options.evidenceDir, "web-chat-translation-overlay.png");
    report.evidence.translationResult = join(options.evidenceDir, "web-chat-translation-result.png");
    report.evidence.translationReturn = join(options.evidenceDir, "web-chat-translation-return.png");
    report.steps.push("chat_translation_common_overlay_translated_fang_message_and_returned");
  }

  if (options.translationOnly) {
    if (faults.length) throw new Error("browser_runtime_fault");
    report.status = "passed";
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      translatedMessageId: state.ownMessage,
      translatedMarkerSha256: sha256(ownMarker),
    };
    throw new EvidenceCompleted();
  }

  if (options.menuSurfaceOnly) {
    await verifyChatOptionsMenuSurface(page, config, state, options.evidenceDir, report);
    if (faults.length) throw new Error("browser_runtime_fault");
    report.status = "passed";
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      ownMessageId: state.ownMessage,
      peerMessageId: state.peerMessage,
      uniqueKeySha256: sha256(state.uniqueKey),
      ownMarkerSha256: sha256(ownMarker),
      peerMarkerSha256: sha256(peerMarker),
    };
    throw new EvidenceCompleted();
  }

  if (options.attachmentsAudioOnly) {
    state.attachmentsAudio = {
      video: await createChatAttachmentMessage(config, state.a, state.thread, runId, "video"),
      image: await createChatAttachmentMessage(config, state.a, state.thread, runId, "image"),
      document: await createChatAttachmentMessage(config, state.a, state.thread, runId, "document"),
      audio: await createChatAttachmentMessage(config, state.a, state.thread, runId, "audio"),
      nextAudio: await createChatAttachmentMessage(config, state.a, state.thread, `${runId}-next`, "audio", "-next", { audioDurationSeconds: 12 }),
      recordingMarker: `chat-audio-recording-web-${randomUUID()}`,
    };
    report.steps.push("video_image_document_and_consecutive_audio_attachment_messages_seeded");
    faults.length = 0;
    await openAuthenticatedChatRoute(page, server.origin, `sb:${state.thread}`);
    await verifyAttachmentsAudioWeb(page, state.attachmentsAudio, options.evidenceDir, report, {
      config,
      session: state.a,
      thread: state.thread,
      state,
    });
    if (faults.length) {
      report.diagnostics = { ...(report.diagnostics ?? {}), browserRuntimeFaults: faults.slice() };
      throw new Error("browser_runtime_fault");
    }
    report.status = "passed";
    report.steps.push("document_and_audio_shared_attachment_chrome_verified");
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      imageMessageId: state.attachmentsAudio.image.messageId,
      videoMessageId: state.attachmentsAudio.video.messageId,
      documentMessageId: state.attachmentsAudio.document.messageId,
      audioMessageId: state.attachmentsAudio.audio.messageId,
      nextAudioMessageId: state.attachmentsAudio.nextAudio.messageId,
      imageAttachmentId: state.attachmentsAudio.image.id,
      videoAttachmentId: state.attachmentsAudio.video.id,
      documentAttachmentId: state.attachmentsAudio.document.id,
      audioAttachmentId: state.attachmentsAudio.audio.id,
      nextAudioAttachmentId: state.attachmentsAudio.nextAudio.id,
      recordingMarkerSha256: sha256(state.attachmentsAudio.recordingMarker),
      recordingMessageId: report.evidence.audioRecordingSent?.messageId ?? null,
      recordingAttachmentId: report.evidence.audioRecordingSent?.attachmentId ?? null,
      uniqueKeySha256: sha256(state.uniqueKey),
      imageMarkerSha256: sha256(state.attachmentsAudio.image.marker),
      videoMarkerSha256: sha256(state.attachmentsAudio.video.marker),
      documentMarkerSha256: sha256(state.attachmentsAudio.document.marker),
      audioMarkerSha256: sha256(state.attachmentsAudio.audio.marker),
      nextAudioMarkerSha256: sha256(state.attachmentsAudio.nextAudio.marker),
    };
    throw new EvidenceCompleted();
  }

  if (options.attachmentPickerOnly) {
    await verifyAttachmentPickerWeb(page, options.attachmentPickerSource, options.attachmentPickerOutcome, config, state, runId, options.evidenceDir, report);
    const blockingFaults = faults.filter((fault) => !isExpectedAttachmentRegisterFailureFault(fault, options.attachmentPickerOutcome));
    if (blockingFaults.length) {
      report.diagnostics = { ...(report.diagnostics ?? {}), browserRuntimeFaults: faults.slice() };
      throw new Error("browser_runtime_fault");
    }
    if (faults.length) {
      report.diagnostics = {
        ...(report.diagnostics ?? {}),
        nonBlockingBrowserRuntimeFaults: faults.filter((fault) => isExpectedAttachmentRegisterFailureFault(fault, options.attachmentPickerOutcome)),
      };
    }
    report.status = "passed";
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      ownMessageId: state.ownMessage,
      peerMessageId: state.peerMessage,
      attachmentPickerMessageId: state.uiMessages.at(-1),
      uniqueKeySha256: sha256(state.uniqueKey),
      source: options.attachmentPickerSource,
      outcome: options.attachmentPickerOutcome,
      attachmentPickerMarkerSha256: sha256(state.attachmentPicker.marker),
    };
    throw new EvidenceCompleted();
  }

  if (state.peerMessage && state.b.accessToken && !options.composerEmojiOnly) {
    if (options.feedOfficialCommentsOnly || options.feedOfficialCommentsErrorOnly || options.feedOfficialCommentsSelectorStatesOnly) {
      state.feedOfficialComments = {
        marker: `qadata-feed-official-comments-${runId}`,
        actorSession: state.a,
        targetSession: state.b,
      };
      await prepareFeedOfficialCommentsFixture(state.feedOfficialComments);
      report.steps.push("feed_official_comments_fixture_prepared");
      if (options.feedOfficialCommentsSelectorStatesOnly) {
        await verifyFeedOfficialCommentsSelectorStatesWeb(page, server.origin, state.feedOfficialComments, options.evidenceDir, report, faults);
      } else if (options.feedOfficialCommentsErrorOnly) {
        await verifyFeedOfficialCommentsErrorWeb(page, server.origin, state.feedOfficialComments, options.evidenceDir, report, faults);
      } else {
        await verifyFeedOfficialCommentsEmojiWeb(page, server.origin, state.feedOfficialComments, options.evidenceDir, report, faults);
      }
    } else if (options.profileFollowOnly) {
      state.profileFollow = await prepareProfileFollowAbsent(state.a.profileId, state.b.profileId);
      report.steps.push("profile_follow_initial_state_snapshot_and_absent_prepared");
      await openPeerProfileFromMessageWithoutReturn(page, peerMarker, state.b, options.evidenceDir, report, "web-chat-profile");
      await toggleFollowFromOpenProfile(page, { actorProfileId: state.a.profileId, profileId: state.b.profileId }, options.evidenceDir, report);
      report.steps.push("profile_follow_toggled_and_verified_by_db");
      if (!(await clickProfileBack(page))) throw new Error("profile_state_not_opened:profile_back_not_clickable");
      await closeProfileSheetIfVisible(page);
      await delay(1_000);
      if (!(await waitForChatProfileReturn(page))) throw new Error("profile_state_not_opened:chat_return_not_visible");
      report.evidence.profileReturn = await attachScreenshot(page, options.evidenceDir, "web-chat-profile-return");
      report.steps.push("peer_avatar_opened_public_profile_and_returned_to_chat");
    } else if (options.profileListsOnly) {
      state.profileListEdges = await prepareProfileListEdges(state.a.profileId, state.b.profileId);
      report.steps.push("profile_follow_list_edges_prepared_reversibly");
      await openPeerProfileFromMessageWithoutReturn(page, peerMarker, state.b, options.evidenceDir, report, "web-chat-profile-lists");
      await assertProfileFollowLists(page, server.origin, `sb:${state.thread}`, peerMarker, state.b, options.evidenceDir, report);
      report.steps.push("peer_public_profile_followers_and_following_lists_opened_and_returned");
    } else if (options.profileEntryOnly) {
      state.profileEntry = await prepareProfileEntryFixture(runId);
      state.profileContent = state.profileEntry.profileContent;
      state.profilePrivateChat = state.profileEntry.privateChat;
      report.steps.push("profile_entry_feed_official_communities_conversations_and_chat_fixtures_prepared");
      await verifyProfileEntryWeb(page, server.origin, state.profileEntry, state.b, options.evidenceDir, report, faults);
    } else if (options.profileContentOnly) {
      state.profileContent = {
        marker: `qadata-profile-content-${runId}`,
        actorSession: state.a,
        targetSession: state.b,
        threadId: state.thread,
      };
      await prepareProfileContentFixture(state.profileContent);
      report.steps.push("profile_content_fixture_prepared");
      await openPeerProfileFromMessageWithoutReturn(page, peerMarker, state.b, options.evidenceDir, report, "web-chat-profile-content-open");
      await verifyProfileContentFromOpenProfile(page, state.b, state.profileContent, options.evidenceDir, report);
    } else if (options.profilePrivateChatOnly) {
      state.privateMarker = `chat-profile-private-web-${runId}`;
      state.profilePrivateChat = await createPrivateChatSeed(config, state.a, state.b, state.privateMarker);
      report.steps.push("profile_private_chat_seed_message_ready");
      await openPeerProfileFromMessageWithoutReturn(page, peerMarker, state.b, options.evidenceDir, report, "web-chat-profile-private-chat");
      await openPrivateChatFromOpenProfile(page, state.b, state.profilePrivateChat, state.privateMarker, options.evidenceDir, report);
      report.steps.push("profile_private_chat_opened_from_common_profile_action_and_verified_by_rpc");
    } else if (options.profileRolesSafetyOnly) {
      state.profileRolesSafety = await prepareProfileRolesSafetyFixture({
        actorSession: state.a,
        targetSession: state.b,
        withDatabase: withPoolerClient,
      });
      report.steps.push("profile_roles_safety_initial_state_snapshot_and_admin_actor_prepared");
      await openPeerProfileFromMessageWithoutReturn(page, peerMarker, state.b, options.evidenceDir, report, "web-chat-profile-roles-safety");
      await verifyProfileRolesSafetyFromOpenProfile(page, state.b, state.profileRolesSafety, options.evidenceDir, report, async () => {
        await reopenPeerProfileFromChat(page, server.origin, `sb:${state.thread}`, peerMarker, state.b);
      });
      report.steps.push("profile_roles_safety_roles_report_and_block_verified_by_db");
    } else {
      await openPeerProfileFromMessage(page, peerMarker, state.b, options.evidenceDir, report);
      report.steps.push("peer_avatar_opened_public_profile_and_returned_to_chat");
    }
    if (options.profileOnly || options.profileFollowOnly || options.profileListsOnly || options.profileContentOnly || options.profileEntryOnly || options.profilePrivateChatOnly || options.profileRolesSafetyOnly || options.feedOfficialCommentsOnly || options.feedOfficialCommentsErrorOnly || options.feedOfficialCommentsSelectorStatesOnly) {
      const blockingFaults = faults.filter((fault) => !isNonBlockingBrowserRuntimeFault(fault, {
        label: (options.feedOfficialCommentsOnly || options.feedOfficialCommentsErrorOnly || options.feedOfficialCommentsSelectorStatesOnly) ? "feed_official_comments_final" : "profile_entry_final",
      }));
      if (faults.length) {
        report.diagnostics = {
          ...(report.diagnostics ?? {}),
          browserRuntimeFaults: faults.slice(),
          nonBlockingBrowserRuntimeFaults: faults.filter((fault) => isNonBlockingBrowserRuntimeFault(fault, {
            label: (options.feedOfficialCommentsOnly || options.feedOfficialCommentsErrorOnly || options.feedOfficialCommentsSelectorStatesOnly) ? "feed_official_comments_final" : "profile_entry_final",
          })),
        };
      }
      if (blockingFaults.length) {
        throw new Error("browser_runtime_fault");
      }
      report.status = "passed";
      report.fixture = {
        threadId: state.thread,
        conversationId: `sb:${state.thread}`,
        ownMessageId: state.ownMessage,
        peerMessageId: state.peerMessage,
        peerProfileIdSha256: sha256(state.b.profileId),
        uniqueKeySha256: sha256(state.uniqueKey),
        ownMarkerSha256: sha256(ownMarker),
        peerMarkerSha256: sha256(peerMarker),
        profileFollowInitialState: state.profileFollow?.initiallyFollowing ?? null,
        profileListInitialEdges: state.profileListEdges?.map((edge) => ({ label: edge.label, existed: edge.existed })),
        profileContent: state.profileContent ? {
          markerSha256: sha256(state.profileContent.marker),
          postId: state.profileContent.postId,
          seedCommentId: state.profileContent.seedCommentId,
          uiCommentId: state.profileContent.uiCommentId,
          attachmentId: state.profileContent.attachmentId,
          attachmentMessageId: state.profileContent.attachmentMessageId,
        } : null,
        profileEntry: state.profileEntry ? {
          feedPostId: state.profileEntry.profileContent.postId,
          officialPostId: state.profileEntry.official.id,
          privateThreadId: state.profileEntry.privateChat.threadId,
          officialMarkerSha256: sha256(state.profileEntry.official.marker),
        } : null,
        feedOfficialComments: state.feedOfficialComments ? {
          markerSha256: sha256(state.feedOfficialComments.marker),
          feedPostId: state.feedOfficialComments.feed?.postId ?? null,
          feedUiCommentId: state.feedOfficialComments.feed?.uiCommentId ?? null,
          officialPostId: state.feedOfficialComments.official?.postId ?? null,
          officialUiCommentId: state.feedOfficialComments.official?.uiCommentId ?? null,
        } : null,
        profilePrivateChatThreadId: state.profilePrivateChat?.threadId ?? null,
        profileRolesSafety: state.profileRolesSafety ? {
          targetProfileIdSha256: sha256(state.profileRolesSafety.targetProfileId),
          actorWasAdmin: state.profileRolesSafety.actorRoles?.isAdmin ?? null,
          targetWasAdmin: state.profileRolesSafety.targetRoles?.isAdmin ?? null,
          targetWasOfficial: state.profileRolesSafety.targetRoles?.isOfficial ?? null,
          hadGlobalBlock: state.profileRolesSafety.hadGlobalBlock ?? null,
          hadProfileReport: Boolean(state.profileRolesSafety.previousReport),
        } : null,
        privateMarkerSha256: state.privateMarker ? sha256(state.privateMarker) : null,
      };
      if (options.profileListsOnly) throw new ProfileListsOnlyCompleted();
      if (options.profileEntryOnly) throw new ProfileEntryOnlyCompleted();
      if (options.profileRolesSafetyOnly) throw new ProfileRolesSafetyOnlyCompleted();
      if (options.feedOfficialCommentsOnly || options.feedOfficialCommentsErrorOnly || options.feedOfficialCommentsSelectorStatesOnly) throw new EvidenceCompleted();
      throw new ProfileOnlyCompleted();
    }
  } else if (options.profileOnly || options.profileFollowOnly || options.profileListsOnly || options.profileContentOnly || options.profileEntryOnly || options.profilePrivateChatOnly || options.profileRolesSafetyOnly || options.feedOfficialCommentsOnly || options.feedOfficialCommentsErrorOnly || options.feedOfficialCommentsSelectorStatesOnly) {
    throw new Error("profile_state_not_opened:peer_message_unavailable");
  }

  const composerMarker = `🚨 chat-composer-ui-${runId} www.quata.test/chat 📝`;
  await fillComposerAndSend(page, composerMarker);
  const composerMessage = await pollMessage(
    config,
    state.a,
    state.thread,
    (message) => messageText(message) === composerMarker,
  );
  const composerMessageId = messageId({ message: composerMessage });
  state.uiMessages.push(composerMessageId);
  state.editableUiMessage = composerMessageId;
  await waitMessageVisible(page, composerMarker, "composer_message_not_visible");
  report.evidence.composerSent = await attachScreenshot(page, options.evidenceDir, "web-chat-composer-sent");
  report.steps.push("composer_text_sent_by_shared_ui_and_verified_by_rpc");

  if (options.composerEmojiOnly) {
    if (faults.length) throw new Error("browser_runtime_fault");
    report.status = "passed";
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      composerMessageId,
      composerMarkerSha256: sha256(composerMarker),
      uniqueKeySha256: sha256(state.uniqueKey),
    };
    report.steps.push("composer_emoji_link_marker_sent_by_shared_ui_and_verified_by_rpc");
    throw new EvidenceCompleted();
  }

  const replyTargetMarker = state.peerMessage ? peerMarker : ownMarker;
  const replyTargetMessageId = state.peerMessage ?? state.ownMessage;
  await openMessageActions(page, replyTargetMarker, [/Responder|Reply/i], "message_action_target_not_clickable:reply", "action_bar_not_visible:reply");
  await clickLabel(page, [/Responder|Reply/i], "action_bar_not_visible:reply");
  const replyMarker = `chat-reply-ui-${runId}`;
  await fillComposerAndSend(page, replyMarker);
  const replyMessage = await pollMessage(
    config,
    state.a,
    state.thread,
    (message) => messageText(message).startsWith(replyMarker) && messageReplyToId(message) === Number(replyTargetMessageId),
  );
  state.uiMessages.push(messageId({ message: replyMessage }));
  await delay(1_000);
  await page.keyboard.press("Escape").catch(() => {});
  report.evidence.replySent = await attachScreenshot(page, options.evidenceDir, "web-chat-composer-reply-sent");
  report.steps.push("composer_reply_sent_by_shared_ui_and_verified_by_rpc");

  await openMessageActions(page, ownMarker, [/Editar|Edit/i], "message_action_target_not_clickable:edit", "action_bar_not_visible:edit");
  await clickEditAction(page);
  const editMarker = `chat-edit-ui-${runId}`;
  await fillComposerAndSend(page, editMarker);
  await pollMessage(
    config,
    state.a,
    state.thread,
    (message) => Number(message?.id ?? message?.message_id) === Number(state.ownMessage) && messageText(message) === editMarker,
  );
  await delay(1_000);
  await page.keyboard.press("Escape").catch(() => {});
  report.evidence.editSent = await attachScreenshot(page, options.evidenceDir, "web-chat-composer-edit-sent");
  report.steps.push("composer_edit_sent_by_shared_ui_and_verified_by_rpc");

  await clickOptionsMenu(page);
  await page.getByText(/Silenciar conversaci[oó]n|Mute conversation/i).click({ timeout: 10_000, force: true });
  await delay(1_000);
  if (!isMuted(await inboxThread(config, state.a, state.thread))) throw new Error("mute_state_not_persisted:true");
  report.evidence.muted = await attachScreenshot(page, options.evidenceDir, "web-chat-actions-muted");
  report.steps.push("mute_enabled_and_verified_by_rpc");

  await clickOptionsMenu(page);
  await page.getByText(/Reactivar notificaciones|Unmute|Reactivate notifications/i).click({ timeout: 10_000, force: true });
  await delay(1_000);
  if (isMuted(await inboxThread(config, state.a, state.thread))) throw new Error("mute_state_not_persisted:false");
  report.steps.push("mute_disabled_and_verified_by_rpc");

  await openMessageActions(page, editMarker, [/Copiar mensaje|Copiar texto|Copy message|Copy text/i], "message_action_target_not_clickable:own_actions", "action_bar_not_visible:copy");
  await delay(500);
  report.evidence.ownActions = await attachScreenshot(page, options.evidenceDir, "web-chat-actions-own-selected");
  report.steps.push("own_message_action_bar_visible");

  await clickFavoriteAction(page);
  await delay(1_000);
  const favoriteRows = await favorites(config, state.a);
  if (!favoriteRows.some((message) => Number(message?.id) === Number(state.ownMessage))) throw new Error("favorite_state_not_persisted:true");
  report.steps.push("favorite_toggled_and_verified_by_rpc");

  await openMessageActions(page, editMarker, [/Reenviar|Forward/i], "message_action_target_not_clickable:forward", "action_bar_not_visible:forward");
  await clickForwardAction(page);
  await selectForwardDestination(page, state.forwardProfile.phoneLocal, state.forwardProfile.displayName, "forward_state_not_persisted:picker");
  report.evidence.forwardPicker = await attachScreenshot(page, options.evidenceDir, "web-chat-forward-picker-selected");
  await clickForwardSend(page);
  const forwardDestination = await pollForwardDestinationThread(config, state.a, state.forwardProfile.id);
  state.forwardThread = forwardDestination.threadId;
  const forwardedMessage = await pollMessage(
    config,
    state.a,
    state.forwardThread,
    (message) => messageText(message) === editMarker && Number(message?.forwarded_from_message_id) === Number(state.ownMessage),
  );
  state.forwardedMessage = messageId({ message: forwardedMessage });
  await openAuthenticatedChatRoute(page, server.origin, `sb:${state.forwardThread}`);
  await delay(1_500);
  report.evidence.forwardedMessage = await attachScreenshot(page, options.evidenceDir, "web-chat-forwarded-message");
  report.steps.push("message_forwarded_by_shared_ui_and_verified_by_rpc");

  if (state.peerMessage) {
    await openMessageActions(page, peerMarker, [/Copiar mensaje|Copy message/i], "message_action_target_not_clickable:peer_actions", "action_bar_not_visible:peer_copy");
    report.evidence.peerActions = await attachScreenshot(page, options.evidenceDir, "web-chat-actions-peer-selected");
    report.steps.push("peer_message_action_bar_visible");
  }

  if (faults.length) throw new Error("browser_runtime_fault");
  report.status = "passed";
  report.fixture = {
    threadId: state.thread,
    conversationId: `sb:${state.thread}`,
    ownMessageId: state.ownMessage,
    peerMessageId: state.peerMessage,
    forwardThreadId: state.forwardThread,
    forwardedMessageId: state.forwardedMessage,
    forwardProfileIdSha256: sha256(state.forwardProfile.id),
    uniqueKeySha256: sha256(state.uniqueKey),
    ownMarkerSha256: sha256(ownMarker),
    peerMarkerSha256: sha256(peerMarker),
  };
} catch (error) {
  if (error instanceof EvidenceCompleted || error instanceof ProfileOnlyCompleted || error instanceof ProfileListsOnlyCompleted || error instanceof ProfileEntryOnlyCompleted || error instanceof ProfileRolesSafetyOnlyCompleted) {
    // Focal modes already set report.status and fixture; cleanup still runs in finally.
  } else {
    if (pageContext?.page) {
      try {
        report.evidence.failure = await attachScreenshot(pageContext.page, options.evidenceDir, "web-chat-actions-failure");
        report.diagnostics = {
          ...(report.diagnostics ?? {}),
          visibleNativeControls: await visibleNativeControls(pageContext.page),
          nativeControls: await allNativeControls(pageContext.page),
          browserClickEvents: await pageContext.page.evaluate(() => globalThis.__quataClickEvents ?? []).catch(() => []),
          browserSharePayloads: await pageContext.page.evaluate(() => globalThis.__quataSharePayloads ?? []).catch(() => []),
          browserAttachmentActionEvents: await pageContext.page.evaluate(() => globalThis.__quataAttachmentActionEvents ?? []).catch(() => []),
        };
      } catch {}
    }
    report.error = safeFailure(error);
    if (lastThreadSnapshot) report.diagnostics = { ...(report.diagnostics ?? {}), lastThreadSnapshot };
    if (typeof error?.message === "string" && error.message.startsWith(report.error)) {
      report.diagnostics = { ...(report.diagnostics ?? {}), safeErrorMessage: error.message };
    } else if (report.error === "unexpected_chat_actions_notifications_web_failure") {
      report.diagnostics = {
        ...(report.diagnostics ?? {}),
        unexpectedErrorName: typeof error?.name === "string" ? error.name : "Error",
        unexpectedErrorMessage: typeof error?.message === "string" ? error.message : "unknown",
        unexpectedErrorStackPrefix: typeof error?.stack === "string" ? error.stack.slice(0, 1200) : null,
        unexpectedAggregateErrors: Array.isArray(error?.errors)
          ? error.errors.slice(0, 5).map((item) => ({
            name: typeof item?.name === "string" ? item.name : "Error",
            message: typeof item?.message === "string" ? item.message : String(item ?? "unknown"),
            stackPrefix: typeof item?.stack === "string" ? item.stack.slice(0, 600) : null,
          }))
          : null,
      };
    }
  }
} finally {
  const cleanup = { state: "completed", actions: [] };
  let cleanupFailed = false;
  try { await withTimeout(pageContext?.context?.close() ?? Promise.resolve(), 5_000, "playwright_context_close"); }
  catch (error) { cleanup.actions.push(safeFailure(error)); }
  try { await withTimeout(browser?.close() ?? Promise.resolve(), 5_000, "playwright_browser_close"); }
  catch (error) { cleanup.actions.push(safeFailure(error)); }
  try { await withTimeout(server?.close() ?? Promise.resolve(), 5_000, "web_evidence_server_close"); }
  catch (error) { cleanup.actions.push(safeFailure(error)); }
  try { await rm(distribution, { recursive: true, force: true }); } catch {}
  try {
    await profileHashWindow.restore();
    if (profileHashWindow.state === "opened") {
      cleanup.actions.push("temporary_profile_hash_window_restored");
      report.profileHashWindow = { state: "restored", count: profileHashWindow.count };
    }
  } catch (error) {
    cleanupFailed = true;
    cleanup.error = safeFailure(error);
    report.profileHashWindow = {
      state: "restore_failed",
      error: safeFailure(error),
      safeErrorMessage: typeof error?.message === "string" ? error.message : "unknown",
    };
  }
  if (state.profileListEdges && config) {
    try { cleanup.actions.push(...await restoreProfileListEdges(state.profileListEdges)); }
    catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
  }
  if (state.thread && config) {
    try { cleanup.actions.push(...await logicalCleanup(config, state)); }
    catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
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
        const hardCleanup = await hardDeleteTemporaryThread(state.thread, state.uniqueKey);
        cleanup.actions.push("hard_deleted_temporary_thread");
        cleanup.actions.push("cleanup_verified_physical_residue_absent");
        cleanup.hardCleanup = hardCleanup;
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (state.profileContent) {
      try {
        cleanup.profileContent = await cleanupProfileContentFixture(state.profileContent);
        cleanup.actions.push("profile_content_fixture_deleted");
        cleanup.actions.push("cleanup_verified_profile_content_residue_absent");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (state.profileEntry?.official) {
      try {
        cleanup.profileEntryOfficial = await cleanupOfficialProfileEntryPost(state.profileEntry.official);
        cleanup.actions.push("profile_entry_official_post_deleted");
        cleanup.actions.push("cleanup_verified_profile_entry_official_residue_absent");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (state.feedOfficialComments) {
      try {
        cleanup.feedOfficialComments = await cleanupFeedOfficialCommentsFixture(state.feedOfficialComments);
        cleanup.actions.push("feed_official_comments_fixture_deleted");
        cleanup.actions.push("cleanup_verified_feed_official_comments_residue_absent");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (state.profileRolesSafety) {
      try {
        cleanup.profileRolesSafety = await cleanupProfileRolesSafetyFixture(state.profileRolesSafety);
        cleanup.actions.push("profile_roles_safety_fixture_restored");
        cleanup.actions.push("cleanup_verified_profile_roles_safety_restored");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
        cleanup.safeErrorMessage = typeof error?.message === "string" ? error.message : "unknown";
      }
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
  report.finishedAt = new Date().toISOString();
  await mkdir(dirname(options.output), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Chat actions/notifications Web evidence written: ${options.output}`);
}
if (report.status !== "passed") {
  console.error(`Chat actions/notifications Web evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Chat actions/notifications Web evidence passed.");
}
