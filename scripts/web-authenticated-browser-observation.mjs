export async function waitForCounterQuiescence(
  readCounter,
  {
    quietMs = 1_000,
    timeoutMs = 5_000,
    pollMs = 50,
    now = Date.now,
    wait = (delayMs) => new Promise(resolve => setTimeout(resolve, delayMs)),
  } = {},
) {
  if (typeof readCounter !== "function") throw new TypeError("counter_reader_required");
  if (quietMs <= 0 || timeoutMs < quietMs || pollMs <= 0) {
    throw new RangeError("counter_quiescence_window_invalid");
  }

  const startedAt = now();
  let lastChangedAt = startedAt;
  let value = readCounter();
  while (now() - startedAt < timeoutMs) {
    await wait(pollMs);
    const sampledAt = now();
    if (sampledAt - startedAt > timeoutMs) break;
    const nextValue = readCounter();
    if (nextValue !== value) {
      value = nextValue;
      lastChangedAt = sampledAt;
    } else if (sampledAt - lastChangedAt >= quietMs) {
      return value;
    }
  }
  throw new Error("counter_quiescence_timeout");
}
