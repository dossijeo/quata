import assert from "node:assert/strict";
import test from "node:test";
import { requireCompleteProfileResult, resolveProfileByCountry } from "./profile-resolution.mjs";

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
  assert.equal(resolveProfileByCountry(profiles, "+"), null);
  assert.equal(resolveProfileByCountry(profiles, "abc"), null);
  assert.equal(resolveProfileByCountry(profiles, ""), null);
  assert.equal(resolveProfileByCountry(profiles, null, true), null);
});

test("a missing country accepts only one exact local-number profile", () => {
  assert.equal(resolveProfileByCountry([profiles[0]], undefined)?.id, "es");
  assert.equal(resolveProfileByCountry([], undefined), null);
  assert.throws(() => resolveProfileByCountry(profiles, undefined), /profile_lookup_ambiguous/);
});

test("duplicate exact country and local-number matches fail closed", () => {
  assert.throws(
    () => resolveProfileByCountry([profiles[0], { ...profiles[0], id: "duplicate" }], "34"),
    /profile_lookup_ambiguous/,
  );
});

test("an exact duplicate beyond the former ten-row sample still fails closed", () => {
  const rows = [
    profiles[0],
    ...Array.from({ length: 10 }, (_, index) => ({
      id: `other-${index}`,
      country_code: `24${index}`,
      phone_local: "600000000",
    })),
    { ...profiles[0], id: "duplicate-after-ten" },
  ];
  assert.throws(() => resolveProfileByCountry(rows, "34"), /profile_lookup_ambiguous/);
});

test("a PostgREST response must prove exact cardinality before actor resolution", () => {
  assert.deepEqual(requireCompleteProfileResult(profiles, 2), profiles);
  assert.throws(() => requireCompleteProfileResult([profiles[0]], 2), /profile_lookup_truncated/);
  assert.throws(() => requireCompleteProfileResult([profiles[0]], null), /profile_lookup_truncated/);
});
