import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const readJson = async (path) => JSON.parse(await read(path));

const screenInventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const platformInventory = await read("../docs/MULTIPLATFORM_INVENTORY.md");
const migrationBoard = await read("../docs/MULTIPLATFORM_MIGRATION_BOARD.md");
const neighborhoodModels = await read(
  "../feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/domain/NeighborhoodModels.kt",
);

const profileReceipts = await Promise.all([
  "profile-entry.json",
  "profile-navigation-request-targeting.json",
  "profile-failed-navigation-stack.json",
  "shell-navigation-public-profile-backstack.json",
  "profile-follow-negative.json",
  "profile-follow-retry.json",
  "profile-follow-lists-pagination.json",
  "profile-safety-role-request-serialization.json",
  "profile-content.json",
  "profile-comment-request-targeting.json",
  "profile-post-report-targeting.json",
  "profile-comments-translator-postflight.json",
  "profile-detail-media.json",
  "public-profile-post-detail.json",
  "public-profile-post-detail-scroll-restoration.json",
  "profile-private-chat-error-retry.json",
  "profile-private-chat-race.json",
  "profile-private-chat-rendered-race.json",
  "profile-private-chat-backend-contention.json",
  "profile-roles-safety.json",
  "profile-roles-permissions.json",
  "profile-roles-error-retry.json",
  "profile-safety-negative.json",
  "profile-safety-permissions-backend.json",
  "profile-safety-retry.json",
  "profile-safety-report-retry.json",
  "auth-action-restore-community-profile.json",
  "prof-shared-chat-documents.json",
].map((name) => readJson(`../docs/candidate-attestations/${name}`)));

test("public profile overlay only composes passed thematic receipts", () => {
  assert.equal(profileReceipts.length, 28);
  const coveredUnits = new Set(profileReceipts.flatMap((receipt) => receipt.units ?? []));
  for (const requiredUnit of [
    "PROF-ENTRY",
    "PROF-FOLLOW",
    "PROF-FOLLOW-LISTS",
    "PROF-CONTENT",
    "PROF-MEDIA-DETAIL",
    "PROF-PRIVATE-CHAT",
    "PROF-ROLES",
    "PROF-SAFETY",
    "PROF-SHARED-CHAT-DOCUMENTS",
    "OVR-POST-DETAIL",
    "OVR-AUTH-REQUIRED",
    "FLOW-SHELL-NAV",
  ]) {
    assert.ok(coveredUnits.has(requiredUnit), `missing thematic receipt for ${requiredUnit}`);
  }
  for (const receipt of profileReceipts) {
    const evidenceStatuses = Object.values(receipt.evidence ?? {})
      .map((entry) => entry?.status)
      .filter(Boolean);
    assert.ok(
      receipt.status === "passed" ||
        (evidenceStatuses.length > 0 && evidenceStatuses.every((status) => status === "passed")),
    );
    assert.ok(Array.isArray(receipt.units) && receipt.units.length > 0);
  }
});

test("source-backed profile header contract has no invented biography or document repository", () => {
  const header = screenInventory.split(/\r?\n/).find((line) => line.startsWith("| `PROF-HEADER` |"));
  assert.ok(header);
  assert.match(header, /GO focal para nombre\/barrio\/avatar\/roles\/KPI/);
  assert.match(header, /Sin pendiente interno en `PROF-HEADER`/);
  assert.match(neighborhoodModels, /data class NeighborhoodUser\([\s\S]*?val displayName: String/);
  assert.match(neighborhoodModels, /val neighborhood: String/);
  assert.match(neighborhoodModels, /val avatarUrl: String\?/);
  assert.match(neighborhoodModels, /val isAdmin: Boolean/);
  assert.match(neighborhoodModels, /val isOfficial: Boolean/);
  assert.doesNotMatch(neighborhoodModels, /val biography:/);
  assert.match(neighborhoodModels, /data class CommunityUserProfile\([\s\S]*?val attachments: List<ProfileAttachment>/);
});

test("inventories close only the public profile overlay and preserve thematic limits", () => {
  assert.match(screenInventory, /`OVR-PUBLIC-PROFILE` \| \*\*GO compuesto de overlay\.\*\*/);
  assert.match(screenInventory, /La raíz no conserva un límite residual genérico/);
  assert.match(screenInventory, /cada `PROF-\*`, `OVR-POST-DETAIL`, `OVR-AUTH-REQUIRED` y `FLOW-SHELL-NAV`/);
  assert.doesNotMatch(screenInventory, /`OVR-PUBLIC-PROFILE` \| COMÚN con límites/);
  assert.match(platformInventory, /el perfil público queda en GO compuesto de overlay/);
  assert.match(migrationBoard, /Perfil público compuesto/);
  assert.match(migrationBoard, /veintiocho recibos temáticos aprobados/);
  assert.match(migrationBoard, /No atribuye una nueva ejecución Android, Web\/Wasm o iOS/);
});
