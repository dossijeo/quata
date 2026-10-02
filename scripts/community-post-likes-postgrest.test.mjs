import { createHmac } from "node:crypto";

const [baseUrl, jwtSecret] = process.argv.slice(2);
if (!baseUrl || !jwtSecret) throw new Error("base URL and JWT secret are required");

const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
const jwt = (authUserId) => {
  const header = encode({ alg: "HS256", typ: "JWT" });
  const payload = encode({
    sub: authUserId,
    role: "authenticated",
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
    // Preserve response text for a bounded diagnostic on failure.
  }
  return { status: response.status, value };
};

const expect = (condition, message, detail) => {
  if (!condition) throw new Error(`${message}: ${JSON.stringify(detail)}`);
};

for (let attempt = 0; attempt < 40; attempt += 1) {
  try {
    const probe = await request("/community_post_likes?select=id&limit=1");
    if (probe.status === 200) break;
  } catch {
    // PostgREST may still be loading its schema cache.
  }
  if (attempt === 39) throw new Error("PostgREST did not become ready");
  await new Promise((resolve) => setTimeout(resolve, 250));
}

const ids = {
  actorA: "00000000-0000-4000-8000-000000000001",
  actorB: "00000000-0000-4000-8000-000000000002",
  actorInactive: "00000000-0000-4000-8000-000000000003",
  authA: "20000000-0000-4000-8000-000000000001",
  authB: "20000000-0000-4000-8000-000000000002",
  authInactive: "20000000-0000-4000-8000-000000000003",
  postA: "10000000-0000-4000-8000-000000000001",
  postB: "10000000-0000-4000-8000-000000000002",
  postSpoof: "10000000-0000-4000-8000-000000000003",
};
const tokenA = jwt(ids.authA);
const tokenB = jwt(ids.authB);
const tokenInactive = jwt(ids.authInactive);

const publicBefore = await request("/community_post_likes?select=post_id,profile_id");
expect(publicBefore.status === 200 && publicBefore.value.length === 0, "anonymous read failed", publicBefore);

const anonymousInsert = await request("/community_post_likes", {
  method: "POST",
  body: { post_id: ids.postSpoof, profile_id: ids.actorA },
});
expect(
  (anonymousInsert.status === 401 || anonymousInsert.status === 403) &&
    anonymousInsert.value?.code === "42501",
  "anonymous insert was not rejected",
  anonymousInsert,
);

const ownA = await request("/community_post_likes", {
  token: tokenA,
  method: "POST",
  body: { post_id: ids.postA, profile_id: ids.actorA },
});
expect(ownA.status === 201 && ownA.value.length === 1, "actor A own insert failed", ownA);

const spoof = await request("/community_post_likes", {
  token: tokenA,
  method: "POST",
  body: { post_id: ids.postSpoof, profile_id: ids.actorB },
});
expect(
  spoof.status === 403 && spoof.value?.code === "42501",
  "cross-actor insert was not rejected",
  spoof,
);

const inactive = await request("/community_post_likes", {
  token: tokenInactive,
  method: "POST",
  body: { post_id: ids.postSpoof, profile_id: ids.actorInactive },
});
expect(
  inactive.status === 403 && inactive.value?.code === "42501",
  "inactive mapped actor was not rejected",
  inactive,
);

const ownB = await request("/community_post_likes", {
  token: tokenB,
  method: "POST",
  body: { post_id: ids.postB, profile_id: ids.actorB },
});
expect(ownB.status === 201 && ownB.value.length === 1, "actor B own insert failed", ownB);

const crossDelete = await request(
  `/community_post_likes?post_id=eq.${ids.postB}&profile_id=eq.${ids.actorB}`,
  { token: tokenA, method: "DELETE" },
);
expect(
  crossDelete.status === 200 && crossDelete.value.length === 0,
  "cross-actor delete was not filtered by RLS",
  crossDelete,
);

const publicAfterCrossDelete = await request("/community_post_likes?select=post_id,profile_id&order=post_id.asc");
expect(
  publicAfterCrossDelete.status === 200 && publicAfterCrossDelete.value.length === 2,
  "cross-actor delete changed public state",
  publicAfterCrossDelete,
);

const anonymousDelete = await request(
  `/community_post_likes?post_id=eq.${ids.postA}`,
  { method: "DELETE" },
);
expect(
  (anonymousDelete.status === 401 || anonymousDelete.status === 403) &&
    anonymousDelete.value?.code === "42501",
  "anonymous delete was not rejected",
  anonymousDelete,
);

const deleteA = await request(
  `/community_post_likes?post_id=eq.${ids.postA}&profile_id=eq.${ids.actorA}`,
  { token: tokenA, method: "DELETE" },
);
expect(deleteA.status === 200 && deleteA.value.length === 1, "actor A own delete failed", deleteA);

const deleteB = await request(
  `/community_post_likes?post_id=eq.${ids.postB}&profile_id=eq.${ids.actorB}`,
  { token: tokenB, method: "DELETE" },
);
expect(deleteB.status === 200 && deleteB.value.length === 1, "actor B own delete failed", deleteB);

const publicFinal = await request("/community_post_likes?select=id");
expect(publicFinal.status === 200 && publicFinal.value.length === 0, "PostgREST fixture residue remained", publicFinal);

console.log("COMMUNITY_POST_LIKES_POSTGREST_TEST_OK");
