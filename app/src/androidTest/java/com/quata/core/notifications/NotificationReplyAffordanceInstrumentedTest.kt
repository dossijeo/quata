package com.quata.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.quata.R
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Diagnostic only: no text entry, Send, session installation or backend fixture. */
@RunWith(AndroidJUnit4::class)
class NotificationReplyAffordanceInstrumentedTest {
    @Test
    fun observesSystemReplyEditorWithoutSending() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("quataReplyAffordance") == "1")
        val step = arguments.getString("quataReplyStep") ?: error("reply_step_missing")
        check(UUID.fromString(step).toString() == step) { "reply_step_invalid" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Use the existing custody runner: no QuataApp startup observers/network.
        check(context.applicationContext.javaClass == Application::class.java) { "reply_custody_runner_required" }
        val sessions = context.getSharedPreferences("quata_session", Context.MODE_PRIVATE)
        check(sessions.all.isEmpty()) { "reply_session_not_empty" }
        val manager = context.getSystemService(NotificationManager::class.java)
        check(manager.areNotificationsEnabled()) { "reply_notification_permission_required" }
        check(manager.getNotificationChannel(NotificationChannels.CHANNEL_SOCIAL)?.importance
            ?.let { it > NotificationManager.IMPORTANCE_NONE } == true) { "reply_channel_required" }
        check(manager.activeNotifications.isEmpty()) { "reply_existing_app_notifications" }
        val preferences = context.getSharedPreferences("quata_chat_notifications", Context.MODE_PRIVATE)
        check(preferences.all.keys.all { it == "posted_chat_notification_ids" }
            && preferences.getStringSet("posted_chat_notification_ids", emptySet()).orEmpty().isEmpty()) {
            "reply_existing_notification_custody"
        }
        val beforePreferences = preferences.all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }
        val root = File(context.filesDir, "reply-affordance/$step")
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
        fun awaitCondition(condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (!condition()) {
                check(SystemClock.uptimeMillis() < deadline) { "reply_observation_timeout" }
                SystemClock.sleep(100)
            }
        }
        fun capture(name: String) {
            check(device.takeScreenshot(File(root, "$name.png"))) { "reply_screenshot_failed" }
            device.dumpWindowHierarchy(File(root, "$name.xml"))
        }
        val marker = "qadata-android-reply-$step"
        // Deliberately cannot parse as sb:<positive-id>; even an unintended
        // receiver invocation cannot address a real backend conversation.
        val conversation = "diagnostic-reply-$step"
        val factory = NotificationFactory(context)
        record("intent.json", JSONObject().put("stepId", step).put("marker", marker)
            .put("replySubmitted", false).put("backendVerified", false))
        var attempted = false
        var editorObserved = false
        try {
            attempted = true
            factory.showChatPush(conversation, "QADATA Reply", marker)
            awaitCondition { manager.activeNotifications.any { entry ->
                entry.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == marker
            } }
            val owned = manager.activeNotifications.single { entry ->
                entry.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == marker
            }
            check(owned.notification.actions?.singleOrNull()?.remoteInputs?.singleOrNull()?.resultKey
                == QuataNotificationReplyReceiver.KEY_TEXT_REPLY) { "reply_action_unverified" }
            check(device.openNotification()) { "reply_notification_shade_failed" }
            check(device.wait(Until.hasObject(By.text(marker)), 10_000)) { "reply_marker_not_visible" }
            capture("before-reply")
            // Bind the action to the row containing our unique marker. A Reply
            // elsewhere in System UI cannot satisfy this observation.
            val rows = device.findObjects(By.res("com.android.systemui", "expandableNotificationRow")
                .hasDescendant(By.text(marker)))
            check(rows.size == 1) { "reply_owned_row_not_unique" }
            val actions = rows.single().findObjects(By.text(context.getString(R.string.notification_reply)))
            check(actions.size == 1) { "reply_action_not_unique" }
            actions.single().click()
            val editor = By.pkg("com.android.systemui").clazz(EditText::class.java)
            check(device.wait(Until.hasObject(editor), 10_000)) { "reply_editor_not_visible" }
            check(device.findObjects(editor).size == 1) { "reply_editor_not_unique" }
            val expandedRows = device.findObjects(By.res("com.android.systemui", "expandableNotificationRow")
                .hasDescendant(By.text(marker)).hasDescendant(editor))
            check(expandedRows.size == 1) { "reply_editor_not_bound_to_owned_row" }
            capture("reply-editor")
            editorObserved = true
        } finally {
            if (attempted) factory.clearChatMessage(conversation)
            awaitCondition { manager.activeNotifications.isEmpty() }
            check(sessions.all.isEmpty()) { "reply_session_changed" }
            // The product stores an empty ID set after removing its first entry.
            val after = preferences.all.filterNot { (key, value) ->
                key == "posted_chat_notification_ids" && value is Set<*> && value.isEmpty()
                    && key !in beforePreferences
            }
            check(after == beforePreferences) { "reply_notification_preferences_changed" }
            device.pressBack()
            record("cleanup.json", JSONObject().put("stepId", step)
                .put("ownedNotificationAbsent", true).put("sessionEmpty", true)
                .put("replySubmitted", false).put("backendVerified", false))
        }
        record("observation.json", JSONObject().put("stepId", step)
            .put("editorObserved", editorObserved).put("replySubmitted", false)
            .put("backendVerified", false).put("functionalReplyAccepted", false))
    }

    /** Separate invocation after the observer process has demonstrably stopped. */
    @Test
    fun reconcileOnlyOwnedDiagnosticNotification() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("quataReplyAffordanceCleanup") == "1")
        val step = arguments.getString("quataReplyStep") ?: error("reply_step_missing")
        check(UUID.fromString(step).toString() == step)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.applicationContext.javaClass == Application::class.java)
        val sessions = context.getSharedPreferences("quata_session", Context.MODE_PRIVATE)
        check(sessions.all.isEmpty()) { "reply_session_not_empty" }
        val root = File(context.filesDir, "reply-affordance/$step")
        val intentFile = File(root, "intent.json")
        check(intentFile.isFile && intentFile.length() in 1..2048)
        val intent = JSONObject(intentFile.readText())
        val marker = "qadata-android-reply-$step"
        check(intent.keys().asSequence().toSet() == setOf("stepId", "marker", "replySubmitted", "backendVerified"))
        check(intent.getString("stepId") == step && intent.getString("marker") == marker
            && !intent.getBoolean("replySubmitted") && !intent.getBoolean("backendVerified"))
        val receipt = File(root, "independent-cleanup.json")
        check(!receipt.exists()) { "reply_cleanup_already_recorded" }
        val manager = context.getSystemService(NotificationManager::class.java)
        check(manager.activeNotifications.all { entry ->
            entry.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == marker
                && entry.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "QADATA Reply"
        }) { "reply_foreign_notification_present" }
        NotificationFactory(context).clearChatMessage("diagnostic-reply-$step")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (manager.activeNotifications.isNotEmpty()) {
            check(SystemClock.uptimeMillis() < deadline) { "reply_cleanup_pending" }
            SystemClock.sleep(100)
        }
        val preferences = context.getSharedPreferences("quata_chat_notifications", Context.MODE_PRIVATE)
        check(preferences.all.keys.all { it == "posted_chat_notification_ids" }
            && preferences.getStringSet("posted_chat_notification_ids", emptySet()).orEmpty().isEmpty())
        check(sessions.all.isEmpty())
        check(receipt.createNewFile())
        FileOutputStream(receipt).use { output ->
            output.write(JSONObject().put("stepId", step).put("ownedNotificationAbsent", true)
                .put("sessionEmpty", true).put("replySubmitted", false)
                .put("backendVerified", false).toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }
}
