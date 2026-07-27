import assert from "node:assert/strict";
import test from "node:test";
import { sanitizeWebSmokeRequest, webSmokePhase } from "./backend-compatibility-web-smoke-report.mjs";

const base = "https://project.supabase.co";

test("forbidden request records a path but never its query or token", () => {
  const entry = sanitizeWebSmokeRequest({
    url: `${base}/storage/v1/object/private/avatar.png?access_token=secret&user=alice`,
    method: "GET",
    resourceType: "fetch",
    frame: "main",
    phase: "list",
    route: "feed",
  }, base);
  assert.deepEqual(entry, {
    method: "GET", originMatch: true, pathname: "/storage/v1/object/private/avatar.png",
    resourceType: "fetch", frame: "main", phase: "list", route: "feed",
  });
  assert.equal(JSON.stringify(entry).includes("secret"), false);
  assert.equal(JSON.stringify(entry).includes("alice"), false);
  assert.equal(JSON.stringify(entry).includes("?"), false);
});

test("report records correlate list, detail, and media phases without URLs", () => {
  const entries = [
    sanitizeWebSmokeRequest({ url: `${base}/rest/v1/community_posts?select=*`, method: "GET", resourceType: "fetch", frame: "main", phase: webSmokePhase("feed", "fetch"), route: "feed" }, base),
    sanitizeWebSmokeRequest({ url: `${base}/rest/v1/community_posts?id=eq.post_7`, method: "GET", resourceType: "fetch", frame: "main", phase: webSmokePhase("post/post_7", "fetch"), route: "post/post_7" }, base),
    sanitizeWebSmokeRequest({ url: `${base}/storage/v1/public/post.png?token=never`, method: "GET", resourceType: "image", frame: "subframe", phase: webSmokePhase("post/post_7", "image"), route: "post/post_7" }, base),
  ];
  assert.deepEqual(entries.map(({ phase, route, resourceType }) => ({ phase, route, resourceType })), [
    { phase: "list", route: "feed", resourceType: "fetch" },
    { phase: "detail", route: "post/post_7", resourceType: "fetch" },
    { phase: "media", route: "post/post_7", resourceType: "image" },
  ]);
  assert.equal(JSON.stringify(entries).includes("select=*"), false);
  assert.equal(JSON.stringify(entries).includes("never"), false);
});
