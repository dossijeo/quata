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

export function createRegistrationWebTrial({ chromium, chrome, distribution, outputDirectory, backendUrl,
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
      await page.locator('[id="quata-splash-root"], [title="quata-splash-root"]').waitFor({ state: "hidden", timeout: 30_000 });
      await activate(page, "auth.register");
      await fill(page, "auth.register.display-name", input.displayName);
      await fill(page, "auth.register.neighborhood", input.neighborhood);
      await selectCountryPrefix(page, input.countryCode);
      await fill(page, "auth.register.phone.input", input.phone);
      await fill(page, "auth.register.password", input.password);
      await selectSecretQuestion(page, input.secretQuestion);
      await fill(page, "auth.register.secret-answer", input.secretAnswer);
      const responsePromise = page.waitForResponse((response) => {
        const url = new URL(response.url());
        return url.origin === backendOrigin && url.pathname === REGISTER_PATH && response.request().method() === "POST";
      }, { timeout: 60_000 });
      await activate(page, "auth.register.submit");
      const response = await responsePromise;
      const body = await response.json().catch(() => null);
      if (response.status() !== 202 || body?.accepted !== true || exactRequests !== 1 || !requestMatches) {
        throw new Error("registration_web_product_request_unverified");
      }
      await page.waitForFunction(() => localStorage.getItem("quata_web_access_token") &&
        document.documentElement.getAttribute("data-quata-shell-route") === "feed", null, { timeout: 30_000 });
      return { passed: true, exactSubmits: 1, authenticatedTransition: true, anchors: REQUIRED_ANCHORS };
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
  const option = page.getByText(new RegExp(`\\+${escapeRegex(countryCode)}(?:\\D|$)`)).first();
  await option.waitFor({ state: "visible", timeout: 10_000 });
  await option.click();
}

async function selectSecretQuestion(page, value) {
  await activate(page, "auth.register.secret-question");
  const labels = { madre: /madre|mother|mère/i, barrio: /barrio|neighborhood|quartier/i,
    amigo: /amigo|friend|ami/i, comida: /comida|food|plat/i };
  const option = page.getByText(labels[value] ?? new RegExp(escapeRegex(value), "i")).first();
  await option.waitFor({ state: "visible", timeout: 10_000 });
  await option.click();
}

async function visible(page, value) {
  const selector = `[id=${JSON.stringify(value)}], [title=${JSON.stringify(value)}], [aria-label=${JSON.stringify(value)}], [aria-label*=${JSON.stringify(value)}]`;
  const control = page.locator(selector).first();
  await control.waitFor({ state: "visible", timeout: 20_000 });
  return control;
}

const escapeHtml = (value) => String(value).replaceAll("&", "&amp;").replaceAll('"', "&quot;");
const escapeRegex = (value) => String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const mime = (file) => ({ ".html": "text/html", ".js": "text/javascript", ".mjs": "text/javascript",
  ".wasm": "application/wasm", ".json": "application/json", ".css": "text/css", ".png": "image/png",
  ".svg": "image/svg+xml", ".ttf": "font/ttf", ".woff2": "font/woff2" })[path.extname(file)] ?? "application/octet-stream";
