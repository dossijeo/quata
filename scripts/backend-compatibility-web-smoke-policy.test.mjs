import assert from "node:assert/strict";
import test from "node:test";
import {
  expectedLocalStub,
  inspectAccreditedPublicMediaResponse,
  isPublicStorageMediaRequest,
  isMediaAccreditationRoute,
  TURNSTILE_BOOTSTRAP_URL,
} from "./backend-compatibility-web-smoke-policy.mjs";

const base = "https://project.supabase.co";
const image = `${base}/storage/v1/object/public/community-media/post-7.png`;
const video = `${base}/storage/v1/object/public/community-media/post-7.mp4`;
const accredited = new Set([image, video]);

test("only the exact pinned Turnstile URL is an expected local stub", () => {
  assert.deepEqual(expectedLocalStub({ method: "GET", url: TURNSTILE_BOOTSTRAP_URL }), {
    kind: "localStub", expected: "turnstile-bootstrap",
  });
  for (const request of [
    { method: "POST", url: TURNSTILE_BOOTSTRAP_URL },
    { method: "get", url: TURNSTILE_BOOTSTRAP_URL },
    { method: "Get", url: TURNSTILE_BOOTSTRAP_URL },
    { method: " GET", url: TURNSTILE_BOOTSTRAP_URL },
    { method: "GET ", url: TURNSTILE_BOOTSTRAP_URL },
    { method: "GET", url: "https://challenges.cloudflare.com/turnstile/v0/api.js" },
    { method: "GET", url: `${TURNSTILE_BOOTSTRAP_URL}&debug=1` },
    { method: "GET", url: "https://sub.challenges.cloudflare.com/turnstile/v0/api.js?render=explicit" },
  ]) assert.equal(expectedLocalStub(request), null);
});

test("public Storage media requires an admitted exact 2xx image/video response", () => {
  assert.deepEqual(inspectAccreditedPublicMediaResponse({
    url: image, requestUrl: image, method: "GET", status: 200, contentType: "image/png; charset=binary",
    resourceType: "image", requestAllowed: true, route: "feed", accreditedMediaUrls: accredited,
  }), {
    kind: "publicMedia", requestCorrelated: true, status: 200, contentType: "image/png", resourceType: "image", accepted: true,
  });
  assert.equal(inspectAccreditedPublicMediaResponse({
    url: video, requestUrl: video, method: "GET", status: 206, contentType: "video/mp4",
    resourceType: "media", requestAllowed: true, route: "post/post-7", accreditedMediaUrls: accredited,
  }).accepted, true);
});

test("Storage 404, invalid content type and a mismatched response fail closed", () => {
  const baseResponse = {
    url: image, requestUrl: image, method: "GET", status: 200, contentType: "image/png",
    resourceType: "image", requestAllowed: true, route: "feed", accreditedMediaUrls: accredited,
  };
  assert.equal(inspectAccreditedPublicMediaResponse({ ...baseResponse, status: 404 }).reason, "status_not_2xx");
  assert.equal(inspectAccreditedPublicMediaResponse({ ...baseResponse, contentType: "text/html" }).reason, "content_type_not_image");
  assert.equal(inspectAccreditedPublicMediaResponse({ ...baseResponse, url: `${base}/storage/v1/object/public/community-media/other.png` }).reason, "response_request_mismatch");
  assert.equal(inspectAccreditedPublicMediaResponse({ ...baseResponse, requestAllowed: false }).reason, "request_not_admitted");
});

test("only same-origin public Storage image/video requests may be response-fetched", () => {
  assert.equal(isPublicStorageMediaRequest({ url: image, resourceType: "image" }, base), true);
  assert.equal(isPublicStorageMediaRequest({ url: video, resourceType: "media" }, base), true);
  for (const request of [
    { url: image, resourceType: "fetch" },
    { url: `${base}/storage/v1/object/private/community-media/post-7.png`, resourceType: "image" },
    { url: "https://evil.example/storage/v1/object/public/community-media/post-7.png", resourceType: "image" },
    { url: `${base}/rest/v1/community_posts`, resourceType: "image" },
  ]) assert.equal(isPublicStorageMediaRequest(request, base), false);
});

test("public Storage media is accredited only for its exact feed/detail route", () => {
  const feedOnly = new Set([image]);
  const postOnly = new Set([video]);
  const accepted = inspectAccreditedPublicMediaResponse({
    url: image, requestUrl: image, method: "GET", status: 200, contentType: "image/png",
    resourceType: "image", requestAllowed: true, route: "feed", accreditedMediaUrls: feedOnly,
  });
  assert.equal(accepted.accepted, true, "the same feed route keeps its own accreditation");
  for (const route of ["official", "communities", "post/", "feed/extra", "<unknown>"]) {
    assert.equal(inspectAccreditedPublicMediaResponse({
      url: image, requestUrl: image, method: "GET", status: 200, contentType: "image/png",
      resourceType: "image", requestAllowed: true, route, accreditedMediaUrls: feedOnly,
    }).reason, "route_not_media_accreditable", `cross-route or unknown route fails closed: ${route}`);
  }
  assert.equal(inspectAccreditedPublicMediaResponse({
    url: image, requestUrl: image, method: "GET", status: 404, contentType: "text/html",
    resourceType: "fetch", requestAllowed: true, route: "official", accreditedMediaUrls: feedOnly,
  }).reason, "route_not_media_accreditable", "the route is rejected before status or media-type evaluation");
  assert.equal(inspectAccreditedPublicMediaResponse({
    url: image, requestUrl: image, method: "GET", status: 200, contentType: "image/png",
    resourceType: "image", requestAllowed: true, route: "post/post-7", accreditedMediaUrls: postOnly,
  }).reason, "url_not_accredited", "a detail route cannot reuse feed accreditation");
  assert.equal(inspectAccreditedPublicMediaResponse({
    url: video, requestUrl: video, method: "GET", status: 200, contentType: "video/mp4",
    resourceType: "media", requestAllowed: true, route: "post/post-7", accreditedMediaUrls: postOnly,
  }).accepted, true, "the same detail route keeps its own accreditation");
  assert.equal(inspectAccreditedPublicMediaResponse({
    url: video, requestUrl: video, method: "GET", status: 200, contentType: "video/mp4",
    resourceType: "media", requestAllowed: true, route: "post/post-8", accreditedMediaUrls: new Set(),
  }).reason, "url_not_accredited", "another valid post route cannot reuse detail accreditation");
  assert.equal(isMediaAccreditationRoute("feed"), true);
  assert.equal(isMediaAccreditationRoute("post/post-7"), true);
  assert.equal(isMediaAccreditationRoute("official"), false);
});
