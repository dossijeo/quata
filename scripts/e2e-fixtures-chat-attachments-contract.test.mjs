import assert from "node:assert/strict";
import test from "node:test";

import {
  attachmentStorageFixtures,
  chatAttachmentsBucket,
  createCleanupRegistry,
  seedChatAttachmentFixture,
  validWavFixture,
} from "./e2e-fixtures/chat-attachments.mjs";

test("validWavFixture is shared and produces a playable RIFF/WAVE buffer", () => {
  const wav = validWavFixture();
  assert.equal(wav.subarray(0, 4).toString("ascii"), "RIFF");
  assert.equal(wav.subarray(8, 12).toString("ascii"), "WAVE");
  assert.ok(wav.length > 44);
});

test("cleanup registry deduplicates storage paths", () => {
  const cleanup = createCleanupRegistry();
  cleanup.trackStorageObject({ storagePath: "a/b.wav", name: "audio" });
  cleanup.trackStorageObject({ storagePath: "a/b.wav", name: "audio-again" });
  cleanup.trackStorageObject({ storagePath: "a/c.txt", name: "doc" });
  assert.equal(cleanup.summary().trackedStorageObjects, 2);
  assert.deepEqual(attachmentStorageFixtures({ cleanupRegistry: cleanup }).map((item) => item.storagePath), ["a/b.wav", "a/c.txt"]);
});

test("cleanup keeps trying all storage objects and reports failure diagnostics", async () => {
  const cleanup = createCleanupRegistry();
  cleanup.trackStorageObject({ storagePath: "ok.txt", name: "ok" });
  cleanup.trackStorageObject({ storagePath: "fail.wav", name: "fail" });
  const deleted = [];
  await assert.rejects(
    cleanup.cleanupStorageObjects({
      config: {},
      session: {},
      actions: [],
      storageRequest: async (_config, _session, _path, options) => {
        deleted.push(JSON.parse(options.body).prefixes[0]);
      },
      verifyStorageObjectAbsent: async (_bucket, storagePath) => {
        if (storagePath === "fail.wav") throw new Error("still_present");
      },
    }),
    /cleanup_registry_failed:fail\.wav/,
  );
  assert.deepEqual(deleted, ["ok.txt", "fail.wav"]);
  assert.equal(cleanup.summary().errors.length, 1);
});

test("seedChatAttachmentFixture registers cleanup before remote upload", async () => {
  const cleanup = createCleanupRegistry();
  const order = [];
  await assert.rejects(seedChatAttachmentFixture({
    config: { baseUrl: "https://example.supabase.co" },
    session: { profileId: "profile-a" },
    thread: 123,
    runId: "12345678-1234-1234-1234-123456789abc",
    kind: "audio",
    platformLabel: "web",
    cleanup: {
      trackStorageObject(entry) {
        order.push(`track:${entry.storagePath}`);
        return cleanup.trackStorageObject(entry);
      },
    },
    storageRequest: async () => {
      order.push("upload");
      throw new Error("upload_failed");
    },
    rpc: async () => { throw new Error("must_not_register_after_upload_failure"); },
    pollMessage: async () => {},
    messageText: () => "",
    attachmentId: () => 1,
    messageId: () => 1,
  }), /upload_failed/);
  assert.equal(order[0].startsWith("track:"), true);
  assert.equal(order[1], "upload");
  assert.equal(cleanup.summary().trackedStorageObjects, 1);
});

test("shared fixture seeds document/audio with expected metadata", async () => {
  const calls = [];
  const cleanup = createCleanupRegistry();
  const fixture = await seedChatAttachmentFixture({
    config: { baseUrl: "https://example.supabase.co" },
    session: { profileId: "profile-a" },
    thread: 123,
    runId: "12345678-1234-1234-1234-123456789abc",
    kind: "document",
    platformLabel: "android",
    cleanup,
    storageRequest: async (_config, _session, path, options) => calls.push({ path, options }),
    rpc: async (_config, _session, name) => name === "quata_chat_register_attachment" ? { id: 91 } : { message_id: 92 },
    pollMessage: async () => {},
    messageText: (message) => message.message,
    attachmentId: (payload) => payload.id,
    messageId: (payload) => payload.message_id,
  });
  assert.equal(fixture.id, 91);
  assert.equal(fixture.messageId, 92);
  assert.equal(fixture.mimeType, "text/plain");
  assert.equal(calls[0].options.headers["content-type"], "text/plain");
  assert.match(calls[0].path, new RegExp(`/storage/v1/object/${chatAttachmentsBucket}/`));
  assert.equal(cleanup.summary().trackedStorageObjects, 1);
});

test("shared fixture supports stable visible name suffixes for repeated attachments", async () => {
  const fixture = await seedChatAttachmentFixture({
    config: { baseUrl: "https://example.supabase.co" },
    session: { profileId: "profile-a" },
    thread: 123,
    runId: "12345678-1234-1234-1234-123456789abc-next",
    kind: "audio",
    platformLabel: "web",
    nameSuffix: "-next",
    cleanup: createCleanupRegistry(),
    storageRequest: async () => {},
    rpc: async (_config, _session, name) => name === "quata_chat_register_attachment" ? { id: 91 } : { message_id: 92 },
    pollMessage: async () => {},
    messageText: (message) => message.message,
    attachmentId: (payload) => payload.id,
    messageId: (payload) => payload.message_id,
  });
  assert.equal(fixture.name, "qadata-audio-12345678-next.wav");
});
