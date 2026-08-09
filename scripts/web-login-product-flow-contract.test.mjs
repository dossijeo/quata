import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = path => readFile(new URL(`../${path}`, import.meta.url), "utf8");
const host = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebLoginHost.kt");
const main = await source("web/src/wasmJsMain/kotlin/com/quata/web/Main.kt");
const repository = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebAuthRepository.kt");
const capabilityRoute = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebFeatureCapabilities.kt");
const browserAuthHost = await source("feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/AuthBrowserLoginHostContent.kt");
const productAuthHost = await source("feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/AuthProductHostContent.kt");
const recoveryForm = await source("feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/recovery/ForgotPasswordForm.kt");
const recoveryTags = await source("feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/recovery/ForgotPasswordTestTags.kt");
const authE2eBridge = await source("web/src/wasmJsMain/kotlin/com/quata/web/WebAuthE2eBridge.kt");
const recoveryEvidenceRunner = await source("scripts/web-auth-recovery-evidence.mjs");
const androidRecoveryEvidence = await source("app/src/androidTest/java/com/quata/feature/auth/presentation/AuthRecoveryProductBridgeInstrumentedTest.kt");

test("production Web mounts the common Auth product root without browser visual overrides", () => {
  assert.match(host, /AuthProductHostContent\(/);
  assert.doesNotMatch(host, /AuthBrowserLoginHostContent|WebNativeInput|WebNativeButton|alert\s*\(/);
  assert.match(host, /preferences\.putString\(WebSessionReadyKey, "true"\)/);
});

test("successful Web login activates the existing shell/router and preserves its session contracts", () => {
  assert.match(main, /fun completeLogin\(\)[\s\S]*?isSessionReady = true[\s\S]*?val session = authRepository\.activeProfileSessionOrNull\(\)[\s\S]*?currentUserId = session\?\.userId[\s\S]*?currentUserIsOfficial = session\?\.isOfficial == true[\s\S]*?navigation\.navigate\(pendingAuthenticationFragment \?: ""\)/);
  assert.match(main, /WebLoginHost\([\s\S]*?repository = authRepository,[\s\S]*?preferences = platformServices\.preferences,[\s\S]*?onLoginSuccess = ::completeLogin/);
  assert.match(main, /QuataAuthenticatedShellChrome\(/);
  assert.match(main, /return WebNavigationState\(route = "feed"/);
  assert.match(main, /quataChatDeepLinkOrNull\(\)|quataOfficialPostIdOrNull\(\)|quataPostIdOrNull\(\)/);
  assert.match(main, /sessionCoordinator\.reconcileCurrentSession\(\)/);
  assert.match(main, /sessionCoordinator\.logoutCurrentSession\(\)/);
  assert.match(repository, /put\("action", "web_login"\)/);
  assert.match(repository, /put\("client_instance_id", ensureWebClientInstanceId\(\)\)/);
  assert.match(repository, /WebSessionToken/);
});

test("product routes do not prepend capability diagnostics to the Android-comparable viewport", () => {
  assert.match(capabilityRoute, /showCapabilityNotice: Boolean = false/);
  assert.match(capabilityRoute, /if \(showCapabilityNotice\) \{[\s\S]*?WebFeatureCapabilityNotice\(/);
  assert.doesNotMatch(main, /showCapabilityNotice\s*=\s*true|WebFeatureCapabilityNotice/);
});

test("shared Auth recovery keeps observable common tags for Web, Android and iOS hosts", () => {
  for (const tag of [
    "auth.recovery.root",
    "auth.recovery.country-prefix",
    "auth.recovery.phone",
    "auth.recovery.question",
    "auth.recovery.secret-answer",
    "auth.recovery.new-password",
    "auth.recovery.error",
    "auth.recovery.submit",
    "auth.recovery.back",
  ]) {
    assert.match(recoveryTags, new RegExp(JSON.stringify(tag).slice(1, -1)));
  }
  assert.match(productAuthHost, /AuthProductDestination\.Recovery -> ForgotPasswordScreenHost\(/);
  assert.match(browserAuthHost, /AuthBrowserDestination\.Recovery -> Column\([\s\S]*ForgotPasswordTestTags\.Root[\s\S]*ForgotPasswordForm\(/);
  for (const required of [
    "ForgotPasswordTestTags.CountryPrefix",
    "ForgotPasswordTestTags.Phone",
    "ForgotPasswordTestTags.Question",
    "ForgotPasswordTestTags.SecretAnswer",
    "ForgotPasswordTestTags.NewPassword",
    "ForgotPasswordTestTags.Error",
    "ForgotPasswordTestTags.Submit",
    "ForgotPasswordTestTags.Back",
  ]) {
    assert.match(recoveryForm, new RegExp(required.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
});

test("Web recovery evidence uses the localhost product bridge and WebAuthRepository, not a parallel DOM form", () => {
  assert.match(authE2eBridge, /openRecovery: \(\) => openRecovery\(\)/);
  assert.match(authE2eBridge, /openLogin: \(\) => openLogin\(\)/);
  assert.match(authE2eBridge, /recoveryQuestion: \(countryCode, phone\)/);
  assert.match(authE2eBridge, /resetPassword: \(countryCode, phone, secretAnswer, newPassword\)/);
  assert.doesNotMatch(authE2eBridge, /innerHTML|createElement\(['"]input|createElement\(['"]button/);
  assert.match(main, /openRecovery = \{[\s\S]*authInitialDestination = AuthProductDestination\.Recovery[\s\S]*navigation\.navigate\("auth"\)/);
  assert.match(main, /openLogin = \{[\s\S]*authInitialDestination = AuthProductDestination\.Login[\s\S]*navigation\.navigate\("auth"\)/);
  assert.match(main, /authRepository\.getPasswordRecoveryQuestion\(countryCode, phone\)/);
  assert.match(main, /authRepository\.resetPassword\(countryCode, phone, secretAnswer, newPassword\)/);
  assert.match(recoveryEvidenceRunner, /WEB-AUTH-RECOVERY-001/);
  assert.match(recoveryEvidenceRunner, /globalThis\.__quataAuthE2eProduct\.openRecovery\(\)/);
  assert.match(recoveryEvidenceRunner, /globalThis\.__quataAuthE2eProduct\.recoveryQuestion/);
  assert.match(recoveryEvidenceRunner, /globalThis\.__quataAuthE2eProduct\.resetPassword/);
  assert.match(recoveryEvidenceRunner, /unexpected_external_network/);
  assert.match(recoveryEvidenceRunner, /quata-auth-bridge/);
  assert.doesNotMatch(recoveryEvidenceRunner, /service_role|SUPABASE_DB_URL|supabase db push|migration repair/);
});

test("Android recovery evidence mounts the same AuthProductHostContent recovery destination", () => {
  assert.match(androidRecoveryEvidence, /AuthProductHostContent\(/);
  assert.match(androidRecoveryEvidence, /initialDestination = AuthProductDestination\.Recovery/);
  assert.match(androidRecoveryEvidence, /ANDROID-AUTH-RECOVERY-001/);
  for (const tag of [
    "ForgotPasswordTestTags.Root",
    "ForgotPasswordTestTags.CountryPrefix",
    "ForgotPasswordTestTags.Phone",
    "ForgotPasswordTestTags.Question",
    "ForgotPasswordTestTags.SecretAnswer",
    "ForgotPasswordTestTags.NewPassword",
    "ForgotPasswordTestTags.Submit",
    "ForgotPasswordTestTags.Back",
  ]) {
    assert.match(androidRecoveryEvidence, new RegExp(tag.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
  assert.doesNotMatch(androidRecoveryEvidence, /Supabase|service_role|SUPABASE_DB_URL|migration repair|supabase db push/);
});
