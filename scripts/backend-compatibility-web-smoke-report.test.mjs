import assert from "node:assert/strict";
import test from "node:test";
import { sanitizeWebSmokeRequest, webSmokePhase } from "./backend-compatibility-web-smoke-report.mjs";

const base = "https://project.supabase.co";

test("forbidden request records only a storage category, never a path, query, or token", () => {
  const entry = sanitizeWebSmokeRequest({
    url: `${base}/storage/v1/object/private/avatar.png?access_token=secret&user=alice`,
    method: "GET",
    resourceType: "fetch",
    frame: "main",
    phase: "list",
    route: "feed",
  }, base);
  assert.deepEqual(entry, {
    method: "GET", originMatch: true, pathname: "/storage/v1/<redacted>",
    resourceType: "fetch", frame: "main", phase: "list", route: "feed",
  });
  assert.equal(JSON.stringify(entry).includes("secret"), false);
  assert.equal(JSON.stringify(entry).includes("alice"), false);
  assert.equal(JSON.stringify(entry).includes("?"), false);
});

test("path diagnostics use only safe API templates under adversarial path input", () => {
  const adversarial = [
    {
      name: "encoded email and traversal",
      url: `${base}/storage/v1/object/private/alice%40example.com/..%2Fprivate?access_token=secret#fragment`,
      expected: "/storage/v1/<redacted>",
      secrets: ["alice", "example", "secret", "fragment", "private"],
    },
    {
      name: "token-like Unicode filename",
      url: `${base}/storage/v1/object/public/令牌-JWT.eyJhbGciOiJIUzI1NiJ9.png?token=never`,
      expected: "/storage/v1/<redacted>",
      secrets: ["令牌", "eyJ", "never"],
    },
    {
      name: "UUID under a permitted REST table",
      url: `${base}/rest/v1/community_posts/550e8400-e29b-41d4-a716-446655440000?email=bob%40example.com`,
      expected: "/rest/v1/community_posts/<redacted>",
      secrets: ["550e8400", "bob", "example"],
    },
    {
      name: "unlisted REST resource",
      url: `${base}/rest/v1/rpc/private_lookup/alice%40example.com?apikey=secret`,
      expected: "/rest/v1/<redacted>",
      secrets: ["private_lookup", "alice", "example", "secret"],
    },
    {
      name: "auth dynamic segment",
      url: `${base}/auth/v1/admin/users/550e8400-e29b-41d4-a716-446655440000?token=secret`,
      expected: "/auth/v1/<redacted>",
      secrets: ["admin", "550e8400", "secret"],
    },
    {
      name: "function dynamic segment",
      url: `${base}/functions/v1/send-email/alice%40example.com?code=secret`,
      expected: "/functions/v1/<redacted>",
      secrets: ["send-email", "alice", "example", "secret"],
    },
    {
      name: "other service",
      url: `${base}/realtime/v1/websocket/令牌?token=secret`,
      expected: "/<other>",
      secrets: ["realtime", "websocket", "令牌", "secret"],
    },
  ];

  for (const fixture of adversarial) {
    const entry = sanitizeWebSmokeRequest({
      url: fixture.url,
      method: "GET",
      resourceType: "fetch",
      frame: "main",
      phase: "list",
      route: "feed",
    }, base);
    const serialized = JSON.stringify(entry).toLowerCase();
    assert.equal(entry.pathname, fixture.expected, fixture.name);
    assert.equal(serialized.includes("?"), false, fixture.name);
    assert.equal(serialized.includes("#"), false, fixture.name);
    for (const secret of fixture.secrets) {
      assert.equal(serialized.includes(secret.toLowerCase()), false, `${fixture.name}: ${secret}`);
    }
  }
});

test("only allowlisted REST table labels are retained", () => {
  const permitted = sanitizeWebSmokeRequest({
    url: `${base}/rest/v1/community_posts?select=*`, method: "GET", resourceType: "fetch", frame: "main", phase: "list", route: "feed",
  }, base);
  const unpermitted = sanitizeWebSmokeRequest({
    url: `${base}/rest/v1/community_posts_private?select=*`, method: "GET", resourceType: "fetch", frame: "main", phase: "list", route: "feed",
  }, base);
  assert.equal(permitted.pathname, "/rest/v1/community_posts");
  assert.equal(unpermitted.pathname, "/rest/v1/<redacted>");
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
