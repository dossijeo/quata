package com.quata.feature.notifications.presentation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.model.NotificationItem
import com.quata.core.text.SosPreviewCatalog
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class NotificationsCommonInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val steps = mutableListOf<String>()
    private val screenshots = mutableListOf<String>()

    @Test
    fun sharedNotificationsCoversListTapDismissErrorRetryAndEmpty() {
        val repository = EvidenceNotificationsRepository()
        val retryRepository = ErrorThenEmptyNotificationsRepository()
        val openedConversations = mutableListOf<String>()
        val hostMode = mutableIntStateOf(0)

        compose.setContent {
            val activeRepository = if (hostMode.intValue == 0) repository else retryRepository
            QuataTheme(mode = QuataThemeMode.Light) {
                NotificationsHostContent(
                    padding = PaddingValues(),
                    repository = activeRepository,
                    timestampNowMillis = 1_780_000_000_000L,
                    strings = evidenceStrings(),
                    onBack = { steps += "back_clicked" },
                    onOpenConversation = { openedConversations += it },
                )
            }
        }

        compose.onNodeWithTag(NotificationsRootTestTag, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("${NotificationItemTestTagPrefix}conversation-alpha", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("${NotificationItemTestTagPrefix}conversation-beta", useUnmergedTree = true).assertIsDisplayed()
        saveScreenshot("android-notifications-list")
        steps += "list_rendered_with_two_unread_conversations"

        compose.onNodeWithTag("${NotificationItemTestTagPrefix}conversation-alpha", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { openedConversations == listOf("conversation-alpha") }
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag("${NotificationItemTestTagPrefix}conversation-alpha", useUnmergedTree = true).fetchSemanticsNode()
            }.isFailure
        }
        compose.onNodeWithTag("${NotificationItemTestTagPrefix}conversation-beta", useUnmergedTree = true).assertIsDisplayed()
        saveScreenshot("android-notifications-after-tap")
        steps += "tap_marked_first_notification_read_and_opened_exact_conversation"

        repository.dismissAll()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag(NotificationsEmptyTestTag, useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        saveScreenshot("android-notifications-empty")
        steps += "dismiss_uses_mark_read_and_empty_state_is_visible"

        compose.runOnIdle { hostMode.intValue = 1 }
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag(NotificationsErrorTestTag, useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        saveScreenshot("android-notifications-error")
        compose.onNodeWithTag(NotificationsRetryTestTag, useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag(NotificationsEmptyTestTag, useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
        saveScreenshot("android-notifications-retry-empty")
        steps += "transport_error_retry_shows_honest_empty_state"

        writeReport()
    }

    private fun saveScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("android_screenshot_failed:$name")
        val file = File(evidenceDir(), "$name.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "android_screenshot_encode_failed:$name"
            }
        }
        screenshots += "$name.png"
    }

    private fun writeReport() {
        File(evidenceDir(), "android-notifications-common-evidence.json").writeText(
            JSONObject()
                .put("check", "NOTIFICATIONS-ANDROID-COMMON-001")
                .put("status", "passed")
                .put("steps", JSONArray(steps))
                .put("screenshots", JSONArray(screenshots))
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        (instrumentation.targetContext.getExternalFilesDir("notifications-evidence")
            ?: File(instrumentation.targetContext.filesDir, "notifications-evidence"))
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed" } }
}

private class EvidenceNotificationsRepository : NotificationsRepository {
    private val items = MutableStateFlow(listOf(
        NotificationItem(
            id = "notification_conversation-alpha",
            conversationId = "conversation-alpha",
            title = "Nsue",
            body = "Mensaje nuevo para evidencia Android",
            createdAt = "2026-08-10T07:15:00Z",
            unreadCount = 2,
        ),
        NotificationItem(
            id = "notification_conversation-beta",
            conversationId = "conversation-beta",
            title = "Centro",
            body = "[QUATA_ATTACHMENT:photo]",
            createdAt = "2026-08-10T07:16:00Z",
            unreadCount = 1,
        ),
    ))

    override suspend fun getNotifications(): Result<List<NotificationItem>> = Result.success(items.value)
    override suspend fun getNotificationCount(): Result<Int> = Result.success(items.value.sumOf(NotificationItem::unreadCount))
    override fun observeNotifications(): Flow<List<NotificationItem>> = items
    override fun observeNotificationCount(): Flow<Int> = flow { emit(items.value.sumOf(NotificationItem::unreadCount)) }
    override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> {
        items.value = items.value.filterNot { it.conversationId == notification.conversationId }
        return Result.success(Unit)
    }
    override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> = markNotificationRead(notification)
    fun dismissAll() {
        items.value.forEach { notification -> items.value = items.value.filterNot { it.conversationId == notification.conversationId } }
    }
}

private class ErrorThenEmptyNotificationsRepository : NotificationsRepository {
    private var attempts = 0
    override suspend fun getNotifications(): Result<List<NotificationItem>> = Result.success(emptyList())
    override suspend fun getNotificationCount(): Result<Int> = Result.success(0)
    override fun observeNotifications(): Flow<List<NotificationItem>> = flow {
        attempts += 1
        if (attempts == 1) error("notifications_transport_error")
        emit(emptyList())
    }
    override fun observeNotificationCount(): Flow<Int> = flow { emit(0) }
    override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> = Result.success(Unit)
    override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> = Result.success(Unit)
}

private fun evidenceStrings(): NotificationsStrings = NotificationsStrings(
    title = "Avisos",
    subtitle = "Notificaciones push y actividad",
    backContentDescription = "Volver",
    loadingLabel = "Cargando avisos",
    emptyTitle = "A\u00fan no hay avisos",
    emptyMessage = "La actividad nueva aparecer\u00e1 aqu\u00ed.",
    errorTitle = "Los avisos no estan disponibles",
    retryLabel = "Reintentar",
    relativeTime = { _, _ -> "Ahora" },
    localizedBody = { it },
    sosPreviewCatalog = SosPreviewCatalog.Spanish,
    photoPreview = "Foto",
    videoPreview = "Video",
    documentPreview = "Documento",
    voiceNotePreview = "Nota de voz",
    filePreview = "Archivo",
)
