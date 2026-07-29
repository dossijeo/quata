package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosFeedAvatarContentTest {
    @Test
    fun remote_avatar_loader_refuses_local_and_embedded_sources() {
        assertTrue(isIosAvatarUrl("https://cdn.example.test/avatar?id=profile-1"))
        assertTrue(isIosAvatarUrl("http://localhost/avatar.png"))
        assertFalse(isIosAvatarUrl("file:///private/avatar.png"))
        assertFalse(isIosAvatarUrl("data:image/png;base64,abc"))
        assertFalse(isIosAvatarUrl(""))
    }
}
