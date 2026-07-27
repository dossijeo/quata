/** Pure, preflight policy for the read-only browser gate. */
export function inspectBackendRequest({ url, method, headers }, supabaseBaseUrl) {
  const requestUrl = new URL(url);
  const base = new URL(supabaseBaseUrl);
  const authorization = Object.entries(headers ?? {}).find(([key]) => key.toLowerCase() === 'authorization')?.[1] ?? '';
  const isSupabase = requestUrl.origin === base.origin;
  if (!isSupabase) return { allowed: true };
  if (/^bearer\s+/i.test(authorization) || /session|access.?token|refresh.?token/i.test(JSON.stringify(headers ?? {}))) {
    return { allowed: false, reason: 'supabase_credentials_forbidden' };
  }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) return { allowed: false, reason: 'supabase_method_forbidden' };
  if (!requestUrl.pathname.startsWith('/rest/v1/')) return { allowed: false, reason: 'supabase_path_forbidden' };
  return { allowed: true };
}
