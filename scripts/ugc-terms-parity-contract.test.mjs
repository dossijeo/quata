import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const ugcGate = await source('../core/src/commonMain/kotlin/com/quata/core/moderation/UgcTermsGate.kt');
const ugcGateTest = await source('../core/src/commonTest/kotlin/com/quata/core/moderation/UgcTermsGateContractTest.kt');
const dialog = await source('../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataTermsAcceptanceDialogContent.kt');
const gateContent = await source('../designsystem/src/commonMain/kotlin/com/quata/core/ui/components/QuataUgcTermsGateContent.kt');
const androidRepository = await source('../app/src/main/java/com/quata/core/moderation/ModerationRepository.kt');
const androidNav = await source('../app/src/main/java/com/quata/core/navigation/AppNavGraph.kt');
const androidStrings = await source('../app/src/main/res/values/strings.xml');
const webGateway = await source('../web/src/wasmJsMain/kotlin/com/quata/web/WebUgcTermsGateway.kt');
const webMain = await source('../web/src/wasmJsMain/kotlin/com/quata/web/Main.kt');
const iosHost = await source('../feature/auth/src/iosMain/kotlin/com/quata/feature/auth/presentation/IosUgcTermsHost.kt');
const iosSwift = await source('../iosApp/iosApp/QuataIosApp.swift');

test('UGC terms use one common local-first contract across platforms', () => {
  assert.match(ugcGate, /interface UgcTermsGateway/);
  assert.match(ugcGate, /class LocalFirstUgcTermsGateway/);
  assert.match(ugcGate, /PreferenceUgcTermsAcceptanceStore/);
  assert.match(ugcGate, /markAcceptedPendingSync\(profileId, version\)/);
  assert.match(ugcGate, /acceptance\.acceptTerms\(profileId, version\)/);
  assert.match(ugcGate, /store\.isPending\(profileId, version\)/);
  assert.match(ugcGate, /acceptedKey\(profileId, version\)/);
  assert.match(ugcGate, /pendingKey\(profileId, version\)/);
  assert.match(ugcGateTest, /localAcceptancePreservesAccessWhenRemoteFlushFails/);
  assert.match(ugcGateTest, /pendingLocalAcceptanceIsRetriedAndClearedWhenRemoteRecovers/);
  assert.match(ugcGateTest, /remoteAcceptanceIsCachedAsSynced/);
  assert.match(ugcGateTest, /missingSessionFailsClosed/);
});

test('UGC terms dialog exposes stable common semantic anchors and copy', () => {
  for (const tag of [
    'quata-ugc-terms-dialog',
    'quata-ugc-terms-body',
    'quata-ugc-terms-accept',
    'quata-ugc-terms-logout',
    'quata-ugc-terms-error',
  ]) {
    assert.match(dialog, new RegExp(`"${tag}"`));
  }
  assert.match(dialog, /modifier = Modifier\.testTag\(QuataUgcTermsDialogTestTag\)/);
  assert.match(dialog, /errorMessage: String\? = null/);
  assert.match(gateContent, /fun QuataUgcTermsGateContent\(/);
  assert.match(gateContent, /fun quataUgcTermsStrings\(language: QuataLanguage\)/);
  assert.match(gateContent, /QuataLanguage\.Spanish/);
  assert.match(gateContent, /QuataLanguage\.French/);
  assert.match(gateContent, /Qüata/);
  assert.match(gateContent, /accepted != true/);
  assert.match(gateContent, /checking/);
});

test('Android keeps original moderation repository behavior behind the common gateway', () => {
  assert.match(androidRepository, /class ModerationRepository\([\s\S]*\) : UgcTermsGateway/);
  assert.match(androidRepository, /override suspend fun hasAcceptedTerms/);
  assert.match(androidRepository, /override suspend fun acceptTerms/);
  assert.match(androidRepository, /flushPendingTermsForCurrentUser/);
  assert.match(androidNav, /QuataUgcTermsGateContent\(/);
  assert.match(androidNav, /gateway = container\.moderationRepository/);
  assert.match(androidNav, /QuataLegalDocumentLinksColumnContent\(/);
  assert.match(androidNav, /ugcTermsDocumentViewerState = documentViewerOpeningState\(file\.value\)/);
  assert.match(androidNav, /documentOpenService\.openWithViewerState\(file\.value\)\.completed/);
  assert.match(androidNav, /state = ugcTermsDocumentViewerState/);
  assert.doesNotMatch(androidNav, /private fun UgcTermsDialog/);
  assert.doesNotMatch(androidNav, /R\.string\.ugc_terms_/);
  assert.doesNotMatch(androidStrings, /ugc_terms_/);
});

test('Web and iOS call the same Supabase RPCs with canonical parameter names', () => {
  for (const sourceText of [webGateway, iosHost]) {
    assert.match(sourceText, /quata_has_accepted_ugc_terms/);
    assert.match(sourceText, /quata_accept_ugc_terms/);
    assert.match(sourceText, /"p_actor_profile_id"/);
    assert.match(sourceText, /"p_terms_version"/);
    assert.doesNotMatch(sourceText, /"profile_id"/);
    assert.doesNotMatch(sourceText, /"terms_version"/);
  }
  assert.match(webMain, /webUgcTermsGateway\([\s\S]*authRepository = authRepository/);
  assert.match(webMain, /QuataUgcTermsGateContent\(/);
  assert.match(webMain, /onLogout = \{ completeLogout\(\) \}/);
  assert.match(webMain, /webLegalDocumentFile\(document, language\)/);
  assert.match(webMain, /documentViewerOpeningState\(file\)/);
  assert.match(webMain, /documentOpener\.openWithViewerState\(file\)\.completed/);
  assert.match(webMain, /QuataDocumentViewerStatusContent\(/);
  assert.match(iosHost, /QuataUgcTermsDialogViewController/);
  assert.match(iosHost, /QuataUgcTermsGateContent\(/);
  assert.match(iosSwift, /createIosUgcTermsGateway/);
  assert.match(iosSwift, /installUgcTermsPromptFactory/);
  assert.match(iosSwift, /quata-ios-ugc-terms-dialog/);
});

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8');
}
