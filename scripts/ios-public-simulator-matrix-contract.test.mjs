import assert from 'node:assert/strict';
import test from 'node:test';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const script = readFileSync(resolve(root, 'scripts/run-ios-public-simulator-matrix.sh'), 'utf8');
const classifier = readFileSync(resolve(root, 'scripts/ios-public-screenshot-classifier.swift'), 'utf8');
const parser = resolve(root, 'scripts/ios-public-client-config.py');
const logEvidence = resolve(root, 'scripts/ios-public-log-evidence.py');
const backupLibrary = readFileSync(resolve(root, 'scripts/ios-public-runtime-config-backup.sh'), 'utf8');
const python = process.platform === 'win32' ? 'python' : 'python3';

test('matrix owns one global atomic lock and cleans only its owned resources', () => {
  assert.match(script, /lock_dir="\$\{TMPDIR:-\/tmp\}\/quata-ios-public-simulator-matrix\.lock"/);
  assert.match(script, /if ! mkdir "\$lock_dir"/);
  assert.match(script, /iOS public simulator matrix is already locked/);
  assert.ok(
    script.indexOf('trap cleanup EXIT INT TERM') <
      script.indexOf("printf 'token=%s\\n'"),
    'trap must be installed before the first owner-file write can fail',
  );
  assert.match(
    script.slice(
      script.indexOf('lock_acquired=1'),
      script.indexOf("printf 'token=%s\\n'"),
    ),
    /^lock_acquired=1\r?\ntrap cleanup EXIT INT TERM\r?\n\{\s*$/,
    'no fallible operation may exist between lock ownership and trap installation',
  );
  assert.match(script, /grep -Fxq "token=\$lock_token"/);
  assert.match(script, /Refusing unsafe DerivedData cleanup target/);
  assert.match(backupLibrary, /rm -f "\$config_path"/);
  assert.match(script, /simctl shutdown "\$cleanup_udid"/);
});

test('matrix is fail-closed for launch PID, liveness, mandatory logs and HTTP 200', () => {
  assert.match(script, /sed -nE 's\/\^\[\^:\]\+:\[\[:space:\]\]\*\(\[1-9\]\[0-9\]\*\)/);
  assert.match(script, /simctl spawn "\$udid" launchctl procinfo "\$pid"/);
  assert.match(script, /simctl spawn "\$udid" launchctl print user\/501/);
  assert.match(script, /\/QuataIos\.app\/QuataIos/);
  assert.match(script, /job state = running/);
  assert.match(script, /Launched \$phase PID is not alive as QuataIos/);
  assert.match(script, /processIdentifier == \$cold_pid OR processIdentifier == \$warm_pid/);
  assert.doesNotMatch(script, /log show[^\n]*\|\| true/);
  assert.match(script, /ios-public-log-evidence\.py/);
  assert.match(script, /crash_signatures":0/);
});

function runLogEvidence(content, pids = [101, 202], expectSuccess = true) {
  const directory = mkdtempSync(join(tmpdir(), 'quata-ios-log-'));
  const log = join(directory, 'app.log');
  writeFileSync(log, content);
  try {
    const args = [logEvidence, '--log', log, ...pids.flatMap((pid) => ['--pid', String(pid)])];
    const output = execFileSync(python, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
    assert.equal(expectSuccess, true, 'log verifier unexpectedly accepted adversarial evidence');
    return JSON.parse(output);
  } catch {
    assert.equal(expectSuccess, false, 'log verifier unexpectedly rejected valid evidence');
    return null;
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

test('log evidence accepts only the two exact launch PIDs and rejects foreign HTTP/crash events', () => {
  const validLog = [
    'T Df QuataIos[101:a] Task received response, status 200 content U',
    'T Df QuataIos[202:b] warm launch ready',
  ].join('\n');
  assert.equal(runLogEvidence(validLog).http_200_events, 1);
  runLogEvidence(`${validLog}\nT Df QuataIos[303:c] response_status=200`, [101, 202], false);
  runLogEvidence([
    'T Df QuataIos[101:a] cold ready',
    'T Df QuataIos[202:b] warm ready',
    'T Df QuataIos[303:c] response_status=200',
  ].join('\n'), [101, 202], false);
  runLogEvidence(`${validLog}\nT E QuataIos[202:b] fatal error`, [101, 202], false);
});

test('runtime config backup is always private and restores the original mode', () => {
  assert.match(backupLibrary, /chmod 0600 "\$backup_path"/);
  assert.match(backupLibrary, /QUATA_RUNTIME_CONFIG_MODE="\$\(quata_config_mode "\$config_path"\)"/);
  assert.match(backupLibrary, /chmod "\$QUATA_RUNTIME_CONFIG_MODE" "\$config_path"/);
  assert.match(script, /backup_config=""/);
  assert.match(script, /runtime_config_touched=0/);
  assert.match(script, /quata_backup_runtime_config "\$runtime_config" "\$backup_config"/);
});

test('Vision classifier requires all Feed markers plus dimensions and luminance for pass', () => {
  for (const marker of ['explora quata', 'actualizar', 'conversaciones']) {
    assert.match(classifier, new RegExp(marker));
  }
  assert.match(classifier, /width >= 750 && height >= 1300/);
  assert.match(classifier, /meanLuminance >= 0\.08/);
  assert.match(classifier, /brightPixelFraction >= 0\.12/);
  assert.match(classifier, /markersFound\.count == markers\.count/);
  assert.match(script, /for attempt in 1 2 3/);
  assert.match(script, /"overall":"%s"/);
  assert.match(script, /exit 3/);
});

function runParser(source, expectSuccess) {
  const directory = mkdtempSync(join(tmpdir(), 'quata-ios-parser-'));
  const input = join(directory, 'AppConfig.kt');
  const output = join(directory, 'runtime.xcconfig');
  writeFileSync(input, source);
  try {
    execFileSync(python, [parser, '--source', input, '--output', output], { stdio: 'pipe' });
    assert.equal(expectSuccess, true, 'parser unexpectedly accepted adversarial input');
    return readFileSync(output, 'utf8');
  } catch {
    assert.equal(expectSuccess, false, 'parser unexpectedly rejected valid input');
    return '';
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

const valid = `
object AppConfig {
  const val SUPABASE_URL = "https://project-ref.supabase.co/"
  const val SUPABASE_ANON_KEY: String = "sb_publishable_abcdefghijklmnopqrstuvwxyz"
}`;

test('client parser accepts exact unique declarations and emits slash-indirected xcconfig', () => {
  const output = runParser(valid, true);
  assert.match(output, /https:\$\(QUATA_XCCONFIG_SLASH\)\$\(QUATA_XCCONFIG_SLASH\)project-ref\.supabase\.co\//);
  assert.doesNotMatch(output, /https:\/\//);
});

test('client parser ignores commented decoys and rejects duplicates, Kotlin expressions and xcconfig injection', () => {
  runParser(`// const val SUPABASE_URL = "https://evil.supabase.co/"\n${valid}`, true);
  runParser(`${valid}\nconst val SUPABASE_URL = "https://second.supabase.co/"`, false);
  runParser(valid.replace('"https://project-ref.supabase.co/"', 'buildString { append("https://project-ref.supabase.co/") }'), false);
  runParser(valid.replace('abcdefghijklmnopqrstuvwxyz', 'abc$(INJECT)defghijklmnopqrstuvwxyz'), false);
  runParser(valid.replace('project-ref.supabase.co/', 'attacker.example/'), false);
});

test('matrix still excludes real auth, database and privileged material', () => {
  assert.doesNotMatch(script, /SUPABASE_DB_URL/);
  assert.doesNotMatch(script, /service_role/i);
  assert.match(script, /public-unauthenticated/);
  assert.doesNotMatch(script, /^\s*xcodebuild\s+.*-showBuildSettings/m);
});
