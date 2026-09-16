import assert from "node:assert/strict";
import { test } from "node:test";
import { createApnsProvider } from "./apns-provider.mjs";

const key = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
const pem = `-----BEGIN PRIVATE KEY-----\n${Buffer.from(await crypto.subtle.exportKey("pkcs8", key.privateKey)).toString("base64")}\n-----END PRIVATE KEY-----`;
const config = { keyId: "ABCDEFGHIJ", teamId: "0123456789", topic: "com.quata.ios", environment: "sandbox", privateKeyPem: pem };
const message = { token: "ab".repeat(32), payload: { aps: { alert: { title: "Qüata", body: "Mensaje" } }, thread_id: "7", message_id: "9" } };

test("APNs request has verifiable ES256 JWT, exact destination, payload and bounded delivery", async () => {
  let captured;
  const provider = createApnsProvider(config, { now: () => 1_800_000_000_000, transport: async (url, init) => {
    captured = { url, init };
    return new Response(null, { status: 200 });
  } });
  assert.deepEqual(await provider.send(message), { ok: true });
  assert.equal(captured.url, `https://api.sandbox.push.apple.com/3/device/${message.token}`);
  assert.equal(captured.init.redirect, "error");
  assert.equal(captured.init.headers["apns-topic"], "com.quata.ios");
  assert.equal(captured.init.headers["apns-push-type"], "alert");
  assert.equal(captured.init.headers["apns-expiration"], "0");
  assert.deepEqual(JSON.parse(captured.init.body), message.payload);
  const jwt = captured.init.headers.authorization.slice(7).split(".");
  assert.deepEqual(JSON.parse(Buffer.from(jwt[0], "base64url")), { alg: "ES256", kid: config.keyId });
  assert.deepEqual(JSON.parse(Buffer.from(jwt[1], "base64url")), { iss: config.teamId, iat: 1_800_000_000 });
  assert.equal(await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key.publicKey, Buffer.from(jwt[2], "base64url"), new TextEncoder().encode(jwt.slice(0, 2).join("."))), true);
});

test("provider token is reused, renewed at 50 minutes and reset after clock reversal", async () => {
  let clock = 1_800_000_000_000;
  const jwts = [];
  const provider = createApnsProvider(config, { now: () => clock, transport: async (_url, init) => {
    jwts.push(init.headers.authorization); return new Response(null, { status: 200 });
  } });
  await provider.send(message);
  clock += 2999_000;
  await provider.send(message);
  assert.equal(jwts[0], jwts[1]);
  clock += 1000;
  await provider.send(message);
  assert.notEqual(jwts[1], jwts[2]);
  clock -= 1000;
  await provider.send(message);
  assert.notEqual(jwts[2], jwts[3]);
});

test("production is explicit; invalid environment never guesses or sends", async () => {
  assert.throws(() => createApnsProvider({ ...config, environment: "auto" }, { transport: fetch }), /apns_configuration_invalid/);
  const provider = createApnsProvider({ ...config, environment: "production" }, { transport: async (url) => {
    assert.equal(new URL(url).host, "api.push.apple.com"); return new Response(null, { status: 200 });
  } });
  assert.deepEqual(await provider.send(message), { ok: true });
});

test("only timestamped Unregistered gives a retirement cutoff; provider errors remain sanitized", async () => {
  for (const [status, body, expected] of [
    [410, { reason: "Unregistered", timestamp: 1800000000000 }, { ok: false, code: "apns_410_Unregistered", unregisteredAt: 1800000000000 }],
    [410, { reason: "Unregistered" }, { ok: false, code: "apns_410_Unregistered" }],
    [400, { reason: "BadDeviceToken" }, { ok: false, code: "apns_400_BadDeviceToken" }],
    [403, { reason: "InvalidProviderToken" }, { ok: false, code: "apns_403_InvalidProviderToken" }],
    [500, { reason: `secret ${pem}` }, { ok: false, code: "apns_500_unknown" }],
  ]) {
    const provider = createApnsProvider(config, { transport: async () => Response.json(body, { status }) });
    assert.deepEqual(await provider.send(message), expected);
  }
});

test("invalid token, UTF-8 oversized payload and malformed signing key never reach transport", async () => {
  let calls = 0;
  const transport = async () => { calls++; throw new Error("must not send"); };
  const provider = createApnsProvider(config, { transport });
  assert.equal((await provider.send({ ...message, token: "abc" })).code, "apns_device_token_invalid");
  assert.equal((await provider.send({ ...message, payload: { aps: { alert: "ü".repeat(2100) } } })).code, "apns_payload_too_large");
  assert.equal((await createApnsProvider({ ...config, privateKeyPem: "secret-invalid" }, { transport }).send(message)).code, "apns_signing_failed");
  assert.equal(calls, 0);
});

test("transport failures cannot leak tokens, provider JWTs or private data", async () => {
  const provider = createApnsProvider(config, { transport: async () => { throw new Error(`private ${pem} ${message.token}`); } });
  assert.deepEqual(await provider.send(message), { ok: false, code: "apns_transport_failed" });
});

test("concurrent sends share one signature on initial registration and renewal", async () => {
  let signCalls = 0;
  let clock = 1_800_000_000_000;
  const jwts = [];
  const provider = createApnsProvider(config, {
    now: () => clock,
    cryptoProvider: { subtle: {
      importKey: (...args) => crypto.subtle.importKey(...args),
      sign: async (...args) => { signCalls++; return crypto.subtle.sign(...args); },
    } },
    transport: async (_url, init) => { jwts.push(init.headers.authorization); return new Response(null, { status: 200 }); },
  });
  await Promise.all(Array.from({ length: 10 }, () => provider.send(message)));
  assert.equal(signCalls, 1);
  assert.equal(new Set(jwts).size, 1);
  clock += 3000_000;
  await Promise.all(Array.from({ length: 10 }, () => provider.send(message)));
  assert.equal(signCalls, 2);
  assert.equal(new Set(jwts.slice(10)).size, 1);
  assert.notEqual(jwts[0], jwts[10]);
});

test("late rejection of an old JWT cannot evict a newer provider token", async () => {
  let clock = 1_800_000_000_000;
  let releaseOld;
  let oldRequestStarted;
  const started = new Promise((resolve) => { oldRequestStarted = resolve; });
  const jwts = [];
  const provider = createApnsProvider(config, {
    now: () => clock,
    transport: async (_url, init) => {
      jwts.push(init.headers.authorization);
      if (jwts.length === 1) {
        oldRequestStarted();
        return new Promise((resolve) => { releaseOld = resolve; });
      }
      return new Response(null, { status: 200 });
    },
  });
  const oldSend = provider.send(message);
  await started;
  clock += 3000_000;
  await provider.send(message);
  releaseOld(Response.json({ reason: "ExpiredProviderToken" }, { status: 403 }));
  assert.equal((await oldSend).code, "apns_403_ExpiredProviderToken");
  await provider.send(message);
  assert.notEqual(jwts[0], jwts[1]);
  assert.equal(jwts[1], jwts[2]);
});
