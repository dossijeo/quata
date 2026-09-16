package com.quata.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.R
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Passive host only. The coordinator must first prove closure of the product process. */
@RunWith(AndroidJUnit4::class)
class NotificationReplyReconciliationInstrumentedTest {
    @Test
    fun observesOwnedNotificationAbsent() = inspect(reconcile = false)

    @Test
    fun reconcilesOnlyOwnedNotification() = inspect(reconcile = true)

    private fun inspect(reconcile: Boolean) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("quataReplyReconciliation") == if (reconcile) "cleanup" else "observe")
        fun uuid(name: String): String {
            val value = arguments.getString(name) ?: error("reply_argument_missing")
            check(UUID.fromString(value).toString() == value) { "reply_argument_invalid" }
            return value
        }
        val run = uuid("quataReplyRun")
        val step = uuid("quataReplyStep")
        val observationStep = uuid("quataReplyObservationStep")
        check(observationStep != step) { "reply_observation_step_reused" }
        val actor = uuid("quataReplyActor")
        val thread = arguments.getString("quataReplyThread") ?: error("reply_thread_missing")
        check(thread.toLongOrNull()?.let { it > 0 && it.toString() == thread } == true) { "reply_thread_invalid" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.applicationContext.javaClass == Application::class.java) { "reply_passive_host_required" }
        val sessions = context.getSharedPreferences("quata_session", Context.MODE_PRIVATE)
        val sessionBefore = sessions.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
        val root = File(context.filesDir, "reply-product/$step")
        fun read(name: String): JSONObject {
            val file = File(root, name)
            check(file.isFile && file.length() in 1..4096) { "reply_owned_record_missing" }
            return JSONObject(file.readText())
        }
        val intent = read("intent.json")
        val marker = "qadata-reply-alert-$step"
        val reply = "qadata-reply-text-$step"
        check(intent.keys().asSequence().toSet() == setOf("runId", "stepId", "profileId", "threadId",
            "notificationMarker", "replyMarker", "backendVerified")) { "reply_intent_invalid" }
        check(intent.getString("runId") == run && intent.getString("stepId") == step
            && intent.getString("profileId") == actor && intent.getString("threadId") == thread
            && intent.getString("notificationMarker") == marker && intent.getString("replyMarker") == reply
            && !intent.getBoolean("backendVerified")) { "reply_intent_identity_mismatch" }
        val receipt = File(root, if (reconcile) "independent-cleanup.json" else "independent-outcome.json")
        check(!receipt.exists()) { "reply_reconciliation_already_recorded" }
        val manager = context.getSystemService(NotificationManager::class.java)
        val active = manager.activeNotifications
        val notificationRecord = File(root, "notification.json")
        val recordedId = if (notificationRecord.exists()) {
            val value = read("notification.json")
            check(value.keys().asSequence().toSet() == setOf("stepId", "id")
                && value.getString("stepId") == step) { "reply_notification_record_invalid" }
            value.getInt("id")
        } else null
        val sendIntentPresent = File(root, "send-intent.json").exists()
        if (sendIntentPresent) {
            val sendIntent = read("send-intent.json")
            check(sendIntent.keys().asSequence().toSet() == setOf("runId", "stepId", "backendVerified")
                && sendIntent.getString("runId") == run && sendIntent.getString("stepId") == step
                && !sendIntent.getBoolean("backendVerified")) { "reply_send_intent_invalid" }
        }
        // A crash between notify and recording its ID can only own the original
        // marker, never a generic success/error card produced after submission.
        val ownedId = recordedId ?: active.singleOrNull()?.takeIf {
            it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "QADATA Reply"
                && it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == marker
        }?.id
        check(active.all { entry ->
            val extras = entry.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val original = title == "QADATA Reply" && body == marker
            val failure = recordedId != null && sendIntentPresent && title == context.getString(R.string.notification_reply_failed_title)
                && body == context.getString(R.string.notification_reply_failed_body)
            val success = recordedId != null && sendIntentPresent && title == context.getString(R.string.common_chat)
                && body == context.getString(R.string.notification_reply_sent)
                && extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY)?.map { it.toString() } == listOf(reply)
            entry.id == ownedId && (original || failure || success)
        }) { "reply_foreign_notification_present" }
        val preferences = context.getSharedPreferences("quata_chat_notifications", Context.MODE_PRIVATE)
        check(preferences.all.keys.all { it == "posted_chat_notification_ids" }) { "reply_unknown_preferences" }
        val ids = preferences.getStringSet("posted_chat_notification_ids", emptySet()).orEmpty()
        check(ids.all { it == ownedId?.toString() }) { "reply_foreign_notification_preferences" }
        if (reconcile) {
            // With no ID, both notification and preferences must already be empty.
            if (ownedId != null) NotificationFactory(context).clearChatMessage("sb:$thread")
            val deadline = SystemClock.uptimeMillis() + 10_000
            while (manager.activeNotifications.isNotEmpty()) {
                check(SystemClock.uptimeMillis() < deadline) { "reply_cleanup_pending" }
                SystemClock.sleep(100)
            }
            check(preferences.getStringSet("posted_chat_notification_ids", emptySet()).orEmpty().isEmpty()) {
                "reply_cleanup_preferences_pending"
            }
        } else {
            check(sendIntentPresent) { "reply_send_intent_missing" }
            val submitted = read("submitted.json")
            check(submitted.getString("runId") == run && submitted.getString("stepId") == step
                && submitted.getBoolean("submittedBySystemUi") && !submitted.getBoolean("backendVerified")
                && submitted.getString("replyMarker") == reply && submitted.getString("notificationMarker") == marker) {
                "reply_submission_unverified"
            }
            check(recordedId != null && active.isEmpty() && ids.isEmpty()) { "reply_notification_still_present" }
        }
        check(sessions.all == sessionBefore) { "reply_reconciliation_changed_session" }
        check(receipt.createNewFile()) { "reply_receipt_collision" }
        FileOutputStream(receipt).use { output ->
            output.write(JSONObject().put("runId", run).put("stepId", observationStep).put("attemptStepId", step)
                .put("notificationRemoved", true).put("backendVerified", false)
                .put("reconciled", reconcile).toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }
}
