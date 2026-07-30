import assert from 'node:assert/strict';
import test from 'node:test';
import { isInvalidatedFetchInterceptionError } from './web-browser-smoke-interception.mjs';

test('ignores only the exact CDP error for an already-invalidated Fetch interception', () => {
    assert.equal(
        isInvalidatedFetchInterceptionError(new Error('Invalid InterceptionId.: ')),
        true,
    );
});

test('does not ignore other Fetch/CDP errors or non-error values', () => {
    for (const error of [
        new Error('Invalid InterceptionId.'),
        new Error('Invalid InterceptionId.: different detail'),
        new Error('No resource with given identifier found'),
        new Error('Chrome DevTools connection closed.'),
        { message: 'Invalid InterceptionId.: ' },
        null,
        undefined,
    ]) {
        assert.equal(isInvalidatedFetchInterceptionError(error), false);
    }
});
