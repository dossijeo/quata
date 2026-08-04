package com.quata.feature.chat.presentation.chat

import kotlin.test.Test
import kotlin.test.assertTrue

class ChatUiStateLoadingTest {
    @Test
    fun initialStateKeepsTheSharedSkeletonVisibleUntilTheFirstMessageSnapshot() {
        assertTrue(ChatUiState().isLoading)
    }
}
