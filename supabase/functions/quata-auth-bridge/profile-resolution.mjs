export function resolveProfileByCountry(rows, rawCountryCode, countryCodeProvided = rawCountryCode !== undefined) {
  const candidates = Array.isArray(rows) ? rows : [];
  if (!countryCodeProvided) return candidates[0] ?? null;
  const countryCode = digitsOnly(rawCountryCode || "");
  if (!countryCode) return null;
  return candidates.find((profile) =>
    digitsOnly(profile?.country_code || profile?.code || "") === countryCode
  ) ?? null;
}

function digitsOnly(value) {
  return String(value).replace(/\D/g, "");
}
