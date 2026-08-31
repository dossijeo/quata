import { spawn } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { existsSync } from "node:fs";
import { mkdir, open, readFile, rm, stat, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath } from "node:url";
import pg from "pg";
import {
  assertFeedOfficialCommentAbsent as assertSharedFeedOfficialCommentAbsent,
  cleanupProfileContentFixture as cleanupSharedProfileContentFixture,
  cleanupFeedOfficialCommentsFixture as cleanupSharedFeedOfficialCommentsFixture,
  createCleanupRegistry,
  cleanupProfileRolesSafetyFixture as cleanupSharedProfileRolesSafetyFixture,
  pollFeedOfficialComment as pollSharedFeedOfficialComment,
  pollFeedOfficialReplyComment as pollSharedFeedOfficialReplyComment,
  pollProfileGlobalBlock,
  pollProfileReport,
  pollProfileContentComment as pollSharedProfileContentComment,
  pollProfileContentReplyComment as pollSharedProfileContentReplyComment,
  pollProfileRoles,
  prepareProfileRolesSafetyFixture,
  seedChatAttachmentFixture,
  seedFeedOfficialCommentsFixture,
  seedProfileContentFixture,
} from "./e2e-fixtures/chat-attachments.mjs";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const options = parseArgs(process.argv.slice(2));
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const hardCleanupAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP_AUTHORIZATION";
const hardCleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_HARD_CLEANUP";
const tempProfileHashAuthorizationEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH_AUTHORIZATION";
const tempProfileHashAuthorizationValue = "MANAGER_APPROVED_QADATA_CHAT_ACTIONS_NOTIFICATIONS_TEMP_PROFILE_HASH";
const credentialsFileEnvironment = "QUATA_CHAT_ACTIONS_NOTIFICATIONS_CREDENTIALS_FILE";
const profileOnly = process.argv.includes("--profile-only");
const profileFollowOnly = process.argv.includes("--profile-follow-only");
const profileListsOnly = process.argv.includes("--profile-lists-only");
const profileContentOnly = process.argv.includes("--profile-content-only");
const feedOfficialCommentsOnly = process.argv.includes("--feed-official-comments-only");
const feedOfficialCommentsErrorOnly = process.argv.includes("--feed-official-comments-error-only");
const feedOfficialCommentsSelectorStatesOnly = process.argv.includes("--feed-official-comments-selector-states-only");
const profileEntryOnly = process.argv.includes("--profile-entry-only");
const profilePrivateChatOnly = process.argv.includes("--profile-private-chat-only");
const profileRolesSafetyOnly = process.argv.includes("--profile-roles-safety-only");
const communityChatOnly = process.argv.includes("--community-chat-only");
const menuSurfaceOnly = process.argv.includes("--menu-surface-only");
const attachmentsAudioOnly = process.argv.includes("--attachments-audio-only");
const attachmentPickerOnly = process.argv.includes("--attachment-picker-only");
const composerEmojiOnly = process.argv.includes("--composer-emoji-only");
const groupSosOnly = process.argv.includes("--group-sos-only");
const groupAdminOnly = process.argv.includes("--group-admin-only");
const groupModerationOnly = process.argv.includes("--group-moderation-only");
const useAdjacentAuthorizedProfile = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_USE_ADJACENT_AUTHORIZED_PROFILE === "1";
const deviceCredentialsPath = "app-internal:chat-actions-notifications-credentials.json";
const deviceTempCredentialsPath = "/data/local/tmp/chat-actions-notifications-credentials.json";
const deviceEvidencePath = "files/chat-actions-notifications-evidence";
const adbCommand = resolveAdbCommand();
const androidEvidenceLockPath = join("build-reports", "android", ".chat-actions-notifications.lock");
const androidEvidenceLockTimeoutMs = Number.parseInt(process.env.QUATA_ANDROID_EVIDENCE_LOCK_TIMEOUT_MS ?? "600000", 10);
const androidEvidenceLockStaleMs = Number.parseInt(process.env.QUATA_ANDROID_EVIDENCE_LOCK_STALE_MS ?? "1800000", 10);
const evidenceFiles = [
  "android-chat-translation-before.png",
  "android-chat-translation-overlay.png",
  "android-chat-translation-result.png",
  "android-chat-translation-return.png",
  "android-chat-actions-thread-initial.png",
  "android-chat-composer-sent.png",
  "android-chat-composer-reply-sent.png",
  "android-chat-composer-edit-mode.png",
  "android-chat-composer-edit-filled.png",
  "android-chat-composer-edit-submitted.png",
  "android-chat-composer-edit-sent.png",
  "android-chat-actions-own-selected.png",
  "android-chat-options-menu-surface.png",
  "android-chat-actions-muted.png",
  "android-chat-forward-picker-selected.png",
  "android-chat-forward-submitted.png",
  "android-chat-profile-thread-initial.png",
  "android-chat-profile-message-avatar-open-failed.png",
  "android-chat-profile-open-failed.png",
  "android-chat-profile-open.png",
  "android-chat-profile-return.png",
  "android-chat-profile-follow-before.png",
  "android-chat-profile-follow-after.png",
  "android-chat-profile-follow-return.png",
  "android-chat-profile-lists-thread-initial.png",
  "android-chat-profile-lists-open.png",
  "android-chat-profile-list-followers.png",
  "android-chat-profile-list-following.png",
  "android-chat-profile-lists-return.png",
  "android-chat-profile-content.png",
  "android-chat-profile-content-gallery-page-retry.png",
  "android-chat-profile-content-gallery-page-missing.png",
  "android-chat-profile-content-gallery-page-missing-semantics.txt",
  "android-chat-profile-content-missing-comment.png",
  "android-chat-profile-content-missing-comment-semantics.txt",
  "android-chat-profile-comments-panel-reopen-initial.png",
  "android-chat-profile-comments-panel-reopen-initial-semantics.txt",
  "android-chat-profile-comments-input-missing-after-reply.png",
  "android-chat-profile-comments-input-missing-after-reply-semantics.txt",
  "android-chat-profile-comments-panel-reopen-after-reply.png",
  "android-chat-profile-comments-panel-reopen-after-reply-semantics.txt",
  "android-chat-profile-roles-safety-initial.png",
  "android-chat-profile-roles-safety-role-updating.png",
  "android-chat-profile-safety-report-dialog.png",
  "android-chat-profile-safety-block-dialog.png",
  "android-chat-profile-roles-safety-after-block.png",
  "android-chat-profile-private-chat-before.png",
  "android-chat-profile-private-chat-opened.png",
  "android-profile-entry-feed-source.png",
  "android-profile-entry-feed.png",
  "android-profile-entry-feed-return.png",
  "android-profile-entry-official-source.png",
  "android-profile-entry-official.png",
  "android-profile-entry-official-return.png",
  "android-profile-entry-conversations-source.png",
  "android-profile-entry-conversations.png",
  "android-profile-entry-conversations-return.png",
  "android-profile-entry-communities-source.png",
  "android-profile-entry-communities.png",
  "android-profile-entry-communities-return.png",
  "android-profile-entry-chat-return.png",
  "android-community-chat-list.png",
  "android-community-chat-opened.png",
  "android-feed-comments-emoji-before.png",
  "android-feed-comments-emoji-before-missing-action.png",
  "android-feed-comments-emoji-before-semantics.txt",
  "android-feed-comments-emoji-before-panel-frequent.png",
  "android-feed-comments-emoji-before-panel-flags.png",
  "android-feed-comments-emoji-before-panel-frequent-section-not-clickable.png",
  "android-feed-comments-emoji-before-panel-frequent-section-not-clickable-semantics.txt",
  "android-feed-comments-emoji-before-panel-reset-frequent-section-not-clickable.png",
  "android-feed-comments-emoji-before-panel-reset-frequent-section-not-clickable-semantics.txt",
  "android-feed-comments-emoji-after.png",
  "android-feed-comments-emoji-after-missing-comment.png",
  "android-feed-comments-emoji-after-semantics.txt",
  "android-feed-comments-error-before.png",
  "android-feed-comments-error-after.png",
  "android-feed-comments-emoji-selector-error.png",
  "android-official-comments-emoji-before.png",
  "android-official-comments-emoji-before-missing-action.png",
  "android-official-comments-emoji-before-semantics.txt",
  "android-official-comments-emoji-before-panel-frequent.png",
  "android-official-comments-emoji-before-panel-flags.png",
  "android-official-comments-emoji-before-panel-frequent-section-not-clickable.png",
  "android-official-comments-emoji-before-panel-frequent-section-not-clickable-semantics.txt",
  "android-official-comments-emoji-before-panel-reset-frequent-section-not-clickable.png",
  "android-official-comments-emoji-before-panel-reset-frequent-section-not-clickable-semantics.txt",
  "android-official-comments-emoji-after.png",
  "android-official-comments-emoji-after-missing-comment.png",
  "android-official-comments-emoji-after-semantics.txt",
  "android-official-comments-error-before.png",
  "android-official-comments-error-after.png",
  "android-official-comments-emoji-selector-empty.png",
  "android-chat-attachment-document-visible.png",
  "android-chat-audio-recording-active.png",
  "android-chat-audio-recording-pending-attachment.png",
  "android-chat-audio-recording-ready-to-send.png",
  "android-chat-audio-recording-sent.png",
  "android-chat-audio-player-visible.png",
  "android-chat-audio-toggle-attempted.png",
  "android-chat-audio-seek-attempted.png",
  "android-chat-attachment-picker-pending-document.png",
  "android-chat-attachment-picker-sent-document.png",
  "android-chat-attachment-picker-pending-gallery.png",
  "android-chat-attachment-picker-sent-gallery.png",
  "android-chat-attachment-picker-pending-camera.png",
  "android-chat-attachment-picker-sent-camera.png",
  "android-chat-attachment-picker-register-failure-document.png",
  "android-chat-attachment-picker-register-failure-gallery.png",
  "android-chat-attachment-picker-register-failure-camera.png",
  "android-chat-group-menu-shared-anchors.png",
  "android-chat-sos-location-map-feedback-missing.png",
  "android-chat-sos-location-map-return.png",
  "android-chat-group-admin-participant-picker.png",
  "android-chat-group-admin-participant-selected.png",
  "android-chat-group-admin-member-list.png",
  "android-chat-group-admin-member-menu.png",
  "android-chat-group-admin-member-promoted.png",
  "android-chat-group-moderation-remove-participant-picker.png",
  "android-chat-group-moderation-remove-member-list.png",
  "android-chat-group-moderation-member-removed.png",
  "android-chat-group-moderation-block-participant-picker.png",
  "android-chat-group-moderation-block-member-list.png",
  "android-chat-group-moderation-member-blocked.png",
  "android-chat-actions-notifications-evidence.json",
];
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
for (const prefix of ["android-feed-comments-emoji-before-panel", "android-official-comments-emoji-before-panel"]) {
  for (const section of communityEmojiPanelProbeSections) {
    evidenceFiles.push(`${prefix}-${section}-missing-panel-tag.png`);
    evidenceFiles.push(`${prefix}-${section}-missing-panel-tag-semantics.txt`);
    evidenceFiles.push(`${prefix}-${section}-section-not-clickable.png`);
    evidenceFiles.push(`${prefix}-${section}-section-not-clickable-semantics.txt`);
  }
}
const translationOnly = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_TRANSLATION_ONLY === "1";
let lastThreadSnapshot = null;

function parseArgs(argv) {
  const result = {
    output: join("build-reports", "android", "chat-actions-notifications-evidence.json"),
    evidenceDir: join("build-reports", "android", "chat-actions-notifications-evidence"),
    attachmentPickerSource: process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_ANDROID_ATTACHMENT_PICKER_SOURCE || "document",
    attachmentPickerOutcome: process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_ANDROID_ATTACHMENT_PICKER_OUTCOME || "success",
  };
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    if (key === "--attachment-picker-only") {
      result.output = join("build-reports", "android", "chat-attachment-picker-evidence.json");
      result.evidenceDir = join("build-reports", "android", "chat-attachment-picker-evidence");
      continue;
    }
    if (key === "--group-admin-only") {
      result.output = join("build-reports", "android", "chat-group-admin-evidence.json");
      result.evidenceDir = join("build-reports", "android", "chat-group-admin-evidence");
      continue;
    }
    if (key === "--group-moderation-only") {
      result.output = join("build-reports", "android", "chat-group-moderation-evidence.json");
      result.evidenceDir = join("build-reports", "android", "chat-group-moderation-evidence");
      continue;
    }
    if (key === "--community-chat-only") {
      result.output = join("build-reports", "android", "community-chat-flow-evidence.json");
      result.evidenceDir = join("build-reports", "android", "community-chat-flow-evidence");
      continue;
    }
    if (key === "--feed-official-comments-error-only") {
      result.output = join("build-reports", "android", "feed-official-comments-error-evidence.json");
      result.evidenceDir = join("build-reports", "android", "feed-official-comments-error-evidence");
      continue;
    }
    if (key === "--feed-official-comments-selector-states-only") {
      result.output = join("build-reports", "android", "flow-emoji-selector-states-evidence.json");
      result.evidenceDir = join("build-reports", "android", "flow-emoji-selector-states-evidence");
      continue;
    }
    if (key === "--attachment-picker-source") {
      const value = argv[index + 1];
      if (!value || value.startsWith("--")) throw new Error("invalid_arguments");
      result.attachmentPickerSource = value;
      index += 1;
      continue;
    }
    if (key === "--attachment-picker-outcome") {
      const value = argv[index + 1];
      if (!value || value.startsWith("--")) throw new Error("invalid_arguments");
      result.attachmentPickerOutcome = value;
      index += 1;
      continue;
    }
    if (key === "--composer-emoji-only") {
      result.output = join("build-reports", "android", "chat-composer-emoji-evidence.json");
      result.evidenceDir = join("build-reports", "android", "chat-composer-emoji-evidence");
      continue;
    }
    if (!["--out", "--evidence-dir"].includes(key)) continue;
    const value = argv[index + 1];
    if (!value || value.startsWith("--")) throw new Error("invalid_arguments");
    index += 1;
    if (key === "--out") result.output = value;
    if (key === "--evidence-dir") result.evidenceDir = value;
  }
  if (!["document", "gallery", "camera"].includes(result.attachmentPickerSource)) {
    throw new Error(`invalid_attachment_picker_source:${result.attachmentPickerSource}`);
  }
  if (!["success", "cancelled", "failure", "unsupported", "register-failure"].includes(result.attachmentPickerOutcome)) {
    throw new Error(`invalid_attachment_picker_outcome:${result.attachmentPickerOutcome}`);
  }
  return result;
}

function resolveAdbCommand() {
  const executable = process.platform === "win32" ? "adb.exe" : "adb";
  const candidates = [
    process.env.ADB,
    process.env.ANDROID_HOME ? join(process.env.ANDROID_HOME, "platform-tools", executable) : null,
    process.env.ANDROID_SDK_ROOT ? join(process.env.ANDROID_SDK_ROOT, "platform-tools", executable) : null,
    process.env.LOCALAPPDATA ? join(process.env.LOCALAPPDATA, "Android", "Sdk", "platform-tools", executable) : null,
  ].filter(Boolean);
  return candidates.find((candidate) => existsSync(candidate)) ?? "adb";
}

async function prepareProfileContentFixture(fixture) {
  return seedProfileContentFixture({
    fixture,
    withDatabase,
    rpc,
    storageRequest,
    attachmentId,
    messageId,
    cleanup: state.cleanupRegistry,
  });
}

async function cleanupProfileContentFixture(fixture) {
  return cleanupSharedProfileContentFixture({ fixture, withDatabase });
}

async function pollProfileContentComment(fixture, marker, timeout = 45_000) {
  return pollSharedProfileContentComment({ fixture, marker, withDatabase, delay, timeout });
}

async function pollProfileContentReplyComment(fixture, marker, replyToCommentId, timeout = 45_000) {
  return pollSharedProfileContentReplyComment({ fixture, marker, replyToCommentId, withDatabase, delay, timeout });
}

async function prepareFeedOfficialCommentsFixture(fixture) {
  return seedFeedOfficialCommentsFixture({ fixture, withDatabase });
}

async function cleanupFeedOfficialCommentsFixture(fixture) {
  return cleanupSharedFeedOfficialCommentsFixture({ fixture, withDatabase });
}

async function pollFeedOfficialComment(fixture, surface, marker, timeout = 45_000) {
  return pollSharedFeedOfficialComment({ fixture, surface, marker, withDatabase, delay, timeout });
}

async function assertFeedOfficialCommentAbsent(fixture, surface, marker) {
  return assertSharedFeedOfficialCommentAbsent({ fixture, surface, marker, withDatabase });
}

async function pollFeedOfficialReplyComment(fixture, surface, marker, replyToCommentId, timeout = 45_000) {
  return pollSharedFeedOfficialReplyComment({ fixture, surface, marker, replyToCommentId, withDatabase, delay, timeout });
}

async function cleanupProfileRolesSafetyFixture(fixture) {
  return cleanupSharedProfileRolesSafetyFixture({ fixture, withDatabase });
}

async function prepareProfileEntryFixture(config, runId) {
  const profileContent = {
    marker: `qadata-profile-content-${runId}`,
    config,
    actorSession: state.a,
    targetSession: state.b,
    threadId: state.thread,
  };
  await prepareProfileContentFixture(profileContent);
  const official = await createOfficialProfileEntryPost(state.b.profileId, `qadata-profile-entry-official-android-${runId}`);
  return { profileContent, official };
}

async function createOfficialProfileEntryPost(profileId, marker) {
  const id = randomUUID();
  const translationGroupId = randomUUID();
  const title = `QADATA profile entry official ${marker.slice(-12)}`;
  const publishedAt = new Date().toISOString();
  await withDatabase(async (client) => {
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
  return await withDatabase(async (client) => {
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
      if (Number(remaining.rows[0]?.count ?? 0) !== 0) throw new Error("profile_entry_official_cleanup_residue");
      await client.query("commit");
      return { state: "hard_deleted_verified", deletedRows: deleted.rowCount, remainingRows: 0 };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

function env(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`missing_environment:${name}`);
  return value;
}

async function authorizedUsers() {
  if (!useAdjacentAuthorizedProfile) {
    const file = process.env[credentialsFileEnvironment]?.trim();
    if (file) {
      const parsed = JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""));
      const user = (entry, label) => ({
        label,
        countryCode: String(entry?.country_code ?? entry?.countryCode ?? "").trim(),
        phone: String(entry?.phone ?? "").trim(),
        password: String(entry?.password ?? ""),
      });
      return { a: user(parsed.a, "A"), b: user(parsed.b, "B") };
    }
    return {
      a: { label: "A", countryCode: env("QUATA_CHAT_EVIDENCE_A_COUNTRY_CODE"), phone: env("QUATA_CHAT_EVIDENCE_A_PHONE"), password: env("QUATA_CHAT_EVIDENCE_A_PASSWORD") },
      b: { label: "B", countryCode: env("QUATA_CHAT_EVIDENCE_B_COUNTRY_CODE"), phone: env("QUATA_CHAT_EVIDENCE_B_PHONE"), password: env("QUATA_CHAT_EVIDENCE_B_PASSWORD") },
    };
  }
  const host = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_SSH_HOST?.trim()
    || process.env.QUATA_CHAT_EVIDENCE_SSH_HOST?.trim();
  const file = process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_SSH_CREDENTIALS_FILE?.trim()
    || process.env.QUATA_CHAT_EVIDENCE_SSH_CREDENTIALS_FILE?.trim();
  if (!host || !file) throw new Error("missing_adjacent_profile_credentials_source");
  const credentials = JSON.parse(await runSilent("ssh", [host, `cat ${file}`]));
  const phone = splitPhone(credentials.phone);
  const previousLocal = (BigInt(phone.localPhone) - 1n).toString().padStart(phone.localPhone.length, "0");
  if (previousLocal.length !== phone.localPhone.length) throw new Error("invalid_adjacent_profile_phone");
  return {
    a: { label: "A", countryCode: phone.countryCode, phone: previousLocal, password: credentials.password },
    b: { label: "B", countryCode: phone.countryCode, phone: phone.localPhone, password: credentials.password, adjacentPhoneKeys: adjacentRecipientPhones(phone) },
  };
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
  if (value.startsWith("sb_publishable_")) return true;
  const parts = value.split(".");
  if (parts.length !== 3) return false;
  try { return JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"))?.role === "anon"; } catch { return false; }
}

function headers(config, token) {
  return {
    apikey: config.key,
    "content-type": "application/json",
    "x-client-info": "quata-chat-actions-notifications-android-evidence",
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
      action: "login",
      country_code: user.countryCode,
      phone: user.phone,
      password: user.password,
      client_instance_id: `chat-actions-notifications-android-${user.label.toLowerCase()}-${randomUUID()}`,
    }),
  }, "public_auth_request_failed");
  const session = payload?.session;
  const profileId = payload?.profile?.id;
  const neighborhood = String(payload?.profile?.neighborhood ?? payload?.profile?.barrio ?? "").trim();
  if (!uuid.test(profileId ?? "") || !session?.access_token || !session?.refresh_token || !Number.isFinite(session?.expires_at)) {
    throw new Error(`invalid_auth_response:${user.label}`);
  }
  return { label: user.label, profileId, neighborhood, accessToken: session.access_token };
}

async function rpc(config, session, name, body) {
  const payload = await jsonRequest(`${config.baseUrl}/rest/v1/rpc/${name}`, {
    method: "POST",
    headers: headers(config, session.accessToken),
    body: JSON.stringify(body),
  }, `chat_rpc_failed:${name}`);
  return payload;
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

async function createChatAttachmentMessage(config, session, thread, runId, kind, nameSuffix = "", options = {}) {
  return seedChatAttachmentFixture({
    config,
    session,
    thread,
    runId,
    kind,
    platformLabel: "android",
    rpc,
    storageRequest,
    pollMessage,
    messageText,
    attachmentId,
    messageId,
    cleanup: state.cleanupRegistry,
    nameSuffix,
    audioDurationSeconds: options.audioDurationSeconds,
  });
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
  if (!Number.isInteger(value) || value <= 0) throw new Error("chat_contract_invalid:thread_id");
  return value;
}

function messageId(payload) {
  const raw = payload?.message_id ?? payload?.id ?? payload?.message?.id ?? rows(payload, "messages")[0]?.id;
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) throw new Error("chat_contract_invalid:message_id");
  return value;
}

function attachmentId(payload) {
  const raw = payload?.id ?? payload?.file?.id;
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) throw new Error("chat_contract_invalid:attachment_id");
  return value;
}

function adbShellQuote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`;
}

function messageText(row) {
  return String(row?.body ?? row?.text ?? row?.message ?? "");
}

function messageAttachments(row) {
  const candidates = [
    row?.attachments,
    row?.files,
    row?.attachment ? [row.attachment] : null,
  ].filter(Boolean).flat();
  return candidates
    .filter((entry) => entry && typeof entry === "object")
    .map((attachment) => ({
      ...attachment,
      id: Number(attachment?.id ?? attachment?.file_id ?? attachment?.attachment_id),
      name: attachment?.name ?? attachment?.display_name,
      storagePath: attachment?.storage_path ?? attachment?.storagePath,
      bucket: attachment?.storage_bucket ?? attachment?.storageBucket,
      mimeType: attachment?.mime_type ?? attachment?.mimeType,
    }));
}

function isMuted(row) {
  return row?.muted === true || row?.is_muted === true || row?.isMuted === true;
}

function messageReplyToId(row) {
  const raw = row?.reply_to_message_id ?? row?.replyToMessageId ?? row?.reply?.id;
  const value = Number(raw);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function favoriteMessageId(row) {
  const raw = row?.id ?? row?.message_id ?? row?.messageId ?? row?.message?.id ?? row?.favorite?.message_id;
  const value = Number(raw);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function messageNumericId(row) {
  const raw = row?.id ?? row?.message_id ?? row?.messageId ?? row?.message?.id;
  const value = Number(raw);
  return Number.isInteger(value) && value > 0 ? value : null;
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
    await new Promise((resolve) => setTimeout(resolve, 1_500));
  }
  throw new Error("chat_backend_poll_timeout");
}

async function favorites(config, session) {
  return rows(await rpc(config, session, "quata_chat_get_favorites", {
    p_actor_profile_id: session.profileId,
    p_limit: 100,
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
  const numericThread = Number(thread);
  return allRows.find((row) => Number(row?.thread_id ?? row?.threadId ?? row?.id) === numericThread) ?? null;
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
      ...(Array.isArray(payload?.data?.threads) ? payload.data.threads : []),
      ...(Array.isArray(payload?.data?.conversations) ? payload.data.conversations : []),
      ...(Array.isArray(payload?.update?.threads) ? payload.update.threads : []),
      ...(Array.isArray(payload?.update?.conversations) ? payload.update.conversations : []),
    ].filter(Boolean);
    const match = allRows.find((row) => JSON.stringify(row).includes(profileId));
    if (match) return { threadId: threadId(match), row: match };
    await new Promise((resolve) => setTimeout(resolve, 1_000));
  }
  throw new Error("chat_contract_invalid:forward_destination_thread");
}

function chatUrl(conversationId, messageIdValue) {
  const encodedConversation = encodeURIComponent(conversationId);
  const suffix = messageIdValue ? `?message=${encodeURIComponent(messageIdValue)}` : "";
  return `https://egquata.com/#chat-${encodedConversation}${suffix}`;
}

async function run(command, args, options = {}) {
  await new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: "inherit", shell: false, ...options });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolve() : reject(new Error(`command_failed:${command}:${code}`)));
  });
}

async function runCapture(command, args, options = {}) {
  return await new Promise((resolve, reject) => {
    let output = "";
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: false, ...options });
    child.stdout.on("data", (chunk) => { output += chunk.toString(); process.stdout.write(chunk); });
    child.stderr.on("data", (chunk) => { output += chunk.toString(); process.stderr.write(chunk); });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolve(output) : reject(new Error(`command_failed:${command}:${code}`)));
  });
}

async function runSilent(command, args, options = {}) {
  return await new Promise((resolve, reject) => {
    let output = "";
    let stderr = "";
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"], shell: false, ...options });
    child.stdout.on("data", (chunk) => { output += chunk.toString(); });
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("exit", (code) => code === 0 ? resolve(output) : reject(new Error(`command_failed:${command}:${code}:${stderr.trim()}`)));
  });
}

async function gitMetadata() {
  const head = (await runSilent("git", ["rev-parse", "HEAD"])).trim();
  const status = await runSilent("git", ["status", "--porcelain"]);
  return {
    head,
    workingTreeDirty: status.trim().length > 0,
  };
}

async function adbRunAsCat(remotePath, localPath) {
  const chunks = [];
  await new Promise((resolve, reject) => {
    const script = `if [ -f ${adbShellQuote(remotePath)} ]; then cat ${adbShellQuote(remotePath)}; else exit 44; fi`;
    const child = spawn(adbCommand, ["exec-out", "run-as", "com.quata", "sh", "-c", script], { stdio: ["ignore", "pipe", "pipe"], shell: false });
    let stderr = "";
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("exit", (code) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`adb_exec_out_failed:${code}:${remotePath}:${stderr.trim()}`));
    });
  });
  await writeFile(localPath, Buffer.concat(chunks));
}

async function collectAvailableDeviceEvidence(destination) {
  await rm(destination, { recursive: true, force: true });
  await mkdir(destination, { recursive: true });
  const copied = [];
  const deviceFiles = await runSilent(adbCommand, [
    "shell",
    "run-as",
    "com.quata",
    "sh",
    "-c",
    `ls ${deviceEvidencePath} 2>/dev/null || true`,
  ]).catch(() => "");
  const discoveredFiles = deviceFiles
    .split(/\r?\n/)
    .map((entry) => entry.trim())
    .filter((entry) => /^(android-|ios-|web-).*\.(png|txt|json)$/.test(entry));
  const files = Array.from(new Set([...evidenceFiles, ...discoveredFiles]));
  for (const file of files) {
    try {
      const localFile = join(destination, file);
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, localFile);
      if ((await stat(localFile)).size === 0) {
        await rm(localFile, { force: true });
        continue;
      }
      copied.push(file);
    } catch {}
  }
  return copied;
}

async function logicalCleanup(config, state) {
  const actions = [];
  const favoriteMessageId = state.favoriteMessage ?? state.editedMessage ?? state.message;
  if (state.thread && favoriteMessageId && state.a) {
    await rpc(config, state.a, "quata_chat_set_favorite", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_id: favoriteMessageId,
      p_favorite: false,
    });
    actions.push("favorite_removed");
  }
  const messageIds = [state.message, state.peerMessage, state.editedMessage, state.profileContent?.attachmentMessageId, state.attachmentsAudio?.video?.messageId, state.attachmentsAudio?.image?.messageId, state.attachmentsAudio?.document?.messageId, state.attachmentsAudio?.audio?.messageId, state.attachmentsAudio?.nextAudio?.messageId, ...state.uiMessages]
    .filter((id, index, all) => Number.isInteger(Number(id)) && all.indexOf(id) === index);
  if (state.thread && messageIds.length && state.a) {
    await rpc(config, state.a, "quata_chat_delete_messages", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message_ids: messageIds,
    });
    actions.push("test_messages_deleted");
  }
  if (state.profilePrivateChat && state.profilePrivateChatMarkerMessage && state.b) {
    await rpc(config, state.b, "quata_chat_delete_messages", {
      p_actor_profile_id: state.b.profileId,
      p_thread_id: state.profilePrivateChat,
      p_message_ids: [state.profilePrivateChatMarkerMessage],
    });
    actions.push("profile_private_chat_marker_deleted");
  }
  const deletedStalePrivateMarkers = await deletePrivateChatTestMarkers(config, state);
  if (deletedStalePrivateMarkers > 0) actions.push(`stale_profile_private_chat_markers_deleted:${deletedStalePrivateMarkers}`);
  const privateMarkers = [state.privateMarker].filter(Boolean);
  if (state.profilePrivateChat && state.a && await threadContainsAnyMarker(config, state.a, state.profilePrivateChat, privateMarkers)) {
    throw new Error("cleanup_residue_detected:profile_private_chat_marker_a");
  }
  if (state.profilePrivateChat && state.b && await threadContainsAnyMarker(config, state.b, state.profilePrivateChat, privateMarkers)) {
    throw new Error("cleanup_residue_detected:profile_private_chat_marker_b");
  }
  if (state.profilePrivateChat) actions.push("cleanup_verified_profile_private_chat_marker_absent");
  await state.cleanupRegistry.cleanupStorageObjects({ config, session: state.a, storageRequest, verifyStorageObjectAbsent, actions });
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
  if (!state.profilePrivateChat || !state.a) return 0;
  const detail = await rpc(config, state.a, "quata_chat_get_thread", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.profilePrivateChat,
    p_known_message_ids: [],
    p_limit: 250,
  });
  const messageIds = rows(detail, "messages")
    .filter((message) => /^chat-profile-private-(web|android|ios)-/.test(messageText(message)))
    .map(messageId)
    .filter((id) => Number.isSafeInteger(Number(id)));
  const uniqueIds = [...new Set(messageIds)];
  if (!uniqueIds.length) return 0;
  await rpc(config, state.a, "quata_chat_delete_messages", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.profilePrivateChat,
    p_message_ids: uniqueIds,
  });
  return uniqueIds.length;
}

async function hardDeleteTemporaryThread(thread, uniqueKey) {
  if (process.env[hardCleanupAuthorizationEnvironment]?.trim() !== hardCleanupAuthorizationValue) {
    throw new Error("missing_hard_cleanup_authorization");
  }
  if (!uniqueKey.startsWith("qadata-chat-actions-notifications-android-")) throw new Error("cleanup_residue_detected:unsafe_unique_key");
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
      "select id from public.chat_threads where id = $1 and unique_key = $2 and unique_key like 'qadata-chat-actions-notifications-android-%' for update",
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
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 750));
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
  return { id, phoneLocal, displayName };
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

async function resolveCommunityChatTarget(actorSession) {
  const actorKey = normalizeCommunityName(actorSession?.neighborhood ?? "");
  const preferredKeys = [
    process.env.QUATA_CHAT_ACTIONS_NOTIFICATIONS_COMMUNITY_CHAT_NAME,
    "Ateneo",
    "La Chana",
    actorSession?.neighborhood,
  ].map(normalizeCommunityName).filter(Boolean);
  return await withDatabase(async (client) => {
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
      keys: [row.name, row.slug, row.normalized_name].map(normalizeCommunityName).filter(Boolean),
    })).filter((row) => uuid.test(row.id) && row.name);
    if (!rows.length) throw new Error("community_chat_flow_no_active_wall");
    const matched = preferredKeys
      .map((key) => rows.find((row) => row.keys.includes(key)))
      .find(Boolean)
      ?? (actorKey ? rows.find((row) => row.keys.includes(actorKey)) : null);
    return matched ?? rows[0];
  });
}

function normalizeCommunityName(value) {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function neighborhoodTagSuffix(value) {
  const normalized = String(value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ".")
    .replace(/^\.+|\.+$/g, "");
  return normalized || "unknown";
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

async function assertNoAttachmentPickerResidue(config, state, attachmentName, marker) {
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

async function verifyRecipientParticipant(thread, recipientProfileId) {
  await withDatabase(async (client) => {
    const result = await client.query(
      "select 1 from public.chat_participants where thread_id = $1 and profile_id = $2 limit 1",
      [thread, recipientProfileId],
    );
    if (result.rowCount !== 1) throw new Error("chat_contract_invalid:recipient_participant_missing");
  });
}

async function participantSnapshot(thread) {
  return await withDatabase(async (client) => {
    const result = await client.query(
      `select profile_id, role, left_at
         from public.chat_participants
        where thread_id = $1`,
      [thread],
    );
    return result.rows;
  });
}

function assertParticipant(snapshot, profileId, role, left = false) {
  const row = snapshot.find((entry) => entry.profile_id === profileId);
  if (!row) throw new Error(`chat_group_admin_participant_missing:${profileId}`);
  if (row.role !== role) throw new Error(`chat_group_admin_participant_role_mismatch:${row.role}:${role}`);
  if (left && !row.left_at) throw new Error("chat_group_admin_participant_not_left");
  if (!left && row.left_at) throw new Error("chat_group_admin_participant_left_unexpectedly");
}

async function pollParticipant(thread, profileId, role, left = false, timeout = 45_000) {
  const deadline = Date.now() + timeout;
  let lastSnapshot = [];
  while (Date.now() < deadline) {
    lastSnapshot = await participantSnapshot(thread);
    try {
      assertParticipant(lastSnapshot, profileId, role, left);
      return lastSnapshot;
    } catch {
      await delay(750);
    }
  }
  assertParticipant(lastSnapshot, profileId, role, left);
  return lastSnapshot;
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

function safeFailure(error) {
  const message = typeof error?.message === "string" ? error.message : "";
  const prefix = [
    "missing_environment", "missing_public_supabase_configuration", "invalid_public_supabase_url",
    "invalid_or_privileged_supabase_key", "public_auth_request_failed", "invalid_auth_response",
    "chat_rpc_failed", "chat_contract_invalid", "chat_backend_poll_timeout", "command_failed",
    "missing_hard_cleanup_authorization", "cleanup_residue_detected",
    "missing_adjacent_profile_credentials_source", "invalid_adjacent_profile_phone",
    "missing_adjacent_recipient_profile", "temporary_profile_hash_window",
    "android_instrumentation_not_ok", "android_instrumentation_semantic_failure", "adb_exec_out_failed",
  ].find((candidate) => message.startsWith(candidate));
  return prefix ? message : "unexpected_chat_actions_notifications_android_failure";
}

function sanitizedDiagnosticError(error) {
  const message = typeof error?.message === "string" ? error.message : String(error ?? "unknown");
  return {
    name: typeof error?.name === "string" ? error.name : "Error",
    message: message.slice(0, 1_000),
  };
}

class EvidenceCompleted extends Error {}

async function acquireAndroidEvidenceLock() {
  const started = Date.now();
  await mkdir(dirname(androidEvidenceLockPath), { recursive: true });
  while (true) {
    try {
      const handle = await open(androidEvidenceLockPath, "wx", 0o600);
      const payload = {
        pid: process.pid,
        startedAt: new Date().toISOString(),
        argv: process.argv.slice(2),
      };
      await handle.writeFile(`${JSON.stringify(payload)}\n`);
      await handle.close();
      return async () => {
        await rm(androidEvidenceLockPath, { force: true });
      };
    } catch (error) {
      if (error?.code !== "EEXIST") throw error;
      let ageMs = 0;
      try {
        const lockStat = await stat(androidEvidenceLockPath);
        ageMs = Date.now() - lockStat.mtimeMs;
      } catch {}
      if (ageMs > androidEvidenceLockStaleMs) {
        console.warn(`Removing stale Android evidence lock after ${Math.round(ageMs / 1000)}s: ${androidEvidenceLockPath}`);
        await rm(androidEvidenceLockPath, { force: true });
        continue;
      }
      if (Date.now() - started > androidEvidenceLockTimeoutMs) {
        throw new Error(`android_evidence_lock_timeout:${androidEvidenceLockPath}`);
      }
      await delay(1_000);
    }
  }
}

const report = {
  check: "CHAT-ACTIONS-NOTIFICATIONS-ANDROID-001",
  status: "failed",
  startedAt: new Date().toISOString(),
  git: await gitMetadata(),
  steps: [],
  cleanup: { state: "not_started" },
  evidence: {},
};
const state = { a: null, b: null, thread: null, message: null, peerMessage: null, editableMessage: null, editedMessage: null, uiMessages: [], uniqueKey: null, forwardProfile: null, forwardThread: null, forwardedMessage: null, groupAdminProfile: null, groupRemoveProfile: null, groupBlockProfile: null, profileFollow: null, profileListEdges: null, profileContent: null, feedOfficialComments: null, profileEntry: null, profilePrivateChat: null, profileRolesSafety: null, profilePrivateChatMarkerMessage: null, privateMarker: null, attachmentsAudio: null, attachmentPicker: null, communityChat: null, sosWithLocationMarker: null, sosUnavailableMarker: null, sosWithLocationMessage: null, sosUnavailableMessage: null, cleanupRegistry: createCleanupRegistry() };
let profileHashWindow = { state: "not_started", restored: true, restore: async () => {} };
const localCredentials = join("build-reports", "android", `chat-actions-notifications-credentials-${randomUUID()}.json`);
const evidenceDir = options.evidenceDir;
const releaseAndroidEvidenceLock = await acquireAndroidEvidenceLock();
try {
  const config = await publicBackendConfig();
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co$/i.test(config.baseUrl)) throw new Error("invalid_public_supabase_url");
  if (!isPublicKey(config.key)) throw new Error("invalid_or_privileged_supabase_key");
  const users = await authorizedUsers();
  const userA = users.a;
  const userB = users.b;
  const usersForTemporaryHash = [userA, userB].filter((user) => user?.countryCode && user?.phone && user?.password);
  profileHashWindow = await openTemporaryProfileHashWindow(usersForTemporaryHash);
  if (profileHashWindow.state === "opened") {
    report.steps.push("temporary_profile_hash_window_opened");
  }
  state.a = await login(config, userA);
  if (useAdjacentAuthorizedProfile && !userB.password) {
    state.b = { label: "B", profileId: await resolveAdjacentRecipientProfile(userB.adjacentPhoneKeys) };
    report.steps.push("authorized_profile_logged_in_and_recipient_resolved");
  } else {
    state.b = await login(config, userB);
    report.steps.push("two_authorized_profiles_logged_in");
  }

  const runId = randomUUID();
  state.uniqueKey = `qadata-chat-actions-notifications-android-${runId}`;
  if (groupAdminOnly) {
    state.groupAdminProfile = await createTemporaryForwardProfile(runId);
    state.forwardProfile = state.groupAdminProfile;
    report.steps.push("temporary_group_admin_participant_profile_created");
  }
  if (groupModerationOnly) {
    state.groupRemoveProfile = await createTemporaryForwardProfile(`${runId}-remove`, "1");
    state.groupBlockProfile = await createTemporaryForwardProfile(`${runId}-block`, "2");
    report.steps.push("temporary_group_moderation_participant_profiles_created");
  }
  if (!translationOnly && !profileOnly && !profileFollowOnly && !profileListsOnly && !profileContentOnly && !feedOfficialCommentsOnly && !feedOfficialCommentsErrorOnly && !feedOfficialCommentsSelectorStatesOnly && !profileEntryOnly && !profilePrivateChatOnly && !profileRolesSafetyOnly && !communityChatOnly && !menuSurfaceOnly && !attachmentsAudioOnly && !attachmentPickerOnly && !composerEmojiOnly && !groupSosOnly && !groupAdminOnly && !groupModerationOnly) {
    state.forwardProfile = await createTemporaryForwardProfile(runId);
    report.steps.push("temporary_forward_destination_profile_created");
  }
  state.thread = threadId(await rpc(config, state.a, "quata_chat_start_thread", {
    p_actor_profile_id: state.a.profileId,
    p_recipient_profile_ids: [state.b.profileId],
    p_subject: `QADATA chat actions notifications Android ${runId}`,
    p_type: "group",
    p_message: "",
    p_unique_key: state.uniqueKey,
    p_community_id: null,
  }));
  const marker = translationOnly ? "Mbolo" : `chat-actions-notifications-android-${randomUUID()}`;
  const markerProbe = marker.slice(0, 24);
  const peerMarker = `chat-profile-peer-android-${randomUUID()}`;
  const peerProbe = peerMarker.slice(0, 24);
  const privateMarker = `chat-profile-private-android-${randomUUID()}`;
  state.privateMarker = privateMarker;
  const privateProbe = privateMarker.slice(0, 28);
  const composerMarker = `🚨 chat-compose-ui-android-${randomUUID()} www.quata.test/chat 📝`;
  const replyMarker = `chat-reply-ui-android-${randomUUID()}`;
  const editMarker = `chat-edit-ui-android-${randomUUID()}`;
  const attachmentPickerMarker = `chat-attachment-picker-android-${randomUUID()}`;
  const attachmentPickerName = options.attachmentPickerSource === "document"
    ? `qadata-android-picker-${runId}.txt`
    : `qadata-android-picker-${runId}.png`;
  state.message = messageId(await rpc(config, state.a, "quata_chat_send_message", {
    p_actor_profile_id: state.a.profileId,
    p_thread_id: state.thread,
    p_message: marker,
    p_file_ids: [],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-actions-notifications-android-${randomUUID()}`,
  }));
  if (state.b.accessToken) {
    await pollMessage(config, state.b, state.thread, (message) => Number(message?.id) === state.message && messageText(message) === marker);
    state.peerMessage = messageId(await rpc(config, state.b, "quata_chat_send_message", {
      p_actor_profile_id: state.b.profileId,
      p_thread_id: state.thread,
      p_message: peerMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-profile-peer-android-${randomUUID()}`,
    }));
    await pollMessage(config, state.a, state.thread, (message) => Number(message?.id) === state.peerMessage && messageText(message) === peerMarker);
    report.steps.push("unique_own_and_peer_messages_visible");
    if (profilePrivateChatOnly) {
      state.profilePrivateChat = threadId(await rpc(config, state.a, "quata_chat_get_or_create_private_thread", {
        p_actor_profile_id: state.a.profileId,
        p_peer_profile_id: state.b.profileId,
      }));
      await rpc(config, state.b, "quata_chat_send_message", {
        p_actor_profile_id: state.b.profileId,
        p_thread_id: state.profilePrivateChat,
        p_message: privateMarker,
        p_file_ids: [],
        p_reply_to_message_id: null,
        p_client_message_id: `chat-profile-private-android-${randomUUID()}`,
      });
      const privateMessage = await pollMessage(config, state.a, state.profilePrivateChat, (message) => messageText(message) === privateMarker);
      state.profilePrivateChatMarkerMessage = messageId(privateMessage);
      report.steps.push("profile_private_chat_seed_message_ready");
    }
  } else {
    await verifyRecipientParticipant(state.thread, state.b.profileId);
    report.steps.push("adjacent_recipient_participant_verified");
  }
  report.steps.push("isolated_thread_and_own_message_ready");

  if (groupAdminOnly || groupModerationOnly) {
    await withDatabase(async (client) => {
      const result = await client.query(
        `update public.chat_participants
            set role = 'moderator'
          where thread_id = $1
            and profile_id = $2
            and role in ('owner', 'member')`,
        [state.thread, state.a.profileId],
      );
      if (result.rowCount !== 1) throw new Error("chat_group_admin_actor_role_seed_failed");
    });
    report.steps.push("group_admin_actor_seeded_as_moderator_for_ui_management");
  }

  if (groupSosOnly) {
    state.sosWithLocationMarker = "[SOS:kind=update;name=Gabrielo;lat=3.7523;lng=8.7741;age_ms=45000;accuracy_m=18;speed_kmh=0]";
    state.sosUnavailableMarker = "[SOS:kind=alert;name=Gabrielo;custom=Necesito%20ayuda;reason=permission_denied]";
    await rpc(config, state.a, "quata_chat_send_message", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message: state.sosWithLocationMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-group-sos-location-android-${runId}`,
    });
    const sosWithLocationMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === state.sosWithLocationMarker);
    state.sosWithLocationMessage = messageId(sosWithLocationMessage);
    await rpc(config, state.a, "quata_chat_send_message", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_message: state.sosUnavailableMarker,
      p_file_ids: [],
      p_reply_to_message_id: null,
      p_client_message_id: `chat-group-sos-unavailable-android-${runId}`,
    });
    const sosUnavailableMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === state.sosUnavailableMarker);
    state.sosUnavailableMessage = messageId(sosUnavailableMessage);
    report.steps.push("sos_location_and_unavailable_messages_seeded");
  }

  await mkdir(dirname(localCredentials), { recursive: true });
  await writeFile(localCredentials, `${JSON.stringify({ country_code: userA.countryCode, phone: userA.phone, password: userA.password })}\n`, { mode: 0o600 });
  const gradle = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  await run(gradle, [":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain"], {
    env: {
      ...process.env,
      JAVA_HOME: process.env.JAVA_HOME || "C:\\Program Files\\Android\\Android Studio\\jbr",
      ANDROID_HOME: process.env.ANDROID_HOME || `${process.env.LOCALAPPDATA}\\Android\\Sdk`,
      ANDROID_SDK_ROOT: process.env.ANDROID_SDK_ROOT || `${process.env.LOCALAPPDATA}\\Android\\Sdk`,
    },
  });
  await run(adbCommand, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"]);
  await run(adbCommand, ["install", "-r", "-t", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]);
  await run(adbCommand, ["shell", "pm", "clear", "com.quata"]);
  await run(adbCommand, ["push", localCredentials, deviceTempCredentialsPath]);
  await run(adbCommand, ["shell", "chmod", "644", deviceTempCredentialsPath]);
  await run(adbCommand, ["shell", "run-as", "com.quata", "mkdir", "-p", "files"]);
  await run(adbCommand, ["shell", "run-as", "com.quata", "cp", deviceTempCredentialsPath, `files/${deviceCredentialsPath.replace("app-internal:", "")}`]);
  await run(adbCommand, ["shell", "rm", "-f", deviceTempCredentialsPath]);
  await run(adbCommand, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]);
  const runInstrumentationStage = async (stage) => await runCapture(adbCommand, [
    "shell",
    [
      "am", "instrument", "-w", "-r",
      "-e", "class", "com.quata.feature.chat.presentation.chat.ChatActionsNotificationsInstrumentedTest",
      "-e", "quataChatActionsStage", stage,
      "-e", "quataChatActionsCredentialsFile", deviceCredentialsPath,
      "-e", "quataChatActionsUrl", chatUrl(`sb:${state.thread}`),
      "-e", "quataChatActionsOwnProbe", markerProbe,
      "-e", "quataChatActionsPeerProbe", peerProbe,
      "-e", "quataChatActionsPrivateProbe", privateProbe,
      "-e", "quataChatActionsProfileId", state.b.profileId,
      "-e", "quataChatActionsActorProfileId", state.a.profileId,
      "-e", "quataChatActionsProfileNeighborhood", state.b.neighborhood || "Bovano",
      "-e", "quataChatActionsCommunityName", state.communityChat?.name ?? "",
      "-e", "quataChatActionsComposerMarker", composerMarker,
      "-e", "quataChatActionsReplyMarker", replyMarker,
      "-e", "quataChatActionsEditMarker", editMarker,
      "-e", "quataChatActionsForwardQuery", state.forwardProfile?.phoneLocal ?? "translation-only",
      "-e", "quataChatActionsPostId", state.feedOfficialComments?.feed?.postId ?? state.profileContent?.postId ?? "",
      "-e", "quataChatActionsOfficialPostId", state.feedOfficialComments?.official?.postId ?? state.profileEntry?.official?.id ?? "",
      "-e", "quataChatActionsCommentId", state.profileContent?.seedCommentId ?? "",
      "-e", "quataChatActionsAttachmentId", String(state.profileContent?.attachmentId ?? ""),
      "-e", "quataChatActionsProfileContentComment", state.profileContent?.uiCommentMarker ?? "",
      "-e", "quataChatActionsProfileContentReplyComment", state.profileContent?.uiReplyCommentMarker ?? "",
      "-e", "quataChatActionsFeedComment", state.feedOfficialComments?.feed?.uiComment ?? "",
      "-e", "quataChatActionsFeedCommentId", state.feedOfficialComments?.feed?.seedCommentId ?? "",
      "-e", "quataChatActionsFeedReplyComment", state.feedOfficialComments?.feed?.uiReplyComment ?? "",
      "-e", "quataChatActionsOfficialComment", state.feedOfficialComments?.official?.uiComment ?? "",
      "-e", "quataChatActionsOfficialCommentId", state.feedOfficialComments?.official?.seedCommentId ?? "",
      "-e", "quataChatActionsOfficialReplyComment", state.feedOfficialComments?.official?.uiReplyComment ?? "",
      "-e", "quataChatActionsDocumentProbe", state.attachmentsAudio?.document?.markerProbe ?? "",
      "-e", "quataChatActionsAudioProbe", state.attachmentsAudio?.audio?.markerProbe ?? "",
      "-e", "quataChatActionsAudioName", state.attachmentsAudio?.audio?.name ?? "",
      "-e", "quataChatActionsNextAudioName", state.attachmentsAudio?.nextAudio?.name ?? "",
      "-e", "quataChatActionsImageProbe", state.attachmentsAudio?.image?.markerProbe ?? "",
      "-e", "quataChatActionsVideoProbe", state.attachmentsAudio?.video?.markerProbe ?? "",
      "-e", "quataChatActionsAudioRecordingMarker", state.attachmentsAudio?.recordingMarker ?? "",
      "-e", "quataChatActionsAttachmentPickerSource", options.attachmentPickerSource,
      "-e", "quataChatActionsAttachmentPickerOutcome", options.attachmentPickerOutcome,
      "-e", "quataChatActionsAttachmentPickerName", attachmentPickerName,
      "-e", "quataChatActionsAttachmentPickerMarker", attachmentPickerMarker,
      "-e", "quataChatGroupAdminProfileId", state.groupAdminProfile?.id ?? "",
      "-e", "quataChatGroupAdminDisplayName", state.groupAdminProfile?.displayName ?? "",
      "-e", "quataChatGroupAdminSearchQuery", state.groupAdminProfile?.phoneLocal ?? "",
      "-e", "quataChatGroupRemoveProfileId", state.groupRemoveProfile?.id ?? "",
      "-e", "quataChatGroupRemoveDisplayName", state.groupRemoveProfile?.displayName ?? "",
      "-e", "quataChatGroupRemoveSearchQuery", state.groupRemoveProfile?.phoneLocal ?? "",
      "-e", "quataChatGroupBlockProfileId", state.groupBlockProfile?.id ?? "",
      "-e", "quataChatGroupBlockDisplayName", state.groupBlockProfile?.displayName ?? "",
      "-e", "quataChatGroupBlockSearchQuery", state.groupBlockProfile?.phoneLocal ?? "",
      "com.quata.test/androidx.test.runner.AndroidJUnitRunner",
    ].map(adbShellQuote).join(" "),
  ]);
  const assertInstrumentationPassed = (stage, instrumentationOutput) => {
    if (!/OK \(\d+ tests?\)/.test(instrumentationOutput)) {
      report.diagnostics = {
        ...(report.diagnostics ?? {}),
        [`androidInstrumentationTail:${stage}`]: instrumentationOutput.split(/\r?\n/).slice(-80).join("\n"),
      };
      throw new Error(`android_instrumentation_not_ok:${stage}`);
    }
    if (/FAILURES!!!|SKIPPED|AssumptionViolatedException/i.test(instrumentationOutput)) {
      report.diagnostics = {
        ...(report.diagnostics ?? {}),
        [`androidInstrumentationTail:${stage}`]: instrumentationOutput.split(/\r?\n/).slice(-80).join("\n"),
      };
      throw new Error(`android_instrumentation_semantic_failure:${stage}`);
    }
  }

  if (translationOnly) {
    assertInstrumentationPassed("translation", await runInstrumentationStage("translation"));
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((candidate) => candidate.startsWith("android-chat-translation-") || candidate.endsWith(".json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
    }
    report.status = "passed";
    report.steps.push("chat_translation_common_overlay_translated_fang_message_and_returned");
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      translatedMessageId: state.message,
      markerSha256: sha256(marker),
    };
    throw new EvidenceCompleted();
  }

  if (menuSurfaceOnly) {
    assertInstrumentationPassed("menu-surface", await runInstrumentationStage("menu-surface"));
    if (!isMuted(await inboxThread(config, state.a, state.thread))) throw new Error("mute_state_not_persisted:true");
    report.steps.push("options_menu_surface_visible_and_mute_toggled_by_shared_ui");
    await rpc(config, state.a, "quata_chat_set_muted", {
      p_actor_profile_id: state.a.profileId,
      p_thread_id: state.thread,
      p_muted: false,
    });
    if (isMuted(await inboxThread(config, state.a, state.thread))) throw new Error("mute_state_not_persisted:false");
    report.steps.push("options_menu_unmute_verified_by_rpc");
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((name) => name.includes("options-menu") || name.endsWith("evidence.json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file));
    }
    report.status = "passed";
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      seedMessageId: state.message,
      markerSha256: sha256(marker),
    };
    throw new Error("menu_surface_only_completed");
  }

  if (attachmentsAudioOnly) {
    state.attachmentsAudio = {
      video: await createChatAttachmentMessage(config, state.a, state.thread, runId, "video"),
      image: await createChatAttachmentMessage(config, state.a, state.thread, runId, "image"),
      document: await createChatAttachmentMessage(config, state.a, state.thread, runId, "document"),
      audio: await createChatAttachmentMessage(config, state.a, state.thread, runId, "audio"),
      nextAudio: await createChatAttachmentMessage(config, state.a, state.thread, `${runId}-next`, "audio", "-next", { audioDurationSeconds: 12 }),
      recordingMarker: `chat-audio-recording-android-${randomUUID()}`,
    };
    report.steps.push("video_image_document_and_consecutive_audio_attachment_messages_seeded");
    assertInstrumentationPassed("attachments-audio", await runInstrumentationStage("attachments-audio"));
    const recordingMessage = await pollMessage(
      config,
      state.a,
      state.thread,
      (message) => messageText(message) === state.attachmentsAudio.recordingMarker && messageAttachments(message).some((attachment) => /^audio\//i.test(attachment.mimeType ?? "")),
      60_000,
    );
    const recordingAttachment = messageAttachments(recordingMessage).find((attachment) => /^audio\//i.test(attachment.mimeType ?? ""));
    if (!recordingAttachment) throw new Error("audio_recording_sent_attachment_missing");
    const recordingMessageId = messageId(recordingMessage);
    state.uiMessages.push(recordingMessageId);
    state.cleanupRegistry.trackStorageObject({
      bucket: recordingAttachment.bucket || "chat-attachments",
      storagePath: recordingAttachment.storagePath,
      name: recordingAttachment.name || "recorded-audio",
    });
    report.evidence.audioRecordingSent = {
      markerSha256: sha256(state.attachmentsAudio.recordingMarker),
      messageId: recordingMessageId,
      attachmentId: recordingAttachment.id,
      mimeType: recordingAttachment.mimeType,
      storagePathSha256: recordingAttachment.storagePath ? sha256(recordingAttachment.storagePath) : null,
    };
    report.steps.push("android_audio_recording_sent_by_shared_composer_and_verified_by_rpc");
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((name) => name.includes("attachment") || name.includes("audio") || name.endsWith("evidence.json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
    }
    report.status = "passed";
    report.steps.push("document_and_audio_shared_attachment_chrome_verified");
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      videoMessageId: state.attachmentsAudio.video.messageId,
      imageMessageId: state.attachmentsAudio.image.messageId,
      documentMessageId: state.attachmentsAudio.document.messageId,
      audioMessageId: state.attachmentsAudio.audio.messageId,
      nextAudioMessageId: state.attachmentsAudio.nextAudio.messageId,
      videoAttachmentId: state.attachmentsAudio.video.id,
      imageAttachmentId: state.attachmentsAudio.image.id,
      documentAttachmentId: state.attachmentsAudio.document.id,
      audioAttachmentId: state.attachmentsAudio.audio.id,
      nextAudioAttachmentId: state.attachmentsAudio.nextAudio.id,
      recordingMarkerSha256: sha256(state.attachmentsAudio.recordingMarker),
      recordingMessageId: report.evidence.audioRecordingSent.messageId,
      recordingAttachmentId: report.evidence.audioRecordingSent.attachmentId,
      markerSha256: sha256(marker),
      videoMarkerSha256: sha256(state.attachmentsAudio.video.marker),
      imageMarkerSha256: sha256(state.attachmentsAudio.image.marker),
      documentMarkerSha256: sha256(state.attachmentsAudio.document.marker),
      audioMarkerSha256: sha256(state.attachmentsAudio.audio.marker),
      nextAudioMarkerSha256: sha256(state.attachmentsAudio.nextAudio.marker),
    };
    throw new Error("attachments_audio_only_completed");
  }

  if (attachmentPickerOnly) {
    assertInstrumentationPassed("attachment-picker", await runInstrumentationStage("attachment-picker"));
    if (options.attachmentPickerOutcome !== "success" && options.attachmentPickerOutcome !== "register-failure") {
      await rm(evidenceDir, { recursive: true, force: true });
      await mkdir(evidenceDir, { recursive: true });
      for (const file of evidenceFiles.filter((name) => name.includes("attachment-picker") || name.endsWith("evidence.json"))) {
        await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
      }
      report.status = "passed";
      report.steps.push(`attachment_picker_${options.attachmentPickerSource}_${options.attachmentPickerOutcome}_handled_without_attachment`);
      report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
      report.evidence.attachmentPicker = {
        source: options.attachmentPickerSource,
        outcome: options.attachmentPickerOutcome,
        pendingCreated: false,
        messageCreated: false,
      };
      report.fixture = {
        threadId: state.thread,
        conversationId: `sb:${state.thread}`,
        markerSha256: sha256(attachmentPickerMarker),
        source: options.attachmentPickerSource,
        outcome: options.attachmentPickerOutcome,
      };
      throw new Error("attachment_picker_only_completed");
    }
    if (options.attachmentPickerOutcome === "register-failure") {
      await assertNoAttachmentPickerResidue(config, state, attachmentPickerName, attachmentPickerMarker);
      await rm(evidenceDir, { recursive: true, force: true });
      await mkdir(evidenceDir, { recursive: true });
      for (const file of evidenceFiles.filter((name) => name.includes("attachment-picker") || name.endsWith("evidence.json"))) {
        await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
      }
      report.status = "passed";
      report.steps.push(`attachment_picker_${options.attachmentPickerSource}_register_failure_rolled_back_storage`);
      report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
      report.evidence.attachmentPicker = {
        source: options.attachmentPickerSource,
        outcome: options.attachmentPickerOutcome,
        pendingCreated: true,
        messageCreated: false,
        storageResidueCount: 0,
      };
      report.fixture = {
        threadId: state.thread,
        conversationId: `sb:${state.thread}`,
        markerSha256: sha256(attachmentPickerMarker),
        source: options.attachmentPickerSource,
        outcome: options.attachmentPickerOutcome,
      };
      throw new Error("attachment_picker_only_completed");
    }
    const pickerMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === attachmentPickerMarker);
    const pickerMessageId = messageId(pickerMessage);
    state.uiMessages.push(pickerMessageId);
    const attachments = messageAttachments(pickerMessage);
    if (!attachments.length) throw new Error("attachment_picker_message_missing_attachment");
    const uploaded = attachments[0];
    const storagePath = uploaded.storage_path ?? uploaded.storagePath ?? uploaded.path ?? uploaded.object_path ?? uploaded.objectPath;
    if (storagePath) {
      state.cleanupRegistry.trackStorageObject({ storagePath, name: uploaded.name ?? uploaded.file_name ?? attachmentPickerName });
    }
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((name) => name.includes("attachment-picker") || name.endsWith("evidence.json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
    }
    report.status = "passed";
    report.steps.push("attachment_picker_shared_composer_flow_verified");
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.evidence.attachmentPicker = {
      source: options.attachmentPickerSource,
      messageId: pickerMessageId,
      attachmentCount: attachments.length,
      attachmentNames: attachments.map((entry) => entry.name ?? entry.file_name ?? entry.fileName).filter(Boolean),
      storagePathSha256: storagePath ? sha256(storagePath) : null,
    };
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      messageId: pickerMessageId,
      markerSha256: sha256(attachmentPickerMarker),
      source: options.attachmentPickerSource,
      outcome: options.attachmentPickerOutcome,
    };
    throw new Error("attachment_picker_only_completed");
  }

  if (composerEmojiOnly) {
    assertInstrumentationPassed("composer-emoji", await runInstrumentationStage("composer-emoji"));
    const composerMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === composerMarker);
    const composerMessageId = messageId(composerMessage);
    state.uiMessages.push(composerMessageId);
    report.steps.push("composer_emoji_link_marker_sent_by_shared_ui_and_verified_by_rpc");
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((name) => name.includes("composer") || name.includes("thread-initial") || name.endsWith("evidence.json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
    }
    report.status = "passed";
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      seedMessageId: state.message,
      composerMessageId,
      markerSha256: sha256(marker),
      composerMarkerSha256: sha256(composerMarker),
    };
    throw new Error("composer_emoji_only_completed");
  }

  if (groupSosOnly) {
    assertInstrumentationPassed("group-sos", await runInstrumentationStage("group-sos"));
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((name) => name.includes("group") || name.includes("sos") || name.endsWith("evidence.json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
    }
    report.status = "passed";
    report.steps.push("group_menu_and_sos_shared_anchors_verified");
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      seedMessageId: state.message,
      peerMessageId: state.peerMessage,
      sosWithLocationMessageId: state.sosWithLocationMessage,
      sosUnavailableMessageId: state.sosUnavailableMessage,
      markerSha256: sha256(marker),
      peerMarkerSha256: sha256(peerMarker),
      sosWithLocationMarkerSha256: sha256(state.sosWithLocationMarker),
      sosUnavailableMarkerSha256: sha256(state.sosUnavailableMarker),
    };
    throw new Error("group_sos_only_completed");
  }

  if (groupAdminOnly) {
    assertInstrumentationPassed("group-admin", await runInstrumentationStage("group-admin"));
    await pollParticipant(state.thread, state.groupAdminProfile.id, "moderator");
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((name) => name.includes("group-admin") || name.endsWith("evidence.json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
    }
    report.status = "passed";
    report.steps.push("group_participant_promoted_from_shared_member_menu_and_verified_by_db");
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      seedMessageId: state.message,
      peerMessageId: state.peerMessage,
      groupAdminProfileIdSha256: sha256(state.groupAdminProfile.id),
      groupAdminDisplayNameSha256: sha256(state.groupAdminProfile.displayName),
      markerSha256: sha256(marker),
    };
    throw new Error("group_admin_only_completed");
  }

  if (groupModerationOnly) {
    assertInstrumentationPassed("group-moderation", await runInstrumentationStage("group-moderation"));
    await pollParticipant(state.thread, state.groupRemoveProfile.id, "member", true);
    await pollThreadBlock(state.thread, state.a.profileId, state.groupBlockProfile.id);
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((name) => name.includes("group-moderation") || name.endsWith("evidence.json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
    }
    report.status = "passed";
    report.steps.push("group_participant_removed_from_shared_member_menu_and_verified_by_db");
    report.steps.push("group_participant_blocked_from_shared_member_menu_and_verified_by_db");
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      seedMessageId: state.message,
      peerMessageId: state.peerMessage,
      removeProfileIdSha256: sha256(state.groupRemoveProfile.id),
      blockProfileIdSha256: sha256(state.groupBlockProfile.id),
      markerSha256: sha256(marker),
    };
    throw new Error("group_moderation_only_completed");
  }

  if (communityChatOnly) {
    state.communityChat = await resolveCommunityChatTarget(state.a);
    report.steps.push("community_chat_active_wall_selected");
    assertInstrumentationPassed("community-chat", await runInstrumentationStage("community-chat"));
    await rm(evidenceDir, { recursive: true, force: true });
    await mkdir(evidenceDir, { recursive: true });
    for (const file of evidenceFiles.filter((name) => name.startsWith("android-community-chat-") || name.endsWith("evidence.json"))) {
      await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file)).catch(() => {});
    }
    report.status = "passed";
    report.steps.push("community_chat_opened_from_shared_android_community_anchor");
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      seedConversationId: `sb:${state.thread}`,
      communityName: state.communityChat.name,
      communityWallId: state.communityChat.id,
      communityChatTag: `neighborhood.chat.${neighborhoodTagSuffix(state.communityChat.name)}`,
      uniqueKeySha256: sha256(state.uniqueKey),
    };
    throw new Error("community_chat_only_completed");
  }

  if (state.b.accessToken) {
    if (profileFollowOnly) {
      state.profileFollow = await prepareProfileFollowAbsent(state.a.profileId, state.b.profileId);
      report.steps.push("profile_follow_initial_state_snapshot_and_absent_prepared");
    }
    if (profileListsOnly) {
      state.profileListEdges = await prepareProfileListEdges(state.a.profileId, state.b.profileId);
      report.steps.push("profile_follow_list_edges_prepared_reversibly");
    }
    if (profileEntryOnly) {
      state.profileEntry = await prepareProfileEntryFixture(config, runId);
      state.profileContent = state.profileEntry.profileContent;
      report.steps.push("profile_entry_feed_official_communities_conversations_and_chat_fixtures_prepared");
    } else if (feedOfficialCommentsOnly || feedOfficialCommentsErrorOnly || feedOfficialCommentsSelectorStatesOnly) {
      state.feedOfficialComments = {
        marker: `qadata-feed-official-comments-${runId}`,
        actorSession: state.a,
        targetSession: state.b,
      };
      await prepareFeedOfficialCommentsFixture(state.feedOfficialComments);
      state.feedOfficialComments.feed.uiReplyComment = `😀 ${state.feedOfficialComments.marker} feed reply comment`;
      state.feedOfficialComments.official.uiReplyComment = `😀 ${state.feedOfficialComments.marker} official reply comment`;
      report.steps.push("feed_official_comments_fixture_prepared");
    } else if (profileRolesSafetyOnly) {
      state.profileRolesSafety = await prepareProfileRolesSafetyFixture({
        actorSession: state.a,
        targetSession: state.b,
        withDatabase,
      });
      report.steps.push("profile_roles_safety_initial_state_snapshot_and_admin_actor_prepared");
    } else if (profileContentOnly) {
      state.profileContent = {
        marker: `qadata-profile-content-${runId}`,
        config,
        actorSession: state.a,
        targetSession: state.b,
        threadId: state.thread,
      };
      state.profileContent.uiCommentMarker = `😀 ${state.profileContent.marker} android ui comment`;
      state.profileContent.uiReplyCommentMarker = `😀 ${state.profileContent.marker} android reply comment`;
      await prepareProfileContentFixture(state.profileContent);
      report.steps.push("profile_content_fixture_prepared");
    }
    const profileStage = feedOfficialCommentsSelectorStatesOnly ? "feed-official-comments-selector-states" : feedOfficialCommentsErrorOnly ? "feed-official-comments-error" : feedOfficialCommentsOnly ? "feed-official-comments" : profileFollowOnly ? "profile-follow" : profileListsOnly ? "profile-lists" : profileContentOnly ? "profile-content" : profileEntryOnly ? "profile-entry" : profilePrivateChatOnly ? "profile-private-chat" : profileRolesSafetyOnly ? "profile-roles-safety" : "profile";
    assertInstrumentationPassed(profileStage, await runInstrumentationStage(profileStage));
    if (profileFollowOnly) {
      await pollProfileFollowEdge(state.a.profileId, state.b.profileId, true);
      report.steps.push("profile_follow_toggled_and_verified_by_db");
    }
    report.steps.push(profileListsOnly
      ? "peer_public_profile_followers_and_following_lists_opened_and_returned"
      : profileContentOnly
        ? "profile_content_gallery_comments_and_attachments_verified"
        : feedOfficialCommentsSelectorStatesOnly
          ? "flow_emoji_selector_empty_and_error_states_verified_with_common_tags"
        : feedOfficialCommentsErrorOnly
          ? "feed_and_official_comment_error_rollback_verified_with_common_tags"
        : feedOfficialCommentsOnly
          ? "feed_and_official_comments_emoji_picker_verified_with_common_tags"
        : profileEntryOnly
          ? "profile_entry_feed_official_communities_conversations_and_chat_opened_common_profile_and_returned"
        : profilePrivateChatOnly
          ? "profile_private_chat_opened_from_common_profile_action_and_verified_by_rpc"
        : "peer_avatar_opened_public_profile_and_returned_to_chat");
    if (profileContentOnly) {
      state.profileContent.uiReplyCommentId = await pollProfileContentReplyComment(state.profileContent, state.profileContent.uiReplyCommentMarker, state.profileContent.seedCommentId);
      state.profileContent.uiCommentId = await pollProfileContentComment(state.profileContent, state.profileContent.uiCommentMarker);
      report.steps.push("profile_content_reply_created_from_ui_and_verified_by_db");
      report.steps.push("profile_content_comment_created_from_ui_and_verified_by_db");
    }
    if (feedOfficialCommentsOnly) {
      state.feedOfficialComments.feed.uiReplyCommentId = await pollFeedOfficialReplyComment(state.feedOfficialComments, "feed", state.feedOfficialComments.feed.uiReplyComment, state.feedOfficialComments.feed.seedCommentId);
      state.feedOfficialComments.official.uiReplyCommentId = await pollFeedOfficialReplyComment(state.feedOfficialComments, "official", state.feedOfficialComments.official.uiReplyComment, state.feedOfficialComments.official.seedCommentId);
      state.feedOfficialComments.feed.uiCommentId = await pollFeedOfficialComment(state.feedOfficialComments, "feed", state.feedOfficialComments.feed.uiComment);
      state.feedOfficialComments.official.uiCommentId = await pollFeedOfficialComment(state.feedOfficialComments, "official", state.feedOfficialComments.official.uiComment);
      report.steps.push("feed_comments_reply_created_from_ui_and_verified_by_db");
      report.steps.push("official_comments_reply_created_from_ui_and_verified_by_db");
      report.steps.push("feed_comments_emoji_created_from_ui_and_verified_by_db");
      report.steps.push("official_comments_emoji_created_from_ui_and_verified_by_db");
    }
    if (feedOfficialCommentsErrorOnly) {
      await assertFeedOfficialCommentAbsent(state.feedOfficialComments, "feed", state.feedOfficialComments.feed.uiComment);
      await assertFeedOfficialCommentAbsent(state.feedOfficialComments, "official", state.feedOfficialComments.official.uiComment);
      report.steps.push("feed.comments_failed_comment_not_visible_after_rollback");
      report.steps.push("official.comments_failed_comment_not_visible_after_rollback");
      report.steps.push("feed_comments_forced_error_visible_and_rollback_verified");
      report.steps.push("official_comments_forced_error_visible_and_rollback_verified");
    }
    if (feedOfficialCommentsSelectorStatesOnly) {
      report.steps.push("feed_comments_emoji_selector_error_state_visible_with_retry");
      report.steps.push("official_comments_emoji_selector_empty_state_visible_without_cells");
    }
    if (profileRolesSafetyOnly) {
      report.evidence.profileRolesPersisted = await pollProfileRoles({
        fixture: state.profileRolesSafety,
        withDatabase,
        expected: { isAdmin: false, isOfficial: true },
        delay,
      });
      report.evidence.profileReportPersisted = await pollProfileReport({
        fixture: state.profileRolesSafety,
        withDatabase,
        delay,
      });
      report.evidence.profileBlockPersisted = await pollProfileGlobalBlock({
        fixture: state.profileRolesSafety,
        withDatabase,
        expectedBlocked: true,
        delay,
      });
      report.steps.push("profile_roles_safety_roles_report_and_block_verified_by_db");
    }
  }

  if (profileOnly || profileFollowOnly || profileListsOnly || profileContentOnly || feedOfficialCommentsOnly || feedOfficialCommentsErrorOnly || feedOfficialCommentsSelectorStatesOnly || profileEntryOnly || profilePrivateChatOnly || profileRolesSafetyOnly) {
    const focalEvidencePrefix = (feedOfficialCommentsOnly || feedOfficialCommentsErrorOnly || feedOfficialCommentsSelectorStatesOnly) ? /(feed-comments|official-comments)/ : /profile/;
    const copiedEvidenceFiles = await collectAvailableDeviceEvidence(evidenceDir);
    report.evidence.files = copiedEvidenceFiles.filter((name) => focalEvidencePrefix.test(name) || name.endsWith("evidence.json"));
    report.status = "passed";
    report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
    report.fixture = {
      threadId: state.thread,
      conversationId: `sb:${state.thread}`,
      seedMessageId: state.message,
      peerMessageId: state.peerMessage,
      peerProfileIdSha256: sha256(state.b.profileId),
      markerSha256: sha256(marker),
      peerMarkerSha256: sha256(peerMarker),
      privateMarkerSha256: profilePrivateChatOnly ? sha256(privateMarker) : null,
      profilePrivateChatThreadId: state.profilePrivateChat ?? null,
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
      feedOfficialComments: state.feedOfficialComments ? {
        markerSha256: sha256(state.feedOfficialComments.marker),
        feedPostId: state.feedOfficialComments.feed?.postId ?? null,
        feedUiCommentId: state.feedOfficialComments.feed?.uiCommentId ?? null,
        feedPersistedUiComment: state.feedOfficialComments.feed?.persistedUiComment ?? null,
        officialPostId: state.feedOfficialComments.official?.postId ?? null,
        officialUiCommentId: state.feedOfficialComments.official?.uiCommentId ?? null,
        officialPersistedUiComment: state.feedOfficialComments.official?.persistedUiComment ?? null,
      } : null,
      profileEntry: state.profileEntry ? {
        officialPostId: state.profileEntry.official.id,
        officialMarkerSha256: sha256(state.profileEntry.official.marker),
      } : null,
      profileRolesSafety: state.profileRolesSafety ? {
        targetProfileIdSha256: sha256(state.profileRolesSafety.targetProfileId),
        actorWasAdmin: state.profileRolesSafety.actorRoles?.isAdmin ?? null,
        targetWasAdmin: state.profileRolesSafety.targetRoles?.isAdmin ?? null,
        targetWasOfficial: state.profileRolesSafety.targetRoles?.isOfficial ?? null,
        hadGlobalBlock: state.profileRolesSafety.hadGlobalBlock ?? null,
        hadProfileReport: Boolean(state.profileRolesSafety.previousReport),
      } : null,
    };
    throw new Error((feedOfficialCommentsOnly || feedOfficialCommentsErrorOnly || feedOfficialCommentsSelectorStatesOnly) ? "feed_official_comments_only_completed" : profileRolesSafetyOnly ? "profile_roles_safety_only_completed" : profilePrivateChatOnly ? "profile_private_chat_only_completed" : profileEntryOnly ? "profile_entry_only_completed" : profileContentOnly ? "profile_content_only_completed" : profileListsOnly ? "profile_lists_only_completed" : profileFollowOnly ? "profile_follow_only_completed" : "profile_only_completed");
  }

  assertInstrumentationPassed("send-reply", await runInstrumentationStage("send-reply"));
  state.favoriteMessage = state.message;
  const favoriteRowsAfterSeedToggle = await favorites(config, state.a);
  if (!favoriteRowsAfterSeedToggle.some((message) => favoriteMessageId(message) === Number(state.favoriteMessage))) {
    throw new Error("chat_contract_invalid:favorite_not_persisted");
  }
  report.steps.push("favorite_toggled_and_verified_by_rpc");
  const composerMessage = await pollMessage(config, state.a, state.thread, (message) => messageText(message) === composerMarker);
  state.editableMessage = messageId(composerMessage);
  state.uiMessages.push(messageId(composerMessage));
  report.steps.push("composer_text_sent_by_shared_ui_and_verified_by_rpc");
  const replyMessage = await pollMessage(config, state.a, state.thread, (message) =>
    messageText(message) === replyMarker && messageReplyToId(message) === Number(state.message),
  );
  state.uiMessages.push(messageId(replyMessage));
  report.steps.push("composer_reply_sent_by_shared_ui_and_verified_by_rpc");

  assertInstrumentationPassed("edit-favorite", await runInstrumentationStage("edit-favorite"));
  const editedMessage = await pollMessage(config, state.a, state.thread, (message) =>
    messageText(message) === editMarker &&
      Number(message?.id ?? message?.message_id) === Number(state.editableMessage),
  );
  state.editedMessage = messageId(editedMessage);
  report.steps.push("composer_edit_sent_by_shared_ui_and_verified_by_rpc");

  assertInstrumentationPassed("forward", await runInstrumentationStage("forward"));
  const forwardDestination = await pollForwardDestinationThread(config, state.a, state.forwardProfile.id);
  state.forwardThread = forwardDestination.threadId;
  const forwardedMessage = await pollMessage(config, state.a, state.forwardThread, (message) =>
    messageText(message) === editMarker &&
      Number(message?.forwarded_from_message_id) === Number(state.editedMessage),
  );
  state.forwardedMessage = messageId(forwardedMessage);
  report.steps.push("message_forwarded_by_shared_ui_and_verified_by_rpc");

  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  for (const file of evidenceFiles) {
    await adbRunAsCat(`${deviceEvidencePath}/${file}`, join(evidenceDir, file));
  }
  report.status = "passed";
  report.evidence.directory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
  report.fixture = {
    threadId: state.thread,
    conversationId: `sb:${state.thread}`,
    seedMessageId: state.message,
    peerMessageId: state.peerMessage,
    peerProfileIdSha256: sha256(state.b.profileId),
    composerMessageId: messageId(composerMessage),
    replyMessageId: messageId(replyMessage),
    editedMessageId: state.editedMessage,
    forwardThreadId: state.forwardThread,
    forwardedMessageId: state.forwardedMessage,
    forwardProfileIdSha256: sha256(state.forwardProfile.id),
    markerSha256: sha256(marker),
    composerMarkerSha256: sha256(composerMarker),
    replyMarkerSha256: sha256(replyMarker),
    editMarkerSha256: sha256(editMarker),
  };
} catch (error) {
  if (
    error instanceof EvidenceCompleted ||
    error?.message === "menu_surface_only_completed" ||
    error?.message === "attachments_audio_only_completed" ||
    error?.message === "attachment_picker_only_completed" ||
    error?.message === "group_sos_only_completed" ||
    error?.message === "group_admin_only_completed" ||
    error?.message === "group_moderation_only_completed" ||
    error?.message === "community_chat_only_completed" ||
    error?.message === "composer_emoji_only_completed" ||
    error?.message === "profile_only_completed" ||
    error?.message === "profile_follow_only_completed" ||
    error?.message === "profile_lists_only_completed" ||
    error?.message === "profile_entry_only_completed" ||
    error?.message === "profile_content_only_completed" ||
    error?.message === "feed_official_comments_only_completed" ||
    error?.message === "profile_private_chat_only_completed" ||
    error?.message === "profile_roles_safety_only_completed"
  ) {
    // Focal modes finished successfully; cleanup and report writing still happen in finally.
  } else {
    report.error = safeFailure(error);
    report.diagnostics = {
      ...(report.diagnostics ?? {}),
      failure: sanitizedDiagnosticError(error),
    };
    if (lastThreadSnapshot) {
      report.diagnostics = {
        ...(report.diagnostics ?? {}),
        lastThreadSnapshot,
      };
    }
    try {
      const copied = await collectAvailableDeviceEvidence(evidenceDir);
      if (copied.length) {
        report.evidence.failureDirectory = fileURLToPath(new URL(`../${evidenceDir.replaceAll("\\", "/")}`, import.meta.url));
        report.evidence.failureFiles = copied;
      }
    } catch {}
  }
} finally {
  const cleanup = { state: "completed", actions: [] };
  let cleanupFailed = false;
  try { await run(adbCommand, ["shell", "rm", "-f", deviceTempCredentialsPath]); } catch {}
  try { await run(adbCommand, ["shell", "run-as", "com.quata", "rm", "-f", `files/${deviceCredentialsPath.replace("app-internal:", "")}`]); } catch {}
  try { await run(adbCommand, ["shell", "run-as", "com.quata", "rm", "-rf", deviceEvidencePath]); } catch {}
  try { await run(adbCommand, ["uninstall", "com.quata.test"]); } catch {}
  await rm(localCredentials, { force: true }).catch(() => {});
  try {
    await profileHashWindow.restore();
    if (profileHashWindow.state === "opened") {
      cleanup.actions.push("temporary_profile_hash_window_restored");
      report.profileHashWindow = { state: "restored", count: profileHashWindow.count };
    }
  } catch (error) {
    cleanupFailed = true;
    cleanup.error = safeFailure(error);
    report.profileHashWindow = { state: "restore_failed" };
  }
  if (state.thread) {
    const config = await publicBackendConfig().catch(() => null);
    if (config) {
      if (state.profileListEdges) {
        try { cleanup.actions.push(...await restoreProfileListEdges(state.profileListEdges)); }
        catch (error) { cleanupFailed = true; cleanup.error = safeFailure(error); }
      }
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
    if (config && state.profileContent) {
      try {
        cleanup.profileContent = await cleanupProfileContentFixture(state.profileContent);
        cleanup.actions.push("profile_content_fixture_deleted");
        cleanup.actions.push("cleanup_verified_profile_content_residue_absent");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (config && state.profileEntry?.official) {
      try {
        cleanup.profileEntryOfficial = await cleanupOfficialProfileEntryPost(state.profileEntry.official);
        cleanup.actions.push("profile_entry_official_post_deleted");
        cleanup.actions.push("cleanup_verified_profile_entry_official_residue_absent");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (config && state.feedOfficialComments) {
      try {
        cleanup.feedOfficialComments = await cleanupFeedOfficialCommentsFixture(state.feedOfficialComments);
        cleanup.actions.push("feed_official_comments_fixture_deleted");
        cleanup.actions.push("cleanup_verified_feed_official_comments_residue_absent");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
      }
    }
    if (config && state.profileRolesSafety) {
      try {
        cleanup.profileRolesSafety = await cleanupProfileRolesSafetyFixture(state.profileRolesSafety);
        cleanup.actions.push("profile_roles_safety_fixture_restored");
        cleanup.actions.push("cleanup_verified_profile_roles_safety_restored");
      } catch (error) {
        cleanupFailed = true;
        cleanup.error = safeFailure(error);
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
  try { await releaseAndroidEvidenceLock(); } catch {}
  if (report.status === "passed") delete report.error;
  report.finishedAt = new Date().toISOString();
  const output = options.output;
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
  console.log(`Chat actions/notifications Android evidence written: ${output}`);
}
if (report.status !== "passed") {
  console.error(`Chat actions/notifications Android evidence failed: ${report.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Chat actions/notifications Android evidence passed.");
}
