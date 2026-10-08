@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BrowserAudioPlayerLifecycleTest {
    @Test
    fun hiddenDocumentPausesActiveChatAudio() = runTest {
        installBrowserAudioLifecycleFixture()
        val service = BrowserAudioPlayerService()

        try {
            assertIs<PlatformResult.Success<AudioPlaybackState>>(
                service.load(PlatformFile("blob:https://localhost/chat-audio-lifecycle")),
            )
            val playing = assertIs<PlatformResult.Success<AudioPlaybackState>>(service.play())
            assertTrue(playing.value.isPlaying)

            hideBrowserAudioLifecycleDocument()

            assertEquals(1, browserAudioLifecyclePauseCount())
            assertFalse(service.state().isPlaying)
        } finally {
            service.stop()
            restoreBrowserAudioLifecycleFixture()
        }
    }
}

@JsFun(
    """() => {
      if (globalThis.__quataAudioLifecycleTest) throw new Error('audio_lifecycle_test_already_installed');
      const document = globalThis.document;
      const originalCreateElement = document.createElement.bind(document);
      const visibilityDescriptor = Object.getOwnPropertyDescriptor(document, 'visibilityState');
      const state = globalThis.__quataAudioLifecycleTest = {
        visibilityState: 'visible',
        pauses: 0,
        originalCreateElement,
        visibilityDescriptor,
      };
      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        get: () => state.visibilityState,
      });
      document.createElement = function(tagName, ...args) {
        if (String(tagName).toLowerCase() !== 'audio') return originalCreateElement(tagName, ...args);
        const audio = {
          paused: true,
          ended: false,
          readyState: 1,
          currentTime: 0,
          duration: 20,
          preload: '',
          src: '',
          onloadedmetadata: null,
          oncanplay: null,
          onplaying: null,
          onpause: null,
          onended: null,
          onerror: null,
          play() {
            this.paused = false;
            if (this.onplaying) this.onplaying();
            return Promise.resolve();
          },
          pause() {
            if (!this.paused) {
              this.paused = true;
              state.pauses += 1;
              if (this.onpause) this.onpause();
            }
          },
          load() {
            if (this.src && this.onloadedmetadata) this.onloadedmetadata();
          },
          removeAttribute(name) {
            if (name === 'src') this.src = '';
          },
        };
        return audio;
      };
    }""",
)
private external fun installBrowserAudioLifecycleFixture()

@JsFun(
    """() => {
      const state = globalThis.__quataAudioLifecycleTest;
      state.visibilityState = 'hidden';
      globalThis.document.dispatchEvent(new Event('visibilitychange'));
    }""",
)
private external fun hideBrowserAudioLifecycleDocument()

@JsFun("() => globalThis.__quataAudioLifecycleTest.pauses")
private external fun browserAudioLifecyclePauseCount(): Int

@JsFun(
    """() => {
      const state = globalThis.__quataAudioLifecycleTest;
      if (!state) return;
      globalThis.document.createElement = state.originalCreateElement;
      if (state.visibilityDescriptor) {
        Object.defineProperty(globalThis.document, 'visibilityState', state.visibilityDescriptor);
      } else {
        delete globalThis.document.visibilityState;
      }
      delete globalThis.__quataAudioLifecycleTest;
      delete globalThis.__quataAudioPlayers;
    }""",
)
private external fun restoreBrowserAudioLifecycleFixture()
