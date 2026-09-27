import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("./e2e-fixtures/account-lifecycle-web.mjs", import.meta.url), "utf8");

test("Web lifecycle adapter drives the real semantic form from observed control bounds", () => {
  for (const anchor of ["settings-account-lifecycle-deactivate", "settings-account-lifecycle-delete",
    "account-lifecycle.dialog", "account-lifecycle.password", "account-lifecycle.confirmation", "account-lifecycle.confirm"]) {
    assert.match(source, new RegExp(anchor.replaceAll(".", "\\.")));
  }
  assert.match(source, /body\?\.action === action && body\?\.password === password/);
  assert.match(source, /matchingRequests !== 1/);
  assert.match(source, /productSessionCleared: true/);
  assert.match(source, /getByRole\("button", \{ name: openLabel, exact: true \}\)/);
  assert.match(source, /getByText\(openLabel, \{ exact: true \}\)/);
  assert.match(source, /getByText\(deleting \? "Eliminar" : "Desactivar", \{ exact: true \}\)/);
  assert.match(source, /page\.getByRole\("dialog"\)/);
  assert.match(source, /dialog\.locator\('input\[type="password"\]'\)/);
  assert.match(source, /page\.keyboard\.insertText\(text\)/);
  assert.match(source, /waitForEnabled\(confirm, "account-lifecycle\.confirm"\)/);
  assert.match(source, /page\.keyboard\.press\("End"\)/);
  assert.match(source, /\$\{prefix\}-candidates\.json/);
  assert.match(source, /passUgcTermsGate\(page\)/);
  assert.match(source, /__quataUgcTermsE2eProduct/);
  assert.match(source, /page\.reload\(\{ waitUntil: "domcontentloaded"/);
  assert.match(source, /control\.boundingBox\(\)/);
  assert.match(source, /page\.mouse\.click\(box\.x \+ \(box\.width \/ 2\), box\.y \+ \(box\.height \/ 2\)\)/);
  assert.doesNotMatch(source, /page\.mouse\.click\(\s*\d|\.tap\(|position:\s*\{/i);
});

test("Web lifecycle adapter settles or marks every mutation uncertain", () => {
  assert.match(source, /page\.on\("request"/);
  assert.match(source, /page\.on\("requestfinished"/);
  assert.match(source, /page\.on\("requestfailed"/);
  assert.match(source, /operationsSettled: \(\) => pending === 0 && !uncertain/);
});
