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
            service.stop()
            assertEquals(0, browserAudioLifecycleVisibilityListenerCount())
        } finally {
            service.stop()
            restoreBrowserAudioLifecycleFixture()
        }
    }
}

@JsFun(
    """() => {
      if (globalThis.__quataAudioLifecycleTest) throw new Error('audio_lifecycle_test_already_installed');
      const hadDocument = typeof globalThis.document !== 'undefined';
      const document = hadDocument ? globalThis.document : new EventTarget();
      if (!hadDocument) {
        document.createElement = () => ({ });
        globalThis.document = document;
      }
      const originalCreateElement = document.createElement.bind(document);
      const originalAddEventListener = document.addEventListener.bind(document);
      const originalRemoveEventListener = document.removeEventListener.bind(document);
      const visibilityDescriptor = Object.getOwnPropertyDescriptor(document, 'visibilityState');
      const state = globalThis.__quataAudioLifecycleTest = {
        visibilityState: 'visible',
        pauses: 0,
        hadDocument,
        originalCreateElement,
        originalAddEventListener,
        originalRemoveEventListener,
        visibilityListeners: new Set(),
        visibilityDescriptor,
      };
      document.addEventListener = function(type, listener, options) {
        if (type === 'visibilitychange') state.visibilityListeners.add(listener);
        return originalAddEventListener(type, listener, options);
      };
      document.removeEventListener = function(type, listener, options) {
        if (type === 'visibilitychange') state.visibilityListeners.delete(listener);
        return originalRemoveEventListener(type, listener, options);
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

@JsFun("() => globalThis.__quataAudioLifecycleTest.visibilityListeners.size")
private external fun browserAudioLifecycleVisibilityListenerCount(): Int

@JsFun(
    """() => {
      const state = globalThis.__quataAudioLifecycleTest;
      if (!state) return;
      globalThis.document.createElement = state.originalCreateElement;
      globalThis.document.addEventListener = state.originalAddEventListener;
      globalThis.document.removeEventListener = state.originalRemoveEventListener;
      if (state.visibilityDescriptor) {
        Object.defineProperty(globalThis.document, 'visibilityState', state.visibilityDescriptor);
      } else {
        delete globalThis.document.visibilityState;
      }
      delete globalThis.__quataAudioLifecycleTest;
      delete globalThis.__quataAudioPlayers;
      if (!state.hadDocument) delete globalThis.document;
    }""",
)
private external fun restoreBrowserAudioLifecycleFixture()
