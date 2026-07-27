import assert from 'node:assert/strict';
import { accreditPublicMediaUrlsFromResponse, inspectBackendRequest, publicMediaUrlsFromPayload } from './backend-compatibility-request-policy.mjs';
const base = 'https://project.supabase.co';
const app = 'http://127.0.0.1:40124';
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'GET', headers: { apikey: 'public' } }, base).allowed, true);
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'GET', headers: {} }, base).reason, 'supabase_publishable_key_missing');
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'POST', headers: {} }, base).reason, 'supabase_method_forbidden');
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'HEAD', headers: {} }, base).reason, 'supabase_method_forbidden');
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'GET', headers: { Authorization: 'Bearer x' } }, base).reason, 'supabase_credentials_forbidden');
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'GET', headers: { Authorization: 'Basic x' } }, base).reason, 'supabase_credentials_forbidden');
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'GET', headers: { authorization: 'opaque-session' } }, base).reason, 'supabase_credentials_forbidden');
assert.equal(inspectBackendRequest({ url: `${base}/storage/v1/x`, method: 'GET', headers: {} }, base).reason, 'supabase_path_forbidden');
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts?access_token=x`, method: 'GET', headers: {} }, base).reason, 'supabase_credentials_forbidden');

const image = `${base}/storage/v1/object/public/community-media/post-7.png`;
const video = `${base}/storage/v1/object/public/community-media/post-7.mp4?width=640`;
const accredited = new Set([image, video]);
const publicMedia = (overrides = {}) => inspectBackendRequest({
  url: image,
  method: 'GET',
  headers: {},
  resourceType: 'image',
  accreditedMediaUrls: accredited,
  ...overrides,
}, base);

assert.equal(publicMedia().allowed, true, 'only exact public media from an accredited payload is admitted');
assert.equal(inspectBackendRequest({ url: video, method: 'GET', headers: {}, resourceType: 'media', accreditedMediaUrls: accredited }, base).allowed, true);
assert.equal(publicMedia({ accreditedMediaUrls: new Set() }).reason, 'supabase_storage_url_not_accredited', 'media arriving before its feed/detail response is recorded fails closed');
assert.equal(publicMedia({ url: `${base}/storage/v1/object/public/community-media/other.png` }).reason, 'supabase_storage_url_not_accredited');
assert.equal(publicMedia({ url: `${base}/storage/v1/object/private/community-media/post-7.png` }).reason, 'supabase_path_forbidden');
assert.equal(publicMedia({ url: `${base}/storage/v1/object/sign/community-media/post-7.png` }).reason, 'supabase_path_forbidden');
assert.equal(publicMedia({ url: `${base}/storage/v1/object/public/community-media/post-7.png?token=secret` }).reason, 'supabase_credentials_forbidden');
assert.equal(publicMedia({ url: `${base}/storage/v1/object/public/community-media/post-7.png?signature=secret` }).reason, 'supabase_credentials_forbidden');
assert.equal(publicMedia({ url: `${base}/storage/v1/object/public/community-media/post-7.png?signed=secret` }).reason, 'supabase_credentials_forbidden');
assert.equal(publicMedia({ headers: { Authorization: 'Bearer secret' } }).reason, 'supabase_credentials_forbidden');
assert.equal(publicMedia({ method: 'HEAD' }).reason, 'supabase_method_forbidden');
assert.equal(publicMedia({ method: 'POST' }).reason, 'supabase_method_forbidden');
assert.equal(publicMedia({ resourceType: 'fetch' }).reason, 'supabase_storage_resource_type_forbidden');
assert.equal(publicMedia({ resourceType: 'font' }).reason, 'supabase_storage_resource_type_forbidden');
assert.equal(publicMedia({ url: `${base}/storage/v1/object/public/community-media/%2e%2e%2fpost-7.png` }).reason, 'supabase_credentials_forbidden');
assert.equal(inspectBackendRequest({
  url: 'https://evil.example/redirected.png', method: 'GET', headers: {}, resourceType: 'image',
  accreditedMediaUrls: accredited, redirectedFromUrl: image,
}, base).reason, 'redirect_cross_origin');

const payloadUrls = publicMediaUrlsFromPayload(JSON.stringify([
  { id: 'post_7', image_url: image, video_url: video },
  { id: 'private', image_url: `${base}/storage/v1/object/private/community-media/nope.png` },
  { id: 'signed', video_url: `${base}/storage/v1/object/public/community-media/nope.mp4?token=secret` },
  { id: 'foreign', image_url: 'https://evil.example/pixel.png' },
]), base);
assert.deepEqual(payloadUrls.sort(), [image, video].sort(), 'only safe same-origin public media payload URLs can accredit a request');

// Regression mutations: no non-Supabase escape hatch may carry credentials,
// media, an encoded delimiter, or redirect provenance.
assert.equal(inspectBackendRequest({
  url: 'https://evil.example/pixel.png', method: 'GET', headers: { Authorization: 'Bearer leaked' }, resourceType: 'image', applicationOrigin: app,
}, base).reason, 'credentials_forbidden', 'global bearer check precedes origin allowance');
assert.equal(inspectBackendRequest({
  url: 'https://evil.example/pixel.png', method: 'GET', headers: { apikey: 'public-but-not-for-evil' }, resourceType: 'image', applicationOrigin: app,
}, base).reason, 'credentials_forbidden', 'the publishable key cannot leave the configured Supabase origin');
assert.equal(inspectBackendRequest({
  url: 'https://evil.example/pixel.png', method: 'GET', headers: {}, resourceType: 'image', applicationOrigin: app,
}, base).reason, 'cross_origin_forbidden', 'foreign media is not generically allowed');
assert.equal(inspectBackendRequest({
  url: 'https://user:password@project.supabase.co/rest/v1/community_posts', method: 'GET', headers: { apikey: 'public' }, applicationOrigin: app,
}, base).reason, 'supabase_credentials_forbidden', 'userinfo is a credential bypass');
assert.equal(inspectBackendRequest({
  url: `${base}/storage/v1/object/public/community-media/%252e%252e%252fpost.png`, method: 'GET', headers: {}, resourceType: 'image', accreditedMediaUrls: accredited, applicationOrigin: app,
}, base).reason, 'supabase_credentials_forbidden', 'double-encoded traversal fails before path approval');
assert.equal(inspectBackendRequest({
  url: `${base}/storage/v1/object/public/community-media/post%255csecret.png`, method: 'GET', headers: {}, resourceType: 'image', accreditedMediaUrls: accredited, applicationOrigin: app,
}, base).reason, 'supabase_credentials_forbidden', 'double-encoded backslash is forbidden');
assert.equal(inspectBackendRequest({
  url: `${base}/rest/v1/community_posts?%2561ccess%255ftoken=leaked`, method: 'GET', headers: { apikey: 'public' }, applicationOrigin: app,
}, base).reason, 'supabase_credentials_forbidden', 'double-encoded sensitive query key is forbidden');
assert.equal(inspectBackendRequest({
  url: `${base}/rest/v1/community_posts?SeSsIoN=leaked`, method: 'GET', headers: { apikey: 'public' }, applicationOrigin: app,
}, base).reason, 'supabase_credentials_forbidden', 'mixed case sensitive query key is forbidden');
assert.equal(inspectBackendRequest({
  url: `${base}/rest/v1/community_posts?select%3D*`, method: 'GET', headers: { apikey: 'public' }, applicationOrigin: app,
}, base).reason, 'supabase_credentials_forbidden', 'encoded query delimiter fails closed');
assert.equal(inspectBackendRequest({
  url: `${app}/bundle.js`, method: 'GET', headers: {}, applicationOrigin: app,
}, base).allowed, true, 'the local application origin remains permitted');
assert.equal(inspectBackendRequest({
  url: `${base}/rest/v1/community_posts`, method: 'GET', headers: { apikey: 'public' }, applicationOrigin: app,
  redirectedFromUrl: `${app}/redirect`,
}, base).reason, 'redirect_cross_origin', 'redirect chains are rejected rather than accredited');

const goodResponse = {
  url: `${base}/rest/v1/community_posts?select=id,image_url`,
  requestUrl: `${base}/rest/v1/community_posts?select=id,image_url`,
  method: 'GET', headers: { apikey: 'public' }, status: 200, contentType: 'application/json; charset=utf-8',
  resourceType: 'fetch', serviceWorker: false, requestAllowed: true,
  payload: JSON.stringify([{ id: 'post_7', image_url: image }]),
};
assert.deepEqual(accreditPublicMediaUrlsFromResponse(goodResponse, base), [image], 'only a proved direct JSON response accredits exact payload media');
for (const mutation of [
  { requestAllowed: false },
  { url: 'https://evil.example/rest/v1/community_posts', requestUrl: 'https://evil.example/rest/v1/community_posts' },
  { contentType: 'text/html' },
  { serviceWorker: true },
  { redirectedFromUrl: `${base}/rest/v1/redirect` },
  { redirectedToUrl: `${base}/rest/v1/other` },
  { url: `${base}/rest/v1/community_posts/other`, requestUrl: `${base}/rest/v1/community_posts/other` },
  { headers: { apikey: 'public', Authorization: 'Bearer x' } },
]) {
  assert.deepEqual(accreditPublicMediaUrlsFromResponse({ ...goodResponse, ...mutation }, base), [], `accreditation rejects ${Object.keys(mutation)[0]}`);
}
