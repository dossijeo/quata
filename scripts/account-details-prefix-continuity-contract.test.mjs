import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const repository = read("feature/profile/src/commonMain/kotlin/com/quata/feature/profile/data/KmpProfileRepository.kt");
const profileHost = read("feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt");
const renderedTest = read("feature/profile/src/commonTest/kotlin/com/quata/feature/profile/presentation/ProfilePrefixRenderedInteractionTest.kt");
const webGateway = read("web/src/wasmJsMain/kotlin/com/quata/web/WebProfileRemoteGateway.kt");
const iosGateway = read("feature/profile/src/iosMain/kotlin/com/quata/feature/profile/data/IosProfilePostgrestGateway.kt");
const authBridge = read("supabase/functions/quata-auth-bridge/index.ts");
const profileResolution = read("supabase/functions/quata-auth-bridge/profile-resolution.mjs");
const inventory = read("docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const packageJson = JSON.parse(read("package.json"));

test("prefix options expose stable digits-only anchors and the save targets the active profile", () => {
  assert.match(profileHost, /\$ProfileDetailsCountryCodeButtonTestTag\.option\.\$\{code\.filter\(Char::isDigit\)\}/);
  assert.match(repository, /remote\.saveProfile\(session\.profileId/);
});

test("the rendered shared form clicks the selector, exact option and Save", () => {
  assert.match(renderedTest, /onNodeWithTag\(ProfileDetailsCountryCodeButtonTestTag\)\.performClick\(\)/);
  assert.match(renderedTest, /profileDetailsCountryCodeOptionTestTag\("34"\)/);
  assert.match(renderedTest, /onNodeWithTag\(ProfileDetailsSaveTestTag\)\.performClick\(\)/);
  assert.match(renderedTest, /assertEquals\("34", saved\.countryCode\)/);
  assert.match(renderedTest, /assertEquals\("600000000", saved\.phone\)/);
});

test("the canonical and discovery aliases move together on every prefix change", () => {
  for (const field of ["country_code", "code", "phone_local", "phone_normalized", "phone_e164", "phone", "telefono"]) {
    assert.match(repository, new RegExp(`put\\(\\"${field}\\"`));
  }
  for (const gateway of [webGateway, iosGateway]) {
    assert.match(gateway, /phone_normalized/);
    assert.match(gateway, /phone_e164/);
  }
});

test("the legacy Auth bridge fails closed for linked actors and ambiguous phone matches", () => {
  assert.match(authBridge, /if \(error \|\| !data\.user\) throw error \?\? new Error\("Could not update linked auth user"\)/);
  assert.match(authBridge, /requireUnlinkedAuthEmailAvailable\(existing\)/);
  assert.doesNotMatch(authBridge, /updateUserById\(existing\.id/);
  assert.doesNotMatch(authBridge, /query\.limit\(10\)/);
  assert.match(authBridge, /select\(profileSelect, \{ count: "exact" \}\)/);
  assert.match(authBridge, /requireCompleteProfileResult\([^;]+, count\)/);
  assert.match(profileResolution, /profile_lookup_ambiguous/);
  assert.match(profileResolution, /if \(candidates\.length > 1\) throw new Error\("profile_lookup_ambiguous"\)/);
});

test("the inventory closes only prefix continuity and keeps broader validation limits", () => {
  const row = inventory.split("\n").find((line) => line.startsWith("| `ACCOUNT-DETAILS`"));
  assert.ok(row);
  assert.match(row, /cambio de prefijo acreditado/);
  assert.match(row, /validación exhaustiva de entrada inválida y colisiones/);
  assert.doesNotMatch(row, /cambio de prefijo, validación exhaustiva/);
});

test("the focal contract is present in both fast suites", () => {
  for (const script of ["test:web-wave2-contracts", "test:ci-fast-contracts"]) {
    assert.match(packageJson.scripts[script], /scripts\/account-details-prefix-continuity-contract\.test\.mjs/);
  }
});
