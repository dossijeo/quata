export function resolveFederatedProfile(rows) {
  const profiles = Array.isArray(rows) ? rows : [];
  if (profiles.length === 0) return { error: "federated_profile_not_linked", status: 403 };
  if (profiles.length !== 1) return { error: "federated_profile_ambiguous", status: 403 };
  const profile = profiles[0];
  if (profile?.account_status === "deactivated" || profile?.deactivated_at) {
    return { error: "account_deactivated", status: 403 };
  }
  return { profile };
}
