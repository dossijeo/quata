export function buildCommunityRealtimeSuccessReport({
  checkedAt,
  publicTables,
  authenticatedTables,
  publicCount,
  authenticatedCount,
  authenticatedSession,
  triggerTable,
  triggerRowSha256,
}) {
  const authenticatedAttempted = authenticatedSession != null;
  return {
    check: "COMMUNITY-DIRECTORY-REALTIME-PUBLIC-AUTHENTICATED",
    checkedAt,
    ok: true,
    mode: authenticatedAttempted ? "public-and-authenticated" : "public-only",
    publicTables,
    authenticatedTables,
    public: { joinedWithoutAccessToken: true, matchingEventObserved: true, eventCount: publicCount },
    authenticated: authenticatedAttempted
      ? {
          attempted: true,
          joinedWithAccessToken: true,
          authSource: authenticatedSession.source,
          actorSha256: authenticatedSession.actorSha256,
          matchingEventObserved: true,
          eventCount: authenticatedCount,
        }
      : { attempted: false, reason: "public_only_mode" },
    trigger: { table: triggerTable, rowSha256: triggerRowSha256, ownedFixtureRemoved: true },
    cleanup: {
      databaseFixtureRemoved: true,
      authenticationSessionRevocationRequired: authenticatedAttempted,
      authenticationSessionRevoked: authenticatedAttempted ? true : null,
    },
    secretsPersisted: false,
  };
}
