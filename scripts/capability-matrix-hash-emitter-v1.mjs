import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

export const emitterVersion = 'capability-matrix-hash-emitter-v1';

const root = resolve(import.meta.dirname, '..');
const matrixPath = resolve(root, 'capabilities/platform-capability-matrix.json');

const normalizedDigest = async (path) => createHash('sha256')
  .update((await readFile(resolve(root, path), 'utf8')).replaceAll('\r\n', '\n'))
  .digest('hex');

const withoutHashes = (value) => {
  if (Array.isArray(value)) return value.map(withoutHashes);
  if (value && typeof value === 'object') return Object.fromEntries(
    Object.entries(value).map(([key, child]) => [key, key === 'sha256' ? '<reviewed-hash>' : withoutHashes(child)]),
  );
  return value;
};

const declarations = (matrix) => {
  const values = [];
  for (const capability of matrix.capabilities) {
    if (capability.contract) values.push(capability.contract);
    for (const platform of Object.values(capability.platforms)) values.push(...platform.evidence);
  }
  return values;
};

export async function emitReviewedHashes(paths, { write = false } = {}) {
  if (!Array.isArray(paths) || paths.length === 0 || new Set(paths).size !== paths.length) {
    throw new Error(`${emitterVersion}: provide a non-empty unique path list`);
  }
  const originalText = await readFile(matrixPath, 'utf8');
  const original = JSON.parse(originalText);
  const originalStructure = JSON.stringify(withoutHashes(original));
  let emittedText = originalText;
  const changes = [];

  for (const path of paths) {
    const matches = declarations(original).filter((entry) => entry.path === path);
    if (matches.length === 0) throw new Error(`${emitterVersion}: path is not declared: ${path}`);
    const previous = [...new Set(matches.map(({ sha256 }) => sha256))];
    if (previous.length !== 1) throw new Error(`${emitterVersion}: inconsistent prior hashes: ${path}`);
    const sha256 = await normalizedDigest(path);
    const token = `"sha256": "${previous[0]}"`;
    const replacement = `"sha256": "${sha256}"`;
    const occurrences = emittedText.split(token).length - 1;
    if (occurrences < matches.length) throw new Error(`${emitterVersion}: declaration replacement underflow: ${path}`);
    emittedText = emittedText.replaceAll(token, replacement);
    changes.push({ path, declarations: matches.length, previous: previous[0], sha256 });
  }

  const emitted = JSON.parse(emittedText);
  if (JSON.stringify(withoutHashes(emitted)) !== originalStructure) {
    throw new Error(`${emitterVersion}: catalogue, states, operations, composition, or evidence paths changed`);
  }
  if (write) await writeFile(matrixPath, emittedText, 'utf8');
  return { emitterVersion, structuralProjection: 'all-fields-except-sha256-v1', changes };
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(import.meta.filename)) {
  const write = process.argv[2] === '--write';
  const paths = process.argv.slice(write ? 3 : 2);
  console.log(JSON.stringify(await emitReviewedHashes(paths, { write }), null, 2));
}
