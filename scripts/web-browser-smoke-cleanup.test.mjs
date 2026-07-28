import assert from 'node:assert/strict';
import test from 'node:test';
import { removeChromeProfile } from './web-browser-smoke-cleanup.mjs';

test('profile cleanup is fail-closed when a locked profile remains after bounded retries', async () => {
    let removals = 0;
    let waits = 0;
    await assert.rejects(
        removeChromeProfile('opaque-profile', {
            attempts: 3,
            remove: async () => {
                removals += 1;
                throw new Error('EPERM C:/sensitive/profile');
            },
            pathExists: async () => true,
            wait: async () => { waits += 1; },
        }),
        error => error?.message === 'chrome_profile_cleanup_failed',
    );
    assert.equal(removals, 3);
    assert.equal(waits, 2);
});

test('profile cleanup accepts a profile that disappeared despite a transient remove error', async () => {
    let waits = 0;
    await removeChromeProfile('opaque-profile', {
        remove: async () => { throw new Error('ENOENT'); },
        pathExists: async () => false,
        wait: async () => { waits += 1; },
    });
    assert.equal(waits, 0);
});
