import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const commentsHeader = await readFile(
  'designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataCommentsPanelHeaderContent.kt',
  'utf8',
);
const conversationAvatar = await readFile(
  'feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatConversationAvatarContent.kt',
  'utf8',
);
const deliveryIndicators = await readFile(
  'feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatBubbleIndicatorsContent.kt',
  'utf8',
);
const reelHost = await readFile(
  'feature/feed/src/commonMain/kotlin/com/quata/feature/feed/presentation/FeedReelVideoPlaybackHostContent.kt',
  'utf8',
);

test('common UI controls use portable Material ImageVectors instead of font glyphs', () => {
  assert.match(commentsHeader, /Icons\.Filled\.ChatBubble/);
  assert.match(commentsHeader, /contentDescription = null/);
  assert.match(commentsHeader, /modifier = Modifier\.size\(16\.dp\)/);
  assert.doesNotMatch(commentsHeader, /\\uD83D\\uDCAC|💬/);

  assert.match(conversationAvatar, /Icons\.Filled\.NotificationsOff/);
  assert.match(conversationAvatar, /contentDescription = null/);
  assert.doesNotMatch(conversationAvatar, /\\uD83D\\uDD15|🔕/);

  assert.match(deliveryIndicators, /Icons\.Filled\.Done/);
  assert.match(deliveryIndicators, /Icons\.Filled\.DoneAll/);
  assert.match(deliveryIndicators, /modifier = Modifier\.size\(12\.dp\)/);
  assert.doesNotMatch(deliveryIndicators, /Text\("✓"|Text\("✓✓"/);

  assert.match(reelHost, /Icons\.Filled\.PlayArrow/);
  assert.match(reelHost, /Icons\.Filled\.Pause/);
  assert.match(reelHost, /FeedReelPlaybackFeedbackIconContent\(/);
  assert.match(reelHost, /RoundedCornerShape\(46\.dp\)/);
  assert.match(reelHost, /Color\.Black\.copy\(alpha = 0\.38f\)/);
  assert.match(reelHost, /padding\(horizontal = 20\.dp, vertical = 10\.dp\)/);
  assert.match(reelHost, /modifier = Modifier\.size\(54\.dp\)/);
  assert.doesNotMatch(reelHost, /ReelPlaybackFeedbackContent\(|FeedReelPlaybackFeedbackTextContent|"▶"|"Ⅱ"/);
});
