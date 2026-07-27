import assert from "node:assert/strict";
import test from "node:test";
import {
  hashRegistrationPassword,
  verifyProfilePassword,
  verifyRegistrationPassword,
} from "./web-registration-security.mjs";

const registrationVector =
  "pbkdf2_sha256$210000$00112233445566778899aabbccddeeff$3d71005d4f34335e1be3cf50591aac79f415a4f2511e0808979080d6396e3ed4";

test("registration PBKDF2 vector is accepted by lifecycle credential verification", async () => {
  assert.equal(
    await hashRegistrationPassword("CorrectHorseBattery1", "00112233445566778899aabbccddeeff"),
    registrationVector,
  );
  assert.equal(await verifyRegistrationPassword(registrationVector, "CorrectHorseBattery1"), true);
  assert.equal(await verifyProfilePassword({ pass_hash: registrationVector, pass_plain: null }, "CorrectHorseBattery1"), true);
  assert.equal(await verifyProfilePassword({ pass_hash: registrationVector, pass_plain: null }, "correcthorsebattery1"), false);
});

test("malformed PBKDF2 credentials fail closed before deriving an expensive key", async () => {
  const malformed = [
    "pbkdf2_sha256$99999$00112233445566778899aabbccddeeff$3d71005d4f34335e1be3cf50591aac79f415a4f2511e0808979080d6396e3ed4",
    "pbkdf2_sha256$1000001$00112233445566778899aabbccddeeff$3d71005d4f34335e1be3cf50591aac79f415a4f2511e0808979080d6396e3ed4",
    "pbkdf2_sha256$210000$not-hex$3d71005d4f34335e1be3cf50591aac79f415a4f2511e0808979080d6396e3ed4",
    "pbkdf2_sha256$210000$00112233445566778899aabbccddeeff$short",
    "pbkdf2_sha1$210000$00112233445566778899aabbccddeeff$3d71005d4f34335e1be3cf50591aac79f415a4f2511e0808979080d6396e3ed4",
  ];
  for (const stored of malformed) {
    assert.equal(await verifyRegistrationPassword(stored, "CorrectHorseBattery1"), false, stored);
    assert.equal(
      await verifyProfilePassword({ pass_hash: stored, pass_plain: "CorrectHorseBattery1" }, "CorrectHorseBattery1"),
      false,
      `malformed PBKDF2 must not fall back to pass_plain: ${stored}`,
    );
  }
  assert.equal(await verifyRegistrationPassword(registrationVector, "x".repeat(4097)), false);
});

test("any non-empty pass_hash is authoritative over pass_plain", async () => {
  const plain = "CorrectHorseBattery1";
  const invalidHashes = [
    "not-a-password-hash",
    "38D9CB195BF0A4315D2202BE23C01F7D5FDFF0A276425E88DE56324D5B818F0z",
    "38d9",
    "pbkdf2_sha256$210000$00112233445566778899aabbccddeeff$not-hex",
    "pbkdf2_sha256$not-an-integer$00112233445566778899aabbccddeeff$3d71005d4f34335e1be3cf50591aac79f415a4f2511e0808979080d6396e3ed4",
    "pbkdf2_sha1$210000$00112233445566778899aabbccddeeff$3d71005d4f34335e1be3cf50591aac79f415a4f2511e0808979080d6396e3ed4",
  ];
  for (const pass_hash of invalidHashes) {
    assert.equal(
      await verifyProfilePassword({ pass_hash, pass_plain: plain }, plain),
      false,
      `non-empty malformed/unknown pass_hash must not fall back: ${pass_hash}`,
    );
  }

  const validShaForDifferentPassword = await (async () => {
    const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode("DifferentPassword7"));
    return Array.from(new Uint8Array(hash), (byte) => byte.toString(16).padStart(2, "0")).join("");
  })();
  assert.equal(
    await verifyProfilePassword({ pass_hash: validShaForDifferentPassword, pass_plain: plain }, plain),
    false,
    "a valid hash must take precedence even when pass_plain matches",
  );
  assert.equal(
    await verifyProfilePassword({ pass_hash: validShaForDifferentPassword, pass_plain: plain }, "DifferentPassword7"),
    true,
    "the authoritative valid hash remains usable",
  );
});

test("SHA-256 and pass_plain legacy profiles remain lifecycle-compatible", async () => {
  const legacySha = "38D9CB195BF0A4315D2202BE23C01F7D5FDFF0A276425E88DE56324D5B818F00";
  assert.equal(await verifyProfilePassword({ pass_hash: legacySha, pass_plain: null }, "LegacyPass9"), true);
  assert.equal(await verifyProfilePassword({ pass_hash: legacySha, pass_plain: null }, "LegacyPass8"), false);
  assert.equal(await verifyProfilePassword({ pass_hash: null, pass_plain: "LegacyPass9" }, "LegacyPass9"), true);
  assert.equal(await verifyProfilePassword({ pass_hash: null, pass_plain: "LegacyPass9" }, "LegacyPass8"), false);
  assert.equal(await verifyProfilePassword({ pass_hash: "   ", pass_plain: "LegacyPass9" }, "LegacyPass9"), true);
});
