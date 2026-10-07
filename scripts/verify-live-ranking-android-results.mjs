import { readdir, readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const expectedCases = new Map([
  [
    'com.quata.feature.feed.presentation.FeedRemoteRankingInstrumentedTest',
    'remoteSecondPageFailsClosedRetriesAndOpensExactTarget',
  ],
  [
    'com.quata.feature.feed.presentation.FeedDeepPaginationInstrumentedTest',
    'nativePagerPreservesFirstPageRetriesAndReachesDeepTarget',
  ],
  [
    'com.quata.feature.official.presentation.OfficialRemoteRankingInstrumentedTest',
    'remoteSecondPageFailsClosedRetriesAndOpensExactTarget',
  ],
  [
    'com.quata.feature.official.presentation.OfficialDeepPaginationInstrumentedTest',
    'nativePagerPreservesFirstPageRetriesAndReachesDeepTarget',
  ],
]);

async function xmlFiles(root) {
  const entries = await readdir(root, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = resolve(root, entry.name);
    if (entry.isDirectory()) return xmlFiles(path);
    return entry.isFile() && /^TEST-.*\.xml$/.test(entry.name) ? [path] : [];
  }));
  return nested.flat();
}

function attribute(source, name) {
  return source.match(new RegExp(`\\b${name}="([^"]+)"`))?.[1] ?? null;
}

export async function verifyLiveRankingAndroidResults(root) {
  const files = await xmlFiles(resolve(root));
  if (files.length === 0) throw new Error('live_ranking_android_junit_missing');

  const observed = new Map();
  for (const file of files) {
    const xml = await readFile(file, 'utf8');
    const cases = xml.matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g);
    for (const match of cases) {
      const className = attribute(match[1], 'classname');
      const testName = attribute(match[1], 'name');
      if (!expectedCases.has(className) || expectedCases.get(className) !== testName) continue;
      const key = `${className}#${testName}`;
      if (observed.has(key)) throw new Error(`live_ranking_android_duplicate:${key}`);
      const body = match[2] ?? '';
      if (/<(?:failure|error|skipped)\b/i.test(body)) throw new Error(`live_ranking_android_not_passed:${key}`);
      observed.set(key, file);
    }
  }

  const missing = [...expectedCases].map(([className, testName]) => `${className}#${testName}`)
    .filter((key) => !observed.has(key));
  if (missing.length > 0) throw new Error(`live_ranking_android_missing:${missing.join(',')}`);

  return {
    ok: true,
    expected: expectedCases.size,
    passed: observed.size,
    cases: [...observed.keys()].sort(),
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = process.argv[2];
  if (!root) throw new Error('usage: node scripts/verify-live-ranking-android-results.mjs <junit-directory>');
  console.log(JSON.stringify(await verifyLiveRankingAndroidResults(root), null, 2));
}
