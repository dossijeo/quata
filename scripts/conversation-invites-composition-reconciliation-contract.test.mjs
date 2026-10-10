import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = async (path) => readFile(new URL(path, import.meta.url), "utf8");
const readJson = async (path) => JSON.parse(await read(path));

const screenInventory = await read("../docs/SCREEN_MIGRATION_INVENTORY_V2.md");
const platformInventory = await read("../docs/MULTIPLATFORM_INVENTORY.md");
const migrationBoard = await read("../docs/MULTIPLATFORM_MIGRATION_BOARD.md");
const androidIntent = await read("../app/src/main/java/com/quata/feature/chat/presentation/conversations/InviteChannelIntents.kt");
const androidDispatchTest = await read("../app/src/androidTest/java/com/quata/feature/chat/presentation/conversations/InviteChannelIntentsInstrumentedTest.kt");
const commonDispatchTest = await read("../feature/chat/src/commonTest/kotlin/com/quata/feature/chat/presentation/conversations/ConversationInviteChannelSheetTest.kt");
const receiptNames = [
  "conversation-invites-parity.json",
  "conversation-invites-web-contact-picker.json",
];
const receipts = await Promise.all(
  receiptNames.map((name) => readJson(`../docs/candidate-attestations/${name}`)),
);

test("conversation invitations compose only passed platform receipts", () => {
  assert.equal(receipts.length, 2);
  for (const [index, receipt] of receipts.entries()) {
    assert.deepEqual(receipt.units, ["CONV-INVITES"], `unexpected unit: ${receiptNames[index]}`);
    assert.equal(receipt.status, "passed", `receipt is not passed: ${receiptNames[index]}`);
    const statuses = Object.values(receipt.evidence ?? {}).map((entry) => entry?.status);
    assert.ok(statuses.length > 0 && statuses.every((status) => status === "passed"));
  }
  assert.deepEqual(Object.keys(receipts[0].evidence).sort(), ["android", "ios", "web"]);
  assert.deepEqual(Object.keys(receipts[1].evidence), ["web"]);
});

test("inventories close the Qüata-owned selection and dispatch path", () => {
  assert.match(screenInventory, /`CONV-INVITES` \| \*\*GO focal de selección y despacho\.\*\*/);
  assert.match(platformInventory, /`CONV-INVITES` queda en GO focal de selección y despacho/);
  assert.match(migrationBoard, /El alcance propio de `CONV-INVITES` queda en GO focal/);
  assert.match(migrationBoard, /Android, Web\/Wasm e iOS/);
});

test("Android selection maps, filters and dispatches one exact SMS intent", () => {
  assert.match(androidIntent, /internal fun quataInvitationIntent/);
  assert.match(androidIntent, /requestedIntent\.resolveActivity\(context\.packageManager\)/);
  assert.match(androidIntent, /Intent\(requestedIntent\)\.setComponent\(resolvedComponent\)/);
  assert.match(androidDispatchTest, /must leave through an explicit component/);
  assert.match(androidDispatchTest, /platformContactsForChatInvites/);
  assert.match(androidDispatchTest, /filterInviteContacts\(mapped, "Ada Test"\)\.single\(\)/);
  assert.match(androidDispatchTest, /assertEquals\(1, context\.started\.size\)/);
  assert.match(androidDispatchTest, /assertEquals\(Intent\.ACTION_SENDTO, intent\.action\)/);
  assert.match(androidDispatchTest, /assertEquals\("smsto:%2B34%20699%20000%20101", intent\.dataString\)/);
  assert.match(androidDispatchTest, /assertEquals\("\+34 699 000 101", intent\.data\?\.schemeSpecificPart\)/);
  assert.match(androidDispatchTest, /assertEquals\(message, intent\.getStringExtra\("sms_body"\)\)/);
});

test("common rendered sheet clicks the visible target and dispatches one exact share payload", () => {
  assert.match(commonDispatchTest, /PlatformInviteChannelSheet\(/);
  assert.match(commonDispatchTest, /ConversationInviteTargetTestTagPrefix \+ "platform-share"/);
  assert.match(commonDispatchTest, /\.performClick\(\)/);
  assert.match(commonDispatchTest, /assertEquals\(1, payloads\.size\)/);
  assert.match(commonDispatchTest, /assertEquals\(strings\.message, payloads\.single\(\)\.text\)/);
  assert.match(commonDispatchTest, /assertEquals\(strings\.shareTitle, payloads\.single\(\)\.title\)/);
  assert.match(commonDispatchTest, /assertEquals\(emptyList\(\), payloads\.single\(\)\.files\)/);
});

test("composition keeps only materially external third-party effects outside acceptance", () => {
  assert.match(screenInventory, /Recepción, instalación y entrega por terceros son efectos externos/);
  assert.match(screenInventory, /no se inventa un flujo interno de aceptar\/rechazar/);
  assert.match(migrationBoard, /no son observables ni controlables desde Qüata/);
  assert.match(platformInventory, /no se convierten en una carencia funcional o de paridad de Qüata/);
});
