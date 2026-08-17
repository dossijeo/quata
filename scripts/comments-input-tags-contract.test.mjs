import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const input = readFileSync(join(root, 'designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataCommentInputContent.kt'), 'utf8');
const feed = readFileSync(join(root, 'feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt'), 'utf8');
const official = readFileSync(join(root, 'feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialCommentsPanelContent.kt'), 'utf8');

for (const tag of ['inputTestTag', 'sendTestTag']) assert.match(input, new RegExp(tag));
for (const prefix of ['feed.comments.input', 'feed.comments.send', 'feed.comments.emoji']) assert.match(feed, new RegExp(prefix.replaceAll('.', '\\.')));
for (const prefix of ['official.comments.input', 'official.comments.send', 'official.comments.emoji']) assert.match(official, new RegExp(prefix.replaceAll('.', '\\.')));
console.log('Comments input tags contract passed.');
