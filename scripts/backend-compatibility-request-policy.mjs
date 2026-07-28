/**
 * Fail-closed network policy for the credential-free browser feed smoke.
 *
 * Public Storage is deliberately not a permission boundary here: an exact
 * object URL may be used only after this same browser run has received it in
 * a tightly validated `community_posts` JSON response.
 */
export function inspectBackendRequest({
  url, method, headers, resourceType, accreditedMediaUrls, redirectedFromUrl,
  applicationOrigin,
}, supabaseBaseUrl) {
  const base = safelyParseUrl(supabaseBaseUrl);
  const requestUrl = safelyParseUrl(url);
  if (!base || !requestUrl) return denied('invalid_url');

  // Do this before the origin allowance.  A credentialed request must never
  // become acceptable merely because it points at a non-Supabase origin.
  const isSupabase = requestUrl.origin === base.origin;
  const isStorageObject = isPublicStorageObject(requestUrl, url);
  if (hasUserInfo(requestUrl) || hasUnsafeUrlEncoding(url, requestUrl) ||
      hasCredentials(headers, requestUrl, url, { allowPublishableApiKey: isSupabase && !isStorageObject })) {
    return denied(isSupabase ? 'supabase_credentials_forbidden' : 'credentials_forbidden');
  }

  const app = applicationOrigin ? safelyParseUrl(applicationOrigin) : null;
  const isApplication = app && requestUrl.origin === app.origin;
  if (redirectedFromUrl && hasUnexpectedRedirect(redirectedFromUrl, requestUrl, base, app)) {
    return denied('redirect_cross_origin');
  }

  // The smoke owns only its local static server and its configured Supabase
  // project.  There is no generic image/media escape hatch for another host.
  if (!isSupabase && !isApplication) return denied('cross_origin_forbidden');
  if (isApplication) return allowed();

  if (String(method ?? '').toUpperCase() !== 'GET') return denied('supabase_method_forbidden');
  if (isStorageObject) {
    if (!isAllowedMediaType(resourceType)) return denied('supabase_storage_resource_type_forbidden');
    if (!hasExactAccreditedMediaUrl(requestUrl, accreditedMediaUrls)) return denied('supabase_storage_url_not_accredited');
    return allowed();
  }
  if (!requestUrl.pathname.startsWith('/rest/v1/')) return denied('supabase_path_forbidden');
  if (!hasHeader(headers, 'apikey')) return denied('supabase_publishable_key_missing');
  return allowed();
}

/**
 * Returns media URLs only when the response has proved it is the direct,
 * credential-free community-post feed/detail response that was admitted by
 * `inspectBackendRequest`.  The caller supplies the already-recorded policy
 * decision for the Playwright Request so a response cannot self-accredit.
 */
export function accreditPublicMediaUrlsFromResponse({
  url, requestUrl, method, headers, status, contentType, resourceType,
  serviceWorker, redirectedFromUrl, redirectedToUrl, requestAllowed, payload,
}, supabaseBaseUrl) {
  const base = safelyParseUrl(supabaseBaseUrl);
  const responseUrl = safelyParseUrl(url);
  const originalRequestUrl = safelyParseUrl(requestUrl);
  if (!base || !responseUrl || !originalRequestUrl || requestAllowed !== true) return [];
  if (responseUrl.origin !== base.origin || originalRequestUrl.origin !== base.origin) return [];
  if (responseUrl.href !== originalRequestUrl.href || hasUserInfo(responseUrl) ||
      hasUnsafeUrlEncoding(url, responseUrl)) return [];
  if (String(method ?? '').toUpperCase() !== 'GET' || !isCommunityPostsEndpoint(responseUrl)) return [];
  if (!Number.isInteger(status) || status < 200 || status >= 300) return [];
  if (!isJsonContentType(contentType) || serviceWorker === true || redirectedFromUrl || redirectedToUrl) return [];

  // Defense in depth: the request decision must have checked this, but keep
  // accreditation independent from an accidental future route-handler change.
  const requestDecision = inspectBackendRequest({
    url: requestUrl, method, headers, resourceType, applicationOrigin: undefined,
  }, supabaseBaseUrl);
  if (!requestDecision.allowed) return [];
  return publicMediaUrlsFromPayload(payload, supabaseBaseUrl);
}

/** Returns only public media URLs declared by a normal PostgREST row payload. */
export function publicMediaUrlsFromPayload(text, supabaseBaseUrl) {
  let payload;
  try { payload = JSON.parse(text); } catch { return []; }
  const base = safelyParseUrl(supabaseBaseUrl);
  if (!base) return [];
  const rows = Array.isArray(payload) ? payload : [payload];
  const result = new Set();
  for (const row of rows) {
    if (!row || typeof row !== 'object' || Array.isArray(row)) continue;
    for (const field of ['image_url', 'video_url']) {
      const value = row[field];
      const candidate = typeof value === 'string' ? safelyParseUrl(value) : null;
      if (!candidate || candidate.origin !== base.origin || hasUserInfo(candidate) ||
          hasUnsafeUrlEncoding(value, candidate) || hasSensitiveQuery(candidate, value) ||
          !isPublicStorageObject(candidate, value)) continue;
      result.add(candidate.href);
    }
  }
  return [...result];
}

function allowed() { return { allowed: true }; }
function denied(reason) { return { allowed: false, reason }; }
function safelyParseUrl(value) { try { return new URL(value); } catch { return null; } }
function hasHeader(headers, target) {
  return Object.entries(headers ?? {}).some(([key, value]) => key.toLowerCase() === target && typeof value === 'string' && value.trim() !== '');
}
function hasUserInfo(url) { return url.username !== '' || url.password !== ''; }
function hasCredentials(headers, requestUrl, rawUrl, { allowPublishableApiKey }) {
  const headerText = Object.entries(headers ?? {}).map(([key, value]) => `${key}:${value}`).join('\n');
  return /(?:^|\n)\s*(?:authorization|proxy-authorization|cookie|x-api-key)\s*:\s*\S/i.test(headerText) ||
    (!allowPublishableApiKey && hasHeader(headers, 'apikey')) ||
    /\bbearer\b|(?:session|access.?token|refresh.?token|credential)/i.test(headerText) ||
    hasSensitiveQuery(requestUrl, rawUrl);
}

function isPublicStorageObject(url, rawUrl = url.href) {
  if (!/^\/storage\/v1\/object\/public\/[^/?#]+\/[^?#]+$/i.test(url.pathname)) return false;
  return !hasUnsafePath(rawPathFromUrl(rawUrl, url));
}
function isAllowedMediaType(value) { return ['image', 'media'].includes(String(value ?? '').toLowerCase()); }
function hasExactAccreditedMediaUrl(url, accreditedMediaUrls) {
  return accreditedMediaUrls instanceof Set && accreditedMediaUrls.has(url.href);
}
function isCommunityPostsEndpoint(url) { return url.pathname === '/rest/v1/community_posts'; }
function isJsonContentType(value) { return /^(?:application\/json|application\/[^;]+\+json)(?:\s*;|$)/i.test(String(value ?? '')); }

function hasSensitiveQuery(url, rawUrl) {
  const rawQuery = rawQueryFromUrl(rawUrl, url);
  const layers = decodedUrlLayers(rawQuery);
  if (!layers) return true;
  // Inspect every bounded decoding layer.  A single-pass check of the raw
  // query misses `%2526`/`%2523`: after one or more downstream decodes those
  // values become delimiters and can create a new credential-bearing field.
  // Literal `&` and `=` remain valid for an ordinary PostgREST query; only an
  // escape which would materialize a structural character is refused.
  return layers.some((query) => {
    if (hasUnsafeText(query) || hasEncodedQueryDelimiter(query)) return true;
    return query.split('&').some((part) => {
      const key = part.split('=', 1)[0].replaceAll('+', ' ');
      const decoded = decodeRepeatedly(key);
      return decoded === null || /(?:token|signature|sign(?:ed)?|access|auth|api(?:key)?|key|credential|session|expires?)/i.test(decoded);
    });
  });
}

function hasEncodedQueryDelimiter(value) {
  // `;` is included because it is accepted as a query separator by some
  // intermediaries.  The remaining characters either split query structure or
  // change the URL component being interpreted.
  return /%(?:26|3b|3d|3f|23|2f|5c|40)/i.test(value);
}

function hasUnsafeUrlEncoding(rawUrl, parsedUrl) {
  const rawPath = rawPathFromUrl(rawUrl, parsedUrl);
  const rawQuery = rawQueryFromUrl(rawUrl, parsedUrl);
  return hasUnsafePath(rawPath) || rawQuery === null || hasUnsafeText(rawQuery);
}
function hasUnsafePath(rawPath) {
  const decoded = decodeRepeatedly(rawPath);
  if (decoded === null || hasUnsafeText(decoded) || /\\/.test(decoded)) return true;
  // Reject traversal both before and after decoding.  `new URL` normalizes
  // literal dot segments, therefore the raw input remains authoritative.
  return /(?:^|\/)\.{1,2}(?:\/|$)/.test(decoded) || /%/i.test(decoded);
}
function hasUnsafeText(value) {
  const decoded = decodeRepeatedly(value);
  return decoded === null || /[\u0000-\u001f\u007f\\]/.test(decoded) || /%/i.test(decoded);
}
function decodeRepeatedly(value) {
  let current = String(value);
  for (let index = 0; index < 4; index += 1) {
    let next;
    try { next = decodeURIComponent(current); } catch { return null; }
    if (next === current) return next;
    current = next;
  }
  // Four layers is intentionally the bounded maximum.  A remaining escape is
  // ambiguous, so fail closed rather than attempting an unbounded decode.
  return /%[0-9a-f]{2}/i.test(current) ? null : current;
}
function decodedUrlLayers(value) {
  const layers = [];
  let current = String(value);
  for (let index = 0; index < 4; index += 1) {
    layers.push(current);
    let next;
    try { next = decodeURIComponent(current); } catch { return null; }
    if (next === current) return layers;
    current = next;
  }
  // A fifth unresolved escape is intentionally ambiguous.  Do not rely on a
  // consumer agreeing with this policy about how many times to decode it.
  return /%[0-9a-f]{2}/i.test(current) ? null : [...layers, current];
}
function rawPathFromUrl(rawUrl, parsedUrl) {
  const text = String(rawUrl);
  const authority = text.match(/^[a-z][a-z0-9+.-]*:\/\/[^/?#]*/i)?.[0];
  if (!authority) return parsedUrl.pathname;
  const remainder = text.slice(authority.length);
  return (remainder.split(/[?#]/, 1)[0] || '/');
}
function rawQueryFromUrl(rawUrl, parsedUrl) {
  const text = String(rawUrl);
  const queryIndex = text.indexOf('?');
  if (queryIndex < 0) return parsedUrl.search.slice(1);
  return text.slice(queryIndex + 1).split('#', 1)[0];
}
function hasUnexpectedRedirect(redirectedFromUrl, requestUrl, base, app) {
  const previous = safelyParseUrl(redirectedFromUrl);
  if (!previous) return true;
  // Any redirect provenance is ambiguous for this no-redirect smoke.  This
  // includes same-origin redirects, not only Supabase -> foreign redirects.
  return previous.origin !== requestUrl.origin || previous.origin === base.origin || (app && previous.origin === app.origin);
}
