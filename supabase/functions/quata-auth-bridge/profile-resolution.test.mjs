import assert from "node:assert/strict";
import test from "node:test";
import { resolveProfileByCountry } from "./profile-resolution.mjs";

const profiles = [
  { id: "es", country_code: "34", phone_local: "600000000" },
  { id: "gq", code: "+240", phone_local: "600000000" },
];

test("an explicit country selects only the matching profile", () => {
  assert.equal(resolveProfileByCountry(profiles, "+240")?.id, "gq");
  assert.equal(resolveProfileByCountry(profiles, "34")?.id, "es");
});

test("an explicit wrong country never falls back to another profile with the same local number", () => {
  assert.equal(resolveProfileByCountry(profiles, "44"), null);
});

test("a missing country preserves the legacy first-row fallback", () => {
  assert.equal(resolveProfileByCountry(profiles, "")?.id, "es");
  assert.equal(resolveProfileByCountry([], ""), null);
});
