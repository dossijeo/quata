/**
 * Produces diagnostic records safe to attach to CI output.  In particular, do
 * not put request URLs, request headers, query strings, or response bodies in
 * the report: a failed request can otherwise turn a harmless report into a
 * credential or user-data disclosure.
 */
export function sanitizeWebSmokeRequest({ url, method, resourceType, frame, phase, route }, supabaseBaseUrl) {
  const parsed = safeUrl(url);
  const base = safeUrl(supabaseBaseUrl);
  return {
    method: safeMethod(method),
    originMatch: parsed != null && base != null && parsed.origin === base.origin,
    // Pathnames can contain object names, email addresses, opaque tokens, or
    // user-controlled Unicode.  This is a diagnostic label, never a copy of
    // a request path.  Keep only known, non-sensitive API categories.
    pathname: safePathname(parsed),
    resourceType: safeResourceType(resourceType),
    frame: safeFrame(frame),
    phase: safePhase(phase),
    route: safeRoute(route),
  };
}

export function webSmokePhase(route, resourceType) {
  if (isMediaResource(resourceType)) return "media";
  return String(route ?? "").startsWith("post/") ? "detail" : "list";
}

function safeUrl(value) {
  try { return new URL(value); } catch { return null; }
}

const SAFE_REST_TABLES = new Set([
  "community_posts",
  "community_profiles",
  "official_posts",
]);

function safePathname(parsed) {
  if (parsed == null) return "/<invalid>";

  // Do not decode or return a URL segment here.  Besides avoiding accidental
  // disclosure, this makes percent-encoded and Unicode input follow exactly
  // the same redaction path as plain text.
  const segments = parsed.pathname.split("/").filter(Boolean);
  const [service, version, first, ...remaining] = segments;

  if (service === "rest" && version === "v1") {
    if (!SAFE_REST_TABLES.has(first)) return "/rest/v1/<redacted>";
    return remaining.length === 0
      ? `/rest/v1/${first}`
      : `/rest/v1/${first}/<redacted>`;
  }
  if (service === "storage" && version === "v1") return "/storage/v1/<redacted>";
  if (service === "auth" && version === "v1") return "/auth/v1/<redacted>";
  if (service === "functions" && version === "v1") return "/functions/v1/<redacted>";
  return "/<other>";
}

function safeMethod(value) {
  const method = String(value ?? "").toUpperCase();
  return /^[A-Z]{1,16}$/.test(method) ? method : "<invalid>";
}

function safeResourceType(value) {
  const type = String(value ?? "other").toLowerCase();
  return /^[a-z]{1,32}$/.test(type) ? type : "other";
}

function safeFrame(value) {
  return value === "subframe" ? "subframe" : "main";
}

function safePhase(value) {
  return value === "media" || value === "detail" ? value : "list";
}

function safeRoute(value) {
  const route = String(value ?? "");
  // Route values are application labels, not URLs.  Keep the bounded post id
  // that is already accepted by publicPostIdFromPayload and redact all else.
  if (/^(feed|official|communities)$/.test(route)) return route;
  if (/^post\/[A-Za-z0-9_-]{1,128}$/.test(route)) return route;
  return "<unknown>";
}

function isMediaResource(resourceType) {
  return ["image", "media", "font"].includes(String(resourceType ?? "").toLowerCase());
}
