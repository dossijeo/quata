import { isDeepStrictEqual } from "node:util";

// The caller owns a dedicated connection and serializes access to the authorized
// fixture account. Values remain private in this closure; never serialize them.
// Password restoration belongs to the recovery product flow, not this SQL helper.
function fieldsFor(storageFormat) {
  if (storageFormat === "legacy-v32") return ["secret_question", "secret_answer"];
  if (storageFormat === "hashed-v1") return ["secret_question", "secret_answer", "secret_answer_hash"];
  throw new Error("recovery_snapshot_storage_format_invalid");
}

function reader({ client, profileId, authUserId, storageFormat }) {
  if (!client?.query || !profileId || !authUserId) throw new Error("recovery_snapshot_identity_required");
  const identity = [profileId, authUserId];
  return async () => {
    const result = await client.query(
      `select ${fieldsFor(storageFormat).join(", ")}
         from public.community_profiles where id = $1 and auth_user_id = $2`, identity,
    );
    if (result.rowCount !== 1) throw new Error("recovery_snapshot_identity_mismatch");
    return result.rows[0];
  };
}

export async function snapshotRecoverySecret({ client, profileId, authUserId, persistSnapshot, storageFormat = "hashed-v1" }) {
  const read = reader({ client, profileId, authUserId, storageFormat });
  const original = await read();
  // The private journal must durably persist before the caller can start Save.
  if (persistSnapshot) await persistSnapshot({ ...original });
  return restorer({ client, profileId, authUserId, read, original, storageFormat });
}

export async function resumeRecoverySecretSnapshot({ client, profileId, authUserId, original, storageFormat = "hashed-v1" }) {
  const fields = fieldsFor(storageFormat).sort();
  if (!original || !isDeepStrictEqual(Object.keys(original).sort(), fields) ||
      fields.some(key => original[key] !== null && typeof original[key] !== "string")) {
    throw new Error("recovery_snapshot_fields_invalid");
  }
  const read = reader({ client, profileId, authUserId, storageFormat });
  await read(); // Require the exact current actor before offering restoration.
  return restorer({ client, profileId, authUserId, read, original: { ...original }, storageFormat });
}

function restorer({ client, profileId, authUserId, read, original, storageFormat }) {
  const identity = [profileId, authUserId];
  const fields = fieldsFor(storageFormat);
  let restored = false;
  return Object.freeze({
    async verify() {
      return isDeepStrictEqual(await read(), original);
    },
    async restore() {
      if (restored) {
        if (!isDeepStrictEqual(await read(), original)) throw new Error("recovery_restore_changed_after_verification");
        return true;
      }
      await client.query("begin");
      try {
        const result = await client.query(
          `update public.community_profiles
              set ${fields.map((field, index) => `${field} = $${index + 1}`).join(", ")}
            where id = $${fields.length + 1} and auth_user_id = $${fields.length + 2} returning id`,
          [...fields.map(field => original[field]), ...identity],
        );
        if (result.rowCount !== 1) throw new Error("recovery_restore_identity_mismatch");
        if (!isDeepStrictEqual(await read(), original)) throw new Error("recovery_restore_readback_mismatch");
        await client.query("commit");
        restored = true;
        return true;
      } catch (error) {
        await client.query("rollback").catch(() => {});
        throw error;
      }
    },
  });
}
