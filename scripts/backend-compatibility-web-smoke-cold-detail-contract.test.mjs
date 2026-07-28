import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const source = await readFile(new URL("./backend-compatibility-web-smoke.mjs", import.meta.url), "utf8");

test("detail evidence starts from a local cold document, not a hash-only transition", () => {
  assert.match(source, /function detailColdDocumentUrl\(origin, postId\)/);
  assert.match(source, /\?quata_detail_cold=1#post-\$\{encodeURIComponent\(postId\)\}/);
  assert.match(source, /page\.goto\(detailColdDocumentUrl\(server\.origin, observedPostId\), \{ waitUntil: "domcontentloaded" \}\)/);
  assert.match(source, /detailNavigationType !== "navigate"/);
  assert.doesNotMatch(source, /page\.goto\(`\$\{server\.origin\}\/\#post-\$\{observedPostId\}`/);
});

test("expected media waits for its epoch outcome instead of scanning route records", () => {
  assert.match(source, /expectedMedia \? await waitForMediaOutcome\(activeMediaEpoch\) : false/);
  assert.match(source, /detailExpectedMedia \? await waitForMediaOutcome\(activeMediaEpoch\) : false/);
  assert.doesNotMatch(source, /mediaResponses\.some/);
});
