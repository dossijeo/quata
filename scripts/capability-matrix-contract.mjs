import { readFile } from 'node:fs/promises';
import { resolve, relative, sep } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const matrixPath = resolve(root, 'capabilities/platform-capability-matrix.json');
const allowedStates = new Set(['implemented', 'read-only', 'contract-only', 'blocked', 'external']);
const platformNames = ['android', 'web', 'ios'];

function fail(message) { throw new Error(`CAPABILITY-DRIFT-001: ${message}`); }
function safeRepoPath(path) {
  if (typeof path !== 'string' || !path || path.includes('\\') || path.startsWith('/') || path.split('/').includes('..')) fail(`unsafe evidence path: ${path}`);
  const absolute = resolve(root, path);
  if (relative(root, absolute).startsWith(`..${sep}`) || relative(root, absolute) === '..') fail(`evidence escapes repository: ${path}`);
  return absolute;
}

export async function validateCapabilityMatrix(matrix, read = (path) => readFile(path, 'utf8')) {
  if (!matrix || matrix.schemaVersion !== 1 || !Array.isArray(matrix.capabilities)) fail('unsupported or missing schemaVersion/capabilities');
  if (JSON.stringify(matrix.states) !== JSON.stringify([...allowedStates])) fail('states must be the canonical ordered allowlist');
  const ids = new Set();
  for (const capability of matrix.capabilities) {
    if (!/^[a-z][a-z0-9-]*(?:\.[a-z][a-z0-9-]*)+$/.test(capability.id || '')) fail(`invalid capability id: ${capability.id}`);
    if (ids.has(capability.id)) fail(`duplicate capability id: ${capability.id}`);
    ids.add(capability.id);
    if (!['flow', 'mutation'].includes(capability.kind)) fail(`${capability.id}: invalid kind`);
    for (const platform of platformNames) {
      const declaration = capability.platforms?.[platform];
      if (!declaration || !allowedStates.has(declaration.state) || !Array.isArray(declaration.evidence) || declaration.evidence.length === 0) fail(`${capability.id}/${platform}: incomplete declaration`);
      if (capability.kind === 'mutation' && declaration.state === 'external') fail(`${capability.id}/${platform}: a mutation cannot be delegated to an external capability`);
      for (const evidence of declaration.evidence) {
        if (!evidence || typeof evidence.anchor !== 'string' || !evidence.anchor.trim()) fail(`${capability.id}/${platform}: invalid evidence anchor`);
        const source = await read(safeRepoPath(evidence.path));
        if (!source.includes(evidence.anchor)) fail(`${capability.id}/${platform}: missing typed source anchor ${evidence.path}#${evidence.anchor}`);
      }
    }
  }
  const mutationIds = matrix.capabilities.filter(({ kind }) => kind === 'mutation').map(({ id }) => id);
  if (mutationIds.length === 0) fail('no mutation declarations: matrix would not guard advertised writes');
  return matrix.capabilities.map(({ id, kind, platforms }) => ({ id, kind, platforms: Object.fromEntries(platformNames.map((platform) => [platform, platforms[platform].state])) }));
}

export async function loadAndValidateCapabilityMatrix() {
  let matrix;
  try { matrix = JSON.parse(await readFile(matrixPath, 'utf8')); } catch (error) { fail(`matrix is not valid JSON: ${error.message}`); }
  return validateCapabilityMatrix(matrix);
}

if (process.argv[1] === new URL(import.meta.url).pathname) {
  const capabilities = await loadAndValidateCapabilityMatrix();
  process.stdout.write(`${JSON.stringify({ schemaVersion: 1, capabilities }, null, 2)}\n`);
}
