/**
 * Hermetic-only request and response facts for the public Web feed smoke.
 *
 * These helpers deliberately accept no wildcard host/path/query variants.
 * The Turnstile resource is fulfilled by Playwright before Chromium can issue
 * a network request; the Storage response helper never exposes object URLs.
 */
export const TURNSTILE_BOOTSTRAP_URL =
  "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

export function expectedLocalStub(request) {
  return String(request?.method ?? "").toUpperCase() === "GET" &&
    request?.url === TURNSTILE_BOOTSTRAP_URL
    ? { kind: "localStub", expected: "turnstile-bootstrap" }
    : null;
}

/**
 * Validates a response to a request already admitted by the request policy.
 * The returned record is safe for a CI report: it intentionally omits the
 * object URL, request headers and content body.
 */
export function inspectAccreditedPublicMediaResponse({
  url, requestUrl, method, status, contentType, resourceType, requestAllowed,
  accreditedMediaUrls,
}) {
  const request = safeUrl(requestUrl);
  const response = safeUrl(url);
  const type = String(resourceType ?? "").toLowerCase();
  const base = {
    kind: "publicMedia",
    requestCorrelated: Boolean(request && response && request.href === response.href),
    status: Number.isInteger(status) ? status : null,
    contentType: safeContentType(contentType),
    resourceType: safeResourceType(type),
  };
  if (!request || !response || request.href !== response.href) return denied(base, "response_request_mismatch");
  if (String(method ?? "").toUpperCase() !== "GET") return denied(base, "method_not_get");
  if (requestAllowed !== true) return denied(base, "request_not_admitted");
  if (!(accreditedMediaUrls instanceof Set) || !accreditedMediaUrls.has(request.href)) {
    return denied(base, "url_not_accredited");
  }
  if (!Number.isInteger(status) || status < 200 || status >= 300) return denied(base, "status_not_2xx");
  if (type === "image" && !/^image\//i.test(String(contentType ?? ""))) return denied(base, "content_type_not_image");
  if (type === "media" && !/^video\//i.test(String(contentType ?? ""))) return denied(base, "content_type_not_video");
  if (type !== "image" && type !== "media") return denied(base, "resource_type_not_media");
  return { ...base, accepted: true };
}

function denied(base, reason) { return { ...base, accepted: false, reason }; }
function safeUrl(value) { try { return new URL(value); } catch { return null; } }
function safeContentType(value) {
  const media = String(value ?? "").split(";", 1)[0].trim().toLowerCase();
  return /^(?:image|video)\/[a-z0-9.+-]{1,80}$/.test(media) ? media : "<other>";
}
function safeResourceType(value) { return value === "image" || value === "media" ? value : "other"; }