import assert from 'node:assert/strict';
import { publicPostIdFromPayload, detailEvidence } from './backend-compatibility-feed-detail.mjs';
assert.equal(publicPostIdFromPayload('[{"id":"post_7"}]'), 'post_7');
assert.equal(publicPostIdFromPayload('[{"id":"../../x"}]'), null);
assert.equal(publicPostIdFromPayload('bad'), null);
assert.equal(detailEvidence([{ method:'GET', status:200, table:'posts', query:'id=eq.post_7', payloadPostId:'post_7' }], 'post_7'), true);
assert.equal(detailEvidence([{ method:'GET', status:200, table:'posts', query:'select=id&order=created_at.desc&id=eq.post_7', payloadPostId:'post_7' }], 'post_7'), true);
assert.equal(detailEvidence([{ method:'GET', status:200, table:'posts', query:'select=id', payloadPostId:'post_7' }], 'post_7'), false);
assert.equal(detailEvidence([{ method:'GET', status:200, table:'posts', query:'id=eq.post_7', payloadPostId:'other' }], 'post_7'), false);
