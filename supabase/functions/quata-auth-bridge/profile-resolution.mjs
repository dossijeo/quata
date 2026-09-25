export function resolveProfileByCountry(rows, rawCountryCode) {
  const candidates = Array.isArray(rows) ? rows : [];
  const countryCode = digitsOnly(rawCountryCode || "");
  if (!countryCode) return candidates[0] ?? null;
  return candidates.find((profile) =>
    digitsOnly(profile?.country_code || profile?.code || "") === countryCode
  ) ?? null;
}

function digitsOnly(value) {
  return String(value).replace(/\D/g, "");
}
