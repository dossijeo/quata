import { chromium } from "playwright-core";

const DEFAULT_TIMEOUT_MS = 45_000;

export async function acquireTurnstileToken({
  siteKey,
  action,
  pageUrl,
  executablePath,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  launch = (options) => chromium.launch(options),
}) {
  requireNonEmpty(siteKey, "turnstile_site_key_required");
  requireNonEmpty(action, "turnstile_action_required");
  requireNonEmpty(pageUrl, "turnstile_page_url_required");
  const parsed = new URL(pageUrl);
  if (parsed.protocol !== "https:") throw new Error("turnstile_https_origin_required");
  if (!/^register_(web|android|ios)$/.test(action)) {
    throw new Error("turnstile_action_not_allowed");
  }

  const browser = await launch({
    headless: true,
    ...(executablePath ? { executablePath } : {}),
  });
  try {
    const page = await browser.newPage();
    await page.goto(parsed.href, { waitUntil: "domcontentloaded", timeout: timeoutMs });
    if (new URL(page.url()).hostname !== parsed.hostname) {
      throw new Error("turnstile_origin_redirected");
    }
    const token = await page.evaluate(async ({ widgetSiteKey, widgetAction, waitMs }) => {
      const globalKey = "__quataTurnstileEvidence";
      window[globalKey] = { token: null, error: null };
      let script = document.querySelector("script[data-quata-turnstile-evidence]");
      if (!script) {
        script = document.createElement("script");
        script.dataset.quataTurnstileEvidence = "true";
        script.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
        script.async = true;
        document.head.appendChild(script);
      }
      const deadline = Date.now() + waitMs;
      while (!window.turnstile && Date.now() < deadline) {
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
      if (!window.turnstile) throw new Error("turnstile_script_unavailable");
      const host = document.createElement("div");
      host.id = "quata-turnstile-evidence-widget";
      host.style.cssText = "position:fixed;z-index:2147483647;inset:20px;background:white";
      document.body.appendChild(host);
      window.turnstile.render(host, {
        sitekey: widgetSiteKey,
        action: widgetAction,
        execution: "execute",
        appearance: "interaction-only",
        callback: (value) => { window[globalKey].token = value; },
        "error-callback": (code) => { window[globalKey].error = String(code || "widget_error"); },
        "timeout-callback": () => { window[globalKey].error = "widget_timeout"; },
      });
      window.turnstile.execute(host);
      while (!window[globalKey].token && !window[globalKey].error && Date.now() < deadline) {
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
      if (window[globalKey].error) throw new Error(`turnstile_widget_failed:${window[globalKey].error}`);
      if (!window[globalKey].token) throw new Error("turnstile_token_timeout");
      return window[globalKey].token;
    }, { widgetSiteKey: siteKey, widgetAction: action, waitMs: timeoutMs });
    if (typeof token !== "string" || token.length < 20) {
      throw new Error("turnstile_token_invalid");
    }
    return token;
  } finally {
    await browser.close();
  }
}

function requireNonEmpty(value, code) {
  if (typeof value !== "string" || !value.trim()) throw new Error(code);
}
