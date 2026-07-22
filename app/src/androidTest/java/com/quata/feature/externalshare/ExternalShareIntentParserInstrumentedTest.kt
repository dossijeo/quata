package com.quata.feature.externalshare

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.content.pm.ShortcutManagerCompat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExternalShareIntentParserInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun acceptsTenSupportedFilesAndPreservesTheirOrder() = runBlocking {
        val uris = createTextFiles(ExternalShareIntentParser.MAX_SHARED_FILES)
        val result = ExternalShareIntentParser.parse(context, multipleShareIntent(uris))

        assertTrue(result is ExternalShareParseResult.Accepted)
        val payload = (result as ExternalShareParseResult.Accepted).payload
        assertEquals(10, payload.attachments.size)
        assertEquals("shared-0.txt", payload.attachments.first().name)
        assertEquals("shared-9.txt", payload.attachments.last().name)
    }

    @Test
    fun rejectsMoreThanTenFiles() = runBlocking {
        val uris = createTextFiles(ExternalShareIntentParser.MAX_SHARED_FILES + 1)
        val result = ExternalShareIntentParser.parse(context, multipleShareIntent(uris))

        assertTrue(result is ExternalShareParseResult.TooManyFiles)
    }

    @Test
    fun acceptsMixedSupportedTypesDeclaredAsWildcard() = runBlocking {
        val image = File(context.cacheDir, "shared-image.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val document = File(context.cacheDir, "shared-document.pdf").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val intent = multipleShareIntent(arrayListOf(Uri.fromFile(image), Uri.fromFile(document))).apply {
            type = "*/*"
        }

        val result = ExternalShareIntentParser.parse(context, intent)

        assertTrue(result is ExternalShareParseResult.Accepted)
        assertEquals(2, (result as ExternalShareParseResult.Accepted).payload.attachments.size)
    }

    @Test
    fun extractsDirectShareConversationFromShortcutId() = runBlocking {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "direct share")
            putExtra(
                ShortcutManagerCompat.EXTRA_SHORTCUT_ID,
                ShareConversationShortcuts.shortcutId("thread:42")
            )
        }

        val result = ExternalShareIntentParser.parse(context, intent)

        assertTrue(result is ExternalShareParseResult.Accepted)
        assertEquals(
            "thread:42",
            (result as ExternalShareParseResult.Accepted).payload.directConversationId
        )
    }

    private fun createTextFiles(count: Int): ArrayList<Uri> = ArrayList(
        (0 until count).map { index ->
            File(context.cacheDir, "shared-$index.txt").apply { writeText("file $index") }
                .let(Uri::fromFile)
        }
    )

    private fun multipleShareIntent(uris: ArrayList<Uri>) = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "text/plain"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
}
