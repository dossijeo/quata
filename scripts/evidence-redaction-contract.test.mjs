import assert from "node:assert/strict";
import test from "node:test";

import {
  registerEvidenceSecret,
  redactEvidenceReport,
  redactEvidenceString,
  redactEvidenceUrl,
} from "./e2e-fixtures/evidence-redaction.mjs";

test("evidence redaction hashes storage paths in urls, report keys and diagnostics", () => {
  assert.doesNotMatch(
    redactEvidenceUrl("https://example.test/storage/v1/object/chat-attachments/profile-secret/private-file.txt?token=abc"),
    /profile-secret|private-file|token=abc/,
  );
  assert.doesNotMatch(
    redactEvidenceString("storagePath=profile-secret/private-file.txt"),
    /profile-secret|private-file/,
  );
  assert.doesNotMatch(
    JSON.stringify(redactEvidenceReport({ storagePath: "profile-secret/private-file.txt" })),
    /profile-secret|private-file/,
  );
});

test("evidence redaction removes local paths and credentials across host styles", () => {
  const redacted = redactEvidenceString([
    "C:/Users/PC/private/report.png",
    "D:/private/share/payload.txt",
    "/Users/gabriel/Library/file.txt",
    "/home/gabriel/.cache/file.txt",
    "/private/tmp/quata/file.txt",
    "Bearer abc.def",
    "password=21085800",
  ].join(" "));
  assert.doesNotMatch(redacted, /Users|gabriel|private\/share|\.cache|21085800|abc\.def/);
  assert.match(redacted, /<local-path-redacted>/);
  assert.match(redacted, /Bearer <redacted>/);
  assert.match(redacted, /password=<redacted>/);
});

test("evidence redaction removes markers and local paths containing spaces", () => {
  const marker = "qadata-external-share-web-11111111-2222-3333-4444-555555555555";
  registerEvidenceSecret(marker, "evidence-marker");
  const redacted = redactEvidenceString([
    marker,
    `diagnostic ${marker}`,
    "qadata-short-marker",
    "C:/Users/Alice Example/private report.json",
    "C:/Users/Alice Example/private folder",
    "/Users/Alice Example/private report.json",
    "/Users/Alice Example/private folder",
    "/home/Alice Example/private report.json",
    "/home/Alice Example/private folder",
  ].join(" "));
  assert.doesNotMatch(redacted, /qadata-|Alice Example|private report|private folder/);
  assert.match(redacted, /<evidence-marker/);
  assert.match(redacted, /<local-path-redacted>/);
});

test("evidence redaction does not treat https scheme text as a Windows drive", () => {
  const redacted = redactEvidenceString("https://example.supabase.co/rest/v1/rpc/quata_chat_get_inbox");
  assert.equal(redacted, "https://example.supabase.co/rest/v1/rpc/quata_chat_get_inbox");
});

test("evidence redaction removes sensitive object keys and JSON-like credentials", () => {
  const report = redactEvidenceReport({
    password: "password-secret",
    nested: {
      apikey: "secret-key",
      web_session_token: "session-secret",
      ordinary: "safe-value",
    },
  });
  const serialized = JSON.stringify(report);
  assert.doesNotMatch(serialized, /password-secret|secret-key|session-secret/);
  assert.match(serialized, /password-redacted/);
  assert.match(serialized, /apikey-redacted/);
  assert.match(serialized, /web_session_token-redacted/);
  assert.equal(report.nested.ordinary, "safe-value");

  const redacted = redactEvidenceString(JSON.stringify({ apikey: "secret-key", password: "password-secret" }));
  assert.doesNotMatch(redacted, /secret-key|password-secret/);
  assert.match(redacted, /apikey=<redacted>/);
  assert.match(redacted, /password=<redacted>/);
});

test("evidence redaction handles quoted storage paths with spaces", () => {
  const redacted = redactEvidenceString(`storagePath="profile secret/private file.txt"`);
  assert.doesNotMatch(redacted, /profile secret|private file/);
  assert.match(redacted, /storage-path-sha256/);
});
