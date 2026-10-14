import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const inventory = readFileSync(
  new URL("../docs/MULTIPLATFORM_INVENTORY.md", import.meta.url),
  "utf8",
);

const statusRow = inventory
  .split("\n")
  .find((line) => line.startsWith("| Notifications |"));

assert.ok(statusRow, "the Notifications status row must exist");
assert.match(statusRow, /Pantalla común con cierres focales separados de lista, push y Reply/);
assert.match(statusRow, /SCR-NOTIFICATIONS/);
assert.match(statusRow, /FLOW-PUSH-LIFECYCLE/);
assert.match(statusRow, /FLOW-NOTIFICATION-REPLY/);
assert.match(statusRow, /no como un residual KMP genérico/);
assert.match(statusRow, /APNs real, device token y distribución iOS continúan como fronteras externas/);
assert.match(statusRow, /notifications-postflight\.json/);
assert.match(statusRow, /web-notification-native-activation\.json/);
assert.match(statusRow, /web-notification-reply-authenticated\.json/);
assert.match(statusRow, /ios-apns-logout-remote\.json/);
assert.doesNotMatch(statusRow, /\| KMP parcial;/);

console.log("notifications status reconciliation contract: PASS");
