import assert from 'node:assert/strict';
import { inspectBackendRequest } from './backend-compatibility-request-policy.mjs';
const base = 'https://project.supabase.co';
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'GET', headers: {} }, base).allowed, true);
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'POST', headers: {} }, base).reason, 'supabase_method_forbidden');
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'HEAD', headers: {} }, base).reason, 'supabase_method_forbidden');
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts`, method: 'GET', headers: { Authorization: 'Bearer x' } }, base).reason, 'supabase_credentials_forbidden');
assert.equal(inspectBackendRequest({ url: `${base}/storage/v1/x`, method: 'GET', headers: {} }, base).reason, 'supabase_path_forbidden');
assert.equal(inspectBackendRequest({ url: `${base}/rest/v1/posts?access_token=x`, method: 'GET', headers: {} }, base).reason, 'supabase_credentials_forbidden');
