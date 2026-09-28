export const cleanupAuthorizationEnvironment = "QUATA_CONVERSATIONS_DEEP_PAGINATION_CLEANUP_AUTHORIZATION";
export const cleanupAuthorizationValue = "MANAGER_APPROVED_QADATA_CONVERSATIONS_DEEP_PAGINATION_CLEANUP";

export function requireCleanupAuthorization(environment = process.env) {
  if (environment[cleanupAuthorizationEnvironment]?.trim() !== cleanupAuthorizationValue) {
    throw new Error("missing_cleanup_authorization");
  }
}
