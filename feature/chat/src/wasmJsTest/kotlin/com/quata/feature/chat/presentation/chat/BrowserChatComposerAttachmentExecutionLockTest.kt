package com.quata.feature.chat.presentation.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.random.Random

class BrowserChatComposerAttachmentExecutionLockTest {
    @Test
    fun separateBrowserStoresSerializeOneActorGeneration() = runTest {
        val first = BrowserChatComposerAttachmentExecutionLock()
        val second = BrowserChatComposerAttachmentExecutionLock()
        val actor = "composer-lock-${Random.nextLong().toString(16)}"
        val firstAcquired = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondAcquired = CompletableDeferred<Unit>()

        val firstJob = async {
            first.withLock(actor, 7L) {
                firstAcquired.complete(Unit)
                releaseFirst.await()
            }
        }
        firstAcquired.await()
        val secondJob = async {
            second.withLock(actor, 7L) {
                secondAcquired.complete(Unit)
            }
        }

        yield()
        assertFalse(secondAcquired.isCompleted)
        releaseFirst.complete(Unit)
        firstJob.await()
        secondJob.await()
        assertTrue(secondAcquired.isCompleted)
    }

    @Test
    fun cancelledWaiterNeverRunsItsAttachmentEffectLater() = runTest {
        val first = BrowserChatComposerAttachmentExecutionLock()
        val second = BrowserChatComposerAttachmentExecutionLock()
        val actor = "composer-cancel-${Random.nextLong().toString(16)}"
        val firstAcquired = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val cancelledEffectStarted = CompletableDeferred<Unit>()

        val owner = async {
            first.withLock(actor, 3L) {
                firstAcquired.complete(Unit)
                releaseFirst.await()
            }
        }
        firstAcquired.await()
        val waiter = async {
            second.withLock(actor, 3L) {
                cancelledEffectStarted.complete(Unit)
            }
        }
        yield()
        waiter.cancelAndJoin()
        releaseFirst.complete(Unit)
        owner.await()

        second.withLock(actor, 3L) { Unit }
        assertFalse(cancelledEffectStarted.isCompleted)
    }

    @Test
    fun cancellingAnAcquiredEffectReleasesTheBrowserLock() = runTest {
        val first = BrowserChatComposerAttachmentExecutionLock()
        val second = BrowserChatComposerAttachmentExecutionLock()
        val actor = "composer-acquired-cancel-${Random.nextLong().toString(16)}"
        val acquired = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()
        val nextAcquired = CompletableDeferred<Unit>()

        val owner = async {
            first.withLock(actor, 9L) {
                acquired.complete(Unit)
                neverCompletes.await()
            }
        }
        acquired.await()
        owner.cancelAndJoin()

        second.withLock(actor, 9L) {
            nextAcquired.complete(Unit)
        }
        assertTrue(nextAcquired.isCompleted)
    }
}
