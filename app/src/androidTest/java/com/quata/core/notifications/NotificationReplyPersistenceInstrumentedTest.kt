package com.quata.core.notifications

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Repair only a proven no-send attempt whose prior cleanup did not persist. */
@RunWith(AndroidJUnit4::class)
class NotificationReplyPersistenceInstrumentedTest {
    @Test
    fun reconcilesExactHistoricalPreferenceResidue() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quataReplyPersistence") == "1")
        fun uuid(name: String): String = args.getString(name)!!.also {
            check(UUID.fromString(it).toString() == it)
        }
        val run = uuid("quataReplyRun")
        val step = uuid("quataReplyStep")
        val repair = uuid("quataReplyRepairStep")
        val cleanupStep = uuid("quataReplyCleanupStep")
        check(setOf(step, repair, cleanupStep).size == 3)
        val actor = uuid("quataReplyActor")
        val thread = args.getString("quataReplyThread")!!
        check(thread.toLongOrNull()?.let { it > 0 && it.toString() == thread } == true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.applicationContext.javaClass == Application::class.java)
        val sessions = context.getSharedPreferences("quata_session", Context.MODE_PRIVATE)
        check(sessions.all.isEmpty())
        val root = File(context.filesDir, "reply-product/$step")
        fun read(name: String): JSONObject {
            val file = File(root, name)
            check(file.isFile && file.length() in 1..4096)
            return JSONObject(file.readText())
        }
        val intent = read("intent.json")
        check(intent.getString("runId") == run && intent.getString("stepId") == step
            && intent.getString("profileId") == actor && intent.getString("threadId") == thread
            && intent.getString("notificationMarker") == "qadata-reply-alert-$step"
            && intent.getString("replyMarker") == "qadata-reply-text-$step"
            && !intent.getBoolean("backendVerified"))
        val prior = read("independent-cleanup.json")
        check(prior.getString("runId") == run && prior.getString("attemptStepId") == step
            && prior.getString("stepId") == cleanupStep && prior.getBoolean("reconciled")
            && prior.getBoolean("notificationRemoved") && !prior.getBoolean("backendVerified"))
        check(listOf("send-intent.json", "submitted.json", "outcome.json").none { File(root, it).exists() })
        val notification = read("notification.json")
        val id = notification.getInt("id")
        check(notification.getString("stepId") == step
            && id == 20_000 + ("sb:$thread".hashCode() and 0x0FFFFFFF))
        val manager = context.getSystemService(NotificationManager::class.java)
        check(manager.activeNotifications.isEmpty())
        val preferences = context.getSharedPreferences("quata_chat_notifications", Context.MODE_PRIVATE)
        check(preferences.all.keys == setOf("posted_chat_notification_ids"))
        check(preferences.getStringSet("posted_chat_notification_ids", emptySet()) == setOf(id.toString()))
        val receipt = File(root, "persistence-repair-$repair.json")
        check(!receipt.exists())
        NotificationFactory(context).clearChatMessage("sb:$thread")
        check(preferences.getStringSet("posted_chat_notification_ids", emptySet()).orEmpty().isEmpty())
        check(preferences.edit().putStringSet("posted_chat_notification_ids", emptySet()).commit())
        check(manager.activeNotifications.isEmpty() && sessions.all.isEmpty())
        check(receipt.createNewFile())
        FileOutputStream(receipt).use { output ->
            output.write(JSONObject().put("runId", run).put("attemptStepId", step)
                .put("repairStepId", repair).put("priorCleanupStepId", cleanupStep)
                .put("notificationId", id).put("preferencesCommitted", true)
                .put("notificationAbsent", true).put("sessionEmpty", true)
                .put("replySubmitted", false).put("backendVerified", false)
                .toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }
}
