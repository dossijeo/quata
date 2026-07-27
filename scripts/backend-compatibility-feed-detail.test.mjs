import assert from 'node:assert/strict';
import { publicPostIdFromPayload, detailEvidence } from './backend-compatibility-feed-detail.mjs';
assert.equal(publicPostIdFromPayload('[{"id":"post_7"}]'), 'post_7');
assert.equal(publicPostIdFromPayload('[{"id":"../../x"}]'), null);
assert.equal(publicPostIdFromPayload('bad'), null);
assert.equal(detailEvidence([{ method:'GET', status:200, table:'posts', postId:'post_7' }], 'post_7'), true);
