const [baseUrl] = process.argv.slice(2);
if (!baseUrl) throw new Error("PostgREST base URL is required");

const request = async (query, language = "en") => {
  const headers = {
    apikey: "isolated-public-key",
    "x-quata-official-language": language,
  };
  if (Object.keys(headers).some((name) => name.toLowerCase() === "authorization")) {
    throw new Error("anonymous Official feed probe must not send Authorization");
  }
  const response = await fetch(`${baseUrl}/rpc/quata_official_feed_page?${new URLSearchParams(query)}`, {
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

const first = await request({ p_limit: "2" });
expect(first.status === 200, "first anonymous page failed", first);
expect(first.value.map(({ id }) => id).join(",") === [
  "00000000-0000-0000-0000-000000000102",
  "00000000-0000-0000-0000-000000000105",
].join(","), "first page order or language selection changed", first);

const cursor = first.value.at(-1);
expect(cursor.published_at === "2026-10-02T09:00:00+00:00", "sort timestamp lost precision", cursor);
expect(cursor.created_at === "2026-10-02T08:00:00+00:00", "creation timestamp lost precision", cursor);

const second = await request({
  p_limit: "1",
  p_before_sort_at: cursor.published_at,
  p_before_created_at: cursor.created_at,
  p_before_id: cursor.id,
});
expect(second.status === 200, "second anonymous page failed", second);
expect(second.value.map(({ id }) => id).join(",") === "00000000-0000-0000-0000-000000000104", "second page skipped or duplicated a tie", second);

const all = [...first.value, ...second.value];
expect(all.length === 3 && new Set(all.map(({ id }) => id)).size === 3, "pages are not unique and complete", all);

const deepPages = [];
let deepCursor = second.value.at(-1);
for (const expectedSize of [50, 50, 1]) {
  const page = await request({
    p_limit: "50",
    p_before_sort_at: deepCursor.published_at,
    p_before_created_at: deepCursor.created_at,
    p_before_id: deepCursor.id,
  });
  expect(page.status === 200, "deep anonymous page failed", page);
  expect(page.value.length === expectedSize, "deep page boundary changed", { expectedSize, page });
  deepPages.push(...page.value);
  deepCursor = page.value.at(-1);
}
expect(deepPages.length === 101, "deep pagination did not cross two full client pages", deepPages.length);
expect(new Set(deepPages.map(({ id }) => id)).size === 101, "deep pagination duplicated a post", deepPages);
expect(deepPages.every(({ title }) => title.startsWith("deep ")), "deep pagination escaped its fixture", deepPages);
for (let index = 1; index < deepPages.length; index += 1) {
  const previous = deepPages[index - 1];
  const current = deepPages[index];
  const previousTuple = [previous.published_at, previous.created_at, previous.id];
  const currentTuple = [current.published_at, current.created_at, current.id];
  expect(previousTuple.join("\u0000") > currentTuple.join("\u0000"), "deep page order is not strictly descending", {
    previous: previousTuple,
    current: currentTuple,
  });
}

const french = await request({ p_limit: "10" }, "fr");
expect(french.status === 200, "French anonymous page failed", french);
expect(french.value.some(({ id }) => id === "00000000-0000-0000-0000-000000000106"), "requested French variant was not selected", french);

const incomplete = await request({ p_limit: "2", p_before_sort_at: cursor.published_at });
expect(incomplete.status === 400 && incomplete.value?.code === "22023", "partial cursor did not fail closed", incomplete);

console.log("OFFICIAL_FEED_TOTAL_ORDER_POSTGREST_OK");
