import assert from 'node:assert/strict';
import { inspectBackendRequest, publicMediaUrlsFromPayload } from './backend-compatibility-request-policy.mjs';
const base = 'https://project.supabase.co';
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
assert.equal(publicMedia({ url: `${base}/storage/v1/object/public/community-media/%2e%2e%2fpost-7.png` }).reason, 'supabase_path_forbidden');
assert.equal(inspectBackendRequest({
  url: 'https://evil.example/redirected.png', method: 'GET', headers: {}, resourceType: 'image',
  accreditedMediaUrls: accredited, redirectedFromUrl: image,
}, base).reason, 'supabase_redirect_cross_origin');

const payloadUrls = publicMediaUrlsFromPayload(JSON.stringify([
  { id: 'post_7', image_url: image, video_url: video },
  { id: 'private', image_url: `${base}/storage/v1/object/private/community-media/nope.png` },
  { id: 'signed', video_url: `${base}/storage/v1/object/public/community-media/nope.mp4?token=secret` },
  { id: 'foreign', image_url: 'https://evil.example/pixel.png' },
]), base);
assert.deepEqual(payloadUrls.sort(), [image, video].sort(), 'only safe same-origin public media payload URLs can accredit a request');
