import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const input = readFileSync(join(root, 'designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataCommentInputContent.kt'), 'utf8');
const row = readFileSync(join(root, 'designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataCommentRowContent.kt'), 'utf8');
const replyBanner = readFileSync(join(root, 'designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataReplyTargetBannerContent.kt'), 'utf8');
const feed = readFileSync(join(root, 'feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedScreenHost.kt'), 'utf8');
const official = readFileSync(join(root, 'feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialCommentsPanelContent.kt'), 'utf8');
const profileDialog = readFileSync(join(root, 'feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentsDialogContent.kt'), 'utf8');
const profileRow = readFileSync(join(root, 'feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentRowContent.kt'), 'utf8');
const profileInput = readFileSync(join(root, 'feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileCommentInputContent.kt'), 'utf8');
const androidNeighborhoods = readFileSync(join(root, 'app/src/main/java/com/quata/feature/neighborhoods/data/NeighborhoodRepositoryImpl.kt'), 'utf8');
const iosNeighborhoods = readFileSync(join(root, 'feature/neighborhoods/src/iosMain/kotlin/com/quata/feature/neighborhoods/data/IosNeighborhoodsReadRepository.kt'), 'utf8');
const webFeedRepository = readFileSync(join(root, 'web/src/wasmJsMain/kotlin/com/quata/web/WebFeedRepository.kt'), 'utf8');
const iosFeedRepository = readFileSync(join(root, 'feature/feed/src/iosMain/kotlin/com/quata/feature/feed/data/IosAuthenticatedFeedRepository.kt'), 'utf8');

for (const tag of ['inputTestTag', 'sendTestTag']) assert.match(input, new RegExp(tag));
for (const tag of ['replyTestTagPrefix', 'reportTestTagPrefix', 'replyQuoteTestTagPrefix']) assert.match(row, new RegExp(tag));
for (const tag of ['targetTestTag', 'cancelTestTag']) assert.match(replyBanner, new RegExp(tag));
for (const prefix of ['feed.comments.input', 'feed.comments.send', 'feed.comments.emoji', 'feed.comments.reply.', 'feed.comments.replyTarget.', 'feed.comments.replyCancel.', 'feed.comments.replyTo.']) assert.match(feed, new RegExp(prefix.replaceAll('.', '\\.')));
for (const prefix of ['official.comments.input', 'official.comments.send', 'official.comments.emoji', 'official.comments.reply.', 'official.comments.replyTarget.', 'official.comments.replyCancel.', 'official.comments.replyTo.']) assert.match(official, new RegExp(prefix.replaceAll('.', '\\.')));
for (const prefix of ['public-profile.comments.input', 'public-profile.comments.send', 'public-profile.comments.emoji', 'public-profile.comments.reply.', 'public-profile.comments.replyTarget.', 'public-profile.comments.replyCancel.', 'public-profile.comments.replyTo.']) {
  assert.match(`${profileDialog}\n${profileRow}\n${profileInput}`, new RegExp(prefix.replaceAll('.', '\\.')));
}
assert.match(profileDialog, /copy\([\s\S]*replyToAuthorName = target\?\.authorName[\s\S]*replyToMessage = target\?\.message[\s\S]*replyToCommentId = target\?\.id/);
assert.match(androidNeighborhoods, /comment\.toRemoteCommentBody\(\)/);
assert.match(iosNeighborhoods, /comment\.toRemoteCommentBody\(\)/);
assert.match(webFeedRepository, /comment\.toRemoteCommentBody\(\)/);
assert.match(iosFeedRepository, /comment\.toRemoteCommentBody\(\)/);
console.log('Comments input tags contract passed.');
