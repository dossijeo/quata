/**
 * Hermetic staging-evidence gate for the documented DocMentis lifecycle.
 *
 * This deliberately validates only a small, redacted event transcript. It does
 * not start a browser, contact DocMentis, inspect a canvas, or establish that
 * any pixels were rendered.
 */
const FIXTURES = Object.freeze({
  'staging-pdf': 'PDF',
  'staging-docx': 'DOCX',
  'staging-pptx': 'PPTX',
  'staging-xlsx': 'XLSX',
});

const REQUIRED_EVENTS = Object.freeze([
  'document:load',
  'customPageOverlay',
  'isLoaded',
  'pageCount',
  'cleanup',
]);

const FAILURE = Object.freeze({
  invalidEvidence: 'invalid_evidence',
  unauthorizedFixture: 'unauthorized_fixture',
  unauthorizedFormat: 'unauthorized_format',
  invalidEventShape: 'invalid_event_shape',
  lifecycleIncomplete: 'lifecycle_incomplete',
});

function failed(code) {
  // Keep reports suitable for CI artifacts: never reflect caller-supplied text.
  return Object.freeze({
    status: 'failed',
    evidenceKind: 'docmentis_lifecycle',
    code,
    visualEvidence: 'not_evaluated',
  });
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value, keys) {
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  return actual.length === expected.length && actual.every((key, index) => key === expected[index]);
}

function isAllowedEvent(event, expectedType, sequence) {
  if (!isPlainObject(event) || event.type !== expectedType || event.sequence !== sequence) return false;
  if (expectedType === 'isLoaded') return hasExactKeys(event, ['type', 'sequence', 'value']) && event.value === true;
  if (expectedType === 'pageCount') return hasExactKeys(event, ['type', 'sequence', 'value']) && Number.isInteger(event.value) && event.value >= 1;
  return hasExactKeys(event, ['type', 'sequence']);
}

export const AUTHORIZED_DOCMENTIS_STAGING_FORMATS = Object.freeze(Object.values(FIXTURES));

/**
 * Accepts one authorized fixture transcript. Failure reports contain only fixed
 * codes, so URLs, tokens, storage paths, and personal data cannot leak into CI.
 */
export function validateDocmentisStagingLifecycle(evidence) {
  if (!isPlainObject(evidence) || !hasExactKeys(evidence, ['fixtureId', 'format', 'events'])) {
    return failed(FAILURE.invalidEvidence);
  }
  if (!Object.hasOwn(FIXTURES, evidence.fixtureId)) return failed(FAILURE.unauthorizedFixture);
  if (!AUTHORIZED_DOCMENTIS_STAGING_FORMATS.includes(evidence.format)) return failed(FAILURE.unauthorizedFormat);
  if (FIXTURES[evidence.fixtureId] !== evidence.format) return failed(FAILURE.unauthorizedFixture);
  if (!Array.isArray(evidence.events) || evidence.events.length !== REQUIRED_EVENTS.length) return failed(FAILURE.lifecycleIncomplete);

  for (const [index, expectedType] of REQUIRED_EVENTS.entries()) {
    const event = evidence.events[index];
    if (!isPlainObject(event) || !hasExactKeys(event, expectedType === 'isLoaded' || expectedType === 'pageCount' ? ['type', 'sequence', 'value'] : ['type', 'sequence'])) {
      return failed(FAILURE.invalidEventShape);
    }
    if (!isAllowedEvent(event, expectedType, index + 1)) return failed(FAILURE.lifecycleIncomplete);
  }

  return Object.freeze({
    status: 'passed',
    evidenceKind: 'docmentis_lifecycle',
    fixtureId: evidence.fixtureId,
    format: evidence.format,
    lifecycle: Object.freeze({
      documentLoad: true,
      customPageOverlay: true,
      isLoaded: true,
      pageCount: true,
      cleanup: true,
    }),
    visualEvidence: 'not_evaluated',
  });
}

export { FAILURE as DOCMENTIS_STAGING_LIFECYCLE_FAILURE };
