import { randomBytes, randomInt, randomUUID } from "node:crypto";
import { mkdir, open, unlink } from "node:fs/promises";
import path from "node:path";
import { createRecoveryJournal } from "./e2e-fixtures/recovery-private-journal.mjs";
import { createAccountLifecycleFixture, retireAccountLifecycleFixture, seedAccountLifecycleEffects, verifyAccountDeactivated,
  verifyAccountDeleted } from "./e2e-fixtures/account-lifecycle.mjs";
import { loginAccountLifecycleSession } from "./e2e-fixtures/account-lifecycle-session.mjs";

export async function runAccountLifecycleTrial({ platform, client, serviceKey, privateDirectory, backendUrl,
  publicKey, preflight, createUi, fetchImpl = fetch }) {
  if (!path.isAbsolute(privateDirectory) || !["web", "android", "ios"].includes(platform) ||
      typeof serviceKey !== "string" || serviceKey.length < 32 || typeof preflight !== "function" ||
      typeof createUi !== "function") throw new Error("account_lifecycle_trial_configuration_invalid");
  await mkdir(privateDirectory, { recursive: true });
  const lockPath = path.join(privateDirectory, `account-lifecycle-${platform}.lock`);
  const lock = await open(lockPath, "wx", 0o600);
  const runId = randomUUID();
  const report = { unit: "ACCOUNT-LIFECYCLE", runId, platform, status: "failed", phase: "preflight",
    actions: [], cleanupComplete: false };
  const actors = [];
  let pendingAdmin = 0;
  let adminUncertain = false;
  let ui;
  const adminRequest = async ({ method, path: requestPath, body }) => {
    if (method !== "POST" || requestPath !== "/auth/v1/admin/users") {
      throw new Error("account_lifecycle_admin_scope_invalid");
    }
    pendingAdmin += 1;
    try {
      const response = await fetchImpl(new URL(requestPath, backendUrl), { method,
        headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "content-type": "application/json" },
        body: JSON.stringify(body), signal: AbortSignal.timeout(15_000) });
      const result = { status: response.status, body: await response.json() };
      if (response.status !== 200) adminUncertain = true;
      return result;
    } catch {
      adminUncertain = true;
      throw new Error("account_lifecycle_admin_transport_uncertain");
    } finally { pendingAdmin -= 1; }
  };
  const settled = () => pendingAdmin === 0 && !adminUncertain && (ui?.operationsSettled() ?? true);
  try {
    await lock.writeFile(JSON.stringify({ runId, pid: process.pid }));
    await lock.sync();
    if (await preflight() !== true) throw new Error("account_lifecycle_preflight_failed");
    ui = await createUi({ runId });
    for (const action of ["deactivate", "delete"]) {
      const authUserId = randomUUID();
      const password = randomBytes(24).toString("base64url");
      const record = { runId, profileId: randomUUID(), authUserId,
        email: `account-lifecycle-${authUserId}@example.invalid`, countryCode: "240",
        phone: `97${randomInt(100000000, 1000000000)}`, password, state: { sessions: [] } };
      report.phase = `${action}_journal`;
      const journal = await createRecoveryJournal({ directory: privateDirectory, record });
      const actor = { action, record, journal, verified: false };
      actors.push(actor);
      report.phase = `${action}_fixture`;
      await createAccountLifecycleFixture({ client, journal, record, password, adminRequest });
      const ticket = { runId, profileId: record.profileId, authUserId, purpose: "account_lifecycle",
        clientInstanceId: randomUUID() };
      const durable = await journal.read();
      durable.state.sessions.push(ticket);
      await journal.checkpoint(durable.state);
      report.phase = `${action}_login`;
      const session = await loginAccountLifecycleSession({ client, journal, record, ticket, password,
        backendUrl, publicKey, fetchImpl });
      report.phase = `${action}_effects`;
      await seedAccountLifecycleEffects({ client, journal, record, action });
      report.phase = `${action}_ui`;
      const observation = await ui.run({ action, session, record, password,
        clientInstanceId: ticket.clientInstanceId });
      if (observation?.passed !== true) throw new Error(`account_lifecycle_${action}_ui_failed`);
      report.phase = `${action}_verify`;
      const verification = action === "deactivate"
        ? await verifyAccountDeactivated({ client, record,
          sessionRejected: () => authSessionRejected({ backendUrl, publicKey, accessToken: session.accessToken, fetchImpl }),
          protectedActionRejected: () => chatActionRejected({ backendUrl, publicKey, accessToken: session.accessToken,
            profileId: record.profileId, fetchImpl }) })
        : await verifyAccountDeleted({ client, record });
      actor.verified = true;
      report.actions.push({ action, observation, verification });
    }
    report.status = "passed";
  } catch (error) {
    report.error = safeFailure(error);
  } finally {
    report.phase = "cleanup";
    await ui?.close().catch(() => { adminUncertain = true; });
    let cleanupComplete = settled();
    if (cleanupComplete) {
      for (const actor of actors.reverse()) {
        try {
          if (actor.action === "delete" && actor.verified) await verifyAccountDeleted({ client, record: actor.record });
          else await retireAccountLifecycleFixture({ client, journal: actor.journal, record: actor.record,
            operationsSettled: async () => settled() });
          await actor.journal.removeAfterVerification(async () => {
            await verifyAccountDeleted({ client, record: actor.record });
            return { password: true, secret: true, sessions: true };
          });
        } catch { cleanupComplete = false; }
      }
    }
    report.cleanupComplete = cleanupComplete;
    report.phase = "complete";
    await lock.close().catch(() => {});
    await unlink(lockPath).catch(() => {});
  }
  return report;
}

async function authSessionRejected({ backendUrl, publicKey, accessToken, fetchImpl }) {
  try {
    const response = await fetchImpl(new URL("/auth/v1/user", backendUrl), {
      headers: { apikey: publicKey, Authorization: `Bearer ${accessToken}` }, signal: AbortSignal.timeout(10_000) });
    return response.status === 401 || response.status === 403;
  } catch { return false; }
}

async function chatActionRejected({ backendUrl, publicKey, accessToken, profileId, fetchImpl }) {
  try {
    const response = await fetchImpl(new URL("/rest/v1/rpc/quata_chat_get_thread", backendUrl), { method: "POST",
      headers: { apikey: publicKey, Authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
      body: JSON.stringify({ p_actor_profile_id: profileId, p_thread_id: 1, p_known_message_ids: [], p_limit: 1 }),
      signal: AbortSignal.timeout(10_000) });
    return response.status === 401 || response.status === 403;
  } catch { return false; }
}

function safeFailure(error) {
  return String(error?.message ?? error)
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi,
      "$1[REDACTED]").slice(0, 300);
}
