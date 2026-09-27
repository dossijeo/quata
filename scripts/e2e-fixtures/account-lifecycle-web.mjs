import { createServer } from "node:http";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import path from "node:path";

const lifecyclePath = "/functions/v1/quata-account-lifecycle";

export function createAccountLifecycleWebTrial({ chromium, chrome, distribution, outputDirectory, backendUrl, publicKey }) {
  const root = path.resolve(distribution);
  const output = path.resolve(outputDirectory);
  const backendOrigin = new URL(backendUrl).origin;
  let server;
  let browser;
  let pending = 0;
  let uncertain = false;

  async function start() {
    if (server) return `http://127.0.0.1:${server.address().port}`;
    await mkdir(output, { recursive: true });
    const mime = new Map([[".html", "text/html"], [".js", "text/javascript"], [".mjs", "text/javascript"],
      [".wasm", "application/wasm"], [".json", "application/json"], [".css", "text/css"],
      [".png", "image/png"], [".svg", "image/svg+xml"], [".ttf", "font/ttf"], [".woff2", "font/woff2"]]);
    const attr = (value) => value.replaceAll("&", "&amp;").replaceAll('"', "&quot;");
    server = createServer(async (request, response) => {
      try {
        if (!["GET", "HEAD"].includes(request.method)) return response.writeHead(405).end();
        const pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
        const file = path.resolve(root, `.${pathname === "/" ? "/index.html" : pathname}`);
        if (!file.startsWith(`${root}${path.sep}`) || !(await stat(file).catch(() => null))?.isFile()) {
          return response.writeHead(404).end();
        }
        let bytes = await readFile(file);
        if (file.endsWith("index.html")) bytes = bytes.toString()
          .replace(/(<meta name="quata-supabase-url" content=")[^"]*(">)/, (_, before, after) => before + attr(backendUrl) + after)
          .replace(/(<meta name="quata-supabase-publishable-key" content=")[^"]*(">)/,
            (_, before, after) => before + attr(publicKey) + after);
        response.writeHead(200, { "Content-Type": mime.get(path.extname(file)) ?? "application/octet-stream",
          "Cache-Control": "no-store", "Cross-Origin-Opener-Policy": "same-origin",
          "Cross-Origin-Embedder-Policy": "require-corp" });
        response.end(request.method === "HEAD" ? undefined : bytes);
      } catch { response.writeHead(500).end(); }
    });
    await new Promise((resolve, reject) => { server.once("error", reject); server.listen(0, "127.0.0.1", resolve); });
    browser = await chromium.launch({ executablePath: chrome, headless: true,
      args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"] });
    return `http://127.0.0.1:${server.address().port}`;
  }

  async function run({ action, session, password, clientInstanceId }) {
    if (!["deactivate", "delete"].includes(action) || session?.authUserId == null || session?.profileId == null ||
        typeof password !== "string" || password.length < 20) throw new Error("account_lifecycle_web_input_invalid");
    const origin = await start();
    const context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, serviceWorkers: "block" });
    const requests = new Set();
    let matchingRequests = 0;
    let requestVerified = false;
    await context.addInitScript((state) => {
      localStorage.setItem("quata_web_access_token", state.accessToken);
      localStorage.setItem("quata_web_refresh_token", state.refreshToken);
      localStorage.setItem("quata_web_session_token", state.webSessionToken);
      localStorage.setItem("quata_web_user_id", state.authUserId);
      localStorage.setItem("quata_web_expires_at", String(state.expiresAt));
      localStorage.setItem("web.auth.session_ready", "true");
      localStorage.setItem("quata_web_client_instance_id", state.clientInstanceId);
    }, { ...session, clientInstanceId });
    const page = await context.newPage();
    page.on("request", (request) => {
      const url = new URL(request.url());
      if (url.origin !== backendOrigin || url.pathname !== lifecyclePath || request.method() !== "POST") return;
      pending += 1;
      requests.add(request);
      matchingRequests += 1;
      try {
        const body = request.postDataJSON();
        requestVerified = body?.action === action && body?.password === password &&
          request.headers().authorization === `Bearer ${session.accessToken}`;
      } catch { requestVerified = false; }
    });
    page.on("requestfinished", (request) => { if (requests.delete(request)) pending -= 1; });
    page.on("requestfailed", (request) => { if (requests.delete(request)) { pending -= 1; uncertain = true; } });
    try {
      await page.goto(`${origin}/?quata-auth-e2e=1#settings`, { waitUntil: "domcontentloaded", timeout: 60_000 });
      await page.locator('[id="quata-splash-root"], [title="quata-splash-root"]').waitFor({ state: "hidden", timeout: 30_000 });
      await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-shell-route") === "settings", null,
        { timeout: 30_000 });
      await passUgcTermsGate(page);
      await page.keyboard.press("End");
      await page.waitForTimeout(400);
      const deleting = action === "delete";
      const openTag = deleting ? "settings-account-lifecycle-delete" : "settings-account-lifecycle-deactivate";
      const openLabel = deleting ? "Eliminar datos de la cuenta" : "Desactivar cuenta";
      const open = await firstVisible([
        page.getByText(openLabel, { exact: true }).first(),
        page.getByRole("button", { name: openLabel, exact: true }).first(),
        tag(page, openTag),
      ]);
      await open.scrollIntoViewIfNeeded();
      await activateObservedControl(page, open, openTag);
      const dialog = await firstVisible([tag(page, "account-lifecycle.dialog"), page.getByRole("dialog").first()]);
      await fillProductInput(page, dialog, "account-lifecycle.password", password, { password: true });
      if (deleting) await fillProductInput(page, dialog, "account-lifecycle.confirmation", "ELIMINAR", { index: 1 });
      const confirm = await firstVisible([
        page.getByText(deleting ? "Eliminar" : "Desactivar", { exact: true }).first(),
        page.getByRole("button", { name: deleting ? "Eliminar" : "Desactivar", exact: true }).first(),
        tag(page, "account-lifecycle.confirm"),
      ]);
      await waitForEnabled(confirm, "account-lifecycle.confirm");
      await page.screenshot({ path: path.join(output, `web-account-${action}-confirmed.png`), fullPage: true });
      const responsePromise = page.waitForResponse((response) => {
        const url = new URL(response.url());
        return url.origin === backendOrigin && url.pathname === lifecyclePath && response.request().method() === "POST";
      }, { timeout: 25_000 });
      await activateObservedControl(page, confirm, "account-lifecycle.confirm");
      const response = await responsePromise;
      const body = await response.json().catch(() => null);
      if (response.status() !== 200 || body?.ok !== true || body?.action !== action || matchingRequests !== 1 || !requestVerified) {
        throw new Error("account_lifecycle_web_product_request_unverified");
      }
      await page.waitForFunction(() => !localStorage.getItem("quata_web_access_token") &&
        document.documentElement.getAttribute("data-quata-shell-route") === "feed", null, { timeout: 20_000 });
      await page.screenshot({ path: path.join(output, `web-account-${action}-logged-out.png`), fullPage: true });
      return { passed: true, action, exactRequests: 1, productSessionCleared: true,
        anchors: [openTag, "account-lifecycle.dialog", "account-lifecycle.password",
          ...(action === "delete" ? ["account-lifecycle.confirmation"] : []), "account-lifecycle.confirm"] };
    } catch (error) {
      const prefix = `web-account-${action}-failure`;
      await page.screenshot({ path: path.join(output, `${prefix}.png`), fullPage: true }).catch(() => {});
      const candidates = await page.evaluate(() => [...document.querySelectorAll("button, [role], [aria-label], [title], input, textarea")]
        .map((element) => {
          const rect = element.getBoundingClientRect();
          return { tag: element.tagName, role: element.getAttribute("role"), id: element.id || null,
            ariaLabel: element.getAttribute("aria-label"), title: element.getAttribute("title"),
            text: (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 100),
            visible: rect.width > 0 && rect.height > 0,
            bounds: { x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width), height: Math.round(rect.height) } };
        }));
      await writeFile(path.join(output, `${prefix}-candidates.json`), `${JSON.stringify(candidates, null, 2)}\n`, { mode: 0o600 })
        .catch(() => {});
      throw error;
    } finally {
      await context.close().catch(() => {});
      if (requests.size) { uncertain = true; pending -= requests.size; requests.clear(); }
    }
  }

  return Object.freeze({
    run,
    operationsSettled: () => pending === 0 && !uncertain,
    async close() {
      await browser?.close().catch(() => {});
      await new Promise((resolve) => server?.close(resolve) ?? resolve());
      browser = undefined;
      server = undefined;
    },
  });
}

async function passUgcTermsGate(page) {
  await page.waitForFunction(() => ["accepted", "required"].includes(
    document.documentElement.getAttribute("data-quata-ugc-terms-state")), null, { timeout: 30_000 });
  const state = await page.evaluate(() => document.documentElement.getAttribute("data-quata-ugc-terms-state"));
  if (state === "required") {
    const accept = page.getByRole("button", { name: /Acepto|I accept|J'accepte/i }).first();
    if (await accept.isVisible({ timeout: 2_000 }).catch(() => false)) await accept.click({ timeout: 5_000 });
    else {
      await page.evaluate(async () => {
        const bridge = globalThis.__quataUgcTermsE2eProduct;
        if (bridge?.version !== 1) throw new Error("account_lifecycle_terms_bridge_missing");
        await bridge.accept();
      });
      // The localhost bridge invokes the real gateway but cannot mutate the
      // dialog's private Compose state. Reload and require the persisted
      // acceptance to be read through hasAcceptedTerms before continuing.
      await page.reload({ waitUntil: "domcontentloaded", timeout: 60_000 });
      await page.locator('[id="quata-splash-root"], [title="quata-splash-root"]').waitFor({ state: "hidden", timeout: 30_000 });
      await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-shell-route") === "settings",
        null, { timeout: 30_000 });
    }
  }
  await page.waitForFunction(() => document.documentElement.getAttribute("data-quata-ugc-terms-state") === "accepted",
    null, { timeout: 20_000 });
}

function tag(page, value) {
  const escaped = JSON.stringify(value);
  return page.locator(`[id=${escaped}], [title=${escaped}], [aria-label=${escaped}], [aria-label*=${escaped}]`).first();
}

async function firstVisible(candidates) {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      if (await candidate.isVisible().catch(() => false)) return candidate;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("account_lifecycle_web_accessible_control_missing");
}

async function activateObservedControl(page, control, anchor) {
  const box = await control.boundingBox().catch(() => null);
  if (!box || box.width < 1 || box.height < 1) {
    throw new Error(`account_lifecycle_web_control_bounds_missing:${anchor}`);
  }
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
}

async function fillProductInput(page, dialog, value, text, { password = false, index = 0 } = {}) {
  const tagged = tag(page, value);
  const semantic = password ? dialog.locator('input[type="password"]').first() : dialog.locator("input, textarea").nth(index);
  const root = await firstVisible([tagged, semantic]);
  const directInput = await root.evaluate((element) => ["INPUT", "TEXTAREA"].includes(element.tagName)).catch(() => false);
  const input = directInput ? root : root.locator("input, textarea").first();
  if (await input.isVisible({ timeout: 500 }).catch(() => false)) {
    await input.fill(text);
    if (await input.inputValue() !== text) throw new Error(`account_lifecycle_web_input_failed:${value}`);
    return;
  }
  const box = await root.boundingBox().catch(() => null);
  if (!box || box.width < 1 || box.height < 1) throw new Error(`account_lifecycle_web_input_bounds_missing:${value}`);
  await page.mouse.click(box.x + (box.width / 2), box.y + (box.height / 2));
  await page.keyboard.press("Control+A");
  await page.keyboard.insertText(text);
}

async function waitForEnabled(control, anchor) {
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    if (await control.isEnabled().catch(() => false)) return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`account_lifecycle_web_control_disabled:${anchor}`);
}
