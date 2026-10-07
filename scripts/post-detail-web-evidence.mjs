#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { createServer } from "node:http";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { extname, join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import {
  cleanupFeedOfficialCommentsFixture,
  createCleanupRegistry,
  seedFeedOfficialCommentsFixture,
} from "./e2e-fixtures/chat-attachments.mjs";

const CHECK = "POST-DETAIL-WEB-COMMON-001";
const defaultCredentialsFile = "C:/Users/PC/QUATA_CHAT_GROUP_CREDENTIALS_FILE.txt";
const defaultDbUrlFile = "C:/Users/PC/.quata-supabase-db-url.txt";
const defaultDbTlsCaFile = "C:/Users/PC/.quata-supabase-pooler-ca.pem";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const { chromium } = loadPackage("playwright-core");
const pg = loadPackage("pg");

const options = parseArgs(process.argv.slice(2));
const report = {
  check: CHECK,
  status: "failed",
  startedAt: new Date().toISOString(),
  git: gitMetadata(),
  steps: [],
  evidence: {},
  anchors: [],
  fixture: {},
};

let server;
let browser;
let fixture;
let cleanup;
let config;
let actorSession;
const cleanupRegistry = createCleanupRegistry();

try {
  config = await publicConfig();
  const credentials = await loadCredentials();
  actorSession = await login(config, credentials.a, "post-detail-web-actor");
  const targetSession = await login(config, credentials.b, "post-detail-web-target");
  fixture = {
    marker: `qadata-feed-official-comments-post-detail-${randomUUID()}`,
    actorSession,
    targetSession,
  };
  report.steps.push("authenticated_profiles_loaded_without_logging_credentials");
  await seedFeedOfficialCommentsFixture({
    fixture,
    withDatabase,
    withMedia: true,
    withFeedVideo: options.feedVideo,
    withOfficialVideo: options.officialVideo,
    config,
    storageRequest,
    cleanup: cleanupRegistry,
  });
  report.steps.push("shared_feed_official_fixture_seeded");

  server = await startServer(options.distribution, await wordpressBaseUrl(), config);
  browser = await chromium.launch({
    executablePath: options.chrome,
    headless: options.headless,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--force-renderer-accessibility"],
  });
  const context = await browser.newContext({ locale: "es-ES", viewport: { width: 430, height: 930 }, deviceScaleFactor: 1 });
  await context.addInitScript((session) => {
    localStorage.setItem("quata_web_access_token", session.accessToken);
    localStorage.setItem("quata_web_refresh_token", session.refreshToken);
    localStorage.setItem("quata_web_session_token", session.webSessionToken);
    localStorage.setItem("quata_web_user_id", session.profileId);
    localStorage.setItem("quata_web_expires_at", String(session.expiresAt));
    localStorage.setItem("web.auth.session_ready", "true");
    localStorage.setItem("quata_web_client_instance_id", session.clientInstanceId);
  }, actorSession);
  const page = await context.newPage();
  await verifyFeedDetail(page, server.origin, fixture);
  if (!options.feedVideo) await verifyOfficialDetail(page, server.origin, fixture);
  report.status = "passed";
} catch (error) {
  report.error = safeFailure(error);
  report.diagnostics = {
    ...(report.diagnostics ?? {}),
    message: String(error?.message ?? error).slice(0, 500),
  };
} finally {
  await browser?.close().catch(() => {});
  await server?.close?.().catch(() => {});
  if (fixture) {
    cleanup = await cleanupFeedOfficialCommentsFixture({ fixture, withDatabase }).catch((error) => ({ status: "failed", error: safeFailure(error) }));
    if (config && actorSession) {
      const storageActions = await cleanupRegistry.cleanupStorageObjects({
        config,
        session: actorSession,
        storageRequest,
        verifyStorageObjectAbsent,
        actions: [],
      }).then((actions) => {
        cleanup = {
          ...(cleanup ?? {}),
          storage: { state: "completed", actions, ...cleanupRegistry.summary() },
        };
        return actions;
      }).catch((error) => {
        cleanup = { ...(cleanup ?? {}), status: "failed", error: safeFailure(error) };
        return [];
      });
      if (storageActions.length > 0) report.steps.push("post_detail_media_storage_cleanup_verified_absent");
    }
    report.cleanup = cleanup;
    if (cleanup?.status?.startsWith("cleanup_verified")) report.steps.push("shared_fixture_cleanup_verified_zero_residue");
  }
  report.finishedAt = new Date().toISOString();
  await mkdir(resolve(options.output, ".."), { recursive: true });
  await writeFile(options.output, `${JSON.stringify(redactReport(report), null, 2)}\n`);
  console.log(`Post detail Web evidence written: ${options.output}`);
}

if (report.status !== "passed" || !cleanup?.status?.startsWith("cleanup_verified")) {
  console.error(`Post detail Web evidence failed: ${report.error ?? cleanup?.error ?? "unknown"}.`);
  process.exitCode = 1;
} else {
  console.log("Post detail Web evidence passed.");
}

async function verifyFeedDetail(page, origin, state) {
  const mediaOpenAnchor = state.feed.videoUrl
    ? `feed.post.video.fullscreen.open.${state.feed.postId}`
    : `feed.post.media.open.${state.feed.postId}`;
  await openRoute(page, origin, `post-${encodeURIComponent(state.feed.postId)}`, `post/${state.feed.postId}`);
  await waitForAttribute(page, "data-quata-feed-detail", state.feed.postId, "feed_detail_marker_missing");
  await waitForAnchor(page, "feed.detail.chrome");
  await waitForAnchor(page, "feed.detail.back");
  await waitForAnchor(page, `feed.post.media.${state.feed.postId}`);
  await waitForAnchor(page, mediaOpenAnchor);
  await waitForAttribute(page, "data-quata-feed-detail-text", state.feed.postBody, "feed_detail_body_marker_missing");
  if (state.feed.videoUrl) {
    await verifyFeedVideoPositionSurvivesReload(page, state);
    await waitForAnchor(page, "feed.detail.chrome");
    await waitForAnchor(page, mediaOpenAnchor);
  }
  const bodyVisibleInAccessibility = await visibleText(page, state.feed.postBody, 2_000);
  report.anchors.push("feed.detail.chrome", "feed.detail.back", `feed.post.media.${state.feed.postId}`, mediaOpenAnchor);
  report.diagnostics = { ...(report.diagnostics ?? {}), feedBodyVisibleInAccessibility: bodyVisibleInAccessibility };
  report.evidence.feedDetail = await screenshot(page, "web-post-detail-feed-open");
  await clickAnchor(page, mediaOpenAnchor);
  await waitForAnchor(page, "fullscreen-media.title");
  report.evidence.feedMedia = await screenshot(page, "web-post-detail-feed-media");
  await clickAnchor(page, "fullscreen-media.close");
  await waitForAnchor(page, "feed.detail.chrome");
  await waitForAnchor(page, mediaOpenAnchor);
  report.steps.push("feed_detail_fullscreen_media_opened_and_returned_to_detail");
  await clickAnchor(page, "feed.detail.back");
  await waitForRoute(page, "feed", "feed_back_route_missing");
  await waitForAttribute(page, "data-quata-feed-detail", "", "feed_detail_marker_not_cleared");
  report.evidence.feedBack = await screenshot(page, "web-post-detail-feed-back");
  report.steps.push("feed_detail_common_chrome_and_back_verified");
}

async function verifyFeedVideoPositionSurvivesReload(page, state) {
  const fullscreenAnchor = `feed.post.video.fullscreen.open.${state.feed.postId}`;
  await clickAnchor(page, fullscreenAnchor);
  await waitForAnchor(page, "fullscreen-media.title");
  await page.waitForFunction(() => {
    const root = document.getElementById("quata-root");
    const videos = [...(root?.shadowRoot?.querySelectorAll("video") ?? root?.querySelectorAll("video") ?? [])];
    const video = videos.at(-1);
    return Boolean(video && Number.isFinite(video.duration) && video.duration > 4);
  }, null, { timeout: 20_000 }).catch(async () => {
    const mediaState = await page.evaluate(() => {
      const root = document.getElementById("quata-root");
      const videos = [...(root?.shadowRoot?.querySelectorAll("video") ?? root?.querySelectorAll("video") ?? [])];
      return videos.map((video) => ({
        readyState: video.readyState,
        networkState: video.networkState,
        duration: Number.isFinite(video.duration) ? video.duration : String(video.duration),
        currentTime: video.currentTime,
        paused: video.paused,
        errorCode: video.error?.code ?? null,
        sourcePath: (() => { try { return new URL(video.currentSrc || video.src).pathname; } catch { return "invalid"; } })(),
      }));
    });
    report.diagnostics = { ...(report.diagnostics ?? {}), feedVideoMediaState: mediaState };
    throw new Error("feed_video_metadata_not_ready_for_reload_probe");
  });
  const seededPositionSeconds = await page.locator("video").last().evaluate((video) => {
    const target = Math.min(8, Math.max(3, video.duration * 0.4));
    video.currentTime = target;
    video.dispatchEvent(new Event("timeupdate"));
    return target;
  });
  const storageKey = `quata.feed.video_positions.v1.${state.actorSession.profileId}`;
  const mediaId = `${state.feed.postId}\u001f${state.feed.videoUrl.trim()}`;
  await page.waitForFunction(({ key, id, minimumMs }) => {
    try {
      const snapshot = JSON.parse(localStorage.getItem(key) ?? "null");
      const entry = snapshot?.entries?.find((candidate) => candidate?.mediaId === id);
      return snapshot?.version === 1 && Number(entry?.positionMs) >= minimumMs;
    } catch {
      return false;
    }
  }, { key: storageKey, id: mediaId, minimumMs: Math.floor(seededPositionSeconds * 1_000) - 500 }, { timeout: 10_000 }).catch(() => {
    throw new Error("feed_video_position_not_persisted_before_reload");
  });
  const persistedPositionMs = await page.evaluate(({ key, id }) => {
    const snapshot = JSON.parse(localStorage.getItem(key));
    return Number(snapshot.entries.find((entry) => entry.mediaId === id).positionMs);
  }, { key: storageKey, id: mediaId });

  await page.reload({ waitUntil: "domcontentloaded", timeout: 60_000 });
  await waitForAttribute(page, "data-quata-feed-detail", state.feed.postId, "feed_detail_marker_missing_after_reload");
  await page.waitForFunction(() => {
    const root = document.getElementById("quata-root");
    const video = root?.shadowRoot?.querySelector("video") ?? root?.querySelector("video");
    return Boolean(video && Number.isFinite(video.duration) && video.duration > 0 && video.currentTime > 0);
  }, null, { timeout: 20_000 }).catch(() => {
    throw new Error("feed_video_position_not_restored_after_reload");
  });
  const restoredPositionMs = await page.locator("video").first().evaluate((video) => {
    video.pause();
    return Math.floor(video.currentTime * 1_000);
  });
  if (restoredPositionMs < persistedPositionMs - 1_500) {
    throw new Error(`feed_video_position_restore_mismatch:${persistedPositionMs}:${restoredPositionMs}`);
  }
  report.evidence.feedVideoPositionReload = {
    storageKeySha256: sha256(storageKey),
    mediaIdSha256: sha256(mediaId),
    persistedPositionMs,
    restoredPositionMs,
  };
  report.steps.push("feed_fullscreen_video_position_persisted_and_restored_after_document_reload");
}

async function verifyOfficialDetail(page, origin, state) {
  await openRoute(page, origin, `official-${encodeURIComponent(state.official.postId)}`, `official/${state.official.postId}`);
  await waitForAnchor(page, "official.detail.chrome");
  await waitForAnchor(page, "official.detail.back");
  await waitForAttribute(page, "data-quata-official-detail-title", state.official.title, "official_detail_title_marker_missing");
  await waitForAttribute(page, "data-quata-official-detail-summary", state.official.summary, "official_detail_summary_marker_missing");
  await waitForAttributeContains(page, "data-quata-official-detail-article", state.official.article, "official_detail_article_marker_missing");
  await waitForAttribute(page, "data-quata-official-detail-link", state.official.linkUrl, "official_detail_link_marker_missing");
  if (options.officialVideoPositionLifecycle) {
    await verifyOfficialVideoPositionSurvivesReload(page, state);
    await waitForAnchor(page, "official.detail.chrome");
  }
  const titleVisibleInAccessibility = await visibleText(page, state.official.title, 2_000);
  await clickAnchor(page, `official.detail.read-more.${state.official.postId}`);
  await waitForAnchor(page, "official.detail.panel");
  await waitForAnchor(page, "official.detail.article");
  await waitForAnchor(page, "official.detail.media");
  await waitForAnchor(page, "official.detail.link");
  await waitForAnchor(page, "official.detail.profile");
  const articleVisibleInAccessibility = await visibleText(page, state.official.article, 2_000);
  const linkVisibleInAccessibility = await visibleText(page, state.official.linkUrl, 2_000);
  report.evidence.officialPanel = await screenshot(page, "web-post-detail-official-panel");
  if (state.official.mediaType === "video") {
    await clickAnchor(page, "official.detail.media");
    await waitForAnchor(page, "fullscreen-media.title");
    const video = page.locator("video").last();
    await video.waitFor({ state: "visible", timeout: 15_000 }).catch(() => {
      throw new Error("official_detail_video_element_missing");
    });
    await page.waitForFunction(() => {
      const root = document.getElementById("quata-root");
      const videos = [...(root?.shadowRoot?.querySelectorAll("video") ?? root?.querySelectorAll("video") ?? [])];
      const element = videos.at(-1);
      return Boolean(element && Number.isFinite(element.duration) && element.duration > 0 && element.currentTime > 0.15);
    }, null, { timeout: 15_000 }).catch(() => {
      throw new Error("official_detail_video_playback_not_observed");
    });
    report.evidence.officialMediaUrlSha256 = sha256(state.official.mediaUrl);
    report.steps.push("official_detail_video_native_browser_playback_observed");
    await clickAnchor(page, "fullscreen-media.close");
  } else {
    const mediaPopupPromise = page.waitForEvent("popup", { timeout: 10_000 });
    await clickAnchor(page, "official.detail.media");
    const mediaPopup = await mediaPopupPromise.catch(() => null);
    if (!mediaPopup) throw new Error("official_detail_media_browser_viewer_missing");
    await mediaPopup.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => {});
    if (mediaPopup.url() !== state.official.mediaUrl) throw new Error("official_detail_media_browser_viewer_url_mismatch");
    report.evidence.officialMediaUrlSha256 = sha256(mediaPopup.url());
    await mediaPopup.close();
  }
  await waitForAnchor(page, "official.detail.panel");
  report.steps.push("official_detail_media_browser_viewer_opened_and_returned_to_panel");
  await clickAnchor(page, "official.detail.profile");
  await waitForAttribute(page, "data-quata-member-profile-id", state.targetSession.profileId, "official_detail_profile_route_missing", 20_000);
  await waitForAnchor(page, "public-profile.back");
  report.evidence.officialProfile = await screenshot(page, "web-post-detail-official-profile");
  await clickAnchor(page, "public-profile.back");
  await waitForAttributeCleared(page, "data-quata-member-profile-id", "official_detail_profile_route_not_cleared", 20_000);
  await openRoute(page, origin, `official-${encodeURIComponent(state.official.postId)}`, `official/${state.official.postId}`);
  await waitForAnchor(page, "official.detail.back");
  report.anchors.push(
    "official.detail.chrome",
    "official.detail.back",
    `official.detail.read-more.${state.official.postId}`,
    "official.detail.panel",
    "official.detail.article",
    "official.detail.media",
    "official.detail.link",
    "official.detail.profile",
    "official.detail.panel.close",
  );
  report.diagnostics = {
    ...(report.diagnostics ?? {}),
    officialTitleVisibleInAccessibility: titleVisibleInAccessibility,
    officialArticleVisibleInAccessibility: articleVisibleInAccessibility,
    officialLinkVisibleInAccessibility: linkVisibleInAccessibility,
  };
  report.evidence.officialDetail = await screenshot(page, "web-post-detail-official-open");
  await clickAnchor(page, "official.detail.back");
  await waitForRoute(page, "official", "official_back_route_missing");
  report.evidence.officialBack = await screenshot(page, "web-post-detail-official-back");
  report.steps.push("official_detail_common_chrome_and_back_verified");
}

async function verifyOfficialVideoPositionSurvivesReload(page, state) {
  await waitForAnchor(page, "official.media.open");
  await clickAnchor(page, "official.media.open");
  await waitForAnchor(page, "fullscreen-media.title");
  const video = page.locator("video").last();
  await page.waitForFunction(() => {
    const root = document.getElementById("quata-root");
    const videos = [...(root?.shadowRoot?.querySelectorAll("video") ?? root?.querySelectorAll("video") ?? [])];
    const element = videos.at(-1);
    return Boolean(element && Number.isFinite(element.duration) && element.duration > 4);
  }, null, { timeout: 20_000 }).catch(() => {
    throw new Error("official_video_metadata_not_ready_for_reload_probe");
  });
  const seededPositionSeconds = await video.evaluate((element) => {
    const target = Math.min(8, Math.max(3, element.duration * 0.4));
    element.currentTime = target;
    element.dispatchEvent(new Event("timeupdate"));
    return target;
  });
  const storageKey = `quata.official.video_positions.v1.${state.actorSession.profileId}`;
  const mediaId = `${state.official.postId}\u001f${state.official.mediaUrl.trim()}`;
  await page.waitForFunction(({ key, id, minimumMs }) => {
    try {
      const snapshot = JSON.parse(localStorage.getItem(key) ?? "null");
      const entry = snapshot?.entries?.find((candidate) => candidate?.mediaId === id);
      return snapshot?.version === 1 && Number(entry?.positionMs) >= minimumMs;
    } catch {
      return false;
    }
  }, { key: storageKey, id: mediaId, minimumMs: Math.floor(seededPositionSeconds * 1_000) - 500 }, { timeout: 10_000 }).catch(() => {
    throw new Error("official_video_position_not_persisted_before_reload");
  });
  const persistedPositionMs = await page.evaluate(({ key, id }) => {
    const snapshot = JSON.parse(localStorage.getItem(key));
    return Number(snapshot.entries.find((entry) => entry.mediaId === id).positionMs);
  }, { key: storageKey, id: mediaId });

  await page.reload({ waitUntil: "domcontentloaded", timeout: 60_000 });
  await waitForAttribute(page, "data-quata-official-detail-title", state.official.title, "official_detail_title_marker_missing_after_reload");
  await waitForAnchor(page, "official.media.open");
  await clickAnchor(page, "official.media.open");
  await waitForAnchor(page, "fullscreen-media.title");
  await page.waitForFunction(() => {
    const root = document.getElementById("quata-root");
    const videos = [...(root?.shadowRoot?.querySelectorAll("video") ?? root?.querySelectorAll("video") ?? [])];
    const element = videos.at(-1);
    return Boolean(element && Number.isFinite(element.duration) && element.duration > 0 && element.currentTime > 0);
  }, null, { timeout: 20_000 }).catch(() => {
    throw new Error("official_video_position_not_restored_after_reload");
  });
  const restoredPositionMs = await page.locator("video").last().evaluate((element) => {
    element.pause();
    return Math.floor(element.currentTime * 1_000);
  });
  if (restoredPositionMs < persistedPositionMs - 1_500) {
    throw new Error(`official_video_position_restore_mismatch:${persistedPositionMs}:${restoredPositionMs}`);
  }
  report.evidence.officialVideoPositionReload = {
    storageKeySha256: sha256(storageKey),
    mediaIdSha256: sha256(mediaId),
    persistedPositionMs,
    restoredPositionMs,
  };
  report.steps.push("official_fullscreen_video_position_persisted_and_restored_after_document_reload");
  await clickAnchor(page, "fullscreen-media.close");
}

async function openRoute(page, origin, fragment, expectedRoute) {
  await page.goto(`${origin}/?quata-post-detail-e2e=1&route-reload=${Date.now()}#${fragment}`, {
    waitUntil: "domcontentloaded",
    timeout: 60_000,
  });
  await waitForRoute(page, expectedRoute, `route_missing:${expectedRoute}`, 35_000);
  await delay(1_200);
}

async function waitForRoute(page, expectedRoute, error, timeout = 15_000) {
  await page.waitForFunction(
    (route) => document.documentElement.getAttribute("data-quata-shell-route") === route,
    expectedRoute,
    { timeout },
  ).catch(() => {
    throw new Error(error);
  });
}

async function waitForAttribute(page, name, value, error, timeout = 15_000) {
  await page.waitForFunction(
    ({ name, value }) => document.documentElement.getAttribute(name) === value,
    { name, value },
    { timeout },
  ).catch(() => {
    throw new Error(error);
  });
}

async function waitForAttributeContains(page, name, value, error, timeout = 15_000) {
  await page.waitForFunction(
    ({ name, value }) => String(document.documentElement.getAttribute(name) ?? "").includes(value),
    { name, value },
    { timeout },
  ).catch(() => {
    throw new Error(error);
  });
}

async function waitForAttributeCleared(page, name, error, timeout = 15_000) {
  await page.waitForFunction(
    (name) => !document.documentElement.hasAttribute(name) || document.documentElement.getAttribute(name) === "",
    name,
    { timeout },
  ).catch(() => {
    throw new Error(error);
  });
}

async function waitForAnchor(page, tag, timeout = 15_000) {
  const locator = await anchorLocator(page, tag, timeout);
  await locator.waitFor({ state: "attached", timeout: 1_000 });
  return locator;
}

async function clickAnchor(page, tag) {
  const locator = await anchorLocator(page, tag, 15_000);
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  if (await locator.click({ timeout: 2_000 }).then(() => true).catch(() => false)) {
    await delay(500);
    return;
  }
  const box = await locator.boundingBox().catch(() => null);
  if (!box || box.width <= 0 || box.height <= 0) throw new Error(`anchor_not_visible:${tag}`);
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  await delay(500);
}

async function anchorLocator(page, tag, timeout) {
  const deadline = Date.now() + timeout;
  const scrollPattern = [0, -700, -700, -700, 1400, 700, 700, -1400];
  let index = 0;
  while (Date.now() < deadline) {
    const locator = page.locator(`[id=${cssString(tag)}], [aria-label*=${cssString(tag)}], [title*=${cssString(tag)}]`).first();
    if (await locator.count()) {
      const box = await locator.boundingBox().catch(() => null);
      if (box && box.width > 0 && box.height > 0) return locator;
    }
    const delta = scrollPattern[index % scrollPattern.length];
    index += 1;
    if (delta !== 0) await page.mouse.wheel(0, delta).catch(() => {});
    await delay(250);
  }
  throw new Error(`missing_stable_anchor:${tag}`);
}

async function visibleText(page, text, timeout = 15_000) {
  const compactNeedle = compact(text);
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const foundInDom = await page.evaluate((needle) => {
      const compact = (value) => String(value ?? "").replace(/\s+/g, "");
      return compact(document.body?.innerText ?? document.body?.textContent ?? "").includes(needle);
    }, compactNeedle).catch(() => false);
    if (foundInDom) return true;
    const foundInAccessibility = await accessibilityTreeContainsText(page, compactNeedle);
    if (foundInAccessibility) return true;
    await page.mouse.wheel(0, 420).catch(() => {});
    await delay(250);
  }
  return false;
}

async function accessibilityTreeContainsText(page, compactNeedle) {
  const snapshot = await page.accessibility?.snapshot({ interestingOnly: false }).catch(() => null);
  const stack = snapshot ? [snapshot] : [];
  while (stack.length) {
    const node = stack.pop();
    const text = compact(`${node?.name ?? ""} ${node?.value ?? ""} ${node?.description ?? ""}`);
    if (text.includes(compactNeedle)) return true;
    for (const child of node?.children ?? []) stack.push(child);
  }
  return false;
}

async function screenshot(page, name) {
  await mkdir(options.evidenceDir, { recursive: true });
  const path = join(options.evidenceDir, `${name}.png`);
  await page.screenshot({ path, fullPage: true });
  return path;
}

async function withDatabase(callback) {
  const [connectionString, ca] = await Promise.all([
    readFile(process.env.SUPABASE_DB_URL_FILE?.trim() || defaultDbUrlFile, "utf8"),
    readFile(process.env.SUPABASE_DB_TLS_CA_FILE?.trim() || defaultDbTlsCaFile, "utf8"),
  ]);
  const parsedConnection = new URL(connectionString.trim());
  parsedConnection.searchParams.delete("sslmode");
  const client = new pg.Client({
    connectionString: parsedConnection.toString(),
    ssl: { ca, rejectUnauthorized: true, servername: parsedConnection.hostname },
  });
  await client.connect();
  try {
    return await callback(client);
  } finally {
    await client.end().catch(() => {});
  }
}

async function publicConfig() {
  const source = await readFile(new URL("../core/src/commonMain/kotlin/com/quata/core/config/QuataPublicBackendConfig.kt", import.meta.url), "utf8");
  const baseUrl = /SUPABASE_URL\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  const key = /SUPABASE_PUBLISHABLE_KEY\s*=\s*"([^"]+)"/.exec(source)?.[1];
  if (!baseUrl || !key) throw new Error("missing_public_supabase_configuration");
  return { baseUrl, url: baseUrl, key };
}

async function storageRequest(config, session, path, options, prefix) {
  let response;
  try {
    response = await fetch(`${config.baseUrl}${path}`, {
      ...options,
      headers: {
        apikey: config.key,
        ...(options.headers ?? {}),
        ...(session?.accessToken ? { authorization: `Bearer ${session.accessToken}` } : {}),
      },
      signal: AbortSignal.timeout(20_000),
    });
  } catch {
    throw new Error(`${prefix}:network`);
  }
  if (!response.ok) throw new Error(`${prefix}:http_${response.status}`);
  return await response.text();
}

async function verifyStorageObjectAbsent(bucket, storagePath) {
  await withDatabase(async (client) => {
    const result = await client.query(
      "select count(*)::int as count from storage.objects where bucket_id = $1 and name = $2",
      [bucket, storagePath],
    );
    if (Number(result.rows[0]?.count ?? 0) !== 0) throw new Error("cleanup_residue_detected:storage_object");
  });
}

async function wordpressBaseUrl() {
  const source = await readFile(new URL("../web/src/wasmJsMain/kotlin/com/quata/web/WebRuntimeConfiguration.kt", import.meta.url), "utf8");
  const url = /wordpressBaseUrl:\s*String\s*=\s*"([^"]+)"/.exec(source)?.[1]?.replace(/\/+$/, "");
  if (!url) throw new Error("missing_public_wordpress_configuration");
  return url;
}

async function login(config, user, label) {
  const response = await fetch(`${config.baseUrl}/functions/v1/quata-auth-bridge`, {
    method: "POST",
    headers: { apikey: config.key, "content-type": "application/json", "x-client-info": "quata-post-detail-web-evidence" },
    body: JSON.stringify({
      action: "web_login",
      country_code: String(user.country_code),
      phone_local: localPhone(user.country_code, user.phone),
      password: String(user.password),
      client_instance_id: `${label}-${randomUUID()}`,
    }),
    signal: AbortSignal.timeout(30_000),
  }).catch(() => null);
  if (!response) throw new Error("public_auth_request_failed:network");
  const text = await response.text();
  const payload = JSON.parse(text || "{}");
  if (!response.ok) throw new Error(`public_auth_request_failed:http_${response.status}`);
  const session = payload?.session;
  const profile = payload?.profile;
  const webSession = payload?.web_session;
  if (!session?.access_token || !session?.refresh_token || !webSession?.token || !uuid.test(profile?.id ?? "")) {
    throw new Error("invalid_auth_response");
  }
  return {
    profileId: profile.id,
    displayName: String(profile.display_name ?? profile.displayName ?? "").trim(),
    accessToken: session.access_token,
    refreshToken: session.refresh_token,
    webSessionToken: webSession.token,
    expiresAt: Number(session.expires_at ?? Math.floor(Date.now() / 1000) + Number(session.expires_in ?? 3600)),
    clientInstanceId: `${label}-${randomUUID()}`,
  };
}

async function loadCredentials() {
  const credentials = JSON.parse(await readFile(process.env.QUATA_POST_DETAIL_CREDENTIALS_FILE?.trim() || defaultCredentialsFile, "utf8"));
  for (const profile of ["a", "b"]) for (const field of ["country_code", "phone", "password"]) {
    if (!credentials?.[profile]?.[field]) throw new Error(`credentials_missing:${profile}.${field}`);
  }
  return credentials;
}

async function startServer(root, wordpressBase, publicBackend) {
  let origin;
  const raw = createServer(async (request, response) => {
    try {
      if (!origin) throw new Error("server_origin_missing");
      const url = new URL(request.url ?? "/", origin);
      if (url.pathname === "/favicon.ico") return response.writeHead(204).end();
      if (url.pathname.startsWith("/wordpress-proxy/")) return proxyWordpressRequest(request, response, wordpressBase, url);
      const file = resolve(root, `.${url.pathname === "/" ? "/index.html" : decodeURIComponent(url.pathname)}`);
      if (!(await stat(file).catch(() => null))?.isFile()) return response.writeHead(404).end();
      response.writeHead(200, {
        "Content-Type": contentType(file),
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Embedder-Policy": "require-corp",
        "Cache-Control": "no-store",
      });
      response.end(await readStaticFileWithEvidenceConfig(file, publicBackend));
    } catch {
      response.writeHead(500).end();
    }
  });
  await new Promise((ok, fail) => {
    raw.once("error", fail);
    raw.listen(0, "127.0.0.1", ok);
  });
  const address = raw.address();
  if (!address || typeof address === "string") throw new Error("static_server_start_failed");
  origin = `http://127.0.0.1:${address.port}`;
  return { origin, close: () => new Promise((ok, fail) => raw.close((error) => error ? fail(error) : ok())) };
}

async function readStaticFileWithEvidenceConfig(file, publicBackend) {
  if (!file.toLowerCase().endsWith("index.html")) return readFile(file);
  const body = await readFile(file, "utf8");
  return body
    .replace(/<meta name="quata-supabase-url" content="[^"]*">/, `<meta name="quata-supabase-url" content="${htmlAttr(publicBackend.baseUrl)}">`)
    .replace(/<meta name="quata-supabase-publishable-key" content="[^"]*">/, `<meta name="quata-supabase-publishable-key" content="${htmlAttr(publicBackend.key)}">`);
}

async function proxyWordpressRequest(request, response, wordpressBase, url) {
  const target = `${wordpressBase}${url.pathname.replace(/^\/wordpress-proxy/, "")}${url.search}`;
  const upstream = await fetch(target, { method: request.method, headers: wordpressProxyHeaders(request), signal: AbortSignal.timeout(120_000) });
  response.writeHead(upstream.status, { "Content-Type": upstream.headers.get("content-type") ?? "application/octet-stream", "Cache-Control": "no-store" });
  response.end(Buffer.from(await upstream.arrayBuffer()));
}

function wordpressProxyHeaders(request) {
  const headers = {};
  for (const [key, value] of Object.entries(request.headers)) {
    const lower = key.toLowerCase();
    if (["host", "connection", "content-length"].includes(lower)) continue;
    headers[key] = Array.isArray(value) ? value.join(", ") : value;
  }
  return headers;
}

function parseArgs(args) {
  const parsed = {
    distribution: resolve("web/build/dist/wasmJs/productionExecutable"),
    chrome: process.env.QUATA_CHROME_PATH || "C:/Program Files/Google/Chrome/Application/chrome.exe",
    output: resolve("build-reports/web/post-detail-evidence.json"),
    evidenceDir: resolve("build-reports/web/post-detail-evidence"),
    headless: true,
    feedVideo: false,
    officialVideo: false,
    officialVideoPositionLifecycle: false,
  };
  for (let index = 0; index < args.length; index += 1) {
    const key = args[index];
    if (key === "--headed") {
      parsed.headless = false;
      continue;
    }
    if (key === "--feed-video") {
      parsed.feedVideo = true;
      parsed.output = resolve("build-reports/web/post-detail-feed-video-evidence.json");
      parsed.evidenceDir = resolve("build-reports/web/post-detail-feed-video-evidence");
      continue;
    }
    if (key === "--official-video") {
      parsed.officialVideo = true;
      parsed.output = resolve("build-reports/web/post-detail-official-video-evidence.json");
      parsed.evidenceDir = resolve("build-reports/web/post-detail-official-video-evidence");
      continue;
    }
    if (key === "--official-video-position-lifecycle") {
      parsed.officialVideo = true;
      parsed.officialVideoPositionLifecycle = true;
      parsed.output = resolve("build-reports/web/official-video-position-lifecycle-evidence.json");
      parsed.evidenceDir = resolve("build-reports/web/official-video-position-lifecycle-evidence");
      continue;
    }
    const value = args[index + 1];
    if (!["--dist", "--chrome", "--out", "--evidence-dir"].includes(key) || !value || value.startsWith("--")) throw new Error("invalid_arguments");
    index += 1;
    if (key === "--dist") parsed.distribution = resolve(value);
    if (key === "--chrome") parsed.chrome = resolve(value);
    if (key === "--out") parsed.output = resolve(value);
    if (key === "--evidence-dir") parsed.evidenceDir = resolve(value);
  }
  return parsed;
}

function contentType(path) {
  return new Map([
    [".html", "text/html; charset=utf-8"],
    [".js", "text/javascript; charset=utf-8"],
    [".mjs", "text/javascript; charset=utf-8"],
    [".wasm", "application/wasm"],
    [".json", "application/json"],
    [".css", "text/css"],
    [".svg", "image/svg+xml"],
    [".webp", "image/webp"],
    [".png", "image/png"],
  ]).get(extname(path).toLowerCase()) ?? "application/octet-stream";
}

function localPhone(countryCode, phone) {
  const country = String(countryCode ?? "").replace(/\D/g, "");
  const digits = String(phone ?? "").replace(/\D/g, "");
  return digits.startsWith(country) ? digits.slice(country.length) : digits;
}

function cssString(value) {
  return `"${String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

function compact(value) {
  return String(value ?? "").replace(/\s+/g, "");
}

function sha256(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

function htmlAttr(value) {
  return String(value).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function gitMetadata() {
  return {
    head: execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim(),
    branch: execFileSync("git", ["branch", "--show-current"], { encoding: "utf8" }).trim(),
    workingTreeDirty: execFileSync("git", ["status", "--porcelain", "--untracked-files=no"], { encoding: "utf8" }).trim().length > 0,
  };
}

function redactReport(value) {
  return JSON.parse(JSON.stringify(value, (key, entry) => {
    if (/token|password|authorization|apikey|secret/i.test(key)) return "[REDACTED]";
    return entry;
  }));
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(0, 500);
}

function loadPackage(name) {
  const require = createRequire(import.meta.url);
  try {
    return require(name);
  } catch (error) {
    const extra = process.env.QUATA_NODE_MODULES?.trim() || "C:/Users/PC/StudioProjects/quata/node_modules";
    try {
      return require(require.resolve(name, { paths: [extra] }));
    } catch {}
    throw error;
  }
}
