/**
 * Pure, preflight policy for the read-only browser gate.
 *
 * `accreditedMediaUrls` is deliberately supplied by the caller rather than
 * inferred from a bucket name.  A public Storage bucket is not a blanket
 * permission to fetch arbitrary objects during this smoke: an image/video is
 * usable only after its exact URL appeared in a successful feed/detail
 * response in this browser run.
 */
export function inspectBackendRequest({ url, method, headers, resourceType, accreditedMediaUrls, redirectedFromUrl }, supabaseBaseUrl) {
  const requestUrl = new URL(url);
  const base = new URL(supabaseBaseUrl);
  const authorization = Object.entries(headers ?? {}).find(([key]) => key.toLowerCase() === 'authorization')?.[1] ?? '';
  const apiKey = Object.entries(headers ?? {}).find(([key]) => key.toLowerCase() === 'apikey')?.[1] ?? '';
  const isSupabase = requestUrl.origin === base.origin;
  if (redirectedFromUrl && isRedirectFromSupabaseToAnotherOrigin(redirectedFromUrl, requestUrl, base)) {
    return { allowed: false, reason: 'supabase_redirect_cross_origin' };
  }
  if (!isSupabase) return { allowed: true };
  if ((typeof authorization === 'string' && authorization.trim() !== '') || /session|access.?token|refresh.?token/i.test(JSON.stringify(headers ?? {})) ||
      hasSensitiveQuery(requestUrl)) {
    return { allowed: false, reason: 'supabase_credentials_forbidden' };
  }
  if (method.toUpperCase() !== 'GET') return { allowed: false, reason: 'supabase_method_forbidden' };
  if (isPublicStorageObject(requestUrl)) {
    if (!isAllowedMediaType(resourceType)) return { allowed: false, reason: 'supabase_storage_resource_type_forbidden' };
    if (!hasExactAccreditedMediaUrl(requestUrl, accreditedMediaUrls)) return { allowed: false, reason: 'supabase_storage_url_not_accredited' };
    return { allowed: true };
  }
  if (!requestUrl.pathname.startsWith('/rest/v1/')) return { allowed: false, reason: 'supabase_path_forbidden' };
  if (typeof apiKey !== 'string' || apiKey.trim() === '') return { allowed: false, reason: 'supabase_publishable_key_missing' };
  return { allowed: true };
}

/** Returns only public media URLs declared by a normal PostgREST row payload. */
export function publicMediaUrlsFromPayload(text, supabaseBaseUrl) {
  let payload;
  try { payload = JSON.parse(text); } catch { return []; }
  const base = new URL(supabaseBaseUrl);
  const rows = Array.isArray(payload) ? payload : [payload];
  const result = new Set();
  for (const row of rows) {
    if (!row || typeof row !== 'object' || Array.isArray(row)) continue;
    for (const field of ['image_url', 'video_url']) {
      const value = row[field];
      if (typeof value !== 'string' || value.trim() === '') continue;
      let candidate;
      try { candidate = new URL(value); } catch { continue; }
      if (candidate.origin !== base.origin || !isPublicStorageObject(candidate) || hasSensitiveQuery(candidate)) continue;
      result.add(candidate.href);
    }
  }
  return [...result];
}

function isPublicStorageObject(url) {
  // A path is intentionally strict: Storage's anonymous public-object route
  // only.  No private/authenticated/render/object-sign routes are admitted.
  if (!/^\/storage\/v1\/object\/public\/[^/?#]+\/[^?#]+$/i.test(url.pathname)) return false;
  // Do not let encoded separators/traversal turn a syntactically public path
  // into another Storage object after a server-side decode.
  return !/%(?:2e|2f|5c)/i.test(url.pathname);
}

function isAllowedMediaType(value) {
  return ['image', 'media'].includes(String(value ?? '').toLowerCase());
}

function hasExactAccreditedMediaUrl(url, accreditedMediaUrls) {
  if (!(accreditedMediaUrls instanceof Set)) return false;
  return accreditedMediaUrls.has(url.href);
}

function hasSensitiveQuery(url) {
  return [...url.searchParams.keys()].some((key) => /(?:token|signature|sign(?:ed)?|access|auth|api(?:key)?|key|credential|session|expires?)/i.test(key));
}

function isRedirectFromSupabaseToAnotherOrigin(redirectedFromUrl, requestUrl, base) {
  try {
    return new URL(redirectedFromUrl).origin === base.origin && requestUrl.origin !== base.origin;
  } catch {
    // Malformed redirect provenance is never a reason to broaden the policy.
    return true;
  }
}
