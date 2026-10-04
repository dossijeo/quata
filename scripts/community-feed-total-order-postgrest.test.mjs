const [baseUrl] = process.argv.slice(2);
if (!baseUrl) throw new Error("PostgREST base URL is required");

const request = async (query) => {
  const headers = { apikey: "isolated-public-key" };
  if (Object.keys(headers).some((name) => name.toLowerCase() === "authorization")) {
    throw new Error("anonymous Community feed probe must not send Authorization");
  }
  const response = await fetch(`${baseUrl}/rpc/quata_community_feed_page?${new URLSearchParams(query)}`, {
    method: "GET",
    headers,
  });
  const text = await response.text();
  let value = text;
  try { value = text ? JSON.parse(text) : null; } catch { /* retain diagnostic text */ }
  return { status: response.status, value };
};

const expect = (condition, message, detail) => {
  if (!condition) throw new Error(`${message}: ${JSON.stringify(detail)}`);
};

for (let attempt = 0; attempt < 40; attempt += 1) {
  try {
    const probe = await request({ p_limit: "1" });
    if (probe.status === 200) break;
  } catch { /* PostgREST schema cache may still be starting. */ }
  if (attempt === 39) throw new Error("PostgREST did not become ready");
  await new Promise((resolve) => setTimeout(resolve, 250));
}

const all = [];
let cursor = null;
for (const expectedSize of [50, 50, 1]) {
  const query = { p_limit: "50" };
  if (cursor) {
    query.p_before_created_at = cursor.created_at;
    query.p_before_id = cursor.id;
  }
  const page = await request(query);
  expect(page.status === 200, "anonymous page failed", page);
  expect(page.value.length === expectedSize, "page boundary changed", { expectedSize, page });
  all.push(...page.value);
  cursor = page.value.at(-1);
}
expect(all.length === 101, "pagination did not exhaust every post", all.length);
expect(new Set(all.map(({ id }) => id)).size === 101, "pagination duplicated a post", all);
expect(all.every(({ created_at }) => created_at === "2026-10-04T10:00:00+00:00"), "fixture timestamp changed", all);
for (let index = 1; index < all.length; index += 1) {
  expect(all[index - 1].id > all[index].id, "equal-timestamp ID order is not strictly descending", {
    previous: all[index - 1].id,
    current: all[index].id,
  });
}

const incomplete = await request({ p_limit: "2", p_before_created_at: cursor.created_at });
expect(incomplete.status === 400 && incomplete.value?.code === "22023", "partial cursor did not fail closed", incomplete);

console.log("COMMUNITY_FEED_TOTAL_ORDER_POSTGREST_OK");
