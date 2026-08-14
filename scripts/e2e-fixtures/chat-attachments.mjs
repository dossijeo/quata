export const chatAttachmentsBucket = "chat-attachments";

export function validWavFixture() {
  const sampleRate = 8_000;
  const durationSeconds = 1;
  const samples = sampleRate * durationSeconds;
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
}) {
  const isAudio = kind === "audio";
  const extension = isAudio ? "wav" : "txt";
  const mimeType = isAudio ? "audio/wav" : "text/plain";
  const marker = `chat-${kind}-attachment-${platformLabel}-${runId}`;
  const safeNameSuffix = String(nameSuffix).replace(/[^a-z0-9_-]/gi, "").slice(0, 24);
  const name = `qadata-${kind}-${runId.slice(0, 8)}${safeNameSuffix}.${extension}`;
  const content = isAudio
    ? validWavFixture()
    : Buffer.from(`QADATA ${platformLabel} document fixture ${marker}\n`, "utf8");
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
         insert into public.community_posts(id, wall_id, profile_id, body)
         select gen_random_uuid(), wall.id, $1, $2
         from wall
         returning id`,
        [fixture.targetSession.profileId, `${marker} post body`],
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
  while (Date.now() < deadline) {
    const result = await withDatabase(async (client) => await client.query(
      `select id
         from public.community_comments
        where post_id = $1 and profile_id = $2 and body = $3
        order by created_at desc
        limit 1`,
      [fixture.postId, fixture.actorSession.profileId, marker],
    ));
    const id = result.rows[0]?.id;
    if (uuid.test(id ?? "")) return id;
    await delay(1_000);
  }
  throw new Error("profile_content_comment_not_persisted");
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const nilUuid = "00000000-0000-0000-0000-000000000000";
