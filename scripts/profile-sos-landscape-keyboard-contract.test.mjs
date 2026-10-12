import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

test('the landscape SOS editor does not apply a second global IME inset', () => {
  const layout = read(
    'feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsEditorLayoutContent.kt',
  );
  assert.doesNotMatch(layout, /import androidx\.compose\.foundation\.layout\.imePadding/);
  const landscape = layout.slice(
    layout.indexOf('fun EmergencyContactsLandscapeEditorLayoutContent'),
    layout.indexOf('fun EmergencyContactsPortraitEditorLayoutContent'),
  );
  assert.match(landscape, /Column\(Modifier\.fillMaxSize\(\)\)/);
  assert.doesNotMatch(landscape, /imePadding\(\)/);

  const editor = read(
    'feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsEditorContent.kt',
  );
  const landscapeEditor = editor.slice(
    editor.indexOf('if (isLandscapeLayout)'),
    editor.indexOf('EmergencyContactsPortraitEditorLayoutContent'),
  );
  assert.match(
    landscapeEditor,
    /modifier\s*\.verticalScroll\(messageScrollState\)[\s\S]*?\.imePadding\(\)/,
  );
  assert.match(editor, /val keyboardSessionActive = isImeVisible \|\| keyboardOwner != null/);
  assert.match(landscapeEditor, /if \(compactForIme\) 1 else 4/);
  assert.match(
    landscapeEditor,
    /val compactForIme = keyboardSessionActive &&\s*keyboardOwner == EmergencyContactsKeyboardOwner\.Message/,
  );
  assert.match(landscapeEditor, /if \(!compactForIme\) \{\s*EmergencyContactsLandscapeMessageIntroContent/);
  assert.match(editor, /LaunchedEffect\([\s\S]*?messageScrollState\.maxValue/);

  const panel = read(
    'feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsLandscapeMessagePanelContent.kt',
  );
  assert.match(panel, /contentPadding = PaddingValues\(if \(compactForIme\) 4\.dp else 12\.dp\)/);
  assert.match(panel, /if \(!compactForIme\) \{[\s\S]*?Text\(title/);
});

test('the native keyboard backdrop keeps the replacement frame across rotation hide events', () => {
  const app = read('iosApp/iosApp/QuataIosApp.swift');
  assert.match(app, /latestKeyboardFrame = keyboardFrame\s*let convertedFrame/);
  assert.match(app, /if #available\(iOS 17\.0, \*\) \{\s*hostView\.keyboardLayoutGuide\.usesBottomSafeArea = false/);
  assert.match(app, /keyboardLayoutGuideOverlap\(in: container\)/);
  assert.match(app, /let layoutFrame = hostView\.keyboardLayoutGuide\.layoutFrame/);
  assert.match(app, /layoutFrame\.height <= hostView\.safeAreaInsets\.bottom \+ 1/);
  assert.match(app, /container\.convert\(layoutFrame, from: hostView\)/);
  assert.match(app, /@objc private func keyboardDidHide[\s\S]*?finishKeyboardHide\(\)/);
  const finishHide = app.slice(
    app.indexOf('func finishKeyboardHide()'),
    app.indexOf('private func keyboardLayoutGuideOverlap'),
  );
  assert.match(finishHide, /if let overlap = keyboardLayoutGuideOverlap[\s\S]*?render\(overlap:/);
  assert.match(finishHide, /hide\(\)/);
  assert.doesNotMatch(finishHide, /latestKeyboardFrame/);

  const tests = read('iosApp/iosAppTests/QuataFeedFrameworkTests.swift');
  assert.match(tests, /func testKeyboardBackdropHidesAfterDismissWhenLayoutGuideHasNoKeyboardOverlap/);
  assert.match(tests, /controller\.finishKeyboardHide\(\)/);
  assert.match(tests, /if #available\(iOS 17\.0, \*\) \{\s*XCTAssertFalse\(hostView\.keyboardLayoutGuide\.usesBottomSafeArea\)/);
  assert.match(tests, /XCTAssertTrue\(backdrop\.isHidden\)/);
});

test('responsive SOS layout preserves the active keyboard owner while fields remount', () => {
  const editor = read(
    'feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/EmergencyContactsEditorContent.kt',
  );
  assert.match(
    editor,
    /withFrameNanos \{ \}\s*withFrameNanos \{ \}\s*if \(!isMessageFocused && !isSearchFocused && !isImeVisible\) keyboardOwner = null/,
  );
  assert.match(
    editor,
    /LaunchedEffect\(isLandscapeLayout\) \{\s*val ownerToRestore = keyboardOwner \?: return@LaunchedEffect\s*withFrameNanos \{ \}/,
  );
  assert.doesNotMatch(
    editor,
    /keyboardOwner != null && \(isMessageFocused \|\| isSearchFocused \|\| isImeVisible\)/,
  );
});

test('the iOS regression test still asserts the exact message draft across rotation', () => {
  const uiTest = read('iosApp/iosAppUITests/QuataIosHostUITests.swift');
  const start = uiTest.indexOf(
    'func testAuthenticatedProfileSosKeepsRealComposeDraftAboveGlobalKeyboardAcrossRotation()',
  );
  assert.notEqual(start, -1);
  const body = uiTest.slice(start, uiTest.indexOf('\n    func ', start + 10));
  assert.match(body, /device\.orientation = \.landscapeLeft/);
  assert.match(body, /let restoredSearch = app\.descendants\(matching: \.any\)/);
  assert.match(body, /restoredSearch\.typeText\("\\n"\)/);
  assert.match(body, /let messageTab = app\.descendants\(matching: \.any\)/);
  assert.match(body, /messageTab\.coordinate\(withNormalizedOffset:[\s\S]*?\.tap\(\)/);
  assert.doesNotMatch(body, /searchKeyboardDismissed/);
  assert.doesNotMatch(body, /let searchDone = app\.keyboards\.buttons\["Return"\]/);
  assert.match(body, /let input = app\.textViews\["profile\.sos\.message\.input"\]/);
  assert.doesNotMatch(body, /app\.descendants\(matching: \.any\)[\s\S]{0,160}profile\.sos\.message\.input/);
  assert.match(body, /contains: marker/);
  assert.match(body, /context: "Profile SOS landscape"/);
  assert.match(body, /aboveGlobalKeyboardIn: app/);

  const helperStart = uiTest.indexOf('private func assertAuthenticatedProfileInput');
  const helper = uiTest.slice(helperStart, uiTest.indexOf('\n    private func ', helperStart + 10));
  assert.match(helper, /let keyboardIntersection = keyboard\.frame\.intersection\(windowFrame\)/);
  assert.match(helper, /keyboardIntersection\.isNull \|\| keyboardIntersection\.isEmpty\s*\? measuredFrame/);
  assert.match(helper, /NSPredicate\(format: "hasKeyboardFocus == 1"\)\.evaluate\(with: input\)/);
  assert.match(helper, /input\.frame\.maxY,\s*keyboardFrame\.minY \+ tolerance/);
  assert.doesNotMatch(helper, /app\.descendants\(matching: \.any\)[\s\S]*?input\.identifier/);
});
