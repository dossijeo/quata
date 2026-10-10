package com.quata.feature.chat.presentation.conversations

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.quata.core.platform.PlatformContact
import com.quata.feature.chat.domain.ChatInviteContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InviteChannelIntentsInstrumentedTest {
    private val contact = ChatInviteContact(
        id = "invite-test",
        displayName = "Ada Test",
        phone = "+34 699 000 101",
        phoneKeys = setOf("34699000101"),
        internationalPhone = "+34699000101"
    )

    @Test
    fun buildsWhatsAppPhoneLinkWithPrefilledText() {
        assertEquals(
            "https://wa.me/34699000101?text=Hola%20desde%20Q%C3%BCata",
            whatsAppInviteUri(contact.internationalPhone!!, "Hola desde Qüata").toString()
        )
    }

    @Test
    fun buildsTelegramPhoneLinkWithPrefilledText() {
        assertEquals(
            "tg://resolve?phone=34699000101&text=Hola%20desde%20Q%C3%BCata",
            telegramInviteUri(contact.internationalPhone!!, "Hola desde Qüata").toString()
        )
    }

    @Test
    fun offersSmsWhenHandlerExists() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val targets = availableInviteTargets(context, contact)

        assertTrue(targets.any { it.route == InviteRoute.Sms })
        assertTrue(targets.count { it.packageName == "com.whatsapp" } <= 1)
        targets.firstOrNull { it.packageName == "com.whatsapp" }?.let { target ->
            assertEquals(InviteRoute.WhatsApp, target.route)
        }
    }

    @Test
    fun doesNotOfferEmailStorageOrBrowserShareTargets() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packages = availableInviteTargets(context, contact).mapNotNull { it.packageName }

        assertTrue("Chrome must not be offered for a phone invitation", "com.android.chrome" !in packages)
        assertTrue("Drive must not be offered for a phone invitation", "com.google.android.apps.docs" !in packages)
        assertTrue("Gmail must not be offered for a phone invitation", "com.google.android.gm" !in packages)
    }

    @Test
    fun selectedPlatformContactMapsFiltersAndDispatchesTheExactSmsPayloadOnce() {
        val mapped = platformContactsForChatInvites(
            listOf(
                PlatformContact(
                    displayName = " Ada Test ",
                    phones = listOf("+34 699 000 101", "699-000-101"),
                    emails = listOf("ada@example.test"),
                ),
            ),
        )
        val selected = filterInviteContacts(mapped, "Ada Test").single()
        val message = "Hola desde Qüata"
        val context = RecordingContext(ApplicationProvider.getApplicationContext())

        launchQuataInvitation(
            context = context,
            contact = selected,
            target = InviteTarget(id = "sms", label = "SMS", route = InviteRoute.Sms),
            message = message,
            chooserTitle = "Invitar",
        )

        assertEquals(1, context.started.size)
        val intent = context.started.single()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:%2B34%20699%20000%20101", intent.dataString)
        assertEquals("+34 699 000 101", intent.data?.schemeSpecificPart)
        assertEquals(message, intent.getStringExtra("sms_body"))
        assertNotNull("phone and message must leave through an explicit component", intent.component)
        assertEquals("platform-contact:34699000101:699000101", selected.id)
        assertEquals(setOf("34699000101", "699000101"), selected.phoneKeys)
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += Intent(intent)
        }
    }
}
