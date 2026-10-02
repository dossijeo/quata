const CURSOR_KEYS = ["p_before_sort_at", "p_before_created_at", "p_before_id"];

export function officialFeedPageFixtureResponse({ method, authorization, query = {} }) {
  if (method !== "GET") {
    return { status: 405, body: { error: "fixture_public_feed_method_forbidden" } };
  }
  if (typeof authorization === "string" && authorization.trim().length > 0) {
    return { status: 400, body: { error: "fixture_public_feed_bearer_forbidden" } };
  }
  if (query.p_limit !== "50" || CURSOR_KEYS.some((name) => Object.hasOwn(query, name))) {
    return { status: 400, body: { error: "fixture_public_feed_initial_page_required" } };
  }
  return { status: 200, body: [] };
}
