import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const screenInventory = readFileSync("docs/SCREEN_MIGRATION_INVENTORY_V2.md", "utf8");
const platformInventory = readFileSync("docs/MULTIPLATFORM_INVENTORY.md", "utf8");

function rowFor(id) {
  const marker = `| \`${id}\` |`;
  const row = screenInventory.split("\n").find((line) => line.startsWith(marker));
  assert.ok(row, `missing ${id} inventory row`);
  return row;
}

test("SCR-AUTH-LOGIN records its finite focal closure", () => {
  const row = rowFor("SCR-AUTH-LOGIN");
  assert.match(row, /^\| `SCR-AUTH-LOGIN` \| GO focal de acceso, sesión, retorno y salida;/);
  assert.doesNotMatch(row, /^\| `SCR-AUTH-LOGIN` \| (?:COMÚN con límites|IMPLEMENTADO \/ evidencia pendiente)/);
  for (const evidence of [
    "auth-login-failure-retry.json",
    "native-auth-refresh-http-boundary.json",
    "auth-register-login-return.json",
    "account-recovery-login-entry-web.json",
    "auth-global-logout.json",
  ]) {
    assert.ok(row.includes(evidence), `SCR-AUTH-LOGIN must retain ${evidence}`);
  }
});

test("Google OAuth remains isolated as its external acceptance boundary", () => {
  const login = rowFor("SCR-AUTH-LOGIN");
  const google = rowFor("SCR-AUTH-GOOGLE");
  assert.ok(login.includes("Google OAuth conserva su frontera externa en `SCR-AUTH-GOOGLE`"));
  assert.match(google, /pendiente de aceptación remota/);
  assert.match(platformInventory, /falta habilitar el proveedor con credenciales Google/);
});

test("non-focal platform matrices do not reopen SCR-AUTH-LOGIN", () => {
  const row = rowFor("SCR-AUTH-LOGIN");
  assert.match(row, /Siguen fuera la traza HTTP de paquete, el device token emitido por Apple, la entrega APNs y una recertificación global equivalente Android\/Web\/iOS/);
  assert.match(row, /Las rutas normales de retorno desde Registro y Recovery quedan acreditadas en las tres plataformas/);
});
