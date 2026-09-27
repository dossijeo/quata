import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const runtime = await readFile(new URL("../core/src/iosMain/kotlin/com/quata/core/platform/IosApnsSessionRuntime.kt", import.meta.url), "utf8");
const transport = await readFile(new URL("../core/src/iosMain/kotlin/com/quata/core/platform/IosApnsRegistrationTransport.kt", import.meta.url), "utf8");
const host = await readFile(new URL("../iosApp/iosApp/QuataIosApp.swift", import.meta.url), "utf8");
const nativeTest = await readFile(new URL("../iosApp/iosAppTests/QuataIosApnsRemoteLogoutEvidenceTests.swift", import.meta.url), "utf8");
const runner = await readFile(new URL("./ios-apns-logout-remote-evidence.mjs", import.meta.url), "utf8");
const shellRunner = await readFile(new URL("./run-ios-apns-logout-remote-xctest.sh", import.meta.url), "utf8");
const signedBuild = await readFile(new URL("./build-ios-intel-simulator-signed.sh", import.meta.url), "utf8");

test("iOS host blocks Auth logout until APNs cleanup succeeds", () => {
  assert.match(host, /apnsRuntime\.prepareForLogout[\s\S]*if ready\.boolValue[\s\S]*logoutHandler\.logout/);
  assert.match(host, /reportLogoutFailure\(\)/);
  assert.match(runtime, /fun prepareForLogout[\s\S]*coordinator\.synchronize\(null, null, environment\) == ApnsSynchronization\.Applied/);
  assert.match(runtime, /removed && transport\.recover\(fresh\)[\s\S]*transport\.unregister/);
  assert.match(runtime, /val success = remoteSuccess && sameLifecycle[\s\S]*onCompleted\(success\)/);
});

test("native evidence uses the production APNs transport and owned authenticated session", () => {
  assert.match(nativeTest, /QUATA_IOS_APNS_REMOTE_EVIDENCE/);
  assert.match(nativeTest, /IosApnsRegistrationTransport/);
  assert.match(nativeTest, /transport\.register\(registration: registration, completionHandler: completion\)/);
  assert.match(nativeTest, /transport\.unregister\(registration: registration, completionHandler: completion\)/);
  assert.match(nativeTest, /result\?\.boolValue == true/);
  assert.match(transport, /override suspend fun register[\s\S]*registerResult\(registration\)\.confirmed/);
  assert.match(transport, /override suspend fun unregister[\s\S]*unregisterResult\(registration\)\.confirmed/);
  assert.match(transport, /NSURLSession\.sessionWithConfiguration\(settings, delegate, null\)/);
  assert.match(transport, /session\.dataTaskWithRequest\(request\)/);
  assert.match(transport, /invokeOnCancellation \{ task\.cancel\(\); session\.invalidateAndCancel\(\) \}/);
  assert.match(transport, /willPerformHTTPRedirection[\s\S]*terminalFailure = IosApnsTransportResult\.RedirectRejected[\s\S]*completionHandler\(null\)/);
  assert.match(transport, /didReceiveData[\s\S]*bytes\.size\.toULong\(\) \+ didReceiveData\.length > 16_384uL[\s\S]*ResponseTooLarge[\s\S]*dataTask\.cancel\(\)/);
  assert.match(transport, /response\?\.URL\?\.absoluteString != expectedUrl[\s\S]*RedirectRejected/);
  assert.match(transport, /status !in 200\.\.299[\s\S]*HttpRejected/);
  assert.match(transport, /Json\.parseToJsonElement[\s\S]*booleanOrNull == true/);
  assert.match(transport, /"quata_unregister_push_token", registration/);
  assert.match(transport, /"p_profile_id"[\s\S]*"p_token"/);
});

test("remote evidence is explicit, reversible, exact-SHA and credential safe", () => {
  assert.match(runner, /QUATA_IOS_APNS_REMOTE_EVIDENCE !== "1"/);
  assert.match(runner, /createRequire\(options\.dependencyPackage\)\("pg"\)/);
  assert.match(runner, /synthetic_apns_token_absent_before_execution/);
  assert.match(runner, /exact_candidate_sha_materialized_on_mac/);
  assert.match(runner, /database_confirmed_owned_ios_token_disabled_on_logout/);
  assert.match(runner, /delete from public\.push_tokens where token=\$1 and user_id=\$2::uuid and auth_user_id=\$3::uuid and platform='ios'/);
  assert.match(runner, /\/auth\/v1\/logout\?scope=local/);
  assert.match(runner, /select exists\(select 1 from auth\.sessions where id=\$1::uuid and user_id=\$2::uuid\) as active/);
  assert.match(runner, /cleanup\.tokenResidue === 0 && report\.cleanup\.sessionRevoked && report\.cleanup\.temporaryCredentialsRemoved/);
  assert.doesNotMatch(runner, /console\.(?:log|error)\([^\n]*(?:accessToken|password|deviceToken)/);
  assert.doesNotMatch(runner, /service[_-]?role/i);
  assert.match(shellRunner, /xcodebuild test-without-building/);
  assert.match(shellRunner, /-only-testing:"\$selected_test"/);
  assert.match(shellRunner, /check-ios-xctest-executed\.py/);
  assert.match(shellRunner, /private XCTest input appeared in xcodebuild log/);
  assert.match(shellRunner, /trap cleanup EXIT/);
  assert.match(shellRunner, /scrub_private_log/);
  assert.match(shellRunner, /rm -f "\$log" "\$summary"/);
  assert.match(shellRunner, /quata-signed-build-provenance\.json/);
  assert.match(shellRunner, /document\.get\("productSha"\) != expected_sha/);
  assert.match(shellRunner, /record\.get\("sha256"\) != digest\.hexdigest\(\)/);
  assert.match(signedBuild, /quata-signed-build-provenance\.json/);
  assert.match(signedBuild, /"productSha": product_sha/);
  assert.match(signedBuild, /"relativePath": relative, "sha256": digest\.hexdigest\(\)/);
});
