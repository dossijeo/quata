import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import {
  ACCOUNT_AVATAR_CHECK,
  ACCOUNT_AVATAR_CREDENTIALS_ENV,
  ACCOUNT_AVATAR_MUTATION_OPT_IN,
  ACCOUNT_AVATAR_STEPS,
  validateAccountAvatarEvidence,
} from "./account-avatar-evidence-contract.mjs";

const sha = "0123456789abcdef0123456789abcdef01234567";
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
const backendRunner = await readFile(new URL("./account-avatar-backend-evidence.mjs", import.meta.url), "utf8");
const aggregateRunner = await readFile(new URL("./account-avatar-aggregate-evidence.mjs", import.meta.url), "utf8");
const webRunner = await readFile(new URL("./account-avatar-web-evidence.mjs", import.meta.url), "utf8");
const androidRunner = await readFile(new URL("./account-avatar-android-evidence.mjs", import.meta.url), "utf8");
const iosRunner = await readFile(new URL("./account-avatar-ios-evidence.mjs", import.meta.url), "utf8");
const storageCleanup = await readFile(new URL("./e2e-fixtures/supabase-storage-cleanup.mjs", import.meta.url), "utf8");
const iosShellRunner = await readFile(new URL("./run-ios-account-avatar-ui-test.sh", import.meta.url), "utf8");
const androidTest = await readFile(new URL("../app/src/androidTest/java/com/quata/feature/profile/presentation/ProfileAvatarRealInstrumentedTest.kt", import.meta.url), "utf8");
const iosTest = await readFile(new URL("../iosApp/iosAppUITests/QuataIosAuthenticatedAccountAvatarUITests.swift", import.meta.url), "utf8");
const mainActivity = await readFile(new URL("../app/src/main/java/com/quata/MainActivity.kt", import.meta.url), "utf8");
const androidProfileScreen = await readFile(new URL("../app/src/main/java/com/quata/feature/profile/presentation/ProfileScreen.kt", import.meta.url), "utf8");
const iosProfileHost = await readFile(new URL("../feature/profile/src/iosMain/kotlin/com/quata/feature/profile/presentation/IosProfileHost.kt", import.meta.url), "utf8");
const iosRemoteAvatar = await readFile(new URL("../designsystem/src/iosMain/kotlin/com/quata/core/ui/components/IosRemoteAvatar.kt", import.meta.url), "utf8");
const kmpProfileRepository = await readFile(new URL("../feature/profile/src/commonMain/kotlin/com/quata/feature/profile/data/KmpProfileRepository.kt", import.meta.url), "utf8");

function evidence(overrides = {}) {
  const platform = {
    status: "passed",
    sha,
    report: "build-reports/account-avatar/web.json",
    steps: [...ACCOUNT_AVATAR_STEPS],
    rollback: { triggered: true, verified: true, physicalResidue: 0 },
    cleanup: { verified: true, physicalResidue: 0 },
  };
  return {
    version: 1,
    units: [ACCOUNT_AVATAR_CHECK],
    productSha: sha,
    execution: { mode: "fixture", credentialsSource: ACCOUNT_AVATAR_CREDENTIALS_ENV },
    evidence: {
      web: structuredClone(platform),
      android: structuredClone(platform),
      ios: structuredClone(platform),
    },
    ...overrides,
  };
}

test("accepts the bounded three-platform account-avatar contract", () => {
  assert.deepEqual(validateAccountAvatarEvidence(evidence()).platforms, ["web", "android", "ios"]);
});

test("requires persistence, rollback and cleanup evidence on every platform", () => {
  const report = evidence();
  report.evidence.ios.steps = report.evidence.ios.steps.filter((step) => step !== "avatar_rollback_verified");
  assert.throws(() => validateAccountAvatarEvidence(report), /ios_steps_incomplete/);
});

test("fails closed on residue, absolute paths and recorded secrets", () => {
  const residue = evidence();
  residue.evidence.android.cleanup.physicalResidue = 1;
  assert.throws(() => validateAccountAvatarEvidence(residue), /android_cleanup_not_verified/);

  const absolutePath = evidence();
  absolutePath.evidence.web.report = "C:/tmp/evidence.json";
  assert.throws(() => validateAccountAvatarEvidence(absolutePath), /web\.report_must_be_repository_relative/);

  const secret = evidence();
  secret.accessToken = "must never be written";
  assert.throws(() => validateAccountAvatarEvidence(secret), /accessToken_must_not_be_recorded/);
});

test("real mode requires the explicit reversible mutation opt-in", () => {
  const report = evidence();
  report.execution = { mode: "real", credentialsSource: ACCOUNT_AVATAR_CREDENTIALS_ENV };
  assert.throws(() => validateAccountAvatarEvidence(report), /real_mode_requires_explicit_mutation_opt_in/);
  report.execution.mutationOptIn = ACCOUNT_AVATAR_MUTATION_OPT_IN;
  assert.equal(validateAccountAvatarEvidence(report).status, "passed");
});

test("ACCOUNT-AVATAR contract is part of local fast contract suites", () => {
  assert.match(packageJson.scripts["test:web-wave2-contracts"], /scripts\/account-avatar-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["test:ci-fast-contracts"], /scripts\/account-avatar-evidence-contract\.test\.mjs/);
  assert.match(packageJson.scripts["evidence:account-avatar-backend"], /scripts\/account-avatar-backend-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:account-avatar-aggregate"], /scripts\/account-avatar-aggregate-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:account-avatar-web"], /scripts\/account-avatar-web-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:account-avatar-android"], /scripts\/account-avatar-android-evidence\.mjs/);
  assert.match(packageJson.scripts["evidence:account-avatar-ios"], /scripts\/account-avatar-ios-evidence\.mjs/);
});

test("aggregate account-avatar evidence consumes the real exact-SHA platform reports", () => {
  assert.match(aggregateRunner, /build-reports\/web\/account-avatar-evidence\.json/);
  assert.match(aggregateRunner, /build-reports\/android\/account-avatar-evidence\.json/);
  assert.match(aggregateRunner, /build-reports\/ios\/account-avatar-evidence\.json/);
  assert.match(aggregateRunner, /report\?\.git\?\.head !== productSha/);
  assert.match(aggregateRunner, /report\?\.git\?\.workingTreeDirty !== false/);
  assert.match(aggregateRunner, /cleanup\.physicalResidue !== 0/);
  assert.match(aggregateRunner, /validateAccountAvatarEvidence\(report\)/);
  assert.doesNotMatch(aggregateRunner, /executePlatform|qadata-account-avatar-\$\{platform\}/);
});

test("shared storage cleanup probe is read-only, parametrized and uses the TLS pooler files", () => {
  assert.match(storageCleanup, /begin read only/);
  assert.match(storageCleanup, /storage\.objects where bucket_id = \$1 and name = \$2/);
  assert.match(storageCleanup, /C:\/Users\/PC\/\.quata-supabase-db-url\.txt/);
  assert.match(storageCleanup, /C:\/Users\/PC\/\.quata-supabase-pooler-ca\.pem/);
  assert.doesNotMatch(storageCleanup, /SUPABASE_DB_URL=/);
});

test("backend runner is opt-in, reversible and avoids recorded secrets", () => {
  assert.match(backendRunner, /QUATA_ACCOUNT_AVATAR_REAL_MUTATION_OPT_IN/);
  assert.match(backendRunner, /ACCOUNT_AVATAR_MUTATION_OPT_IN/);
  assert.match(backendRunner, /ACCOUNT_AVATAR_CREDENTIALS_ENV/);
  assert.match(backendRunner, /ACCOUNT_AVATAR_CREDENTIALS_FALLBACK/);
  assert.match(backendRunner, /const BUCKET = "community-posts"/);
  assert.match(backendRunner, /avatars\/\$\{session\.userId\}\/qadata-account-avatar-\$\{platform\}-/);
  assert.match(backendRunner, /uploadAvatarObject/);
  assert.match(backendRunner, /deleteStorageObject/);
  assert.match(backendRunner, /storage\.objects where bucket_id = \$1 and name = \$2/);
  assert.match(backendRunner, /restoreProfileAvatar/);
  assert.match(backendRunner, /validateAccountAvatarEvidence\(report\)/);
  assert.doesNotMatch(backendRunner, /680242607|680242608|21085800|ghp_|service_role|SUPABASE_DB_URL=/);
});

test("Web visual runner uses semantic anchors and reversible cleanup", () => {
  assert.match(webRunner, /ACCOUNT-AVATAR-WEB-REAL-001/);
  assert.match(webRunner, /I_ACCEPT_WEB_ACCOUNT_AVATAR_FIXTURE/);
  assert.match(webRunner, /clickSemantic\(page, "profile\.avatar\.change"\)/);
  assert.match(webRunner, /clickSemantic\(page, "profile\.avatar\.gallery"\)/);
  assert.match(webRunner, /clickSemantic\(page, "profile\.save"\)/);
  assert.match(webRunner, /roleFallbackFor\(id\)/);
  assert.match(webRunner, /id === "profile\.save"[\s\S]*Guardar cambios\|Save changes/);
  assert.match(webRunner, /bridgeFallbackFor\(id\)/);
  assert.match(webRunner, /data-quata-account-avatar-bridge/);
  assert.match(webRunner, /__quataAccountAvatarE2EProduct/);
  assert.match(webRunner, /saveProfile/);
  assert.match(webRunner, /missing_stable_anchor:\$\{id\}/);
  assert.match(webRunner, /clickEditorAction\(page, "post-image-editor\.save"/);
  assert.match(webRunner, /cleanupUploadedAvatar/);
  assert.match(webRunner, /patchProfileAvatar/);
  assert.match(webRunner, /deleteStorageObject/);
  assert.match(webRunner, /assertStorageObjectAbsent/);
  assert.match(webRunner, /physicalResidue/);
  assert.doesNotMatch(webRunner, /680242607|680242608|21085800|ghp_|service_role|SUPABASE_DB_URL=/);
});

test("Android visual runner uses common profile anchors and reversible cleanup", () => {
  assert.match(androidRunner, /ACCOUNT-AVATAR-ANDROID-REAL-001/);
  assert.match(androidRunner, /ProfileAvatarRealInstrumentedTest#authenticatedUserChangesProfileAvatarFromCommonAccount/);
  assert.match(androidRunner, /quataAccountAvatarCredentialsFile/);
  assert.match(androidRunner, /quataAccountAvatarEvidence/);
  assert.match(androidRunner, /account-avatar-evidence/);
  assert.match(androidRunner, /run-as", "com\.quata", "rm", "-rf", deviceEvidencePath/);
  assert.match(androidRunner, /verifyAndroidPhysicalCleanup/);
  assert.match(androidRunner, /assertStorageObjectAbsent/);
  assert.doesNotMatch(androidRunner, /680242607|680242608|21085800|ghp_|service_role|SUPABASE_DB_URL=/);

  assert.match(androidTest, /ProfileAvatarChangeTestTag/);
  assert.match(androidTest, /ProfileAvatarGalleryTestTag/);
  assert.match(androidTest, /ProfileSaveChangesTestTag/);
  assert.match(androidTest, /PostImageEditorRootTestTag/);
  assert.match(androidTest, /PostImageEditorRotateTestTag/);
  assert.match(androidTest, /PostImageEditorSaveTestTag/);
  assert.match(androidTest, /updateProfile\(profileId, mapOf\("avatar_url" to originalAvatar\)\)/);
  assert.match(androidTest, /deletePostImageObject\(path\)/);
  assert.match(androidTest, /getProfiles\(ids = listOf\(profileId\), cacheMode = SupabaseCacheMode\.NETWORK_ONLY\)/);
  assert.match(androidTest, /probePublicAvatar/);
  assert.doesNotMatch(androidTest, /680242607|680242608|21085800|ghp_|service_role|SUPABASE_DB_URL=/);
});

test("Android profile evidence hook is debug-only and keeps the real picker path", () => {
  assert.match(mainActivity, /EXTRA_ACCOUNT_AVATAR_EVIDENCE_IMAGE_URI/);
  assert.match(mainActivity, /AppDestinations\.Profile\.route/);
  assert.match(mainActivity, /accountAvatarEvidenceImageUri = accountAvatarEvidenceImageUri/);
  assert.match(androidProfileScreen, /accountAvatarEvidenceImageUri: String\? = null/);
  assert.match(androidProfileScreen, /takeIf \{ BuildConfig\.DEBUG \}/);
  assert.match(androidProfileScreen, /picker\.launch\(PickVisualMediaRequest\(ActivityResultContracts\.PickVisualMedia\.ImageOnly\)\)/);
});

test("iOS visual runner uses Account anchors, fixture opt-in and reversible cleanup", () => {
  assert.match(iosRunner, /ACCOUNT-AVATAR-IOS-REAL-001/);
  assert.match(iosRunner, /I_ACCEPT_IOS_ACCOUNT_AVATAR_PICKER_FIXTURE/);
  assert.match(iosRunner, /scripts\/run-ios-account-avatar-ui-test\.sh/);
  assert.match(iosRunner, /waitForRemoteAvatarChange/);
  assert.match(iosRunner, /probePublicAvatar/);
  assert.match(iosRunner, /patchProfileAvatar/);
  assert.match(iosRunner, /deleteStorageObject/);
  assert.match(iosRunner, /assertStorageObjectAbsent/);
  assert.match(iosRunner, /physicalResidue/);
  assert.match(iosRunner, /mac_checkout_sha_matches_local_candidate/);
  assert.doesNotMatch(iosRunner, /680242607|680242608|21085800|ghp_|service_role|SUPABASE_DB_URL=/);

  assert.match(iosShellRunner, /QUATA_IOS_ACCOUNT_AVATAR_UI_E2E/);
  assert.match(iosShellRunner, /QUATA_IOS_ACCOUNT_AVATAR_PICKER_PATH/);
  assert.match(iosShellRunner, /QuataIosAuthenticatedAccountAvatarUITests\/testAuthenticatedSessionChangesProfileAvatarFromCommonAccount/);
  assert.match(iosShellRunner, /check-ios-xctest-executed\.py/);

  assert.match(iosTest, /navigation\.primary\.profile/);
  assert.match(iosTest, /profile\.avatar\.change/);
  assert.match(iosTest, /profile\.avatar\.gallery/);
  assert.match(iosTest, /post-image-editor\.root/);
  assert.match(iosTest, /post-image-editor\.rotate/);
  assert.match(iosTest, /post-image-editor\.save/);
  assert.match(iosTest, /profile\.save/);
  assert.doesNotMatch(iosTest, /matching\(identifier: "profile\.avatar\.change"\)[\s\S]{0,240}return true/);
  assert.match(iosTest, /IOS_ACCOUNT_AVATAR_UI_GATE_PASSED/);
  assert.doesNotMatch(iosTest, /680242607|680242608|21085800|ghp_|service_role|SUPABASE_DB_URL=/);
});

test("iOS Account avatar fixture hook is opt-in and keeps the real picker path", () => {
  assert.match(iosProfileHost, /IosProfileAvatarEvidenceFilePicker/);
  assert.match(iosProfileHost, /I_ACCEPT_IOS_ACCOUNT_AVATAR_PICKER_FIXTURE/);
  assert.match(iosProfileHost, /QUATA_IOS_ACCOUNT_AVATAR_PICKER_PATH/);
  assert.match(iosProfileHost, /if \(source != FilePickerSource\.Gallery\) return null/);
  assert.match(iosProfileHost, /return delegate\.pick\(request\)/);
  assert.match(iosRemoteAvatar, /isIosAvatarPreviewUrl/);
  assert.match(iosRemoteAvatar, /value\.startsWith\("file:\/\/"\)/);
  assert.match(iosRemoteAvatar, /NSData\.dataWithContentsOfURL/);
});

test("common profile save rolls back uploaded avatars after any post-upload failure", () => {
  assert.match(kmpProfileRepository, /previousAvatarUrl = remote\.getProfile\(session\.profileId\)\?\.avatarUrl/);
  assert.match(kmpProfileRepository, /var profilePatchPersisted = false/);
  assert.match(kmpProfileRepository, /profilePatchPersisted = true/);
  assert.match(kmpProfileRepository, /avatarUploader\.rollbackUploaded\(session\.profileId, avatarUrl\.orEmpty\(\)\)/);
  assert.match(kmpProfileRepository, /mapOf\("avatar_url" to previousAvatarUrl\.cleanProfileValue\(\)\)/);
});
