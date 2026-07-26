import { createHmac } from "node:crypto";

const [baseUrl, jwtSecret] = process.argv.slice(2);
if (!baseUrl || !jwtSecret) {
  throw new Error("Usage: node community-profiles-postgrest.test.mjs <base-url> <jwt-secret>");
}

const encode = (value) =>
  Buffer.from(JSON.stringify(value))
    .toString("base64url");

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
    // Keep the response text for a useful assertion error.
  }
  return { status: response.status, value };
};

const expect = (condition, message, detail) => {
  if (!condition) {
    throw new Error(`${message}: ${JSON.stringify(detail)}`);
  }
};

for (let attempt = 0; attempt < 40; attempt += 1) {
  try {
    const probe = await request("/community_profiles?select=id&limit=1");
    if (probe.status === 200) break;
  } catch {
    // PostgREST may still be loading its schema cache.
  }
  if (attempt === 39) throw new Error("PostgREST did not become ready");
  await new Promise((resolve) => setTimeout(resolve, 250));
}

const actorToken = jwt("11111111-1111-4111-8111-111111111111");

const feed = await request(
  "/community_profiles?select=id,display_name&display_name=eq.Actor%20A%20edited",
);
expect(feed.status === 200 && feed.value.length === 1, "anonymous feed read failed", feed);

const ownUpdate = await request(
  "/community_profiles?id=eq.aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  {
    token: actorToken,
    method: "PATCH",
    body: { display_name: "Actor A via PostgREST" },
  },
);
expect(
  ownUpdate.status === 200 &&
    ownUpdate.value.length === 1 &&
    ownUpdate.value[0].display_name === "Actor A via PostgREST",
  "own PostgREST update failed",
  ownUpdate,
);

const impersonation = await request(
  "/community_profiles?id=eq.bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
  {
    token: actorToken,
    method: "PATCH",
    body: { display_name: "Impersonated via PostgREST" },
  },
);
expect(
  impersonation.status === 200 &&
    Array.isArray(impersonation.value) &&
    impersonation.value.length === 0,
  "outsider PostgREST update was not filtered",
  impersonation,
);

const escalation = await request(
  "/community_profiles?id=eq.aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  {
    token: actorToken,
    method: "PATCH",
    body: { is_admin: true },
  },
);
expect(
  escalation.status === 403 && escalation.value?.code === "42501",
  "admin escalation did not fail with 42501",
  escalation,
);

const anonymousUpdate = await request(
  "/community_profiles?id=eq.aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  {
    method: "PATCH",
    body: { display_name: "Anonymous rewrite" },
  },
);
expect(
  anonymousUpdate.status === 401 || anonymousUpdate.status === 403,
  "anonymous PostgREST update unexpectedly succeeded",
  anonymousUpdate,
);

const chosenId = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
const registration = await request("/community_profiles", {
  method: "POST",
  body: {
    id: chosenId,
    display_name: "PostgREST registration",
    phone: "+34555",
    pass_hash: "hash-e",
    phone_normalized: "555",
    phone_local: "555",
  },
});
expect(
  registration.status === 201 &&
    registration.value.length === 1 &&
    registration.value[0].id !== chosenId,
  "anonymous registration did not receive a server id",
  registration,
);

const maliciousRegistration = await request("/community_profiles", {
  method: "POST",
  body: {
    display_name: "PostgREST escalation",
    phone: "+34666",
    pass_hash: "hash-f",
    phone_normalized: "666",
    phone_local: "666",
    is_admin: true,
  },
});
expect(
  (maliciousRegistration.status === 401 ||
    maliciousRegistration.status === 403) &&
    maliciousRegistration.value?.code === "42501",
  "anonymous insert escalation did not fail with 42501",
  maliciousRegistration,
);

for (const [label, forbidden] of [
  ["auth identity", { auth_user_id: "99999999-9999-4999-8999-999999999999" }],
  ["lifecycle", { account_status: "deactivated" }],
  ["official role", { is_official: true }],
  ["counter", { followers_count: 1000000 }],
]) {
  const response = await request("/community_profiles", {
    method: "POST",
    body: {
      display_name: `PostgREST ${label}`,
      phone: "+34667",
      pass_hash: "hash-g",
      phone_normalized: "667",
      phone_local: "667",
      ...forbidden,
    },
  });
  expect(
    (response.status === 401 || response.status === 403) &&
      response.value?.code === "42501",
    `anonymous ${label} insert did not fail with 42501`,
    response,
  );
}

console.log("COMMUNITY_PROFILES_POSTGREST_TEST_OK");
