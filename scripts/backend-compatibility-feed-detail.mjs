/** Selects only a scalar post id observed in a successful public GET payload. */
export function publicPostIdFromPayload(text) {
  let payload; try { payload = JSON.parse(text); } catch { return null; }
  const rows = Array.isArray(payload) ? payload : [payload];
  const value = rows.find((row) => row && typeof row.id === 'string')?.id?.trim();
  return value && /^[A-Za-z0-9_-]{1,128}$/.test(value) ? value : null;
}
export function detailEvidence(events, postId) {
  return detailEvidenceEvent(events, postId) != null;
}
export function detailEvidenceEvent(events, postId) {
  return events.find((event) => event.method === 'GET' && event.status >= 200 && event.status < 300 && event.table === 'posts' &&
    new URLSearchParams(event.query).get('id') === `eq.${postId}` && event.payloadPostId === postId);
}
