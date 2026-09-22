const expectedOwnershipMessages = Object.freeze({
  edit: "message cannot be edited",
  delete: "messages cannot be deleted",
});

export function createBackendHttpError(prefix, status, responseText) {
  const error = new Error(`${prefix}:http_${status}`);
  let payload = null;
  try {
    payload = responseText ? JSON.parse(responseText) : null;
  } catch {
    // A non-JSON error response is never accepted as ownership evidence.
  }
  Object.defineProperties(error, {
    httpStatus: { value: status },
    backendCode: { value: typeof payload?.code === "string" ? payload.code : null },
    backendMessage: { value: typeof payload?.message === "string" ? payload.message : null },
  });
  return error;
}

export function isExpectedMessageOwnershipRejection(error, kind) {
  const expectedMessage = expectedOwnershipMessages[kind];
  return Boolean(
    expectedMessage
      && Number.isInteger(error?.httpStatus)
      && error.httpStatus >= 400
      && error.httpStatus < 500
      && error?.backendCode === "42501"
      && error?.backendMessage?.trim().toLowerCase() === expectedMessage,
  );
}

export async function expectMessageOwnershipRejection(kind, operation) {
  try {
    await operation();
  } catch (error) {
    if (isExpectedMessageOwnershipRejection(error, kind)) return;
    const failure = error?.httpStatus
      ? `http_${error.httpStatus}:${error.backendCode ?? "missing_code"}`
      : error?.message?.endsWith(":network")
        ? "network"
        : "unclassified";
    throw new Error(`message_permissions_backend_${kind}_unexpected_error:${failure}`);
  }
  throw new Error(`message_permissions_backend_mutation_accepted:${kind}`);
}
