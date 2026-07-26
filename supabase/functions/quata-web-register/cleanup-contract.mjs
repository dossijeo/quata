export async function cleanupRegistration(record, dependencies) {
  const events = [];
  try {
    await dependencies.revokeWebSessions(record.profileId, record.authUserId);
    events.push("web_sessions_revoked");
    await dependencies.deleteProfile(record.profileId, record.authUserId);
    events.push("profile_deleted");
    await dependencies.deleteAuthUser(record.authUserId);
    events.push("auth_deleted");
    await dependencies.markCleaned(record.id, events);
    await dependencies.alert("registration_cleanup_completed", record.id);
    return events;
  } catch (error) {
    await dependencies.markCleanupRequired(record.id, "cleanup_retry_failed");
    await dependencies.alert("registration_cleanup_required", record.id);
    throw error;
  }
}
