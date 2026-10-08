package com.quata.core.platform

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class AndroidAudioPlayerLifecycleInstrumentedTest {
    @Test
    fun competingAudioFocusPausesActiveChatAudio() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = File(context.cacheDir, "chat-audio-lifecycle.wav")
        val service = AndroidAudioPlayerService(context)
        var activity: Activity? = null

        try {
            writeSilentWave(fixture, durationSeconds = 20)
            val launchIntent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(launchIntent)
            withContext(Dispatchers.Main.immediate) {
                val loaded = service.load(
                    PlatformFile(
                        reference = fixture.toURI().toString(),
                        displayName = fixture.name,
                        mimeType = "audio/wav",
                        sizeBytes = fixture.length(),
                    ),
                )
                assertTrue("Expected local WAV load to succeed, got $loaded", loaded is PlatformResult.Success)
                awaitState(service) { it.phase == AudioPlaybackPhase.Ready }

                assertTrue(service.play() is PlatformResult.Success)
                awaitState(service) { it.isPlaying }

                val audioManager = context.getSystemService(AudioManager::class.java)
                val competingFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                assertTrue(
                    audioManager.requestAudioFocus(competingFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                )
                try {
                    val paused = awaitState(service) { !it.isPlaying && it.phase != AudioPlaybackPhase.Ended }
                    assertFalse(paused.isPlaying)
                } finally {
                    audioManager.abandonAudioFocusRequest(competingFocus)
                }
                delay(500)
                assertFalse("Chat audio must not resume without an explicit user action", service.state().isPlaying)
            }
        } finally {
            val stopResult = runCatching {
                withContext(Dispatchers.Main.immediate) { service.stop() }
            }.getOrNull()
            runCatching { activity?.finish() }
            if (fixture.exists()) fixture.delete()
            assertTrue("Audio service cleanup must complete", stopResult is PlatformResult.Success)
            assertFalse("Temporary WAV must be removed", fixture.exists())
        }
    }

    private suspend fun awaitState(
        service: AndroidAudioPlayerService,
        predicate: (AudioPlaybackState) -> Boolean,
    ): AudioPlaybackState {
        repeat(100) {
            val state = service.state()
            if (predicate(state)) return state
            delay(50)
        }
        error("Timed out waiting for the expected audio state; last=${service.state()}")
    }

    private fun writeSilentWave(file: File, durationSeconds: Int) {
        val sampleRate = 8_000
        val channelCount = 1
        val bitsPerSample = 16
        val dataSize = sampleRate * channelCount * (bitsPerSample / 8) * durationSeconds
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channelCount.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channelCount * (bitsPerSample / 8))
            putShort((channelCount * (bitsPerSample / 8)).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()
        FileOutputStream(file).use { output ->
            output.write(header)
            output.write(ByteArray(dataSize))
        }
    }
}
