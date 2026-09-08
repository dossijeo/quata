import { isDeepStrictEqual } from "node:util";

// The caller owns a dedicated connection and serializes access to the authorized
// fixture account. Values remain private in this closure; never serialize them.
// Password restoration belongs to the recovery product flow, not this SQL helper.
export async function snapshotRecoverySecret({ client, profileId, authUserId }) {
  if (!client?.query || !profileId || !authUserId) throw new Error("recovery_snapshot_identity_required");
  const identity = [profileId, authUserId];
  const read = async () => {
    const result = await client.query(
      `select secret_question, secret_answer, secret_answer_hash
         from public.community_profiles where id = $1 and auth_user_id = $2`, identity,
    );
    if (result.rowCount !== 1) throw new Error("recovery_snapshot_identity_mismatch");
    return result.rows[0];
  };
  const original = await read();
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
              set secret_question = $1, secret_answer = $2, secret_answer_hash = $3
            where id = $4 and auth_user_id = $5 returning id`,
          [original.secret_question, original.secret_answer, original.secret_answer_hash, ...identity],
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
