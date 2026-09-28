import assert from "node:assert/strict";
import test from "node:test";
import { acquireTurnstileToken } from "./e2e-fixtures/turnstile-browser-token.mjs";

function browserFixture({ redirected = false, result = "token-value-with-enough-characters" } = {}) {
  const calls = [];
  const page = {
    async goto(url) { calls.push(["goto", url]); },
    url() { return redirected ? "https://unexpected.example/" : "https://register.quata.app/turnstile"; },
    async evaluate(_callback, input) { calls.push(["evaluate", input]); return result; },
  };
  const browser = {
    async newPage() { return page; },
    async close() { calls.push(["close"]); },
  };
  return { calls, launch: async (options) => { calls.push(["launch", options]); return browser; } };
}

test("collector binds an allowed registration action to the configured HTTPS hostname", async () => {
  const fixture = browserFixture();
  const token = await acquireTurnstileToken({
    siteKey: "public-site-key",
    action: "register_ios",
    pageUrl: "https://register.quata.app/turnstile",
    executablePath: "browser",
    launch: fixture.launch,
  });
  assert.equal(token, "token-value-with-enough-characters");
  assert.deepEqual(fixture.calls[0], ["launch", { headless: true, executablePath: "browser" }]);
  assert.deepEqual(fixture.calls[2][1], {
    widgetSiteKey: "public-site-key",
    widgetAction: "register_ios",
    waitMs: 45_000,
  });
  assert.deepEqual(fixture.calls.at(-1), ["close"]);
});

test("collector rejects HTTP, unrecognized actions and hostname redirects before returning a token", async () => {
  await assert.rejects(
    acquireTurnstileToken({ siteKey: "key", action: "register_web", pageUrl: "http://register.quata.app" }),
    /turnstile_https_origin_required/,
  );
  await assert.rejects(
    acquireTurnstileToken({ siteKey: "key", action: "login", pageUrl: "https://register.quata.app" }),
    /turnstile_action_not_allowed/,
  );
  const fixture = browserFixture({ redirected: true });
  await assert.rejects(
    acquireTurnstileToken({
      siteKey: "key",
      action: "register_android",
      pageUrl: "https://register.quata.app/turnstile",
      launch: fixture.launch,
    }),
    /turnstile_origin_redirected/,
  );
  assert.deepEqual(fixture.calls.at(-1), ["close"]);
});
