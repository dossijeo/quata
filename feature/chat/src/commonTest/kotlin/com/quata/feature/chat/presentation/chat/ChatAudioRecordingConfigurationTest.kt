package com.quata.feature.chat.presentation.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatAudioRecordingConfigurationTest {
    @Test
    fun defaultsToTheBrowserWebmFormat() {
        assertEquals(
            ChatAudioRecordingConfiguration.WEB_MIME_TYPE,
            ChatAudioRecordingConfiguration().toPlatformOptions().mimeType,
        )
    }

    @Test
    fun acceptsTheIosMp4FormatInjectedByTheHost() {
        val configuration = ChatAudioRecordingConfiguration(
            mimeType = ChatAudioRecordingConfiguration.IOS_MIME_TYPE,
        )

        assertEquals("audio/mp4", configuration.toPlatformOptions().mimeType)
    }
}
