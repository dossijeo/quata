import { readFile } from 'node:fs/promises';
import path from 'node:path';

/** Paths and device identities only; credentials remain in the existing private files/journal. */
export function validateRecoveryRuntime(input, platform) {
  const value = input?.runtime;
  const fail = () => { throw Error('recovery_runtime_configuration_invalid'); };
  if (!value || !['web', 'android', 'ios'].includes(platform)) fail();
  const absolute = ['root', 'dependencyPackage', 'databaseUrlFile', 'databaseCaFile'];
  if (platform === 'web') absolute.push('distribution', 'preparationManifest', 'browserExecutable');
  if (platform === 'android') absolute.push('adbExecutable', 'preparationManifest');
  if (platform === 'ios') absolute.push('sshExecutable', 'resourceDirectory');
  for (const key of absolute) {
    if (typeof value[key] !== 'string' || !path.isAbsolute(value[key]) || /[\r\n\0]/.test(value[key])) fail();
  }
  if (platform === 'android' && (!/^[a-zA-Z0-9_.:-]+$/.test(value.serial ?? '') ||
      !/^[a-zA-Z0-9_.-]+$/.test(value.avdName ?? '') || typeof value.androidApi !== 'string' || !/^\d+$/.test(value.androidApi))) fail();
  if (platform === 'ios') {
    if (!/^\/[a-zA-Z0-9_./-]+$/.test(value.macWorktree ?? '') || value.macWorktree.includes('..') ||
        !/^[a-zA-Z0-9][a-zA-Z0-9_.@-]*$/.test(value.sshHost ?? '') ||
        !/^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$/.test(value.simulator ?? '') ||
        typeof value.simulatorName !== 'string' || !value.simulatorName.trim()) fail();
  }
  return Object.freeze({ ...value });
}

export async function loadRecoveryInput(file, platform) {
  try {
    const input = JSON.parse(await readFile(file, 'utf8'));
    return { input, runtime: validateRecoveryRuntime(input, platform) };
  } catch { throw Error('recovery_runtime_configuration_invalid'); }
}

export function recoveryTransportEnvironment(source = process.env) {
  return Object.fromEntries(['SystemRoot', 'WINDIR', 'USERPROFILE', 'ProgramData', 'TEMP', 'TMP', 'PATH', 'HOME', 'SSH_AUTH_SOCK']
    .filter(key => typeof source[key] === 'string').map(key => [key, source[key]]));
}
