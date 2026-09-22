import assert from "node:assert/strict";
import test from "node:test";
import {
  createBackendHttpError,
  expectMessageOwnershipRejection,
  isExpectedMessageOwnershipRejection,
} from "./e2e-fixtures/chat-message-ownership-rejection.mjs";

function backendError(status, payload) {
  return createBackendHttpError("chat_rpc_failed:test", status, JSON.stringify(payload));
}

test("accepts only the exact SQLSTATE and ownership message for each mutation", async () => {
  const edit = backendError(400, { code: "42501", message: "message cannot be edited" });
  const remove = backendError(400, { code: "42501", message: "messages cannot be deleted" });

  assert.equal(isExpectedMessageOwnershipRejection(edit, "edit"), true);
  assert.equal(isExpectedMessageOwnershipRejection(remove, "delete"), true);
  assert.equal(isExpectedMessageOwnershipRejection(edit, "delete"), false);
  await expectMessageOwnershipRejection("edit", async () => { throw edit; });
  await expectMessageOwnershipRejection("delete", async () => { throw remove; });
});

test("rejects accepted mutations, timeouts, server errors, and unrelated database errors", async () => {
  await assert.rejects(
    expectMessageOwnershipRejection("edit", async () => {}),
    /message_permissions_backend_mutation_accepted:edit/,
  );
  await assert.rejects(
    expectMessageOwnershipRejection("delete", async () => { throw new Error("chat_rpc_failed:test:network"); }),
    /message_permissions_backend_delete_unexpected_error:network/,
  );
  await assert.rejects(
    expectMessageOwnershipRejection("edit", async () => { throw backendError(500, { code: "42501", message: "message cannot be edited" }); }),
    /message_permissions_backend_edit_unexpected_error:http_500:42501/,
  );
  await assert.rejects(
    expectMessageOwnershipRejection("edit", async () => { throw backendError(400, { code: "57014", message: "canceling statement due to statement timeout" }); }),
    /message_permissions_backend_edit_unexpected_error:http_400:57014/,
  );
  await assert.rejects(
    expectMessageOwnershipRejection("delete", async () => { throw backendError(400, { code: "42501", message: "unrelated permission failure" }); }),
    /message_permissions_backend_delete_unexpected_error:http_400:42501/,
  );
});
