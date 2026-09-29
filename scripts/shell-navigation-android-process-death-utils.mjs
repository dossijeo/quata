export function parsePidObservation(rawOutput) {
  const lines = String(rawOutput)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  const marker = lines.at(-1)?.match(/^__QUATA_PID_STATUS__:(\d+)$/);
  if (!marker) throw new Error("shell_process_death_pid_status_missing");
  const status = Number(marker[1]);
  const payload = lines.slice(0, -1).join(" ").trim();
  if (status === 1 && !payload) return null;
  if (status !== 0) throw new Error(`shell_process_death_pid_command_failed:${status}`);
  if (!/^\d+(?:\s+\d+)*$/.test(payload)) {
    throw new Error("shell_process_death_pid_observation_invalid");
  }
  return payload;
}
