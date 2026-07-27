import assert from "node:assert/strict";
import test from "node:test";
import {
  expectedLocalStub,
  inspectAccreditedPublicMediaResponse,
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
    { method: "GET", url: "https://challenges.cloudflare.com/turnstile/v0/api.js" },
    { method: "GET", url: `${TURNSTILE_BOOTSTRAP_URL}&debug=1` },
    { method: "GET", url: "https://sub.challenges.cloudflare.com/turnstile/v0/api.js?render=explicit" },
  ]) assert.equal(expectedLocalStub(request), null);
});

test("public Storage media requires an admitted exact 2xx image/video response", () => {
  assert.deepEqual(inspectAccreditedPublicMediaResponse({
    url: image, requestUrl: image, method: "GET", status: 200, contentType: "image/png; charset=binary",
    resourceType: "image", requestAllowed: true, accreditedMediaUrls: accredited,
  }), {
    kind: "publicMedia", requestCorrelated: true, status: 200, contentType: "image/png", resourceType: "image", accepted: true,
  });
  assert.equal(inspectAccreditedPublicMediaResponse({
    url: video, requestUrl: video, method: "GET", status: 206, contentType: "video/mp4",
    resourceType: "media", requestAllowed: true, accreditedMediaUrls: accredited,
  }).accepted, true);
});

test("Storage 404, invalid content type and a mismatched response fail closed", () => {
  const baseResponse = {
    url: image, requestUrl: image, method: "GET", status: 200, contentType: "image/png",
    resourceType: "image", requestAllowed: true, accreditedMediaUrls: accredited,
  };
  assert.equal(inspectAccreditedPublicMediaResponse({ ...baseResponse, status: 404 }).reason, "status_not_2xx");
  assert.equal(inspectAccreditedPublicMediaResponse({ ...baseResponse, contentType: "text/html" }).reason, "content_type_not_image");
  assert.equal(inspectAccreditedPublicMediaResponse({ ...baseResponse, url: `${base}/storage/v1/object/public/community-media/other.png` }).reason, "response_request_mismatch");
  assert.equal(inspectAccreditedPublicMediaResponse({ ...baseResponse, requestAllowed: false }).reason, "request_not_admitted");
});