import { spawn } from "node:child_process";
import { mkdir, open, readFile, rename, unlink } from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";

// Windows coordinator only. Cleartext travels through anonymous pipes and memory,
// never command arguments, environment variables, logs or temporary files.
async function dpapi(value, decrypt = false) {
  if (process.platform !== "win32") throw new Error("recovery_journal_windows_required");
  const operation = decrypt ? "Unprotect" : "Protect";
  const script = `$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Security; try { $bytes=[Convert]::FromBase64String([Console]::In.ReadToEnd()); $result=[Security.Cryptography.ProtectedData]::${operation}($bytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser); [Console]::Out.Write([Convert]::ToBase64String($result)) } catch { exit 1 }`;
  return new Promise((resolve, reject) => {
    const child = spawn("powershell.exe", ["-NoProfile", "-NonInteractive", "-Command", script],
      { windowsHide: true, stdio: ["pipe", "pipe", "ignore"] });
    const chunks = []; let size = 0;
    const timer = setTimeout(() => child.kill(), 15000);
    child.stdout.on("data", chunk => {
      size += chunk.length;
      if (size > 1024 * 1024) child.kill(); else chunks.push(chunk);
    });
    child.once("error", () => { clearTimeout(timer); reject(new Error("recovery_journal_crypto_failed")); });
    child.once("close", code => {
      clearTimeout(timer);
      if (code !== 0 || size > 1024 * 1024) return reject(new Error("recovery_journal_crypto_failed"));
      resolve(Buffer.from(Buffer.concat(chunks).toString("ascii"), "base64"));
    });
    child.stdin.on("error", () => {});
    child.stdin.end(Buffer.from(value).toString("base64"));
  });
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function validateIdentity(value) {
  if (![value.runId, value.profileId, value.authUserId].every(item => uuid.test(item))) {
    throw new Error("recovery_journal_identity_required");
  }
}

export async function createRecoveryJournal({ directory, record }) {
  validateIdentity(record);
  if (!path.isAbsolute(directory)) throw new Error("recovery_journal_absolute_directory_required");
  await mkdir(directory, { recursive: true });
  // One outstanding journal per actor prevents a second run overwriting recovery data.
  const file = path.join(directory, `recovery-${record.profileId}.dpapi`);
  const sealed = await dpapi(Buffer.from(JSON.stringify({ ...record, version: 1 })));
  const handle = await open(file, "wx", 0o600);
  try { await handle.writeFile(sealed); await handle.sync(); } finally { await handle.close(); }
  return openRecoveryJournal({ file, identity: record });
}

export async function openRecoveryJournal({ file, identity }) {
  validateIdentity(identity);
  if (!path.isAbsolute(file)) throw new Error("recovery_journal_absolute_path_required");
  const read = async () => {
    const clear = await dpapi(await readFile(file), true);
    let record;
    try { record = JSON.parse(clear.toString("utf8")); } finally { clear.fill(0); }
    if (record.version !== 1 || ["runId", "profileId", "authUserId"].some(key => record[key] !== identity[key])) {
      throw new Error("recovery_journal_identity_mismatch");
    }
    return record;
  };
  await read();
  async function exclusive(action) {
    let lock;
    try { lock = await open(`${file}.lock`, "wx", 0o600); }
    catch { throw new Error("recovery_journal_concurrent_operation_or_stale_lock"); }
    try {
      await lock.writeFile(JSON.stringify({pid:process.pid,createdAt:new Date().toISOString()}));
      return await action();
    } finally {
      await lock.close();
      await unlink(`${file}.lock`);
    }
  }
  return Object.freeze({
    read,
    async checkpoint(state) {
      return exclusive(async () => {
        const record = await read();
        const temporary = `${file}.${randomUUID()}.tmp`;
        const sealed = await dpapi(Buffer.from(JSON.stringify({ ...record, state })));
        const handle = await open(temporary, "wx", 0o600);
        try { await handle.writeFile(sealed); await handle.sync(); } finally { await handle.close(); }
        try { await rename(temporary, file); }
        catch { await unlink(temporary).catch(() => {}); throw new Error("recovery_journal_checkpoint_failed"); }
      });
    },
    async removeAfterVerification(verify) {
      return exclusive(async () => {
        const record = await read();
        if (typeof verify !== "function") throw new Error("recovery_journal_verification_required");
        let result;
        try { result = await verify(record); }
        catch { throw new Error("recovery_journal_verification_failed"); }
        if (result?.password !== true || result?.secret !== true || result?.sessions !== true) {
          throw new Error("recovery_journal_cleanup_unverified");
        }
        await unlink(file);
      });
    },
  });
}
