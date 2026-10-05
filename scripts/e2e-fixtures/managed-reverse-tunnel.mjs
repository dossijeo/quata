import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";

export async function startManagedReverseTunnel({
  command = "ssh",
  args,
  spawnOptions,
  verify,
  startupDelayMs = 1_000,
  stopTimeoutMs = 5_000,
  spawnProcess = spawn,
  wait = delay,
  startError = (stderr) => new Error(`reverse_tunnel_start_failed:${stderr}`),
}) {
  const child = spawnProcess(command, args, spawnOptions);
  let stderr = "";
  let spawnFailure = null;
  child.stderr.on("data", (chunk) => { stderr += chunk; });
  const failed = new Promise((resolvePromise, rejectPromise) => child.once("error", (error) => {
    spawnFailure = error;
    rejectPromise(error);
  }));
  const closed = new Promise((resolvePromise) => child.once("close", resolvePromise));

  const stop = async () => {
    if (child.exitCode === null && spawnFailure === null) child.kill("SIGTERM");
    await Promise.race([
      closed,
      wait(stopTimeoutMs).then(() => { throw new Error("reverse_tunnel_stop_timeout"); }),
    ]);
  };

  try {
    await Promise.race([wait(startupDelayMs), failed]);
    if (child.exitCode !== null) throw startError(stderr);
    await Promise.race([verify(), failed]);
    return { stop };
  } catch (error) {
    try {
      await stop();
    } catch (cleanupError) {
      throw new AggregateError([error, cleanupError], "reverse_tunnel_start_cleanup_failed");
    }
    throw error;
  }
}
