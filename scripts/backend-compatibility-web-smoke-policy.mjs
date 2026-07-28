/**
 * Hermetic-only request and response facts for the public Web feed smoke.
 *
 * These helpers deliberately accept no wildcard host/path/query variants.
 * The Turnstile resource is fulfilled by Playwright before Chromium can issue
 * a network request; the Storage response helper never exposes object URLs.
 */
import { setTimeout as delay } from "node:timers/promises";

export const TURNSTILE_BOOTSTRAP_URL =
  "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

export function expectedLocalStub(request) {
  return request?.method === "GET" &&
    request?.url === TURNSTILE_BOOTSTRAP_URL
    ? { kind: "localStub", expected: "turnstile-bootstrap" }
    : null;
}

/** True only for the configured project's public Storage object transport. */
export function isPublicStorageMediaRequest({ url, resourceType }, supabaseBaseUrl) {
  const request = safeUrl(url);
  const base = safeUrl(supabaseBaseUrl);
  return request != null && base != null && request.origin === base.origin &&
    /^(?:image|media)$/.test(String(resourceType ?? "").toLowerCase()) &&
    /^\/storage\/v1\/object\/public\/[^/?#]+\/[^?#]+$/i.test(request.pathname);
}

/** The upstream verification fetch must never inherit page credentials. */
export function publicStorageFetchHeaders(headers) {
  const accept = typeof headers?.accept === "string" && /^[\x20-\x7e]{1,256}$/.test(headers.accept)
    ? headers.accept
    : "*/*";
  return { accept };
}

/**
 * Validates a response to a request already admitted by the request policy.
 * The returned record is safe for a CI report: it intentionally omits the
 * object URL, request headers and content body.
 */
export function inspectAccreditedPublicMediaResponse({
  url, requestUrl, method, status, contentType, resourceType, requestAllowed,
  route, accreditedMediaUrls,
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
  // A Storage object is admissible only for the same feed/detail route that
  // received it in a validated community-post response.  In particular,
  // `official` and `communities` never inherit feed accreditation.
  if (!isMediaAccreditationRoute(route)) return denied(base, "route_not_media_accreditable");
  if (!(accreditedMediaUrls instanceof Set) || !accreditedMediaUrls.has(request.href)) {
    return denied(base, "url_not_accredited");
  }
  if (!Number.isInteger(status) || status < 200 || status >= 300) return denied(base, "status_not_2xx");
  if (type === "image" && !/^image\//i.test(String(contentType ?? ""))) return denied(base, "content_type_not_image");
  if (type === "media" && !/^video\//i.test(String(contentType ?? ""))) return denied(base, "content_type_not_video");
  if (type !== "image" && type !== "media") return denied(base, "resource_type_not_media");
  return { ...base, accepted: true };
}

export function isMediaAccreditationRoute(route) {
  return route === "feed" || /^post\/[A-Za-z0-9_-]{1,128}$/.test(String(route ?? ""));
}

export function createMediaNavigationEpoch(route) {
  return { route, gate: null, outcomeGate: null, accreditedMediaUrls: new Set() };
}

export function openMediaAccreditationGate(epoch) {
  if (!epoch) return null;
  if (epoch?.gate) return epoch.gate;
  let resolve;
  const gate = { settled: false, promise: new Promise((done) => { resolve = done; }), resolve };
  epoch.gate = gate;
  return gate;
}

export function settleMediaAccreditation(epoch, urls) {
  const gate = epoch?.gate;
  if (!gate || gate.settled) return;
  for (const url of urls) epoch.accreditedMediaUrls.add(url);
  gate.settled = true;
  gate.resolve(epoch.accreditedMediaUrls);
}

export async function waitForMediaAccreditation(epoch, timeoutMs = 5_000) {
  if (!epoch?.gate || epoch.gate.settled) return epoch?.gate?.settled ? epoch.accreditedMediaUrls : null;
  return Promise.race([epoch.gate.promise, delay(timeoutMs).then(() => null)]);
}

export function hasAcceptedMediaForEpoch(responses, epoch) {
  return Array.isArray(responses) && responses.some((response) => response?.epoch === epoch && response.accepted === true);
}

export function recordMediaOutcome(epoch, accepted) {
  const gate = openMediaOutcomeGate(epoch);
  if (!gate || gate.settled) return;
  gate.settled = true;
  gate.resolve(accepted === true);
}

export async function waitForMediaOutcome(epoch, timeoutMs = 5_000) {
  const gate = openMediaOutcomeGate(epoch);
  if (!gate) return false;
  if (gate.settled) return gate.value;
  return Promise.race([gate.promise, delay(timeoutMs).then(() => false)]);
}

function openMediaOutcomeGate(epoch) {
  if (!epoch) return null;
  if (epoch.outcomeGate) return epoch.outcomeGate;
  let resolve;
  const gate = { settled: false, value: false, promise: new Promise((done) => { resolve = done; }) };
  gate.resolve = (value) => { gate.value = value; resolve(value); };
  epoch.outcomeGate = gate;
  return gate;
}

function denied(base, reason) { return { ...base, accepted: false, reason }; }
function safeUrl(value) { try { return new URL(value); } catch { return null; } }
function safeContentType(value) {
  const media = String(value ?? "").split(";", 1)[0].trim().toLowerCase();
  return /^(?:image|video)\/[a-z0-9.+-]{1,80}$/.test(media) ? media : "<other>";
}
function safeResourceType(value) { return value === "image" || value === "media" ? value : "other"; }
