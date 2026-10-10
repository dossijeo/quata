export function resolveProfileByCountry(rows, rawCountryCode, countryCodeProvided = rawCountryCode !== undefined) {
  const candidates = Array.isArray(rows) ? rows : [];
  if (!countryCodeProvided) return requireUniqueProfile(candidates);
  const countryCode = digitsOnly(rawCountryCode || "");
  if (!countryCode) return null;
  return requireUniqueProfile(candidates.filter((profile) =>
    digitsOnly(profile?.country_code || profile?.code || "") === countryCode
  ));
}

export function requireCompleteProfileResult(rows, count) {
  const candidates = Array.isArray(rows) ? rows : [];
  if (!Number.isSafeInteger(count) || count < 0 || count !== candidates.length) {
    throw new Error("profile_lookup_truncated");
  }
  return candidates;
}

function requireUniqueProfile(candidates) {
  if (candidates.length > 1) throw new Error("profile_lookup_ambiguous");
  return candidates[0] ?? null;
}

function digitsOnly(value) {
  return String(value).replace(/\D/g, "");
}
