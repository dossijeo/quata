import { rm, stat } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';

const cleanupAttempts = 5;
const retryDelayMs = 250;

/**
 * Removes the disposable Chrome profile and proves it is gone.
 *
 * A locked profile can retain cookies and session state, so a failed cleanup is
 * a failed smoke rather than a warning. The fixed error intentionally contains
 * neither the temporary path nor an operating-system error message.
 */
export async function removeChromeProfile(profileDirectory, dependencies = {}) {
    const remove = dependencies.remove ?? rm;
    const pathExists = dependencies.pathExists ?? exists;
    const wait = dependencies.wait ?? delay;
    const attempts = dependencies.attempts ?? cleanupAttempts;
    for (let attempt = 0; attempt < attempts; attempt += 1) {
        try {
            await remove(profileDirectory, { recursive: true, force: true, maxRetries: 2, retryDelay: 100 });
        } catch (_) {
            // The existence check below distinguishes a stale handle from a
            // concurrently removed directory without exposing OS diagnostics.
        }
        if (!await pathExists(profileDirectory)) return;
        if (attempt + 1 < attempts) await wait(retryDelayMs);
    }
    throw new Error('chrome_profile_cleanup_failed');
}

async function exists(path) {
    try {
        await stat(path);
        return true;
    } catch (error) {
        if (error?.code === 'ENOENT') return false;
        // Unknown filesystem state is not evidence that the profile vanished.
        return true;
    }
}
