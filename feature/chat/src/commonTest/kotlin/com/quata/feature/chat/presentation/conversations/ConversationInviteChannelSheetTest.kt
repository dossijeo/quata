package com.quata.feature.chat.presentation.conversations

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.ClipboardService
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.SharePayload
import com.quata.core.platform.ShareService
import com.quata.feature.chat.domain.ChatInviteContact
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ConversationInviteChannelSheetTest {
    @Test
    fun visiblePlatformTargetDispatchesTheExactPayloadOnce() = runComposeUiTest {
        val strings = conversationsLocaleCatalogForLanguage("es").invitation
        val payloads = mutableListOf<SharePayload>()
        var dismissals = 0
        val share = object : ShareService {
            override suspend fun share(payload: SharePayload): PlatformResult<Unit> {
                payloads += payload
                return PlatformResult.Success(Unit)
            }
        }
        val clipboard = object : ClipboardService {
            override suspend fun readText(): String? = null
            override suspend fun writeText(text: String) = Unit
        }

        setContent {
            QuataTheme {
                PlatformInviteChannelSheet(
                    contact = ChatInviteContact(
                        id = "platform-contact:34699000101",
                        displayName = "Ada Test",
                        phone = "+34 699 000 101",
                        phoneKeys = setOf("34699000101"),
                        internationalPhone = "+34699000101",
                    ),
                    strings = strings,
                    clipboardService = clipboard,
                    shareService = share,
                    onDismiss = { dismissals += 1 },
                )
            }
        }

        onNodeWithTag(ConversationInviteSheetTestTag, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(
            ConversationInviteTargetTestTagPrefix + "platform-share",
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
            .performClick()

        waitUntil(timeoutMillis = 5_000) { payloads.size == 1 && dismissals == 1 }
        runOnIdle {
            assertEquals(1, payloads.size)
            assertEquals(strings.message, payloads.single().text)
            assertEquals(strings.shareTitle, payloads.single().title)
            assertEquals(emptyList(), payloads.single().files)
            assertEquals(1, dismissals)
        }
    }
}
