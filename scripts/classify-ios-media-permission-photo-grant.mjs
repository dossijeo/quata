import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

const expectedTest = 'testGrantedPhotoReadWritePermissionReflectsSimulatorPrivacyState()';
const expectedIdentifier = `IosMediaPermissionRuntimeTests/${expectedTest}`;
const expectedFailure = /^IosMediaPermissionRuntimeTests\.swift:\d+: XCTAssertTrue failed - Expected Granted, received Denied$/;

function collectNodes(nodes, collected = []) {
  for (const node of nodes ?? []) {
    collected.push(node);
    collectNodes(node.children, collected);
  }
  return collected;
}

export function classifyPhotoGrantResult(summary, tests) {
  if (
    summary?.result !== 'Failed' ||
    summary?.totalTestCount !== 1 ||
    summary?.failedTests !== 1 ||
    summary?.passedTests !== 0 ||
    summary?.skippedTests !== 0 ||
    summary?.expectedFailures !== 0
  ) {
    throw new Error('photo_grant_unexpected_summary');
  }

  const summaryFailures = summary.testFailures ?? [];
  if (
    summaryFailures.length !== 1 ||
    summaryFailures[0]?.targetName !== 'QuataIosTests' ||
    summaryFailures[0]?.testName !== expectedTest ||
    summaryFailures[0]?.failureText !== 'XCTAssertTrue failed - Expected Granted, received Denied'
  ) {
    throw new Error('photo_grant_unexpected_summary_failure');
  }

  const nodes = collectNodes(tests?.testNodes);
  const cases = nodes.filter((node) => node.nodeType === 'Test Case');
  if (
    cases.length !== 1 ||
    cases[0]?.name !== expectedTest ||
    cases[0]?.nodeIdentifier !== expectedIdentifier ||
    cases[0]?.result !== 'Failed'
  ) {
    throw new Error('photo_grant_unexpected_test_case');
  }

  const failureMessages = nodes.filter((node) => node.nodeType === 'Failure Message');
  if (
    failureMessages.length !== 2 ||
    failureMessages.some((node) => !expectedFailure.test(node.name ?? ''))
  ) {
    throw new Error('photo_grant_unexpected_failure_messages');
  }

  return {
    classification: 'simulator_read_write_grant_unavailable',
    expectedAssertions: 2,
    unexpectedFailures: 0,
  };
}

async function main(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    if (argv[index] === '--summary') options.summary = argv[++index];
    else if (argv[index] === '--tests') options.tests = argv[++index];
    else throw new Error(`Unknown argument: ${argv[index]}`);
  }
  if (!options.summary || !options.tests) throw new Error('summary_and_tests_are_required');
  const [summary, tests] = await Promise.all([
    readFile(options.summary, 'utf8').then(JSON.parse),
    readFile(options.tests, 'utf8').then(JSON.parse),
  ]);
  process.stdout.write(`${JSON.stringify(classifyPhotoGrantResult(summary, tests), null, 2)}\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2)).catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
