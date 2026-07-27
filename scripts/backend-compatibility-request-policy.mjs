/** Pure, preflight policy for the read-only browser gate. */
export function inspectBackendRequest({ url, method, headers }, supabaseBaseUrl) {
  const requestUrl = new URL(url);
  const base = new URL(supabaseBaseUrl);
  const authorization = Object.entries(headers ?? {}).find(([key]) => key.toLowerCase() === 'authorization')?.[1] ?? '';
  const apiKey = Object.entries(headers ?? {}).find(([key]) => key.toLowerCase() === 'apikey')?.[1] ?? '';
  const isSupabase = requestUrl.origin === base.origin;
  if (!isSupabase) return { allowed: true };
  if ((typeof authorization === 'string' && authorization.trim() !== '') || /session|access.?token|refresh.?token/i.test(JSON.stringify(headers ?? {})) ||
      [...requestUrl.searchParams.keys()].some((key) => /token|session|apikey|key/i.test(key))) {
    return { allowed: false, reason: 'supabase_credentials_forbidden' };
  }
  if (method.toUpperCase() !== 'GET') return { allowed: false, reason: 'supabase_method_forbidden' };
  if (!requestUrl.pathname.startsWith('/rest/v1/')) return { allowed: false, reason: 'supabase_path_forbidden' };
  if (typeof apiKey !== 'string' || apiKey.trim() === '') return { allowed: false, reason: 'supabase_publishable_key_missing' };
  return { allowed: true };
}
