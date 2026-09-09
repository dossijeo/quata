import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {validateRecoveryRuntime,recoveryTransportEnvironment} from './e2e-fixtures/recovery-runtime-config.mjs';

const absolute = path.resolve('synthetic-runtime');
const base = {root:absolute,dependencyPackage:path.join(absolute,'package.json'),databaseUrlFile:path.join(absolute,'db-private'),databaseCaFile:path.join(absolute,'ca')};
const ios = {...base,sshExecutable:path.join(absolute,'ssh'),resourceDirectory:path.join(absolute,'reports'),macWorktree:'/Users/test/quata',sshHost:'test-mac',simulator:'11111111-2222-3333-4444-555555555555',simulatorName:'Owned recovery'};

test('runtime rejects shell syntax and traversal before opening private state or transport',()=>{
  for(const value of ['/Users/test/quata;touch /tmp/x','/Users/test/../quata','/Users/test/$(whoami)','relative']) {
    assert.throws(()=>validateRecoveryRuntime({runtime:{...ios,macWorktree:value}},'ios'),/configuration_invalid/);
  }
  for(const sshHost of ['-oProxyCommand=bad','test;bad','test\nbad']) {
    assert.throws(()=>validateRecoveryRuntime({runtime:{...ios,sshHost}},'ios'),/configuration_invalid/);
  }
  assert.throws(()=>validateRecoveryRuntime({runtime:{...ios,simulator:'booted'}},'ios'),/configuration_invalid/);
  assert.deepEqual(validateRecoveryRuntime({runtime:ios},'ios'),ios);
});

test('runtime requires explicit local artifacts and device identity',()=>{
  assert.throws(()=>validateRecoveryRuntime({runtime:base},'web'),/configuration_invalid/);
  assert.throws(()=>validateRecoveryRuntime({runtime:{...base,root:'.'}},'ios'),/configuration_invalid/);
  const android={...base,adbExecutable:path.join(absolute,'adb'),preparationManifest:path.join(absolute,'manifest'),serial:'emulator-5556',avdName:'OwnedRecovery',androidApi:'28'};
  assert.deepEqual(validateRecoveryRuntime({runtime:android},'android'),android);
  assert.throws(()=>validateRecoveryRuntime({runtime:{...android,androidApi:28}},'android'),/configuration_invalid/);
  assert.throws(()=>validateRecoveryRuntime({runtime:{...android,serial:'all devices'}},'android'),/configuration_invalid/);
});

test('SSH environment does not forward application credentials',()=>{
  assert.deepEqual(recoveryTransportEnvironment({PATH:'tools',SystemRoot:'windows',SUPABASE_SERVICE_ROLE_KEY:'never-forward',PRIVATE_PASSWORD:'never-forward'}),{SystemRoot:'windows',PATH:'tools'});
});

test('prepared callers fail closed without leaking initialization errors',()=>{
  for(const platform of ['web','android','ios']) {
    let failure;
    try {execFileSync(process.execPath,[`scripts/account-recovery-secret-prepared-${platform}.mjs`,path.join(absolute,'absent-private-input')],{encoding:'utf8',stdio:'pipe'});}
    catch(error){failure=error;}
    assert.equal(failure?.status,1);
    assert.equal(failure.stderr,'');
    const output=JSON.parse(failure.stdout);
    assert.equal(output.state,'failed');
    assert.match(output.error,/initialization_failed$/);
    assert.equal(failure.stdout.includes(absolute),false);
  }
});
