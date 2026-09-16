package com.quata.core.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.quata.QuataApp
import com.quata.R
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Real SystemUI submission. Backend observation and independent cleanup belong to the coordinator. */
@RunWith(AndroidJUnit4::class)
class NotificationReplyProductInstrumentedTest {
    @Test
    fun submitsOneReplyThroughSystemUi() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("quataReplyProduct") == "1")
        fun uuid(name: String): String {
            val value = arguments.getString(name) ?: error("reply_argument_missing")
            check(UUID.fromString(value).toString() == value) { "reply_argument_invalid" }
            return value
        }
        val run = uuid("quataReplyRun")
        val step = uuid("quataReplyStep")
        val actor = uuid("quataReplyActor")
        val authActor = uuid("quataReplyAuthActor")
        val thread = arguments.getString("quataReplyThread") ?: error("reply_thread_missing")
        check(thread.toLongOrNull()?.let { it > 0 && it.toString() == thread } == true) { "reply_thread_invalid" }
        val context = instrumentation.targetContext
        val app = context.applicationContext as? QuataApp ?: error("reply_product_host_required")
        val session = app.container.sessionManager.currentSession() ?: error("reply_owned_session_required")
        check(session.userId == actor && session.authUserId == authActor && session.isSupabaseAuthenticated()) {
            "reply_session_owner_mismatch"
        }
        check((session.expiresAt ?: 0) > System.currentTimeMillis() / 1000 + 900) { "reply_fresh_session_required" }
        val manager = context.getSystemService(NotificationManager::class.java)
        check(manager.areNotificationsEnabled()) { "reply_notification_permission_required" }
        check(manager.getNotificationChannel(NotificationChannels.CHANNEL_SOCIAL)?.importance
            ?.let { it > NotificationManager.IMPORTANCE_NONE } == true) { "reply_channel_required" }
        check(manager.activeNotifications.isEmpty()) { "reply_foreign_notification_present" }
        val preferences = context.getSharedPreferences("quata_chat_notifications", Context.MODE_PRIVATE)
        check(preferences.all.keys.all { it == "posted_chat_notification_ids" }
            && preferences.getStringSet("posted_chat_notification_ids", emptySet()).orEmpty().isEmpty()) {
            "reply_existing_notification_custody"
        }
        val root = File(context.filesDir, "reply-product/$step")
        check(!root.exists() && root.mkdirs()) { "reply_attempt_already_exists" }
        fun record(name: String, value: JSONObject) {
            val file = File(root, name)
            check(file.createNewFile()) { "reply_record_already_exists" }
            FileOutputStream(file).use { output ->
                output.write(value.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
        }
        val device = UiDevice.getInstance(instrumentation)
        fun capture(name: String) {
            check(device.takeScreenshot(File(root, "$name.png"))) { "reply_screenshot_failed" }
            device.dumpWindowHierarchy(File(root, "$name.xml"))
        }
        fun awaitCondition(timeout: Long, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + timeout
            while (!condition()) {
                check(SystemClock.uptimeMillis() < deadline) { "reply_outcome_unresolved" }
                SystemClock.sleep(100)
            }
        }
        val notificationMarker = "qadata-reply-alert-$step"
        val replyMarker = "qadata-reply-text-$step"
        record("intent.json", JSONObject().put("runId", run).put("stepId", step)
            .put("profileId", actor).put("threadId", thread).put("notificationMarker", notificationMarker)
            .put("replyMarker", replyMarker).put("backendVerified", false))
        NotificationFactory(context).showChatPush("sb:$thread", "QADATA Reply", notificationMarker)
        awaitCondition(10_000) { manager.activeNotifications.any {
            it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == notificationMarker
        } }
        val notification = manager.activeNotifications.single()
        check(notification.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == notificationMarker)
        record("notification.json", JSONObject().put("stepId", step).put("id", notification.id))
        check(device.openNotification()) { "reply_notification_shade_failed" }
        check(device.wait(Until.hasObject(By.text(notificationMarker)), 10_000)) { "reply_marker_not_visible" }
        val rowSelector = By.res("com.android.systemui", "expandableNotificationRow")
            .hasDescendant(By.text(notificationMarker))
        val rows = device.findObjects(rowSelector)
        check(rows.size == 1) { "reply_owned_row_not_unique" }
        val actions = rows.single().findObjects(By.text(context.getString(R.string.notification_reply)))
        check(actions.size == 1) { "reply_action_not_unique" }
        capture("before-reply")
        actions.single().click()
        val editorSelector = By.res("com.android.systemui", "remote_input_text")
        check(device.wait(Until.hasObject(editorSelector), 10_000)) { "reply_editor_not_visible" }
        val expanded = device.findObjects(rowSelector)
        check(expanded.size == 1) { "reply_owned_row_not_unique" }
        val editors = expanded.single().findObjects(editorSelector)
        check(editors.size == 1) { "reply_editor_not_unique" }
        // Accessibility may expose the hint as text. Require the OS hint flag,
        // rather than accepting the literal word "Message" as empty input.
        val accessibleEditors = instrumentation.uiAutomation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/remote_input_text")
            .orEmpty()
        check(accessibleEditors.size == 1) { "reply_editor_accessibility_unverified" }
        val accessibleEditor = accessibleEditors.single()
        val editorBounds = Rect().also { accessibleEditor.getBoundsInScreen(it) }
        check(editorBounds == editors.single().visibleBounds
            && (accessibleEditor.text.isNullOrEmpty()
                || (accessibleEditor.isShowingHintText && !accessibleEditor.hintText.isNullOrEmpty()
                    && accessibleEditor.text.toString() == accessibleEditor.hintText.toString()))) {
            "reply_editor_not_empty"
        }
        editors.single().text = replyMarker
        val sends = expanded.single().findObjects(By.res("com.android.systemui", "remote_input_send"))
        check(sends.size == 1 && sends.single().isEnabled && editors.single().text == replyMarker) { "reply_send_not_ready" }
        check(app.container.sessionManager.currentSession() == session) { "reply_session_changed_before_send" }
        capture("before-send")
        // Durable uncertainty boundary: never retry this step even if the click/receipt fails.
        record("send-intent.json", JSONObject().put("runId", run).put("stepId", step).put("backendVerified", false))
        sends.single().click()
        record("submitted.json", JSONObject().put("runId", run).put("stepId", step)
            .put("submittedBySystemUi", true).put("backendVerified", false)
            .put("replyMarker", replyMarker).put("notificationMarker", notificationMarker))
        // Keep the real host alive while its receiver runs. This alone never proves a message was sent.
        awaitCondition(60_000) { manager.activeNotifications.none { it.id == notification.id } }
        check(manager.activeNotifications.isEmpty()) { "reply_foreign_notification_present" }
        check(app.container.sessionManager.currentSession() == session) { "reply_session_rotation_requires_reconciliation" }
        capture("notification-removed")
        record("outcome.json", JSONObject().put("runId", run).put("stepId", step)
            .put("notificationRemoved", true).put("backendVerified", false))
        // Do not clear notifications/session in finally: the independent coordinator owns reconciliation.
    }
}
