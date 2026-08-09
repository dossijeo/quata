import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const source = (relative) => readFile(resolve(root, relative), 'utf8');

test('Official editor previews do not expose inert Live or ranking actions', async () => {
  const [
    preview,
    card,
    android,
    web,
    ios,
  ] = await Promise.all([
    source('feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialEditorPostPreviewContent.kt'),
    source('feature/official/src/commonMain/kotlin/com/quata/feature/official/presentation/OfficialPostCardContent.kt'),
    source('app/src/main/java/com/quata/feature/official/presentation/OfficialPostEditorScreen.kt'),
    source('web/src/wasmJsMain/kotlin/com/quata/web/WebOfficialHost.kt'),
    source('feature/official/src/iosMain/kotlin/com/quata/feature/official/presentation/QuataOfficialViewController.kt'),
  ]);

  assert.match(
    preview,
    /actionRail:\s*\(@Composable\s*\(isLandscape:\s*Boolean,\s*Modifier\)\s*->\s*Unit\)\?\s*=\s*null/,
    'the common editor preview must allow hosts to omit the action rail',
  );
  assert.match(
    card,
    /actionRail:\s*\(@Composable\s*\(isLandscape:\s*Boolean,\s*Modifier\)\s*->\s*Unit\)\?/,
    'the shared card must support previews without reserving fake action callbacks',
  );
  assert.match(card, /actionRail\?\.invoke/);

  for (const [name, content] of Object.entries({ android, web, ios })) {
    assert.doesNotMatch(content, /onOpenLive\s*=\s*\{\s*\}/, `${name} preview must not wire an inert Live callback`);
    assert.doesNotMatch(content, /QuataFeedOverflowActionButton[\s\S]{0,500}onOpenLive\s*=\s*\{\s*\}/, `${name} preview must not expose a fake overflow Live action`);
  }
});
