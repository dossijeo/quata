const [baseUrl] = process.argv.slice(2);
if (!baseUrl) throw new Error("PostgREST base URL is required");

const expect = (condition, message, detail) => {
  if (!condition) throw new Error(`${message}: ${JSON.stringify(detail)}`);
};

const request = async (table, foreignKey, postId, afterId) => {
  const query = new URLSearchParams({
    select: `id,${foreignKey},profile_id,body,created_at`,
    [foreignKey]: `eq.${postId}`,
    order: "id.asc",
    limit: "500",
  });
  if (table === "official_post_comments") query.set("deleted_at", "is.null");
  if (afterId) query.set("id", `gt.${afterId}`);
  const response = await fetch(`${baseUrl}/${table}?${query}`, {
    method: "GET",
    headers: { apikey: "isolated-public-key" },
  });
  const text = await response.text();
  let value = text;
  try { value = text ? JSON.parse(text) : null; } catch { /* retain diagnostic text */ }
  return { status: response.status, value };
};

for (let attempt = 0; attempt < 40; attempt += 1) {
  try {
    const probe = await request(
      "community_comments",
      "post_id",
      "aaaaaaaa-0000-4000-8000-000000000001",
    );
    if (probe.status === 200) break;
  } catch { /* PostgREST may still be loading its schema cache. */ }
  if (attempt === 39) throw new Error("PostgREST did not become ready");
  await new Promise((resolve) => setTimeout(resolve, 250));
}

const drain = async (table, foreignKey, postId) => {
  const rows = [];
  const pageSizes = [];
  let cursor;
  while (true) {
    const page = await request(table, foreignKey, postId, cursor);
    expect(page.status === 200 && Array.isArray(page.value), `${table} page failed`, page);
    pageSizes.push(page.value.length);
    for (const row of page.value) {
      expect(!cursor || row.id > cursor, `${table} cursor did not advance`, { cursor, row });
      cursor = row.id;
      rows.push(row);
    }
    if (page.value.length < 500) return { rows, pageSizes };
  }
};

for (const fixture of [
  ["community_comments", "post_id", "aaaaaaaa-0000-4000-8000-000000000001", "10000000"],
  ["official_post_comments", "official_post_id", "bbbbbbbb-0000-4000-8000-000000000001", "20000000"],
]) {
  const [table, foreignKey, postId, prefix] = fixture;
  const { rows, pageSizes } = await drain(table, foreignKey, postId);
  expect(pageSizes.join(",") === "500,500,205", `${table} did not cross the transport ceiling`, pageSizes);
  expect(rows.length === 1205 && new Set(rows.map(({ id }) => id)).size === 1205, `${table} rows incomplete`, rows.length);
  expect(rows.every(({ id }) => id.startsWith(prefix)), `${table} returned another fixture`, rows.slice(0, 3));
  const chronological = [...rows].sort((left, right) =>
    left.created_at.localeCompare(right.created_at) || left.id.localeCompare(right.id));
  expect(chronological[0].id === rows.at(-1).id, `${table} presentation order was not restored`, {
    first: chronological[0],
    lastTransportRow: rows.at(-1),
  });
}

console.log("COMMENTS_DEEP_PAGINATION_POSTGREST_OK");
