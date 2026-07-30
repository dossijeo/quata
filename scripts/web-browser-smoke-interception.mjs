/**
 * Chrome can invalidate a Fetch interception between `Fetch.requestPaused` and the
 * corresponding resolution command while navigation tears down a document.  The
 * in-repository CDP client formats that single protocol response as the exact
 * message below (see CdpClient.receive in web-browser-smoke.mjs).
 *
 * Keep this deliberately exact: a different Fetch/CDP error must still fail the
 * browser smoke instead of being mistaken for an expected navigation race.
 */
const INVALIDATED_INTERCEPTION_MESSAGE = 'Invalid InterceptionId.: ';

export function isInvalidatedFetchInterceptionError(error) {
    return error instanceof Error && error.message === INVALIDATED_INTERCEPTION_MESSAGE;
}
