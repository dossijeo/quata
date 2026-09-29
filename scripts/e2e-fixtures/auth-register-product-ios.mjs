import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

const OPT_IN = "I_ACCEPT_IOS_REGISTRATION_PRODUCT_TRIAL";
const DEFAULT_HOST = "quata-mac";
const DEFAULT_PROJECT = "/Users/gabriel/Documents/Projects/quata-auth-register-ios-product";
const DEFAULT_DERIVED_DATA = "build/ios-intel-simulator-signed-derived-data";

export function createRegistrationIosTrial({
  root = process.cwd(),
  buildEnvironment = process.env,
  host = buildEnvironment.QUATA_IOS_SSH_HOST?.trim() || DEFAULT_HOST,
  project = buildEnvironment.QUATA_IOS_MAC_PROJECT?.trim() || DEFAULT_PROJECT,
  derivedDataPath = buildEnvironment.QUATA_IOS_DERIVED_DATA_PATH?.trim() || DEFAULT_DERIVED_DATA,
} = {}) {
  let simulatorUdid;
  let remoteInput;
  let remoteRunState;
  let remoteRuntimeBackup;
  let remoteRuntimeExisted = false;
  let localPrivateDirectory;
  let running = false;
  let settled = true;
  let remoteResult;

  const restoreRuntime = async () => {
    if (!remoteRuntimeBackup) return;
    await runSshScript(host, `
set -euo pipefail
runtime=${shellQuote(`${project}/iosApp/Configuration/QuataPublicRuntime.local.xcconfig`)}
backup=${shellQuote(remoteRuntimeBackup)}
if [ ${remoteRuntimeExisted ? "1" : "0"} -eq 1 ]; then cp "$backup" "$runtime"; cmp -s "$backup" "$runtime"; else rm -f "$runtime"; fi
rm -f "$backup"
`);
    const restored = (await runSshScript(host, `
set -euo pipefail
runtime=${shellQuote(`${project}/iosApp/Configuration/QuataPublicRuntime.local.xcconfig`)}
backup=${shellQuote(remoteRuntimeBackup)}
test ! -e "$backup"
if [ ${remoteRuntimeExisted ? "1" : "0"} -eq 1 ]; then test -f "$runtime"; else test ! -e "$runtime"; fi
printf restored
`)).trim();
    if (restored !== "restored") throw new Error("registration_product_ios_runtime_restore_unverified");
    remoteRuntimeBackup = undefined;
  };

  const settleRemoteRun = async () => {
    if (!remoteRunState || settled) return;
    const result = (await runSshScript(host, `
set -euo pipefail
state=${shellQuote(remoteRunState)}
pid_file="$state/pid"
status_file="$state/status"
stage_file="$state/stage"
watchdog_manifest="$state/watchdog-manifest"
test -f "$pid_file"
pid="$(cat "$pid_file")"
case "$pid" in (*[!0-9]*|'') exit 3;; esac
if [ ! -f "$status_file" ]; then
  if kill -0 -- "-$pid" 2>/dev/null; then
    kill -TERM -- "-$pid" 2>/dev/null || true
    for _ in $(seq 1 15); do kill -0 -- "-$pid" 2>/dev/null || break; sleep 1; done
    if kill -0 -- "-$pid" 2>/dev/null; then kill -KILL -- "-$pid" 2>/dev/null || true; fi
  fi
  for _ in $(seq 1 30); do kill -0 -- "-$pid" 2>/dev/null || break; sleep 1; done
  if kill -0 -- "-$pid" 2>/dev/null; then exit 4; fi
  printf 'terminated\n' > "$status_file"
fi
if [ -f "$watchdog_manifest" ]; then
  while IFS= read -r watchdog_state; do
    test -f "$watchdog_state"
    read -r watchdog_pid watchdog_settled <<EOF
$(python3 - "$watchdog_state" <<'PY'
import json, sys
with open(sys.argv[1], encoding="ascii") as handle:
    value = json.load(handle)
pid = value.get("pid")
settled = value.get("settled")
if not isinstance(pid, int) or pid <= 0 or not isinstance(settled, bool):
    raise SystemExit(2)
print(pid, "true" if settled else "false")
PY
)
EOF
    if [ "$watchdog_settled" = true ]; then
      if kill -0 -- "-$watchdog_pid" 2>/dev/null; then exit 6; fi
    else
      if kill -0 -- "-$watchdog_pid" 2>/dev/null; then
        kill -TERM -- "-$watchdog_pid" 2>/dev/null || true
        for _ in $(seq 1 15); do kill -0 -- "-$watchdog_pid" 2>/dev/null || break; sleep 1; done
        if kill -0 -- "-$watchdog_pid" 2>/dev/null; then kill -KILL -- "-$watchdog_pid" 2>/dev/null || true; fi
      fi
      for _ in $(seq 1 30); do kill -0 -- "-$watchdog_pid" 2>/dev/null || break; sleep 1; done
      if kill -0 -- "-$watchdog_pid" 2>/dev/null; then exit 5; fi
    fi
  done < "$watchdog_manifest"
fi
stage=unknown
if [ -f "$stage_file" ]; then stage="$(cat "$stage_file")"; fi
printf 'settled:%s:%s\n' "$(cat "$status_file")" "$stage"
`)).trim();
    const match = /^settled:(terminated|[0-9]+):([a-z0-9_-]+)$/.exec(result);
    if (!match) {
      throw new Error("registration_product_ios_remote_termination_unverified");
    }
    remoteResult = { status: match[1], stage: match[2] };
    settled = true;
    return remoteResult;
  };

  const removeRemoteInput = async () => {
    if (!remoteInput) return;
    await runSshScript(host, `set -euo pipefail; rm -f ${shellQuote(remoteInput)}; test ! -e ${shellQuote(remoteInput)}`);
    remoteInput = undefined;
  };

  return Object.freeze({
    async prepare() {
      requireSafeHost(host);
      requireAbsoluteProject(project);
      const expectedHead = await gitHead(root);
      const state = JSON.parse((await runSshScript(host, `
set -euo pipefail
cd ${shellQuote(project)}
head="$(git rev-parse HEAD)"
if [ -n "$(git status --porcelain)" ]; then dirty=true; else dirty=false; fi
printf '{"head":"%s","dirty":%s}\n' "$head" "$dirty"
`)).trim());
      if (state.head !== expectedHead || state.dirty !== false) {
        throw new Error("registration_product_ios_checkout_not_exact_clean_head");
      }

      simulatorUdid = (await runSshScript(host, `
set -euo pipefail
defaults write com.apple.iphonesimulator ConnectHardwareKeyboard -bool false
name="Quata-AUTH-REGISTER-${expectedHead.slice(0, 8)}-$$"
xcrun simctl create "$name" com.apple.CoreSimulator.SimDeviceType.iPhone-16-Plus com.apple.CoreSimulator.SimRuntime.iOS-18-3
`)).trim();
      if (!/^[0-9A-F-]{36}$/.test(simulatorUdid)) throw new Error("registration_product_ios_ephemeral_simulator_create_failed");
      await runSshScript(host, `xcrun simctl boot ${shellQuote(simulatorUdid)} >/dev/null 2>&1 || true`);

      const values = requiredPublicBuildValues(buildEnvironment);
      localPrivateDirectory = await mkdtemp(join(tmpdir(), "quata-ios-register-runtime-"));
      const localRuntime = join(localPrivateDirectory, "QuataPublicRuntime.local.xcconfig");
      await writeFile(localRuntime, renderRuntimeConfig(values), { mode: 0o600 });
      remoteRuntimeBackup = (await runSshScript(host, `
set -euo pipefail
runtime=${shellQuote(`${project}/iosApp/Configuration/QuataPublicRuntime.local.xcconfig`)}
backup="$(mktemp /tmp/quata-ios-register-runtime.XXXXXX)"
if [ -f "$runtime" ]; then cp "$runtime" "$backup"; printf '%s|1\n' "$backup"; else printf '%s|0\n' "$backup"; fi
`)).trim();
      const separator = remoteRuntimeBackup.lastIndexOf("|");
      remoteRuntimeExisted = remoteRuntimeBackup.slice(separator + 1) === "1";
      remoteRuntimeBackup = remoteRuntimeBackup.slice(0, separator);
      const remoteRuntime = `${project}/iosApp/Configuration/QuataPublicRuntime.local.xcconfig`;
      await run("scp", [localRuntime, `${host}:${remoteRuntime}`]);
      try {
        await runSshScript(host, `
set -euo pipefail
cd ${shellQuote(project)}
chmod 600 iosApp/Configuration/QuataPublicRuntime.local.xcconfig
QUATA_IOS_SIGNED_DERIVED_DATA_PATH=${shellQuote(derivedDataPath)} scripts/build-ios-intel-simulator-signed.sh
app="$(find ${shellQuote(`${derivedDataPath}/Build/Products`)} -path '*SimulatorSigned-iphonesimulator/QuataIos.app' -type d -print -quit)"
[[ -n "$app" ]] || { echo registration_product_ios_built_app_missing >&2; exit 2; }
python3 - "$app/Info.plist" <<'PY'
import plistlib
import sys
from urllib.parse import urlparse

with open(sys.argv[1], "rb") as handle:
    info = plistlib.load(handle)

def configured(name):
    value = info.get(name)
    return isinstance(value, str) and bool(value.strip()) and "$(" not in value and "\\n" not in value

enabled = info.get("QUATA_IOS_REGISTRATION_ENABLED")
required = (
    "QUATA_IOS_REGISTRATION_API_KEY",
    "QUATA_IOS_TURNSTILE_SITE_KEY",
    "QUATA_IOS_TURNSTILE_ALLOWED_ORIGIN",
)
origin = urlparse(str(info.get("QUATA_IOS_TURNSTILE_ALLOWED_ORIGIN", "")))
if enabled != "true" or not all(configured(name) for name in required) or origin.scheme != "https" or not origin.hostname:
    raise SystemExit("registration_product_ios_built_runtime_invalid")
PY
`);
      } finally {
        await restoreRuntime();
      }
    },

    async run({ channel, input }) {
      if (channel !== "ios" || !simulatorUdid || running) throw new Error("registration_product_ios_state_invalid");
      running = true;
      settled = false;
      localPrivateDirectory ??= await mkdtemp(join(tmpdir(), "quata-ios-register-input-"));
      const localInput = join(localPrivateDirectory, "auth-register-product-input.json");
      await writeFile(localInput, `${JSON.stringify(input)}\n`, { mode: 0o600 });
      try {
        remoteInput = (await runSshScript(host, "mktemp /tmp/quata-ios-register-input.XXXXXX")).trim();
        await run("scp", [localInput, `${host}:${remoteInput}`]);
        remoteRunState = `/tmp/quata-ios-register-run.${randomUUID()}`;
        await runSshScript(host, `set -euo pipefail; mkdir -m 700 ${shellQuote(remoteRunState)}; test -d ${shellQuote(remoteRunState)}`);
        let runError;
        try {
          await runSshScript(host, `
set -euo pipefail
cd ${shellQuote(project)}
state=${shellQuote(remoteRunState)}
export QUATA_IOS_AUTH_REGISTER_E2E_FILE=${shellQuote(remoteInput)}
export QUATA_IOS_AUTH_REGISTER_REAL_OPT_IN=${shellQuote(OPT_IN)}
export QUATA_IOS_DERIVED_DATA_PATH=${shellQuote(derivedDataPath)}
export QUATA_IOS_SIMULATOR_UDID=${shellQuote(simulatorUdid)}
export QUATA_IOS_AUTH_REGISTER_LOG_DIR=build/reports/ios/auth-register-real-ui
export QUATA_IOS_AUTH_REGISTER_STAGE_FILE="$state/stage"
export QUATA_IOS_AUTH_REGISTER_WATCHDOG_STATE_DIR="$state"
python3 - "$state" <<'PY'
import os
import subprocess
import sys

state = sys.argv[1]
child = subprocess.Popen(
    ["bash", "scripts/run-ios-auth-register-real-ui-test.sh"],
    env=os.environ.copy(),
    start_new_session=True,
)
with open(os.path.join(state, "pid"), "w", encoding="ascii") as handle:
    handle.write(f"{child.pid}\\n")
code = child.wait()
with open(os.path.join(state, "status"), "w", encoding="ascii") as handle:
    handle.write(f"{code}\\n")
raise SystemExit(code)
PY
`);
        } catch (error) {
          runError = error;
        }
        const result = await settleRemoteRun();
        if (runError) {
          if (result?.stage && result.stage !== "unknown") {
            throw new Error(`registration_product_ios_runner_failed:${result.stage}`);
          }
          throw runError;
        }
        return {
          passed: true,
          channel: "ios",
          exactSubmits: 1,
          authenticatedTransition: true,
          anchors: [
            "auth.register.display-name", "auth.register.neighborhood", "auth.register.country-prefix",
            "auth.register.phone.input", "auth.register.password", "auth.register.secret-question",
            "auth.register.secret-answer", "auth.register.submit",
          ],
        };
      } finally {
        try {
          if (settled) await removeRemoteInput();
        } finally {
          await rm(localInput, { force: true });
          running = false;
        }
      }
    },

    operationsSettled() { return settled && !running; },

    async close() {
      const failures = [];
      try { await settleRemoteRun(); } catch { failures.push("remote_termination"); }
      try { await restoreRuntime(); } catch { failures.push("runtime_restore"); }
      if (settled) {
        try { await removeRemoteInput(); } catch { failures.push("remote_input"); }
      }
      if (remoteRunState && settled) {
        try {
          await runSshScript(host, `set -euo pipefail; rm -rf ${shellQuote(remoteRunState)}; test ! -e ${shellQuote(remoteRunState)}`);
          remoteRunState = undefined;
        } catch { failures.push("remote_run_state"); }
      }
      if (simulatorUdid) {
        try {
          await runSshScript(host, `
set -euo pipefail
inventory="$(mktemp /tmp/quata-ios-sim-inventory.XXXXXX)"
trap 'rm -f "$inventory"' EXIT
xcrun simctl list devices -j > "$inventory"
python3 - "$inventory" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle: json.load(handle)
PY
if grep -F ${shellQuote(simulatorUdid)} "$inventory" >/dev/null; then
  xcrun simctl shutdown ${shellQuote(simulatorUdid)} >/dev/null 2>&1 || true
  xcrun simctl delete ${shellQuote(simulatorUdid)} >/dev/null
fi
xcrun simctl list devices -j > "$inventory"
python3 - "$inventory" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as handle: json.load(handle)
PY
! grep -F ${shellQuote(simulatorUdid)} "$inventory" >/dev/null
rm -rf ${shellQuote(`${project}/iosApp/QuataIos.xcodeproj`)}
test ! -e ${shellQuote(`${project}/iosApp/QuataIos.xcodeproj`)}
`);
          simulatorUdid = undefined;
        } catch { failures.push("simulator"); }
      }
      if (localPrivateDirectory) {
        try {
          await rm(localPrivateDirectory, { recursive: true, force: true });
          localPrivateDirectory = undefined;
        } catch { failures.push("local_private_directory"); }
      }
      if (failures.length > 0) throw new Error(`registration_product_ios_cleanup_incomplete_${failures.join("_")}`);
      return true;
    },
  });
}

function requiredPublicBuildValues(environment) {
  const names = [
    "QUATA_SUPABASE_URL", "QUATA_SUPABASE_PUBLISHABLE_KEY", "QUATA_REGISTRATION_API_KEY",
    "QUATA_TURNSTILE_SITE_KEY", "QUATA_TURNSTILE_ALLOWED_ORIGIN",
  ];
  const values = Object.fromEntries(names.map((name) => [name, environment[name]?.trim()]));
  if (names.some((name) => !values[name] || /[\r\n]/.test(values[name]))) {
    throw new Error("registration_product_ios_public_build_configuration_missing");
  }
  return values;
}

function renderRuntimeConfig(values) {
  const xcUrl = (value) => value.replace("://", ":$(QUATA_XCCONFIG_SLASH)$(QUATA_XCCONFIG_SLASH)");
  return [
    `QUATA_SUPABASE_URL = ${xcUrl(values.QUATA_SUPABASE_URL)}`,
    `QUATA_SUPABASE_PUBLISHABLE_KEY = ${values.QUATA_SUPABASE_PUBLISHABLE_KEY}`,
    "QUATA_IOS_REGISTRATION_ENABLED = true",
    `QUATA_IOS_REGISTRATION_API_KEY = ${values.QUATA_REGISTRATION_API_KEY}`,
    `QUATA_IOS_TURNSTILE_SITE_KEY = ${values.QUATA_TURNSTILE_SITE_KEY}`,
    `QUATA_IOS_TURNSTILE_ALLOWED_ORIGIN = ${xcUrl(values.QUATA_TURNSTILE_ALLOWED_ORIGIN)}`,
    "",
  ].join("\n");
}

async function gitHead(root) {
  return (await command("git", ["rev-parse", "HEAD"], { cwd: root })).trim();
}

function requireSafeHost(host) {
  if (!/^[A-Za-z0-9][A-Za-z0-9_.@-]*$/.test(host)) throw new Error("registration_product_ios_ssh_host_invalid");
}

function requireAbsoluteProject(project) {
  if (!project.startsWith("/") || /[\r\n]/.test(project)) throw new Error("registration_product_ios_project_invalid");
}

function shellQuote(value) {
  return `'${String(value).replaceAll("'", `'"'"'`)}'`;
}

function runSshScript(host, script) {
  return command("ssh", ["-o", "BatchMode=yes", "-o", "ConnectTimeout=15", host, "bash", "-s"], {
    input: script,
    timeout: 20 * 60_000,
  });
}

function run(executable, args, options = {}) {
  return command(executable, args, options).then(() => undefined);
}

function command(executable, args, { cwd, input, timeout = 60_000 } = {}) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(executable, args, { cwd, windowsHide: true, stdio: ["pipe", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    const timer = setTimeout(() => child.kill("SIGKILL"), timeout);
    child.stdout.on("data", (chunk) => { stdout += chunk; });
    child.stderr.on("data", (chunk) => { stderr += chunk; });
    child.once("error", () => { clearTimeout(timer); reject(new Error("registration_product_ios_process_start_failed")); });
    child.once("close", (code) => {
      clearTimeout(timer);
      if (code === 0) resolvePromise(stdout);
      else reject(new Error(`registration_product_ios_process_failed:${code}:${redact(stderr)}`));
    });
    child.stdin.end(input ?? "");
  });
}

function redact(value) {
  const raw = String(value);
  const stageMarker = raw.match(/registration_product_ios_runner_failed:[a-z0-9_-]+/gi)?.at(-1);
  if (stageMarker) return stageMarker.toLowerCase();
  return raw
    .replace(/\b\d{6,}\b/g, "[digits]")
    .replace(/(bearer\s+|authorization\s*[:=]\s*|token\s*[:=]\s*|password\s*[:=]\s*|secret\s*[:=]\s*|apikey\s*[:=]\s*)[^\s,;]+/gi, "$1[REDACTED]")
    .slice(-2_000);
}
