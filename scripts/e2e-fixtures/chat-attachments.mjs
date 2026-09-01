import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { randomUUID } from "node:crypto";

export const chatAttachmentsBucket = "chat-attachments";

export function validWavFixture({ durationSeconds = 4 } = {}) {
  const sampleRate = 8_000;
  const boundedDurationSeconds = Math.max(1, Math.min(60, Number(durationSeconds) || 4));
  const samples = sampleRate * boundedDurationSeconds;
  const dataSize = samples * 2;
  const buffer = Buffer.alloc(44 + dataSize);
  buffer.write("RIFF", 0);
  buffer.writeUInt32LE(36 + dataSize, 4);
  buffer.write("WAVEfmt ", 8);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(sampleRate * 2, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write("data", 36);
  buffer.writeUInt32LE(dataSize, 40);
  for (let index = 0; index < samples; index += 1) {
    const value = Math.round(Math.sin((index / sampleRate) * Math.PI * 2 * 440) * 12_000);
    buffer.writeInt16LE(value, 44 + (index * 2));
  }
  return buffer;
}

export function validPngFixture() {
  return Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR42mP8z8Dwn4GBgYFhAQB4iQb9Z11xZQAAAABJRU5ErkJggg==",
    "base64",
  );
}

export function validPdfFixture({ platformLabel = "web", marker = "qadata-document-fixture" } = {}) {
  const safeText = (value) => String(value ?? "")
    .replace(/[^\x20-\x7e]/g, "?")
    .replace(/[()\\]/g, "\\$&")
    .slice(0, 96);
  const lines = [
    "BT",
    "/F1 18 Tf",
    "36 104 Td",
    "(QADATA document fixture) Tj",
    "0 -26 Td",
    `(${safeText(platformLabel)}) Tj`,
    "0 -26 Td",
    `(${safeText(marker)}) Tj`,
    "ET",
  ];
  const stream = `${lines.join("\n")}\n`;
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 360 180] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}endstream`,
  ];
  let body = "%PDF-1.4\n";
  const offsets = [0];
  for (let index = 0; index < objects.length; index += 1) {
    offsets.push(Buffer.byteLength(body));
    body += `${index + 1} 0 obj\n${objects[index]}\nendobj\n`;
  }
  const xrefOffset = Buffer.byteLength(body);
  body += `xref\n0 ${objects.length + 1}\n`;
  body += "0000000000 65535 f \n";
  for (const offset of offsets.slice(1)) {
    body += `${String(offset).padStart(10, "0")} 00000 n \n`;
  }
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
  return Buffer.from(body, "ascii");
}

export function validMp4Fixture() {
  return readFileSync(resolve("play-store/05-assets/quata-demo-video.mp4"));
}

export function longMp4FixturePath() {
  return resolve("play-store/05-assets/source-media/big-buck-bunny-320x180.mp4");
}

export function longMp4Fixture() {
  return readFileSync(longMp4FixturePath());
}

export function pathSegment(path) {
  return String(path).split("/").map(encodeURIComponent).join("/");
}

export function attachmentStorageFixtures(state) {
  const registry = state?.cleanupRegistry;
  if (registry?.storageObjects instanceof Map) {
    return [...registry.storageObjects.values()].map((entry) => ({
      name: entry.name,
      storagePath: entry.storagePath,
      bucket: entry.bucket,
    }));
  }
  const seen = new Set();
  const fixtures = [
    ...(state?.attachmentStoragePaths ?? []),
    state?.attachmentsAudio?.document,
    state?.attachmentsAudio?.audio,
  ].filter((fixture) => fixture?.storagePath);
  return fixtures.filter((fixture) => {
    if (seen.has(fixture.storagePath)) return false;
    seen.add(fixture.storagePath);
    return true;
  });
}

export function createCleanupRegistry() {
  const storageObjects = new Map();
  const errors = [];
  return {
    storageObjects,
    errors,
    trackStorageObject({ bucket = chatAttachmentsBucket, storagePath, name }) {
      if (!storagePath) throw new Error("cleanup_registry_missing_storage_path");
      const key = `${bucket}/${storagePath}`;
      if (!storageObjects.has(key)) storageObjects.set(key, { bucket, storagePath, name: name ?? storagePath });
      return storageObjects.get(key);
    },
    async cleanupStorageObjects({ config, session, storageRequest, verifyStorageObjectAbsent, actions = [] }) {
      for (const fixture of storageObjects.values()) {
        try {
          await storageRequest(config, session, `/storage/v1/object/${fixture.bucket}`, {
            method: "DELETE",
            headers: { "content-type": "application/json" },
            body: JSON.stringify({ prefixes: [fixture.storagePath] }),
          }, "chat_attachments_audio_storage_delete_failed");
          await verifyStorageObjectAbsent(fixture.bucket, fixture.storagePath);
          actions.push(`${fixture.name}_storage_delete_verified_absent`);
        } catch (error) {
          errors.push({ kind: "storage_object", storagePath: fixture.storagePath, error: safeFailure(error) });
        }
      }
      if (errors.length) throw new Error(`cleanup_registry_failed:${errors.map((entry) => entry.storagePath).join(",")}`);
      return actions;
    },
    summary() {
      return {
        trackedStorageObjects: storageObjects.size,
        errors: errors.slice(),
      };
    },
  };
}

export async function seedChatAttachmentFixture({
  config,
  session,
  thread,
  runId,
  kind,
  platformLabel,
  rpc,
  storageRequest,
  pollMessage,
  messageText,
  attachmentId,
  messageId,
  cleanup,
  nameSuffix = "",
  audioDurationSeconds,
}) {
  const media = chatAttachmentFixtureMedia(kind);
  const { extension, mimeType } = media;
  const marker = `chat-${kind}-attachment-${platformLabel}-${runId}`;
  const safeNameSuffix = String(nameSuffix).replace(/[^a-z0-9_-]/gi, "").slice(0, 24);
  const name = `qadata-${kind}-${runId.slice(0, 8)}${safeNameSuffix}.${extension}`;
  const content = media.content({ platformLabel, marker, audioDurationSeconds });
  const storagePath = `${session.profileId}/evidence/${runId}/${name}`;
  cleanup?.trackStorageObject({ bucket: chatAttachmentsBucket, storagePath, name });
  await storageRequest(config, session, `/storage/v1/object/${chatAttachmentsBucket}/${pathSegment(storagePath)}`, {
    method: "POST",
    headers: { "content-type": mimeType, "x-upsert": "false" },
    body: content,
  }, `chat_${kind}_storage_upload_failed`);
  const publicUrl = `${config.baseUrl}/storage/v1/object/public/${chatAttachmentsBucket}/${pathSegment(storagePath)}`;
  const id = attachmentId(await rpc(config, session, "quata_chat_register_attachment", {
    p_actor_profile_id: session.profileId,
    p_thread_id: thread,
    p_file_url: publicUrl,
    p_storage_bucket: chatAttachmentsBucket,
    p_storage_path: storagePath,
    p_mime_type: mimeType,
    p_name: name,
    p_size_bytes: content.length,
    p_ext: extension,
    p_thumb: null,
  }));
  const msg = messageId(await rpc(config, session, "quata_chat_send_message", {
    p_actor_profile_id: session.profileId,
    p_thread_id: thread,
    p_message: marker,
    p_file_ids: [id],
    p_reply_to_message_id: null,
    p_client_message_id: `chat-${kind}-attachment-${platformLabel}-${runId}`,
  }));
  await pollMessage(config, session, thread, (message) => Number(message?.id) === msg && messageText(message) === marker);
  return { id, messageId: msg, marker, markerProbe: marker.slice(0, 28), name, mimeType, storagePath };
}

function chatAttachmentFixtureMedia(kind) {
  if (kind === "audio") {
    return {
      extension: "wav",
      mimeType: "audio/wav",
      content: ({ audioDurationSeconds }) => validWavFixture({ durationSeconds: audioDurationSeconds }),
    };
  }
  if (kind === "image") {
    return {
      extension: "png",
      mimeType: "image/png",
      content: () => validPngFixture(),
    };
  }
  if (kind === "video") {
    return {
      extension: "mp4",
      mimeType: "video/mp4",
      content: () => validMp4Fixture(),
    };
  }
  return {
    extension: "pdf",
    mimeType: "application/pdf",
    content: ({ platformLabel, marker }) => validPdfFixture({ platformLabel, marker }),
  };
}

export async function seedProfileContentFixture({
  fixture,
  config = fixture?.config,
  withDatabase,
  rpc,
  storageRequest,
  attachmentId,
  messageId,
  cleanup,
}) {
  if (!fixture?.marker?.startsWith("qadata-profile-content-")) throw new Error("profile_content_fixture_marker_invalid");
  if (!config || !fixture.actorSession || !fixture.targetSession || !Number.isSafeInteger(Number(fixture.threadId))) {
    throw new Error("profile_content_fixture_invalid_context");
  }
  if (fixture.prepared) return fixture;
  const marker = fixture.marker;
  const content = Buffer.from(`profile content attachment ${marker}\n`, "utf8");
  fixture.storagePath = `${fixture.actorSession.profileId}/profile-content/${marker}.txt`;
  cleanup?.trackStorageObject({
    bucket: chatAttachmentsBucket,
    storagePath: fixture.storagePath,
    name: "profile_content_attachment",
  });
  await storageRequest(config, fixture.actorSession, `/storage/v1/object/${chatAttachmentsBucket}/${pathSegment(fixture.storagePath)}`, {
    method: "POST",
    headers: { "content-type": "text/plain; charset=utf-8", "x-upsert": "false" },
    body: content,
  }, "profile_content_storage_upload_failed");
  const publicUrl = `${config.baseUrl}/storage/v1/object/public/${chatAttachmentsBucket}/${pathSegment(fixture.storagePath)}`;
  const imageContent = validPngFixture();
  fixture.postImageStoragePath = `${fixture.actorSession.profileId}/profile-content/${marker}.png`;
  cleanup?.trackStorageObject({
    bucket: chatAttachmentsBucket,
    storagePath: fixture.postImageStoragePath,
    name: "profile_content_post_image",
  });
  await storageRequest(config, fixture.actorSession, `/storage/v1/object/${chatAttachmentsBucket}/${pathSegment(fixture.postImageStoragePath)}`, {
    method: "POST",
    headers: { "content-type": "image/png", "x-upsert": "false" },
    body: imageContent,
  }, "profile_content_post_image_storage_upload_failed");
  fixture.postImageUrl = `${config.baseUrl}/storage/v1/object/public/${chatAttachmentsBucket}/${pathSegment(fixture.postImageStoragePath)}`;
  fixture.attachmentId = attachmentId(await rpc(config, fixture.actorSession, "quata_chat_register_attachment", {
    p_actor_profile_id: fixture.actorSession.profileId,
    p_thread_id: fixture.threadId,
    p_file_url: publicUrl,
    p_storage_bucket: chatAttachmentsBucket,
    p_storage_path: fixture.storagePath,
    p_mime_type: "text/plain",
    p_name: "qadata-profile-content.txt",
    p_size_bytes: content.length,
    p_ext: "txt",
    p_thumb: null,
  }));
  fixture.attachmentMessageId = messageId(await rpc(config, fixture.actorSession, "quata_chat_send_message", {
    p_actor_profile_id: fixture.actorSession.profileId,
    p_thread_id: fixture.threadId,
    p_message: "",
    p_file_ids: [fixture.attachmentId],
    p_reply_to_message_id: null,
    p_client_message_id: `profile-content-attachment-${marker}`,
  }));
  await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const post = await client.query(
        `with selected_wall as (
           select wall_id as id
           from public.community_members
           where profile_id = $1
           order by created_at desc
           limit 1
         ), fallback_wall as (
           select id
           from public.community_walls_stats
           where is_active = true
           order by sort_order asc
           limit 1
         ), wall as (
           select id from selected_wall
           union all
           select id from fallback_wall
           limit 1
         )
         insert into public.community_posts(id, wall_id, profile_id, body, image_url)
         select gen_random_uuid(), wall.id, $1, $2, $3
         from wall
         returning id`,
        [fixture.targetSession.profileId, `${marker} post body`, fixture.postImageUrl],
      );
      fixture.postId = post.rows[0]?.id;
      if (!uuid.test(fixture.postId ?? "")) throw new Error("profile_content_fixture_wall_unavailable");
      const comment = await client.query(
        `insert into public.community_comments(id, post_id, profile_id, body)
         values (gen_random_uuid(), $1, $2, $3)
         returning id`,
        [fixture.postId, fixture.actorSession.profileId, `${marker} seed comment`],
      );
      fixture.seedCommentId = comment.rows[0]?.id;
      await client.query(
        `insert into public.community_post_likes(post_id, profile_id)
         values ($1, $2)
         on conflict do nothing`,
        [fixture.postId, fixture.actorSession.profileId],
      );
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  fixture.prepared = true;
  return fixture;
}

export async function cleanupProfileContentFixture({
  fixture,
  withDatabase,
}) {
  if (!fixture) return null;
  return await withDatabase(async (client) => {
    await client.query("begin");
    try {
      if (fixture.postId) {
        await client.query(
          `delete from public.community_post_likes
           where post_id in (
             select id from public.community_posts
             where id = $1 and body like $2 and profile_id = $3
           )`,
          [fixture.postId, `%${fixture.marker}%`, fixture.targetSession?.profileId ?? nilUuid],
        );
        await client.query(
          `delete from public.community_comments
           where post_id in (
             select id from public.community_posts
             where id = $1 and body like $2 and profile_id = $3
           )
              or body like $2
              or id = $4`,
          [fixture.postId, `%${fixture.marker}%`, fixture.targetSession?.profileId ?? nilUuid, fixture.seedCommentId ?? nilUuid],
        );
        await client.query(
          `delete from public.community_posts
           where id = $1 and body like $2 and profile_id = $3`,
          [fixture.postId, `%${fixture.marker}%`, fixture.targetSession?.profileId ?? nilUuid],
        );
      }
      if (fixture.attachmentId && fixture.storagePath) {
        await client.query(
          `delete from public.chat_attachments
           where id = $1 and storage_path = $2 and uploaded_by_profile_id = $3`,
          [fixture.attachmentId, fixture.storagePath, fixture.actorSession?.profileId ?? nilUuid],
        );
      }
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
    const residue = await client.query(
      `select
        (select count(*)::int from public.community_posts where (id = $1 or body like $2) and profile_id = $5) as community_posts,
        (select count(*)::int from public.community_comments where post_id = $1 or body like $2) as community_comments,
        (select count(*)::int from public.community_post_likes where post_id = $1) as community_post_likes,
        (select count(*)::int from public.chat_attachments where id = $3 and storage_path = $4) as chat_attachments`,
      [
        fixture.postId ?? nilUuid,
        `%${fixture.marker}%`,
        fixture.attachmentId ?? -1,
        fixture.storagePath ?? "",
        fixture.targetSession?.profileId ?? nilUuid,
      ],
    );
    const counts = residue.rows[0] ?? {};
    if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:profile_content");
    return {
      postId: fixture.postId ?? null,
      seedCommentId: fixture.seedCommentId ?? null,
      attachmentId: fixture.attachmentId ?? null,
      residueCounts: counts,
      status: "cleanup_verified_profile_content_residue_absent",
    };
  });
}

export async function pollProfileContentComment({
  fixture,
  marker,
  withDatabase,
  delay,
  timeout = 45_000,
}) {
  const deadline = Date.now() + timeout;
  const markerProbe = marker.replace(/^😀\s*/, "");
  while (Date.now() < deadline) {
    const result = await withDatabase(async (client) => await client.query(
      `select id
         from public.community_comments
        where post_id = $1 and profile_id = $2 and body like $3
        order by created_at desc
        limit 1`,
      [fixture.postId, fixture.actorSession.profileId, `%${markerProbe}%`],
    ));
    const id = result.rows[0]?.id;
    if (uuid.test(id ?? "")) return id;
    await delay(1_000);
  }
  throw new Error("profile_content_comment_not_persisted");
}

export async function pollProfileContentReplyComment({
  fixture,
  marker,
  replyToCommentId,
  withDatabase,
  delay,
  timeout = 45_000,
}) {
  const deadline = Date.now() + timeout;
  const replyPrefix = `[reply:${replyToCommentId}:`;
  const markerProbe = marker.replace(/^😀\s*/, "");
  while (Date.now() < deadline) {
    const result = await withDatabase(async (client) => await client.query(
      `select id, body
         from public.community_comments
        where post_id = $1
          and profile_id = $2
          and body like $3
          and body like $4
        order by created_at desc
        limit 1`,
      [fixture.postId, fixture.actorSession.profileId, `${replyPrefix}%`, `%${markerProbe}%`],
    ));
    const row = result.rows[0];
    if (uuid.test(row?.id ?? "")) {
      fixture.profileReplyBody = row.body ?? null;
      return row.id;
    }
    await delay(1_000);
  }
  throw new Error("profile_content_reply_comment_not_persisted");
}

export async function prepareProfileRolesSafetyFixture({
  actorSession,
  targetSession,
  withDatabase,
}) {
  if (!uuid.test(actorSession?.profileId ?? "") || !uuid.test(targetSession?.profileId ?? "")) {
    throw new Error("profile_roles_safety_fixture_invalid_profiles");
  }
  if (actorSession.profileId === targetSession.profileId) throw new Error("profile_roles_safety_fixture_self_target");
  const fixture = {
    actorProfileId: actorSession.profileId,
    targetProfileId: targetSession.profileId,
    prepared: false,
  };
  const snapshot = await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const profiles = await client.query(
        `select id, is_admin, is_official
           from public.community_profiles
          where id = any($1::uuid[])
          for update`,
        [[actorSession.profileId, targetSession.profileId]],
      );
      if (profiles.rowCount !== 2) throw new Error("profile_roles_safety_fixture_profiles_not_found");
      const actor = profiles.rows.find((row) => row.id === actorSession.profileId);
      const target = profiles.rows.find((row) => row.id === targetSession.profileId);
      const block = await client.query(
        `select thread_id, blocker_profile_id, blocked_profile_id
           from public.chat_profile_blocks
          where thread_id is null
            and blocker_profile_id = $1::uuid
            and blocked_profile_id = $2::uuid
          for update`,
        [actorSession.profileId, targetSession.profileId],
      );
      const report = await client.query(
        `select id, reporter_profile_id, target_type, target_id, reported_profile_id,
                reason, details, status, created_at, reviewed_at, reviewed_by
           from public.ugc_reports
          where reporter_profile_id = $1::uuid
            and target_type = 'profile'
            and target_id = $2
          for update`,
        [actorSession.profileId, targetSession.profileId],
      );
      await client.query(
        "update public.community_profiles set is_admin = true where id = $1::uuid",
        [actorSession.profileId],
      );
      await client.query(
        "update public.community_profiles set is_admin = false, is_official = false where id = $1::uuid",
        [targetSession.profileId],
      );
      await client.query(
        `delete from public.chat_profile_blocks
          where thread_id is null
            and blocker_profile_id = $1::uuid
            and blocked_profile_id = $2::uuid`,
        [actorSession.profileId, targetSession.profileId],
      );
      await client.query("commit");
      return {
        actorRoles: { isAdmin: actor.is_admin === true, isOfficial: actor.is_official === true },
        targetRoles: { isAdmin: target.is_admin === true, isOfficial: target.is_official === true },
        hadGlobalBlock: block.rowCount > 0,
        previousReport: report.rows[0] ?? null,
      };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  Object.assign(fixture, snapshot, { prepared: true });
  return fixture;
}

export async function pollProfileRoles({
  fixture,
  withDatabase,
  expected,
  delay,
  timeout = 45_000,
}) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const result = await withDatabase(async (client) => await client.query(
      "select is_admin, is_official from public.community_profiles where id = $1::uuid",
      [fixture.targetProfileId],
    ));
    const row = result.rows[0];
    if (row && row.is_admin === expected.isAdmin && row.is_official === expected.isOfficial) {
      return { isAdmin: row.is_admin === true, isOfficial: row.is_official === true };
    }
    await delay(1_000);
  }
  throw new Error("profile_roles_not_persisted");
}

export async function pollProfileGlobalBlock({
  fixture,
  withDatabase,
  expectedBlocked,
  delay,
  timeout = 45_000,
}) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const result = await withDatabase(async (client) => await client.query(
      `select count(*)::int as count
         from public.chat_profile_blocks
        where thread_id is null
          and blocker_profile_id = $1::uuid
          and blocked_profile_id = $2::uuid`,
      [fixture.actorProfileId, fixture.targetProfileId],
    ));
    const blocked = Number(result.rows[0]?.count ?? 0) > 0;
    if (blocked === expectedBlocked) return { blocked };
    await delay(1_000);
  }
  throw new Error(`profile_block_state_not_persisted:${expectedBlocked}`);
}

export async function pollProfileReport({
  fixture,
  withDatabase,
  delay,
  timeout = 45_000,
}) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const result = await withDatabase(async (client) => await client.query(
      `select id, status, reason
         from public.ugc_reports
        where reporter_profile_id = $1::uuid
          and target_type = 'profile'
          and target_id = $2
        order by created_at desc
        limit 1`,
      [fixture.actorProfileId, fixture.targetProfileId],
    ));
    const row = result.rows[0];
    if (row && row.status === "pending" && row.reason === "other") return row;
    await delay(1_000);
  }
  throw new Error("profile_report_not_persisted");
}

export async function seedFeedOfficialCommentsFixture({
  fixture,
  withDatabase,
}) {
  if (!fixture?.marker?.startsWith("qadata-feed-official-comments-")) throw new Error("feed_official_comments_fixture_marker_invalid");
  if (!uuid.test(fixture.actorSession?.profileId ?? "")) throw new Error("feed_official_comments_fixture_invalid_actor");
  if (!uuid.test(fixture.targetSession?.profileId ?? "")) throw new Error("feed_official_comments_fixture_invalid_target");
  if (fixture.prepared) return fixture;
  const marker = fixture.marker;
  fixture.feed = {
    postId: randomUUID(),
    seedCommentId: randomUUID(),
    uiComment: `😀 ${marker} feed ui comment`,
    postBody: `${marker} feed post body`,
  };
  fixture.official = {
    postId: randomUUID(),
    translationGroupId: randomUUID(),
    seedCommentId: randomUUID(),
    uiComment: `😀 ${marker} official ui comment`,
    title: `QADATA official comments ${marker.slice(-12)}`,
    summary: `Fixture reversible de comentarios oficiales ${marker}`,
    article: `Detalle ampliado reversible ${marker}`,
    linkUrl: `https://example.com/quata-post-detail/${marker.slice(-18)}`,
  };
  await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const feedPost = await client.query(
        `with selected_wall as (
           select wall_id as id
           from public.community_members
           where profile_id = $1::uuid
           order by created_at desc
           limit 1
         ), fallback_wall as (
           select id
           from public.community_walls_stats
           where is_active = true
           order by sort_order asc
           limit 1
         ), wall as (
           select id from selected_wall
           union all
           select id from fallback_wall
           limit 1
         )
         insert into public.community_posts(id, wall_id, profile_id, body)
         select $2::uuid, wall.id, $1::uuid, $3
         from wall
         returning id`,
        [fixture.targetSession.profileId, fixture.feed.postId, fixture.feed.postBody],
      );
      if (feedPost.rowCount !== 1) throw new Error("feed_official_comments_fixture_wall_unavailable");
      await client.query(
        `insert into public.community_comments(id, post_id, profile_id, body)
         values ($1::uuid, $2::uuid, $3::uuid, $4)`,
        [fixture.feed.seedCommentId, fixture.feed.postId, fixture.actorSession.profileId, `${marker} feed seed comment`],
      );
      await client.query(
        `insert into public.official_posts(
           id, profile_id, title, summary, post_type, content_html,
           read_more_label, language, translation_group_id, media_url,
           media_type, link_url, is_live, is_published, published_at
         ) values (
           $1::uuid, $2::uuid, $3, $4, 'news', $5,
           'Leer mas', 'es', $6::uuid, null,
           null, $7, false, true, now()
         )`,
        [
          fixture.official.postId,
          fixture.targetSession.profileId,
          fixture.official.title,
          fixture.official.summary,
          `<p>${fixture.official.summary}</p><p>${fixture.official.article}</p>`,
          fixture.official.translationGroupId,
          fixture.official.linkUrl,
        ],
      );
      await client.query(
        `insert into public.official_post_comments(id, official_post_id, profile_id, body)
         values ($1::uuid, $2::uuid, $3::uuid, $4)`,
        [fixture.official.seedCommentId, fixture.official.postId, fixture.actorSession.profileId, `${marker} official seed comment`],
      );
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
  fixture.prepared = true;
  return fixture;
}

export async function cleanupFeedOfficialCommentsFixture({
  fixture,
  withDatabase,
}) {
  if (!fixture?.marker) return null;
  return await withDatabase(async (client) => {
    await client.query("begin");
    try {
      await client.query(
        `delete from public.community_post_likes
         where post_id = $1::uuid`,
        [fixture.feed?.postId ?? nilUuid],
      );
      await client.query(
        `delete from public.community_comments
         where post_id = $1::uuid or body like $2`,
        [fixture.feed?.postId ?? nilUuid, `%${fixture.marker}%`],
      );
      await client.query(
        `delete from public.community_posts
         where (id = $1::uuid or body like $2) and profile_id = $3::uuid`,
        [fixture.feed?.postId ?? nilUuid, `%${fixture.marker}%`, fixture.targetSession?.profileId ?? nilUuid],
      );
      await client.query(
        `delete from public.official_post_likes
         where official_post_id = $1::uuid`,
        [fixture.official?.postId ?? nilUuid],
      );
      await client.query(
        `delete from public.official_post_comments
         where official_post_id = $1::uuid or body like $2`,
        [fixture.official?.postId ?? nilUuid, `%${fixture.marker}%`],
      );
      await client.query(
        `delete from public.official_posts
         where (id = $1::uuid or translation_group_id = $2::uuid or title like $3)
           and profile_id = $4::uuid`,
        [
          fixture.official?.postId ?? nilUuid,
          fixture.official?.translationGroupId ?? nilUuid,
          `%${fixture.marker}%`,
          fixture.targetSession?.profileId ?? nilUuid,
        ],
      );
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
    const residue = await client.query(
      `select
        (select count(*)::int from public.community_posts where id = $1::uuid or body like $3) as community_posts,
        (select count(*)::int from public.community_comments where post_id = $1::uuid or body like $3) as community_comments,
        (select count(*)::int from public.community_post_likes where post_id = $1::uuid) as community_post_likes,
        (select count(*)::int from public.official_posts where id = $2::uuid or translation_group_id = $4::uuid or title like $3) as official_posts,
        (select count(*)::int from public.official_post_comments where official_post_id = $2::uuid or body like $3) as official_post_comments,
        (select count(*)::int from public.official_post_likes where official_post_id = $2::uuid) as official_post_likes`,
      [
        fixture.feed?.postId ?? nilUuid,
        fixture.official?.postId ?? nilUuid,
        `%${fixture.marker}%`,
        fixture.official?.translationGroupId ?? nilUuid,
      ],
    );
    const counts = residue.rows[0] ?? {};
    if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:feed_official_comments");
    return {
      status: "cleanup_verified_feed_official_comments_residue_absent",
      feedPostId: fixture.feed?.postId ?? null,
      officialPostId: fixture.official?.postId ?? null,
      residueCounts: counts,
    };
  });
}

export async function pollFeedOfficialComment({
  fixture,
  surface,
  marker,
  withDatabase,
  delay,
  timeout = 45_000,
}) {
  const deadline = Date.now() + timeout;
  const markerProbe = marker.replace(/^😀\s*/, "");
  while (Date.now() < deadline) {
    const result = await withDatabase(async (client) => {
      if (surface === "feed") {
        return await client.query(
          `select id, body
             from public.community_comments
            where post_id = $1::uuid and profile_id = $2::uuid and body like $3
            order by created_at desc
            limit 1`,
          [fixture.feed?.postId, fixture.actorSession.profileId, `%${markerProbe}%`],
        );
      }
      if (surface === "official") {
        return await client.query(
          `select id, body
             from public.official_post_comments
            where official_post_id = $1::uuid and profile_id = $2::uuid and body like $3
            order by created_at desc
            limit 1`,
          [fixture.official?.postId, fixture.actorSession.profileId, `%${markerProbe}%`],
        );
      }
      throw new Error(`feed_official_comments_unknown_surface:${surface}`);
    });
    const id = result.rows[0]?.id;
    if (uuid.test(id ?? "")) {
      fixture[surface].persistedUiComment = result.rows[0]?.body ?? null;
      return id;
    }
    await delay(1_000);
  }
  throw new Error(`feed_official_${surface}_comment_not_persisted`);
}

export async function assertFeedOfficialCommentAbsent({
  fixture,
  surface,
  marker,
  withDatabase,
}) {
  const markerProbe = marker.replace(/^😀\s*/, "");
  const result = await withDatabase(async (client) => {
    if (surface === "feed") {
      return await client.query(
        `select count(*)::int as count
           from public.community_comments
          where post_id = $1::uuid and profile_id = $2::uuid and body like $3`,
        [fixture.feed?.postId, fixture.actorSession.profileId, `%${markerProbe}%`],
      );
    }
    if (surface === "official") {
      return await client.query(
        `select count(*)::int as count
           from public.official_post_comments
          where official_post_id = $1::uuid and profile_id = $2::uuid and body like $3`,
        [fixture.official?.postId, fixture.actorSession.profileId, `%${markerProbe}%`],
      );
    }
    throw new Error(`feed_official_comments_unknown_surface:${surface}`);
  });
  const count = Number(result.rows[0]?.count ?? 0);
  if (count !== 0) throw new Error(`feed_official_${surface}_comment_rollback_residue:${count}`);
  return { surface, markerProbe, count };
}

export async function pollFeedOfficialReplyComment({
  fixture,
  surface,
  marker,
  replyToCommentId,
  withDatabase,
  delay,
  timeout = 45_000,
}) {
  const deadline = Date.now() + timeout;
  const replyPrefix = `[reply:${replyToCommentId}:`;
  const markerProbe = marker.replace(/^😀\s*/, "");
  let lastMarkerOnlyRow = null;
  while (Date.now() < deadline) {
    const result = await withDatabase(async (client) => {
      if (surface === "feed") {
        return await client.query(
          `select id, body
             from public.community_comments
            where post_id = $1::uuid
              and profile_id = $2::uuid
              and body like $3
              and body like $4
            order by created_at desc
            limit 1`,
          [fixture.feed?.postId, fixture.actorSession.profileId, `${replyPrefix}%`, `%${markerProbe}%`],
        );
      }
      if (surface === "official") {
        return await client.query(
          `select id, body
             from public.official_post_comments
            where official_post_id = $1::uuid
              and profile_id = $2::uuid
              and body like $3
              and body like $4
            order by created_at desc
            limit 1`,
          [fixture.official?.postId, fixture.actorSession.profileId, `${replyPrefix}%`, `%${markerProbe}%`],
        );
      }
      throw new Error(`feed_official_comments_unknown_surface:${surface}`);
    });
    const row = result.rows[0];
    if (uuid.test(row?.id ?? "")) {
      fixture[surface].persistedReplyComment = row.body ?? null;
      return row.id;
    }
    const markerOnly = await withDatabase(async (client) => {
      if (surface === "feed") {
        return await client.query(
          `select id, body
             from public.community_comments
            where post_id = $1::uuid
              and profile_id = $2::uuid
              and body like $3
            order by created_at desc
            limit 1`,
          [fixture.feed?.postId, fixture.actorSession.profileId, `%${markerProbe}%`],
        );
      }
      if (surface === "official") {
        return await client.query(
          `select id, body
             from public.official_post_comments
            where official_post_id = $1::uuid
              and profile_id = $2::uuid
              and body like $3
            order by created_at desc
            limit 1`,
          [fixture.official?.postId, fixture.actorSession.profileId, `%${markerProbe}%`],
        );
      }
      throw new Error(`feed_official_comments_unknown_surface:${surface}`);
    });
    if (uuid.test(markerOnly.rows[0]?.id ?? "")) {
      lastMarkerOnlyRow = {
        id: markerOnly.rows[0].id,
        hasReplyPrefix: String(markerOnly.rows[0].body ?? "").startsWith(replyPrefix),
        bodyPrefix: String(markerOnly.rows[0].body ?? "").slice(0, 80),
      };
    }
    await delay(1_000);
  }
  if (lastMarkerOnlyRow) fixture[surface].replyPollDiagnostic = lastMarkerOnlyRow;
  throw new Error(`feed_official_${surface}_reply_comment_not_persisted`);
}

export function createPostPublishFixture({
  actorSession,
  platformLabel,
  runId = randomUUID(),
  destination = null,
  locationLabel = null,
}) {
  if (!uuid.test(actorSession?.profileId ?? "")) throw new Error("post_publish_fixture_invalid_actor");
  const cleanPlatform = String(platformLabel ?? "")
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, "")
    .slice(0, 24);
  if (!cleanPlatform) throw new Error("post_publish_fixture_invalid_platform");
  const cleanRunId = String(runId).replace(/[^a-z0-9-]/gi, "").slice(0, 36);
  const marker = `qadata-post-publish-${cleanPlatform}-${cleanRunId}`;
  return {
    marker,
    markerProbe: marker.slice(-24),
    platformLabel: cleanPlatform,
    actorSession,
    runId: cleanRunId,
    destination,
    locationLabel,
    publishedPostId: null,
    publishedMediaUrls: [],
  };
}

export async function selectPostPublishDestinationFixture({
  actorSession,
  withDatabase,
}) {
  if (!uuid.test(actorSession?.profileId ?? "")) throw new Error("post_publish_destination_invalid_actor");
  return await withDatabase(async (client) => {
    const result = await client.query(
      `with memberships as (
         select wall_id
           from public.community_members
          where profile_id = $1::uuid
          order by created_at desc
       ), walls as (
         select id, name, slug, city, description
           from public.community_walls_stats
          where is_active = true
          order by sort_order asc, chat_last_at desc nulls last, created_at desc
       ), eligible as (
         select walls.*, exists(select 1 from memberships where memberships.wall_id = walls.id) as is_member
           from walls
       )
       select id, name, slug, city, description, is_member
         from eligible
        where is_member = true or not exists(select 1 from memberships)
        order by is_member desc, name asc nulls last, slug asc nulls last
        limit 3`,
      [actorSession.profileId],
    );
    const rows = result.rows.filter((row) => uuid.test(row.id ?? ""));
    const selected = rows[1] ?? rows[0];
    if (!selected) throw new Error("post_publish_destination_unavailable");
    return {
      wallId: selected.id,
      label: selected.name || selected.slug || "Feed",
      subtitle: selected.city || selected.description || null,
      optionsSeen: rows.map((row) => ({ wallId: row.id, label: row.name || row.slug || "Feed" })),
    };
  });
}

export async function pollPostPublishFixture({
  fixture,
  withDatabase,
  delay,
  timeout = 60_000,
}) {
  if (!fixture?.marker?.startsWith("qadata-post-publish-")) throw new Error("post_publish_fixture_marker_invalid");
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const result = await withDatabase(async (client) => await client.query(
      `select id, wall_id, body, image_url, video_url
         from public.community_posts
        where profile_id = $1::uuid
          and body like $2
        order by created_at desc
        limit 1`,
      [fixture.actorSession.profileId, `%${fixture.marker}%`],
    ));
    const row = result.rows[0];
    if (uuid.test(row?.id ?? "")) {
      fixture.publishedPostId = row.id;
      fixture.publishedWallId = row.wall_id ?? null;
      fixture.publishedBody = row.body ?? null;
      fixture.publishedMediaUrls = [row.image_url, row.video_url].filter(Boolean);
      if (fixture.destination?.wallId && row.wall_id !== fixture.destination.wallId) {
        throw new Error(`post_publish_destination_mismatch:${row.wall_id ?? "missing"}`);
      }
      if (fixture.locationLabel) {
        const location = postLocationFromBody(row.body ?? "");
        if (location !== fixture.locationLabel) {
          throw new Error(`post_publish_location_mismatch:${location || "missing"}`);
        }
      }
      return {
        postId: row.id,
        wallId: row.wall_id ?? null,
        body: row.body ?? null,
        locationLabel: postLocationFromBody(row.body ?? "") || null,
        mediaUrls: fixture.publishedMediaUrls,
      };
    }
    await delay(1_000);
  }
  throw new Error("post_publish_post_not_persisted");
}

function postLocationFromBody(body) {
  return /\[UBICACION:([^\]]+)]/i.exec(String(body ?? ""))?.[1]?.trim() ?? "";
}

export async function cleanupPostPublishFixture({
  fixture,
  withDatabase,
}) {
  if (!fixture?.marker) return null;
  return await withDatabase(async (client) => {
    await client.query("begin");
    try {
      const resolved = await client.query(
        `select id, image_url, video_url
           from public.community_posts
          where (id = $1::uuid or body like $2)
            and profile_id = $3::uuid
          for update`,
        [fixture.publishedPostId ?? nilUuid, `%${fixture.marker}%`, fixture.actorSession?.profileId ?? nilUuid],
      );
      const ids = [...new Set(resolved.rows.map((row) => row.id).filter(Boolean))];
      const mediaUrls = [...new Set([
        ...(fixture.publishedMediaUrls ?? []),
        ...resolved.rows.flatMap((row) => [row.image_url, row.video_url]).filter(Boolean),
      ])];
      if (ids.length) {
        await client.query("delete from public.community_post_likes where post_id = any($1::uuid[])", [ids]);
        await client.query("delete from public.community_comments where post_id = any($1::uuid[]) or body like $2", [ids, `%${fixture.marker}%`]);
        await client.query(
          `delete from public.community_posts
            where id = any($1::uuid[])
              and profile_id = $2::uuid`,
          [ids, fixture.actorSession?.profileId ?? nilUuid],
        );
      }
      await client.query("commit");
      const residueIds = ids.length ? ids : [fixture.publishedPostId ?? nilUuid];
      const residue = await client.query(
        `select
          (select count(*)::int from public.community_posts
            where (id = any($1::uuid[]) or body like $2)
              and profile_id = $3::uuid) as community_posts,
          (select count(*)::int from public.community_comments
            where post_id = any($1::uuid[]) or body like $2) as community_comments,
          (select count(*)::int from public.community_post_likes
            where post_id = any($1::uuid[])) as community_post_likes`,
        [residueIds, `%${fixture.marker}%`, fixture.actorSession?.profileId ?? nilUuid],
      );
      const counts = residue.rows[0] ?? {};
      if (Object.values(counts).some((count) => Number(count) !== 0)) throw new Error("cleanup_residue_detected:post_publish");
      return {
        status: "cleanup_verified_post_publish_residue_absent",
        postIds: ids,
        mediaUrls,
        residueCounts: counts,
      };
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
  });
}

export async function snapshotPostImageStorageObjects({
  actorSession,
  withDatabase,
}) {
  if (!uuid.test(actorSession?.profileId ?? "")) throw new Error("post_image_storage_snapshot_invalid_actor");
  return await withDatabase(async (client) => {
    const result = await client.query(
      `select name
         from storage.objects
        where bucket_id = 'community-posts'
          and name like $1
        order by name`,
      [`${actorSession.profileId}/%`],
    );
    return result.rows.map((row) => row.name).filter(Boolean);
  });
}

export function diffPostImageStorageObjects(before, after) {
  const previous = new Set(before ?? []);
  return [...new Set(after ?? [])].filter((name) => !previous.has(name)).sort();
}

export async function waitForPostImageStorageRollback({
  actorSession,
  before,
  withDatabase,
  delay,
  timeout = 30_000,
}) {
  const deadline = Date.now() + timeout;
  let lastNewObjects = [];
  while (Date.now() < deadline) {
    const current = await snapshotPostImageStorageObjects({ actorSession, withDatabase });
    lastNewObjects = diffPostImageStorageObjects(before, current);
    if (lastNewObjects.length === 0) {
      return { status: "post_image_storage_rollback_verified", newObjectsAfterRollback: [] };
    }
    await delay(1_000);
  }
  throw new Error(`post_image_storage_rollback_residue:${lastNewObjects.join(",")}`);
}

export function postImageStoragePathFromPublicUrl(url) {
  const value = String(url ?? "");
  const marker = "/storage/v1/object/public/community-posts/";
  const index = value.indexOf(marker);
  if (index < 0) return null;
  const path = value.slice(index + marker.length).split(/[?#]/, 1)[0];
  return path ? decodeURIComponent(path) : null;
}

export async function deletePostImageStorageObject({
  backend,
  accessToken,
  storagePath,
}) {
  if (!backend?.url || !backend?.key) throw new Error("post_image_storage_delete_backend_missing");
  if (!accessToken) throw new Error("post_image_storage_delete_access_token_missing");
  if (!storagePath || storagePath.includes("..")) throw new Error("post_image_storage_delete_path_invalid");
  const response = await fetch(`${backend.url}/storage/v1/object/community-posts`, {
    method: "DELETE",
    headers: {
      apikey: backend.key,
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ prefixes: [storagePath] }),
    signal: AbortSignal.timeout(30_000),
  });
  const text = await response.text().catch(() => "");
  if (!response.ok) throw new Error(`post_image_storage_delete_failed:${response.status}:${text.slice(0, 180)}`);
  return { storagePath, status: response.status };
}

export async function cleanupPostPublishStorageObjects({
  backend,
  accessToken,
  mediaUrls = [],
}) {
  const paths = [...new Set(mediaUrls.map(postImageStoragePathFromPublicUrl).filter(Boolean))];
  const deleted = [];
  const errors = [];
  for (const storagePath of paths) {
    try {
      deleted.push(await deletePostImageStorageObject({ backend, accessToken, storagePath }));
    } catch (error) {
      errors.push({ storagePath, error: String(error?.message ?? error).slice(0, 240) });
    }
  }
  if (errors.length) {
    const failure = new Error("post_publish_storage_cleanup_failed");
    failure.details = { deleted, errors };
    throw failure;
  }
  return { deleted };
}

export async function cleanupProfileRolesSafetyFixture({
  fixture,
  withDatabase,
}) {
  if (!fixture?.prepared) return null;
  return await withDatabase(async (client) => {
    await client.query("begin");
    try {
      await client.query(
        "update public.community_profiles set is_admin = $2, is_official = $3 where id = $1::uuid",
        [fixture.actorProfileId, fixture.actorRoles.isAdmin, fixture.actorRoles.isOfficial],
      );
      await client.query(
        "update public.community_profiles set is_admin = $2, is_official = $3 where id = $1::uuid",
        [fixture.targetProfileId, fixture.targetRoles.isAdmin, fixture.targetRoles.isOfficial],
      );
      await client.query(
        `delete from public.chat_profile_blocks
          where thread_id is null
            and blocker_profile_id = $1::uuid
            and blocked_profile_id = $2::uuid`,
        [fixture.actorProfileId, fixture.targetProfileId],
      );
      if (fixture.hadGlobalBlock) {
        await client.query(
          `insert into public.chat_profile_blocks(thread_id, blocker_profile_id, blocked_profile_id)
           values (null, $1::uuid, $2::uuid)
           on conflict do nothing`,
          [fixture.actorProfileId, fixture.targetProfileId],
        );
      }
      await client.query(
        `delete from public.ugc_reports
          where reporter_profile_id = $1::uuid
            and target_type = 'profile'
            and target_id = $2`,
        [fixture.actorProfileId, fixture.targetProfileId],
      );
      if (fixture.previousReport) {
        const report = fixture.previousReport;
        await client.query(
          `insert into public.ugc_reports(
             id, reporter_profile_id, target_type, target_id, reported_profile_id,
             reason, details, status, created_at, reviewed_at, reviewed_by
           ) values (
             $1, $2::uuid, $3, $4, $5::uuid,
             $6, $7, $8, $9, $10, $11::uuid
           )
           on conflict (reporter_profile_id, target_type, target_id) do update
             set reported_profile_id = excluded.reported_profile_id,
                 reason = excluded.reason,
                 details = excluded.details,
                 status = excluded.status,
                 created_at = excluded.created_at,
                 reviewed_at = excluded.reviewed_at,
                 reviewed_by = excluded.reviewed_by`,
          [
            report.id,
            report.reporter_profile_id,
            report.target_type,
            report.target_id,
            report.reported_profile_id,
            report.reason,
            report.details,
            report.status,
            report.created_at,
            report.reviewed_at,
            report.reviewed_by,
          ],
        );
      }
      await client.query("commit");
    } catch (error) {
      await client.query("rollback").catch(() => {});
      throw error;
    }
    const residue = await client.query(
      `select
        (select is_admin from public.community_profiles where id = $1::uuid) as actor_is_admin,
        (select is_official from public.community_profiles where id = $1::uuid) as actor_is_official,
        (select is_admin from public.community_profiles where id = $2::uuid) as target_is_admin,
        (select is_official from public.community_profiles where id = $2::uuid) as target_is_official,
        (select count(*)::int from public.chat_profile_blocks
          where thread_id is null and blocker_profile_id = $1::uuid and blocked_profile_id = $2::uuid) as block_count,
        (select count(*)::int from public.ugc_reports
          where reporter_profile_id = $1::uuid and target_type = 'profile' and target_id = $3) as report_count`,
      [fixture.actorProfileId, fixture.targetProfileId, fixture.targetProfileId],
    );
    const row = residue.rows[0] ?? {};
    const restored =
      row.actor_is_admin === fixture.actorRoles.isAdmin &&
      row.actor_is_official === fixture.actorRoles.isOfficial &&
      row.target_is_admin === fixture.targetRoles.isAdmin &&
      row.target_is_official === fixture.targetRoles.isOfficial &&
      Number(row.block_count ?? 0) === (fixture.hadGlobalBlock ? 1 : 0) &&
      Number(row.report_count ?? 0) === (fixture.previousReport ? 1 : 0);
    if (!restored) throw new Error("cleanup_residue_detected:profile_roles_safety");
    return {
      status: "cleanup_verified_profile_roles_safety_restored",
      targetProfileId: fixture.targetProfileId,
      restored: true,
    };
  });
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const nilUuid = "00000000-0000-0000-0000-000000000000";
