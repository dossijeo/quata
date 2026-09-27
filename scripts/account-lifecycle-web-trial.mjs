import { runAccountLifecycleTrial } from "./account-lifecycle-trial.mjs";
import { createAccountLifecycleWebTrial } from "./e2e-fixtures/account-lifecycle-web.mjs";

export async function runAccountLifecycleWebTrial({ client, serviceKey, chromium, chrome, distribution, outputDirectory,
  privateDirectory, backendUrl, publicKey, preflight, fetchImpl = fetch }) {
  return await runAccountLifecycleTrial({ platform: "web", client, serviceKey, privateDirectory, backendUrl,
    publicKey, preflight, fetchImpl, createUi: () => createAccountLifecycleWebTrial({ chromium, chrome,
      distribution, outputDirectory, backendUrl, publicKey }) });
}
