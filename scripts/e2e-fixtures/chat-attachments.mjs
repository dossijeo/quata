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
}) {
  const isAudio = kind === "audio";
  const extension = isAudio ? "wav" : "txt";
  const mimeType = isAudio ? "audio/wav" : "text/plain";
  const marker = `chat-${kind}-attachment-${platformLabel}-${runId}`;
  const name = `qadata-${kind}-${runId.slice(0, 8)}.${extension}`;
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

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}
