import { createHmac } from "node:crypto";

const [baseUrl, jwtSecret] = process.argv.slice(2);
if (!baseUrl || !jwtSecret) throw new Error("base URL and JWT secret are required");

const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
const jwt = (sub, role = "authenticated") => {
  const header = encode({ alg: "HS256", typ: "JWT" });
  const payload = encode({
    sub,
    role,
    aud: "authenticated",
    exp: Math.floor(Date.now() / 1000) + 600,
  });
  const signature = createHmac("sha256", jwtSecret)
    .update(`${header}.${payload}`)
    .digest("base64url");
  return `${header}.${payload}.${signature}`;
};

const request = async (path, { token, method = "GET", body } = {}) => {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      apikey: "isolated-test-key",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
      ...(body ? { "content-type": "application/json" } : {}),
      prefer: "return=representation",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let value = text;
  try {
    value = text ? JSON.parse(text) : null;
  } catch {
    // Preserve text for diagnostics.
  }
  return { status: response.status, value };
};

const expect = (condition, message, detail) => {
  if (!condition) throw new Error(`${message}: ${JSON.stringify(detail)}`);
};

for (let attempt = 0; attempt < 40; attempt += 1) {
  try {
    const probe = await request("/community_profile_follows?select=id&limit=1");
    if (probe.status === 200) break;
  } catch {
    // Schema cache may still be loading.
  }
  if (attempt === 39) throw new Error("PostgREST did not become ready");
  await new Promise((resolve) => setTimeout(resolve, 250));
}

const ids = {
  a: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  b: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
  admin: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
  inactive: "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
};
const tokens = {
  a: jwt("11111111-1111-4111-8111-111111111111"),
  b: jwt("22222222-2222-4222-8222-222222222222"),
  admin: jwt("33333333-3333-4333-8333-333333333333"),
  inactive: jwt("44444444-4444-4444-8444-444444444444"),
};

const feed = await request("/community_profile_follows?select=id&limit=10");
expect(feed.status === 200 && feed.value.length === 1, "anonymous read failed", feed);

const own = await request("/community_profile_follows", {
  token: tokens.a,
  method: "POST",
  body: { follower_profile_id: ids.a, followed_profile_id: ids.admin },
});
expect(own.status === 201 && own.value.length === 1, "own insert failed", own);

const spoof = await request("/community_profile_follows", {
  token: tokens.a,
  method: "POST",
  body: { follower_profile_id: ids.b, followed_profile_id: ids.admin },
});
expect(
  spoof.status === 403 && spoof.value?.code === "42501",
  "spoof insert was not rejected",
  spoof,
);

const anonymousInsert = await request("/community_profile_follows", {
  method: "POST",
  body: { follower_profile_id: ids.b, followed_profile_id: ids.admin },
});
expect(
  (anonymousInsert.status === 401 || anonymousInsert.status === 403) &&
    anonymousInsert.value?.code === "42501",
  "anonymous insert was not rejected",
  anonymousInsert,
);

const inactive = await request("/community_profile_follows", {
  token: tokens.inactive,
  method: "POST",
  body: { follower_profile_id: ids.inactive, followed_profile_id: ids.admin },
});
expect(
  inactive.status === 403 && inactive.value?.code === "42501",
  "inactive insert was not rejected",
  inactive,
);

const bOwn = await request("/community_profile_follows", {
  token: tokens.b,
  method: "POST",
  body: { follower_profile_id: ids.b, followed_profile_id: ids.admin },
});
expect(bOwn.status === 201 && bOwn.value.length === 1, "B own insert failed", bOwn);

const foreignDelete = await request(
  `/community_profile_follows?follower_profile_id=eq.${ids.b}&followed_profile_id=eq.${ids.admin}`,
  { token: tokens.a, method: "DELETE" },
);
expect(
  foreignDelete.status === 403 && foreignDelete.value?.code === "42501",
  "foreign delete was not rejected",
  foreignDelete,
);

const adminDelete = await request(
  `/community_profile_follows?follower_profile_id=eq.${ids.b}&followed_profile_id=eq.${ids.admin}`,
  { token: tokens.admin, method: "DELETE" },
);
expect(
  adminDelete.status === 200 && adminDelete.value.length === 1,
  "admin delete failed",
  adminDelete,
);

const counters = await request(
  `/community_profiles?select=id,followers_count,following_count&id=in.(${ids.a},${ids.admin})`,
);
expect(counters.status === 200 && counters.value.length === 2, "counter read failed", counters);
const actor = counters.value.find((row) => row.id === ids.a);
const admin = counters.value.find((row) => row.id === ids.admin);
expect(
  actor?.following_count === 2 && admin?.followers_count === 1,
  "insert trigger counters did not converge",
  counters,
);

const ownDelete = await request(
  `/community_profile_follows?follower_profile_id=eq.${ids.a}&followed_profile_id=eq.${ids.admin}`,
  { token: tokens.a, method: "DELETE" },
);
expect(
  ownDelete.status === 200 && ownDelete.value.length === 1,
  "own delete failed",
  ownDelete,
);

const countersAfter = await request(
  `/community_profiles?select=id,followers_count,following_count&id=in.(${ids.a},${ids.admin})`,
);
const actorAfter = countersAfter.value.find((row) => row.id === ids.a);
const adminAfter = countersAfter.value.find((row) => row.id === ids.admin);
expect(
  actorAfter?.following_count === 1 && adminAfter?.followers_count === 0,
  "delete trigger counters did not converge",
  countersAfter,
);

const recalculate = await request("/rpc/recalculate_profile_follow_counts", {
  token: tokens.a,
  method: "POST",
  body: { p_profile_id: ids.a },
});
expect(
  recalculate.status === 403 && recalculate.value?.code === "42501",
  "client recalculate RPC was not denied",
  recalculate,
);

console.log("COMMUNITY_PROFILE_FOLLOWS_POSTGREST_TEST_OK");
