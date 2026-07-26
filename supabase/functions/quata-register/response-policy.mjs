export async function opaqueRegistrationDelay(startedAt, {
  now = Date.now, random = Math.random, sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  floorMs = 900, jitterMs = 200,
} = {}) {
  const targetMs = floorMs + Math.floor(random() * (jitterMs + 1));
  await sleep(Math.max(0, targetMs - (now() - startedAt)));
}
