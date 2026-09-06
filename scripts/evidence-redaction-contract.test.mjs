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
    "C:/Users/Alice Example/private report.json",
    "/Users/Alice Example/private report.json",
    "/home/Alice Example/private report.json",
  ].join(" "));
  assert.doesNotMatch(redacted, /qadata-external-share-web|Alice Example|private report/);
  assert.match(redacted, /<evidence-marker/);
  assert.match(redacted, /<local-path-redacted>/);
});

test("evidence redaction does not treat https scheme text as a Windows drive", () => {
  const redacted = redactEvidenceString("https://example.supabase.co/rest/v1/rpc/quata_chat_get_inbox");
  assert.equal(redacted, "https://example.supabase.co/rest/v1/rpc/quata_chat_get_inbox");
});
