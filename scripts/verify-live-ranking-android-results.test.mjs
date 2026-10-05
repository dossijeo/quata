import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { verifyLiveRankingAndroidResults } from './verify-live-ranking-android-results.mjs';

const feedClass = 'com.quata.feature.feed.presentation.FeedRemoteRankingInstrumentedTest';
const officialClass = 'com.quata.feature.official.presentation.OfficialRemoteRankingInstrumentedTest';
const method = 'remoteSecondPageFailsClosedRetriesAndOpensExactTarget';

function report(cases) {
  return `<?xml version="1.0" encoding="UTF-8"?><testsuite tests="${cases.length}" failures="0" errors="0" skipped="0">${cases.join('')}</testsuite>`;
}

function passed(className) {
  return `<testcase classname="${className}" name="${method}" time="1.0"/>`;
}

async function fixture(cases) {
  const root = await mkdtemp(join(tmpdir(), 'quata-live-ranking-android-'));
  await writeFile(join(root, 'TEST-live-ranking.xml'), report(cases));
  return root;
}

test('accepts exactly the two passing native Ranking cases', async (t) => {
  const root = await fixture([passed(feedClass), passed(officialClass)]);
  t.after(() => rm(root, { recursive: true, force: true }));
  const result = await verifyLiveRankingAndroidResults(root);
  assert.equal(result.passed, 2);
});

test('fails closed when a required native Ranking case is absent', async (t) => {
  const root = await fixture([passed(feedClass)]);
  t.after(() => rm(root, { recursive: true, force: true }));
  await assert.rejects(verifyLiveRankingAndroidResults(root), /live_ranking_android_missing/);
});

test('fails closed when a required native Ranking case is skipped', async (t) => {
  const root = await fixture([
    passed(feedClass),
    `<testcase classname="${officialClass}" name="${method}"><skipped/></testcase>`,
  ]);
  t.after(() => rm(root, { recursive: true, force: true }));
  await assert.rejects(verifyLiveRankingAndroidResults(root), /live_ranking_android_not_passed/);
});

test('fails closed when a required native Ranking case is duplicated', async (t) => {
  const root = await fixture([passed(feedClass), passed(feedClass), passed(officialClass)]);
  t.after(() => rm(root, { recursive: true, force: true }));
  await assert.rejects(verifyLiveRankingAndroidResults(root), /live_ranking_android_duplicate/);
});
