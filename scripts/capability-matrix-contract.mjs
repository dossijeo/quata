import { createHash } from 'node:crypto';
import { lstat, readFile, realpath } from 'node:fs/promises';
import { relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(import.meta.dirname, '..');
const matrixPath = resolve(root, 'capabilities/platform-capability-matrix.json');
const states = ['implemented', 'read-only', 'contract-only', 'blocked', 'external'];
const platforms = ['android', 'web', 'ios'];
const decisiveRoleForState = {
  implemented: 'implementation',
  'read-only': 'read-only-adapter',
  'contract-only': 'contract-boundary',
  blocked: 'explicit-block',
  external: 'external-integration',
};
const stateSemantics = {
  implemented: 'The platform owns an executable implementation of every listed operation.',
  'read-only': 'The platform exposes the listed read flow only; related writes remain unavailable.',
  'contract-only': 'A typed boundary exists, but production execution is disabled pending reviewed runtime or RLS evidence.',
  blocked: 'The executable platform adapter fails closed for every listed operation.',
  external: 'Availability depends on an operating-system, browser, signing, or delivery service outside the repository.',
};

const catalog = {
  'feed.read': ['flow', ['observeFeed', 'getFeed', 'refreshFeed', 'loadOlderFeedPage', 'refreshCurrentUser', 'refreshAuthor', 'refreshPost'], ['implemented', 'read-only', 'read-only']],
  'feed.mutate': ['mutation', ['toggleLike', 'reportPost', 'addComment', 'deletePost'], ['implemented', 'implemented', 'read-only']],
  'official.read': ['flow', ['observeOfficialFeed', 'getOfficialFeed', 'refreshOfficialFeed', 'loadOlderOfficialFeedPage', 'getOfficialPost', 'refreshCurrentUser'], ['implemented', 'read-only', 'read-only']],
  'official.mutate': ['mutation', ['createPost', 'createPosts', 'deletePost', 'toggleLike', 'addComment'], ['implemented', 'blocked', 'blocked']],
  'communities.read': ['flow', ['observeCommunities', 'isCurrentUserAdmin', 'getCachedUserProfile', 'cacheUserProfile', 'observeUserProfile', 'getUserProfile'], ['implemented', 'read-only', 'read-only']],
  'communities.mutate': ['mutation', ['toggleFollowUser', 'reportPost', 'setUserRoles'], ['implemented', 'blocked', 'blocked']],
  'communities.community-chat.open': ['mutation', ['openNeighborhoodChat'], ['implemented', 'blocked', 'blocked']],
  'communities.private-chat.open': ['mutation', ['openPrivateChat'], ['implemented', 'blocked', 'implemented']],
  'composer.publish': ['mutation', ['createPost'], ['implemented', 'contract-only', 'blocked']],
  'profile.remote-mutate': ['mutation', ['saveProfile', 'saveEmergencySettings'], ['implemented', 'contract-only', 'blocked']],
  'profile.avatar-upload': ['mutation', ['uploadIfNeeded'], ['implemented', 'blocked', 'blocked']],
  'push.delivery': ['flow', ['receiveExternalPush'], ['external', 'external', 'external']],
};

function fail(message) { throw new Error(`CAPABILITY-DRIFT-001: ${message}`); }
function same(left, right) { return JSON.stringify(left) === JSON.stringify(right); }
function exactKeys(value, keys, label) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || !same(Object.keys(value).sort(), [...keys].sort())) fail(`${label}: object keys must be exactly ${keys.join(',')}`);
}
function inside(base, candidate) {
  const rel = relative(base, candidate);
  return rel === '' || (!rel.startsWith(`..${sep}`) && rel !== '..');
}

async function verifiedFile(repoPath, expectedHash, io) {
  if (typeof repoPath !== 'string' || !repoPath || repoPath.includes('\\') || repoPath.startsWith('/') || repoPath.split('/').includes('..')) fail(`unsafe source path: ${repoPath}`);
  if (typeof expectedHash !== 'string' || expectedHash.length !== 64) fail(`${repoPath}: invalid SHA-256`);
  const canonicalRoot = await io.realpath(root);
  const candidate = resolve(root, repoPath);
  if (!inside(root, candidate)) fail(`source escapes repository: ${repoPath}`);
  let cursor = root;
  for (const component of repoPath.split('/')) {
    cursor = resolve(cursor, component);
    const stat = await io.lstat(cursor);
    if (stat.isSymbolicLink()) fail(`symlinked source is forbidden: ${repoPath}`);
  }
  const canonical = await io.realpath(candidate);
  if (!inside(canonicalRoot, canonical)) fail(`resolved source escapes repository: ${repoPath}`);
  const stat = await io.lstat(canonical);
  if (!stat.isFile()) fail(`source is not a regular file: ${repoPath}`);
  const normalizedSource = Buffer.from((await io.readFile(canonical)).toString('utf8').replaceAll('\r\n', '\n'));
  const digest = createHash('sha256').update(normalizedSource).digest('hex');
  if (digest !== expectedHash) fail(`${repoPath}: source drift (${digest}); update and review the capability declaration`);
}

export async function validateCapabilityMatrix(matrix, overrides = {}) {
  const io = { readFile, lstat, realpath, ...overrides };
  exactKeys(matrix, ['schemaVersion', 'states', 'stateSemantics', 'capabilities'], 'root');
  if (!matrix || matrix.schemaVersion !== 2 || !Array.isArray(matrix.capabilities)) fail('unsupported or missing schemaVersion/capabilities');
  if (!same(matrix.states, states)) fail('states must be the canonical ordered allowlist');
  exactKeys(matrix.stateSemantics, states, 'stateSemantics');
  if (!same(matrix.stateSemantics, stateSemantics)) fail('state semantics must be explicit and canonical');
  const ids = matrix.capabilities.map(({ id }) => id);
  if (!same(ids, Object.keys(catalog))) fail('capability catalogue is incomplete, reordered, duplicated, or contains an unknown ID');
  for (const capability of matrix.capabilities) {
    exactKeys(capability, ['id', 'kind', 'operations', 'contract', 'platforms'], `capability ${capability?.id}`);
    const [kind, operations, expectedStates] = catalog[capability.id];
    if (capability.kind !== kind || !same(capability.operations, operations)) fail(`${capability.id}: operation catalogue drift`);
    if (capability.contract === null) {
      if (capability.id !== 'push.delivery') fail(`${capability.id}: only external push may omit a Kotlin contract`);
    } else {
      exactKeys(capability.contract, ['path', 'sha256'], `${capability.id}/contract`);
      await verifiedFile(capability.contract?.path, capability.contract?.sha256, io);
    }
    exactKeys(capability.platforms, platforms, `${capability.id}/platforms`);
    for (const [index, platform] of platforms.entries()) {
      const declaration = capability.platforms?.[platform];
      exactKeys(declaration, ['state', 'evidence'], `${capability.id}/${platform}`);
      const expectedState = expectedStates[index];
      if (!declaration || declaration.state !== expectedState) fail(`${capability.id}/${platform}: expected ${expectedState}, got ${declaration?.state}`);
      if (!Array.isArray(declaration.evidence) || declaration.evidence.length < 2) fail(`${capability.id}/${platform}: evidence must cover composition and decisive behavior`);
      const decisiveRole = decisiveRoleForState[declaration.state];
      if (!declaration.evidence.some(({ role }) => role === 'composition')) fail(`${capability.id}/${platform}: evidence lacks composition`);
      if (!declaration.evidence.some(({ role }) => role === decisiveRole)) fail(`${capability.id}/${platform}: evidence lacks decisive ${decisiveRole} behavior`);
      const evidencePaths = declaration.evidence.map(({ path }) => path);
      const evidenceRoles = declaration.evidence.map(({ role }) => role);
      if (new Set(evidencePaths).size !== evidencePaths.length) fail(`${capability.id}/${platform}: duplicate evidence path`);
      if (new Set(evidenceRoles).size !== evidenceRoles.length) fail(`${capability.id}/${platform}: duplicate evidence role`);
      for (const [evidenceIndex, evidence] of declaration.evidence.entries()) {
        exactKeys(evidence, ['path', 'sha256', 'role'], `${capability.id}/${platform}/evidence[${evidenceIndex}]`);
        if (!['composition', 'runtime-bootstrap', 'adapter', ...Object.values(decisiveRoleForState)].includes(evidence.role)) fail(`${capability.id}/${platform}: unknown evidence role ${evidence.role}`);
        await verifiedFile(evidence.path, evidence.sha256, io);
      }
    }
  }
  return matrix.capabilities.map(({ id, kind, operations, platforms: declarations }) => ({
    id,
    kind,
    operations,
    platforms: Object.fromEntries(platforms.map((platform) => [platform, declarations[platform].state])),
  }));
}

export async function loadAndValidateCapabilityMatrix(overrides) {
  let matrix;
  try { matrix = JSON.parse(await readFile(matrixPath, 'utf8')); } catch (error) { fail(`matrix is not valid JSON: ${error.message}`); }
  return validateCapabilityMatrix(matrix, overrides);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const capabilities = await loadAndValidateCapabilityMatrix();
  process.stdout.write(`${JSON.stringify({ schemaVersion: 2, capabilities }, null, 2)}\n`);
}
