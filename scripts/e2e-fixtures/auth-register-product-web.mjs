import { readFile, stat } from "node:fs/promises";
import path from "node:path";

const REGISTER_PATH = "/functions/v1/quata-register";
const REQUIRED_ANCHORS = Object.freeze([
  "auth.register.display-name",
  "auth.register.neighborhood",
  "auth.register.country-prefix",
  "auth.register.phone.input",
  "auth.register.password",
  "auth.register.secret-question",
  "auth.register.secret-answer",
  "auth.register.submit",
]);

export function createRegistrationWebTrial({ chromium, chrome, distribution, outputDirectory: _outputDirectory, backendUrl,
  publishableKey, registrationApiKey, turnstileSiteKey, origin = "https://egquata.com" }) {
  const root = path.resolve(distribution);
  const backendOrigin = new URL(backendUrl).origin;
  const productOrigin = new URL(origin).origin;
  let browser;
  let pending = 0;
  let uncertain = false;

  async function start() {
    browser ??= await chromium.launch({ executablePath: chrome, headless: true,
      args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"] });
  }

  async function run({ channel, input }) {
    if (channel !== "web") throw new Error("registration_web_channel_invalid");
    await start();
    const context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 },
      serviceWorkers: "block" });
    const page = await context.newPage();
    const requests = new Set();
    let exactRequests = 0;
    let requestMatches = false;
    let phase = "launch";
    await page.route(`${productOrigin}/**`, async (route) => serveProduct(route, root, {
      backendUrl, publishableKey, registrationApiKey, turnstileSiteKey,
    }));
    page.on("request", (request) => {
      const url = new URL(request.url());
      if (url.origin !== backendOrigin || url.pathname !== REGISTER_PATH || request.method() !== "POST") return;
      requests.add(request);
      pending += 1;
      exactRequests += 1;
      try {
        const body = request.postDataJSON();
        requestMatches = body?.version === 1 && body?.channel === "web" &&
          body?.display_name === input.displayName && body?.neighborhood === input.neighborhood &&
          body?.country_code === input.countryCode && body?.phone_local === input.phone &&
          body?.password === input.password && body?.secret_question === input.secretQuestion &&
          body?.secret_answer === input.secretAnswer && typeof body?.challenge_token === "string" &&
          body.challenge_token.length > 0;
      } catch { requestMatches = false; }
    });
    const settle = (request, failed) => {
      if (!requests.delete(request)) return;
      pending -= 1;
      uncertain ||= failed;
    };
    page.on("requestfinished", (request) => settle(request, false));
    page.on("requestfailed", (request) => settle(request, true));
    try {
      await page.goto(`${productOrigin}/#auth`, { waitUntil: "domcontentloaded", timeout: 60_000 });
      phase = "splash";
      await page.locator('[id="quata-splash-root"], [title="quata-splash-root"]').waitFor({ state: "hidden", timeout: 30_000 });
      phase = "open_register";
      await activate(page, "auth.register");
      phase = "display_name";
      await fill(page, "auth.register.display-name", input.displayName);
      phase = "neighborhood";
      await fill(page, "auth.register.neighborhood", input.neighborhood);
      phase = "country_prefix";
      await selectCountryPrefix(page, input.countryCode);
      phase = "phone";
      await fill(page, "auth.register.phone.input", input.phone);
      phase = "password";
      await fill(page, "auth.register.password", input.password);
      phase = "secret_question";
      await selectSecretQuestion(page, input.secretQuestion);
      phase = "secret_answer";
      await fill(page, "auth.register.secret-answer", input.secretAnswer);
      phase = "submit";
      const responsePromise = page.waitForResponse((response) => {
        const url = new URL(response.url());
        return url.origin === backendOrigin && url.pathname === REGISTER_PATH && response.request().method() === "POST";
      }, { timeout: 60_000 });
      await activate(page, "auth.register.submit");
      const response = await responsePromise;
      phase = "response";
      const body = await response.json().catch(() => null);
      const responseFailure = classifyRegistrationWebResponse({
        httpStatus: response.status(),
        accepted: isOpaqueAcceptedRegistrationResponse(body),
        exactRequests,
        requestMatches,
      });
      if (responseFailure) throw new Error(responseFailure);
      await page.waitForFunction(() => localStorage.getItem("quata_web_access_token") &&
        document.documentElement.getAttribute("data-quata-shell-route") === "feed", null, { timeout: 30_000 });
      return { passed: true, exactSubmits: 1, authenticatedTransition: true, anchors: REQUIRED_ANCHORS };
    } catch (error) {
      if (String(error?.message ?? "").startsWith("registration_web_response_unverified_")) throw error;
      throw new Error(`registration_web_${phase}_failed`);
    } finally {
      await context.close().catch(() => {});
      if (requests.size) { uncertain = true; pending -= requests.size; requests.clear(); }
    }
  }

  return Object.freeze({
    run,
    operationsSettled: () => pending === 0 && !uncertain,
    async close() { await browser?.close().catch(() => {}); browser = undefined; },
  });
}

export function classifyRegistrationWebResponse({ httpStatus, accepted, exactRequests, requestMatches }) {
  const failures = [];
  if (httpStatus !== 202) failures.push(`http-${Number.isInteger(httpStatus) ? httpStatus : "unknown"}`);
  if (accepted !== true) failures.push("accepted-false");
  if (exactRequests !== 1) failures.push(`request-count-${Number.isInteger(exactRequests) ? exactRequests : "unknown"}`);
  if (requestMatches !== true) failures.push("payload-mismatch");
  return failures.length ? `registration_web_response_unverified_${failures.join("_")}` : null;
}

export function isOpaqueAcceptedRegistrationResponse(body) {
  return body?.version === 1 && body?.status === "accepted" &&
    Object.keys(body).sort().join(",") === "status,version";
}

async function serveProduct(route, root, configuration) {
  const request = route.request();
  if (!['GET', 'HEAD'].includes(request.method())) return route.fallback();
  const pathname = decodeURIComponent(new URL(request.url()).pathname);
  const file = path.resolve(root, `.${pathname === "/" ? "/index.html" : pathname}`);
  if (!file.startsWith(`${root}${path.sep}`) || !(await stat(file).catch(() => null))?.isFile()) {
    return route.fulfill({ status: 404, body: "" });
  }
  let body = await readFile(file);
  if (file.endsWith("index.html")) {
    body = body.toString("utf8")
      .replace(/(<meta name="quata-supabase-url" content=")[^"]*(">)/, `$1${escapeHtml(configuration.backendUrl)}$2`)
      .replace(/(<meta name="quata-supabase-publishable-key" content=")[^"]*(">)/,
        `$1${escapeHtml(configuration.publishableKey)}$2`)
      .replace(/(<meta name="quata-web-registration-enabled" content=")[^"]*(">)/, "$1true$2")
      .replace(/(<meta name="quata-web-registration-api-key" content=")[^"]*(">)/,
        `$1${escapeHtml(configuration.registrationApiKey)}$2`)
      .replace(/(<meta name="quata-turnstile-site-key" content=")[^"]*(">)/,
        `$1${escapeHtml(configuration.turnstileSiteKey)}$2`);
  }
  return route.fulfill({ status: 200, body, contentType: mime(file), headers: { "Cache-Control": "no-store" } });
}

async function activate(page, value) {
  const control = await visible(page, value);
  const box = await control.boundingBox();
  if (!box) throw new Error(`registration_web_bounds_missing:${value}`);
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
}

async function fill(page, value, text) {
  const root = await visible(page, value);
  const direct = await root.evaluate((element) => ["INPUT", "TEXTAREA"].includes(element.tagName)).catch(() => false);
  const input = direct ? root : root.locator("input, textarea").first();
  if (await input.isVisible({ timeout: 500 }).catch(() => false)) await input.fill(text);
  else {
    const box = await root.boundingBox();
    if (!box) throw new Error(`registration_web_input_bounds_missing:${value}`);
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await page.keyboard.press("Control+A");
    await page.keyboard.insertText(text);
  }
}

async function selectCountryPrefix(page, countryCode) {
  if (countryCode === "240") return;
  await activate(page, "auth.register.country-prefix");
  await fill(page, "auth.register.country-prefix.search", countryCode);
  await activate(page, `auth.register.country-prefix.option.${countryCode}`);
}

async function selectSecretQuestion(page, value) {
  await activate(page, "auth.register.secret-question");
  await activate(page, `auth.register.secret-question.option.${value}`);
}

async function visible(page, value) {
  const selector = `[id=${JSON.stringify(value)}], [title=${JSON.stringify(value)}], [aria-label=${JSON.stringify(value)}], [aria-label*=${JSON.stringify(value)}]`;
  const control = page.locator(selector).first();
  await control.waitFor({ state: "visible", timeout: 20_000 });
  return control;
}

const escapeHtml = (value) => String(value).replaceAll("&", "&amp;").replaceAll('"', "&quot;");
const mime = (file) => ({ ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript",
  ".wasm": "application/wasm", ".json": "application/json", ".css": "text/css", ".png": "image/png",
  ".svg": "image/svg+xml", ".ttf": "font/ttf", ".woff2": "font/woff2" })[path.extname(file)] ?? "application/octet-stream";
