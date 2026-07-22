package com.quata.feature.chat.presentation.conversations

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.feature.chat.domain.ChatInviteContact
import org.junit.Assert.assertEquals
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
}
