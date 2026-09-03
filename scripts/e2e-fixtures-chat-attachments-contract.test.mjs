import assert from "node:assert/strict";
import test from "node:test";

import {
  attachmentStorageFixtures,
  chatAttachmentsBucket,
  createCleanupRegistry,
  cleanupFeedOfficialCommentsFixture,
  cleanupPostPublishFixture,
  cleanupProfileContentFixture,
  createPostPublishFixture,
  pollFeedOfficialComment,
  pollFeedOfficialReplyComment,
  pollPostPublishFixture,
  pollProfileContentReplyComment,
  selectPostPublishDestinationFixture,
  seedFeedOfficialCommentsFixture,
  seedProfileContentFixture,
  seedChatAttachmentFixture,
  longMp4Fixture,
  longMp4FixturePath,
  validMp4Fixture,
  validM4aFixture,
  validWavFixture,
} from "./e2e-fixtures/chat-attachments.mjs";

test("validWavFixture is shared and produces a playable RIFF/WAVE buffer", () => {
  const wav = validWavFixture();
  assert.equal(wav.subarray(0, 4).toString("ascii"), "RIFF");
  assert.equal(wav.subarray(8, 12).toString("ascii"), "WAVE");
  assert.ok(wav.length > 44);
});

test("validWavFixture supports longer observable playback windows for E2E chaining", () => {
  const standard = validWavFixture();
  const longer = validWavFixture({ durationSeconds: 12 });
  assert.equal(longer.subarray(0, 4).toString("ascii"), "RIFF");
  assert.ok(longer.length > standard.length * 2);
});

test("validM4aFixture is shared and produces a real AAC/M4A buffer for iOS audio evidence", () => {
  const m4a = validM4aFixture({ durationSeconds: 2 });
  assert.ok(m4a.length > 1_000);
  assert.equal(m4a.subarray(4, 8).toString("ascii"), "ftyp");
});

test("validMp4Fixture is shared and produces a real MP4 buffer", () => {
  const mp4 = validMp4Fixture();
  assert.ok(mp4.length > 8_000);
  assert.equal(mp4.subarray(4, 8).toString("ascii"), "ftyp");
});

test("longMp4Fixture is shared and keeps post picker/camera video evidence realistic", () => {
  const path = longMp4FixturePath();
  const mp4 = longMp4Fixture();
  assert.match(path, /big-buck-bunny-320x180\.mp4$/);
  assert.ok(mp4.length > 10_000_000);
  assert.equal(mp4.subarray(4, 8).toString("ascii"), "ftyp");
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
  assert.equal(fixture.mimeType, "application/pdf");
  assert.equal(calls[0].options.headers["content-type"], "application/pdf");
  assert.match(calls[0].path, /qadata-document-12345678\.pdf$/);
  assert.equal(Buffer.isBuffer(calls[0].options.body), true);
  assert.equal(calls[0].options.body.subarray(0, 8).toString("ascii"), "%PDF-1.4");
  assert.match(calls[0].path, new RegExp(`/storage/v1/object/${chatAttachmentsBucket}/`));
  assert.equal(cleanup.summary().trackedStorageObjects, 1);
});

test("shared fixture seeds image attachments with valid PNG metadata", async () => {
  const calls = [];
  const fixture = await seedChatAttachmentFixture({
    config: { baseUrl: "https://example.supabase.co" },
    session: { profileId: "profile-a" },
    thread: 123,
    runId: "12345678-1234-1234-1234-123456789abc",
    kind: "image",
    platformLabel: "ios",
    cleanup: createCleanupRegistry(),
    storageRequest: async (_config, _session, path, options) => calls.push({ path, options }),
    rpc: async (_config, _session, name) => name === "quata_chat_register_attachment" ? { id: 191 } : { message_id: 192 },
    pollMessage: async () => {},
    messageText: (message) => message.message,
    attachmentId: (payload) => payload.id,
    messageId: (payload) => payload.message_id,
  });
  assert.equal(fixture.id, 191);
  assert.equal(fixture.messageId, 192);
  assert.equal(fixture.mimeType, "image/png");
  assert.equal(fixture.name, "qadata-image-12345678.png");
  assert.equal(calls[0].options.headers["content-type"], "image/png");
  assert.ok(Buffer.isBuffer(calls[0].options.body));
  assert.match(calls[0].path, new RegExp(`/storage/v1/object/${chatAttachmentsBucket}/`));
});

test("shared fixture seeds video attachments with valid MP4 metadata", async () => {
  const calls = [];
  const fixture = await seedChatAttachmentFixture({
    config: { baseUrl: "https://example.supabase.co" },
    session: { profileId: "profile-a" },
    thread: 123,
    runId: "12345678-1234-1234-1234-123456789abc",
    kind: "video",
    platformLabel: "web",
    cleanup: createCleanupRegistry(),
    storageRequest: async (_config, _session, path, options) => calls.push({ path, options }),
    rpc: async (_config, _session, name) => name === "quata_chat_register_attachment" ? { id: 291 } : { message_id: 292 },
    pollMessage: async () => {},
    messageText: (message) => message.message,
    attachmentId: (payload) => payload.id,
    messageId: (payload) => payload.message_id,
  });
  assert.equal(fixture.id, 291);
  assert.equal(fixture.messageId, 292);
  assert.equal(fixture.mimeType, "video/mp4");
  assert.equal(fixture.name, "qadata-video-12345678.mp4");
  assert.equal(calls[0].options.headers["content-type"], "video/mp4");
  assert.ok(Buffer.isBuffer(calls[0].options.body));
  assert.ok(calls[0].options.body.length > 8_000);
  assert.match(calls[0].path, new RegExp(`/storage/v1/object/${chatAttachmentsBucket}/`));
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
  assert.equal(fixture.name, "qadata-audio-12345678-next.m4a");
});

test("seedProfileContentFixture registers storage cleanup before remote upload", async () => {
  const cleanup = createCleanupRegistry();
  const order = [];
  await assert.rejects(seedProfileContentFixture({
    fixture: {
      marker: "qadata-profile-content-12345678-1234-1234-1234-123456789abc",
      actorSession: { profileId: "actor-profile" },
      targetSession: { profileId: "target-profile" },
      threadId: 123,
    },
    config: { baseUrl: "https://example.supabase.co" },
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
    withDatabase: async () => { throw new Error("must_not_insert_post_after_upload_failure"); },
    attachmentId: () => 1,
    messageId: () => 1,
  }), /upload_failed/);
  assert.equal(order[0].startsWith("track:"), true);
  assert.equal(order[1], "upload");
  assert.equal(cleanup.summary().trackedStorageObjects, 1);
});

test("shared profile content fixture seeds post, comment, like and attachment metadata", async () => {
  const calls = [];
  const queries = [];
  const cleanup = createCleanupRegistry();
  const fixture = await seedProfileContentFixture({
    fixture: {
      marker: "qadata-profile-content-12345678-1234-1234-1234-123456789abc",
      actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
      targetSession: { profileId: "22222222-2222-2222-2222-222222222222" },
      threadId: 123,
    },
    config: { baseUrl: "https://example.supabase.co" },
    cleanup,
    storageRequest: async (_config, _session, path, options) => calls.push({ path, options }),
    rpc: async (_config, _session, name) => name === "quata_chat_register_attachment" ? { id: 191 } : { message_id: 192 },
    withDatabase: async (callback) => callback({
      query: async (sql, params = []) => {
        queries.push({ sql, params });
        if (/insert into public\.community_posts/.test(sql)) return { rows: [{ id: "33333333-3333-3333-3333-333333333333" }] };
        if (/insert into public\.community_comments/.test(sql)) return { rows: [{ id: "44444444-4444-4444-4444-444444444444" }] };
        return { rows: [], rowCount: 0 };
      },
    }),
    attachmentId: (payload) => payload.id,
    messageId: (payload) => payload.message_id,
  });
  assert.equal(fixture.attachmentId, 191);
  assert.equal(fixture.attachmentMessageId, 192);
  assert.equal(fixture.postId, "33333333-3333-3333-3333-333333333333");
  assert.equal(fixture.seedCommentId, "44444444-4444-4444-4444-444444444444");
  assert.equal(calls[0].options.headers["content-type"], "text/plain; charset=utf-8");
  assert.match(calls[0].path, new RegExp(`/storage/v1/object/${chatAttachmentsBucket}/`));
  assert.equal(calls[1].options.headers["content-type"], "image/png");
  assert.match(calls[1].path, new RegExp(`/storage/v1/object/${chatAttachmentsBucket}/`));
  assert.ok(queries.some((entry) => /insert into public\.community_post_likes/.test(entry.sql)));
  assert.equal(cleanup.summary().trackedStorageObjects, 2);
});

test("shared profile content cleanup deletes owned rows and verifies residue", async () => {
  const queries = [];
  const summary = await cleanupProfileContentFixture({
    fixture: {
      marker: "qadata-profile-content-12345678-1234-1234-1234-123456789abc",
      actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
      targetSession: { profileId: "22222222-2222-2222-2222-222222222222" },
      postId: "33333333-3333-3333-3333-333333333333",
      seedCommentId: "44444444-4444-4444-4444-444444444444",
      attachmentId: 191,
      storagePath: "11111111-1111-1111-1111-111111111111/profile-content/qadata-profile-content.txt",
    },
    withDatabase: async (callback) => callback({
      query: async (sql, params = []) => {
        queries.push({ sql, params });
        if (/select\s+\(select count\(\*\)::int from public\.community_posts/.test(sql)) {
          return {
            rows: [{
              community_posts: 0,
              community_comments: 0,
              community_post_likes: 0,
              chat_attachments: 0,
            }],
          };
        }
        return { rows: [], rowCount: 0 };
      },
    }),
  });
  assert.equal(summary.status, "cleanup_verified_profile_content_residue_absent");
  assert.ok(queries.some((entry) => /delete from public\.community_post_likes/.test(entry.sql)));
  assert.ok(queries.some((entry) =>
    /delete from public\.community_comments/.test(entry.sql) &&
    /select id from public\.community_posts/.test(entry.sql) &&
    /profile_id = \$3/.test(entry.sql),
  ));
  assert.ok(queries.some((entry) => /delete from public\.community_posts/.test(entry.sql)));
  assert.ok(queries.some((entry) =>
    /delete from public\.chat_attachments/.test(entry.sql) &&
    /uploaded_by_profile_id = \$3/.test(entry.sql),
  ));
  assert.deepEqual(queries.at(-1).params.slice(0, 5), [
    "33333333-3333-3333-3333-333333333333",
    "%qadata-profile-content-12345678-1234-1234-1234-123456789abc%",
    191,
    "11111111-1111-1111-1111-111111111111/profile-content/qadata-profile-content.txt",
    "22222222-2222-2222-2222-222222222222",
  ]);
});

test("shared profile content cleanup fails closed on residue", async () => {
  await assert.rejects(cleanupProfileContentFixture({
    fixture: {
      marker: "qadata-profile-content-12345678-1234-1234-1234-123456789abc",
      actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
      targetSession: { profileId: "22222222-2222-2222-2222-222222222222" },
      postId: "33333333-3333-3333-3333-333333333333",
      seedCommentId: "44444444-4444-4444-4444-444444444444",
      attachmentId: 191,
      storagePath: "11111111-1111-1111-1111-111111111111/profile-content/qadata-profile-content.txt",
    },
    withDatabase: async (callback) => callback({
      query: async (sql) => {
        if (/select\s+\(select count\(\*\)::int from public\.community_posts/.test(sql)) {
          return {
            rows: [{
              community_posts: 1,
              community_comments: 0,
              community_post_likes: 0,
              chat_attachments: 0,
            }],
          };
        }
        return { rows: [], rowCount: 0 };
      },
    }),
  }), /cleanup_residue_detected:profile_content/);
});

test("shared feed/official comments fixture seeds both surfaces with reversible IDs", async () => {
  const queries = [];
  const fixture = await seedFeedOfficialCommentsFixture({
    fixture: {
      marker: "qadata-feed-official-comments-12345678-1234-1234-1234-123456789abc",
      actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
      targetSession: { profileId: "22222222-2222-2222-2222-222222222222" },
    },
    withDatabase: async (callback) => callback({
      query: async (sql, params = []) => {
        queries.push({ sql, params });
        if (/insert into public\.community_posts/.test(sql)) return { rows: [{ id: params[1] }], rowCount: 1 };
        return { rows: [], rowCount: 1 };
      },
    }),
  });
  assert.match(fixture.feed.postId, /^[0-9a-f-]{36}$/);
  assert.match(fixture.official.postId, /^[0-9a-f-]{36}$/);
  assert.match(fixture.feed.uiComment, /^😀 qadata-feed-official-comments-/);
  assert.match(fixture.official.uiComment, /^😀 qadata-feed-official-comments-/);
  assert.ok(queries.some((entry) => /insert into public\.community_posts/.test(entry.sql)));
  assert.ok(queries.some((entry) => /insert into public\.community_comments/.test(entry.sql)));
  assert.ok(queries.some((entry) => /insert into public\.official_posts/.test(entry.sql)));
  assert.ok(queries.some((entry) => /insert into public\.official_post_comments/.test(entry.sql)));
});

test("shared comment reply pollers require remote reply shortcodes", async () => {
  const profileReplyId = "66666666-6666-6666-6666-666666666666";
  const feedReplyId = "77777777-7777-7777-7777-777777777777";
  const queries = [];
  const delay = async () => {};
  const profileResult = await pollProfileContentReplyComment({
    fixture: {
      postId: "33333333-3333-3333-3333-333333333333",
      actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
    },
    marker: "😀 reply marker",
    replyToCommentId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    delay,
    withDatabase: async (callback) => callback({
      query: async (sql, params = []) => {
        queries.push({ sql, params });
        return { rows: [{ id: profileReplyId, body: "[reply:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:Ana] reply marker" }], rowCount: 1 };
      },
    }),
  });
  const feedResult = await pollFeedOfficialReplyComment({
    fixture: {
      actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
      feed: { postId: "33333333-3333-3333-3333-333333333333" },
    },
    surface: "feed",
    marker: "😀 feed reply marker",
    replyToCommentId: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    delay,
    withDatabase: async (callback) => callback({
      query: async (sql, params = []) => {
        queries.push({ sql, params });
        return { rows: [{ id: feedReplyId, body: "[reply:bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb:Ana] feed reply marker" }], rowCount: 1 };
      },
    }),
  });
  assert.equal(profileResult, profileReplyId);
  assert.equal(feedResult, feedReplyId);
  assert.ok(queries.some((entry) => entry.params.includes("[reply:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:%")));
  assert.ok(queries.some((entry) => entry.params.includes("[reply:bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb:%")));
  assert.ok(queries.some((entry) => entry.params.includes("%reply marker%")));
  assert.ok(queries.some((entry) => entry.params.includes("%feed reply marker%")));
  assert.ok(!queries.some((entry) => entry.params.includes("%😀 reply marker%")));
  assert.ok(!queries.some((entry) => entry.params.includes("%😀 feed reply marker%")));
});

test("shared feed/official comments cleanup deletes both surfaces and verifies residue", async () => {
  const queries = [];
  const summary = await cleanupFeedOfficialCommentsFixture({
    fixture: {
      marker: "qadata-feed-official-comments-12345678-1234-1234-1234-123456789abc",
      actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
      targetSession: { profileId: "22222222-2222-2222-2222-222222222222" },
      feed: { postId: "33333333-3333-3333-3333-333333333333" },
      official: {
        postId: "44444444-4444-4444-4444-444444444444",
        translationGroupId: "55555555-5555-5555-5555-555555555555",
      },
    },
    withDatabase: async (callback) => callback({
      query: async (sql, params = []) => {
        queries.push({ sql, params });
        if (/select\s+\(select count\(\*\)::int from public\.community_posts/.test(sql)) {
          return { rows: [{
            community_posts: 0,
            community_comments: 0,
            community_post_likes: 0,
            official_posts: 0,
            official_post_comments: 0,
            official_post_likes: 0,
          }] };
        }
        return { rows: [], rowCount: 1 };
      },
    }),
  });
  assert.equal(summary.status, "cleanup_verified_feed_official_comments_residue_absent");
  assert.ok(queries.some((entry) => /delete from public\.community_comments/.test(entry.sql)));
  assert.ok(queries.some((entry) => /delete from public\.community_posts/.test(entry.sql)));
  assert.ok(queries.some((entry) => /delete from public\.official_post_comments/.test(entry.sql)));
  assert.ok(queries.some((entry) => /delete from public\.official_posts/.test(entry.sql)));
});

test("shared feed/official comments cleanup fails closed on residue", async () => {
  await assert.rejects(cleanupFeedOfficialCommentsFixture({
    fixture: {
      marker: "qadata-feed-official-comments-12345678-1234-1234-1234-123456789abc",
      targetSession: { profileId: "22222222-2222-2222-2222-222222222222" },
      feed: { postId: "33333333-3333-3333-3333-333333333333" },
      official: { postId: "44444444-4444-4444-4444-444444444444", translationGroupId: "55555555-5555-5555-5555-555555555555" },
    },
    withDatabase: async (callback) => callback({
      query: async (sql) => {
        if (/select\s+\(select count\(\*\)::int from public\.community_posts/.test(sql)) {
          return { rows: [{
            community_posts: 0,
            community_comments: 0,
            community_post_likes: 0,
            official_posts: 1,
            official_post_comments: 0,
            official_post_likes: 0,
          }] };
        }
        return { rows: [], rowCount: 0 };
      },
    }),
  }), /cleanup_residue_detected:feed_official_comments/);
});

test("shared feed/official comments poll reads the platform-neutral surface table", async () => {
  const calls = [];
  const fixture = {
    actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
    feed: { postId: "33333333-3333-3333-3333-333333333333" },
    official: { postId: "44444444-4444-4444-4444-444444444444" },
  };
  const withDatabase = async (callback) => callback({
    query: async (sql, params = []) => {
      calls.push({ sql, params });
      return { rows: [{ id: "66666666-6666-6666-6666-666666666666" }] };
    },
  });
  assert.equal(await pollFeedOfficialComment({ fixture, surface: "feed", marker: "feed", withDatabase, delay: async () => {} }), "66666666-6666-6666-6666-666666666666");
  assert.equal(await pollFeedOfficialComment({ fixture, surface: "official", marker: "official", withDatabase, delay: async () => {} }), "66666666-6666-6666-6666-666666666666");
  assert.match(calls[0].sql, /public\.community_comments/);
  assert.match(calls[1].sql, /public\.official_post_comments/);
});

test("post publish fixture creates stable markers per platform", () => {
  const fixture = createPostPublishFixture({
    actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
    platformLabel: "Web/Wasm",
    runId: "12345678-1234-1234-1234-123456789abc",
  });
  assert.equal(fixture.marker, "qadata-post-publish-webwasm-12345678-1234-1234-1234-123456789abc");
  assert.equal(fixture.markerProbe, "4-1234-1234-123456789abc");
});

test("post publish poll stores exact post and media for cleanup", async () => {
  const fixture = createPostPublishFixture({
    actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
    platformLabel: "android",
    runId: "12345678-1234-1234-1234-123456789abc",
    locationLabel: `Malabo Centro ${"qadata-post-publish-android-12345678-1234-1234-1234-123456789abc"}`,
  });
  const result = await pollPostPublishFixture({
    fixture,
    delay: async () => {},
    withDatabase: async (callback) => callback({
      query: async () => ({
        rows: [{
          id: "22222222-2222-2222-2222-222222222222",
          wall_id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
          body: `[CANAL:feed]\n[UBICACION:${fixture.locationLabel}]`,
          image_url: "https://project.supabase.co/storage/v1/object/public/community-posts/actor/img.png",
          video_url: null,
        }],
      }),
    }),
  });
  assert.equal(result.postId, "22222222-2222-2222-2222-222222222222");
  assert.equal(result.locationLabel, fixture.locationLabel);
  assert.equal(fixture.publishedPostId, "22222222-2222-2222-2222-222222222222");
  assert.equal(fixture.publishedMediaUrls.length, 1);
});

test("post publish poll fails closed when expected location is missing", async () => {
  const fixture = createPostPublishFixture({
    actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
    platformLabel: "web",
    runId: "12345678-1234-1234-1234-123456789abc",
    locationLabel: "Malabo Centro qadata-post-publish-web-12345678-1234-1234-1234-123456789abc",
  });

  await assert.rejects(
    pollPostPublishFixture({
      fixture,
      delay: async () => {},
      withDatabase: async (callback) => callback({
        query: async () => ({
          rows: [{
            id: "22222222-2222-2222-2222-222222222222",
            wall_id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            body: `body ${fixture.marker}`,
            image_url: null,
            video_url: null,
          }],
        }),
      }),
    }),
    /post_publish_location_mismatch:missing/,
  );
});

test("post publish destination fixture selects an eligible active wall", async () => {
  const actorSession = { profileId: "11111111-1111-1111-1111-111111111111" };
  const destination = await selectPostPublishDestinationFixture({
    actorSession,
    withDatabase: async (callback) => callback({
      query: async (_sql, params = []) => {
        assert.deepEqual(params, [actorSession.profileId]);
        return {
          rows: [
            { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", name: "Centro", slug: "centro", is_member: true },
            { id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", name: "Bata", slug: "bata", is_member: true },
          ],
        };
      },
    }),
  });

  assert.equal(destination.wallId, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  assert.equal(destination.label, "Bata");
  assert.equal(destination.optionsSeen.length, 2);
});

test("post publish poll fails closed when selected destination was not used", async () => {
  const fixture = createPostPublishFixture({
    actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
    platformLabel: "web",
    runId: "12345678-1234-1234-1234-123456789abc",
    destination: { wallId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", label: "Centro" },
  });

  await assert.rejects(
    pollPostPublishFixture({
      fixture,
      delay: async () => {},
      withDatabase: async (callback) => callback({
        query: async () => ({
          rows: [{
            id: "22222222-2222-2222-2222-222222222222",
            wall_id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            body: `body ${fixture.marker}`,
            image_url: null,
            video_url: null,
          }],
        }),
      }),
    }),
    /post_publish_destination_mismatch/,
  );
});

test("post publish cleanup deletes owned rows and verifies residue", async () => {
  const queries = [];
  const summary = await cleanupPostPublishFixture({
    fixture: {
      marker: "qadata-post-publish-ios-12345678-1234-1234-1234-123456789abc",
      actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
      publishedPostId: "22222222-2222-2222-2222-222222222222",
      publishedMediaUrls: ["https://project.supabase.co/storage/v1/object/public/community-posts/actor/img.png"],
    },
    withDatabase: async (callback) => callback({
      query: async (sql, params = []) => {
        queries.push({ sql, params });
        if (/select id, image_url, video_url/.test(sql)) {
          return {
            rows: [{
              id: "22222222-2222-2222-2222-222222222222",
              image_url: "https://project.supabase.co/storage/v1/object/public/community-posts/actor/img.png",
              video_url: null,
            }],
          };
        }
        if (/select\s+\(select count\(\*\)::int from public\.community_posts/.test(sql)) {
          return {
            rows: [{
              community_posts: 0,
              community_comments: 0,
              community_post_likes: 0,
            }],
          };
        }
        return { rows: [], rowCount: 1 };
      },
    }),
  });
  assert.equal(summary.status, "cleanup_verified_post_publish_residue_absent");
  assert.deepEqual(summary.postIds, ["22222222-2222-2222-2222-222222222222"]);
  assert.equal(summary.mediaUrls.length, 1);
  assert.ok(queries.some((entry) => /delete from public\.community_post_likes/.test(entry.sql)));
  assert.ok(queries.some((entry) => /delete from public\.community_comments/.test(entry.sql)));
  assert.ok(queries.some((entry) => /delete from public\.community_posts/.test(entry.sql)));
});

test("post publish cleanup fails closed on residue", async () => {
  await assert.rejects(cleanupPostPublishFixture({
    fixture: {
      marker: "qadata-post-publish-web-12345678-1234-1234-1234-123456789abc",
      actorSession: { profileId: "11111111-1111-1111-1111-111111111111" },
      publishedPostId: "22222222-2222-2222-2222-222222222222",
    },
    withDatabase: async (callback) => callback({
      query: async (sql) => {
        if (/select\s+\(select count\(\*\)::int from public\.community_posts/.test(sql)) {
          return {
            rows: [{
              community_posts: 1,
              community_comments: 0,
              community_post_likes: 0,
            }],
          };
        }
        return { rows: [], rowCount: 0 };
      },
    }),
  }), /cleanup_residue_detected:post_publish/);
});
