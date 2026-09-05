import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const drawable = join(root, 'designsystem/src/commonMain/composeResources/drawable');
const manifest = JSON.parse(readFileSync(join(root, 'tools/community_emoji_atlases.manifest.json'), 'utf8'));
const panel = readFileSync(join(root, 'designsystem/src/commonMain/kotlin/com/quata/core/ui/components/CommunityEmojiPanelContent.kt'), 'utf8');
const panelState = readFileSync(join(root, 'designsystem/src/commonMain/kotlin/com/quata/core/ui/components/CommunityEmojiPanelState.kt'), 'utf8');

assert.equal(manifest.notoEmojiCommit, '8998f5dd683424a73e2314a8c1f1e359c19e8742');
assert.match(manifest.license, /^Apache-2\.0/);
assert.deepEqual(manifest.encoding, { format: 'PNG', paletteColors: 64 });
assert.deepEqual(Object.keys(manifest.sections), ['recent', 'frequent', 'gestures', 'people', 'animals_nature', 'food_drink', 'objects_symbols', 'flags']);
assert.equal(Object.values(manifest.sections).flatMap(section => section.emojis).length, 338);

for (const section of Object.values(manifest.sections)) {
  const file = join(drawable, section.file);
  assert.ok(statSync(file).size > 0, `${section.file} must not be empty`);
  assert.equal(createHash('sha256').update(readFileSync(file)).digest('hex'), section.sha256, `${section.file} hash mismatch`);
  assert.equal(section.columns, 6);
  assert.equal(section.cellPx, 72);
  assert.equal(section.paletteColors, 64);
}

assert.match(panel, /imageResource\(selectedAtlasLayout\.resource\)/);
assert.match(panel, /drawImage\(/);
assert.doesNotMatch(panel, /Text\(emoji[,) ]/);
assert.match(panel, /CommunityEmojiPanelRootTestTag/);
assert.match(panel, /CommunityEmojiPanelSectionsRowTestTag/);
assert.match(panel, /CommunityEmojiPanelGridTestTagPrefix/);
assert.match(panelState, /sealed interface CommunityEmojiPanelState/);
assert.match(panelState, /data object Loading : CommunityEmojiPanelState/);
assert.match(panelState, /data class Ready\(val sections: List<QuataEmojiSection>\) : CommunityEmojiPanelState/);
assert.match(panelState, /data class Empty\(val message: String = "No hay emojis disponibles"\) : CommunityEmojiPanelState/);
assert.match(panelState, /data class Failed\(val message: String = "No se pudieron cargar los emojis"\) : CommunityEmojiPanelState/);
assert.match(panelState, /CommunityEmojiPanelLoadingTestTag = "community\.emoji\.loading"/);
assert.match(panelState, /CommunityEmojiPanelEmptyTestTag = "community\.emoji\.empty"/);
assert.match(panelState, /CommunityEmojiPanelErrorTestTag = "community\.emoji\.error"/);
assert.match(panel, /CommunityEmojiPanelState\.Empty\(\)/);
assert.match(panel, /CommunityEmojiPanelState\.Ready\(sections\)/);
assert.match(panel, /CommunityEmojiPanelStatusContent/);
assert.doesNotMatch(panel, /if \(sections\.isEmpty\(\)\) return/);
assert.match(panel, /import androidx\.compose\.ui\.semantics\.contentDescription/);
assert.match(panel, /import androidx\.compose\.ui\.semantics\.selected/);
assert.match(panel, /import androidx\.compose\.ui\.semantics\.stateDescription/);
assert.match(panel, /communityEmojiSectionTestTag\(section\.key\)/);
assert.match(panel, /communityEmojiGridTestTag\(selectedSection\.key\)/);
assert.match(panel, /communityEmojiCellTestTag\(selectedSection\.key, index\)/);
assert.match(panel, /clickable\(role = Role\.Button\)/);
assert.match(panel, /selected = section\.key == selectedSectionKey/);
assert.match(panel, /stateDescription = if \(section\.key == selectedSectionKey\) "selected" else "not selected"/);
assert.match(panel, /contentDescription = communityEmojiSectionTestTag\(section\.key\)/);
assert.match(panel, /contentDescription = communityEmojiGridTestTag\(selectedSection\.key\)/);
assert.match(panel, /contentDescription = communityEmojiCellTestTag\(selectedSection\.key, index\)/);
assert.match(panel, /clickable\(role = Role\.Button\) \{ onEmojiClick\(emoji\) \}/);
console.log('Community emoji atlas contract passed.');
