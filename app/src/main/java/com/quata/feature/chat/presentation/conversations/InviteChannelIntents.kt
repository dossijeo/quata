package com.quata.feature.chat.presentation.conversations

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import com.quata.ShareReceiverActivity
import com.quata.feature.chat.domain.ChatInviteContact
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class InviteRoute {
    Sms,
    WhatsApp,
    Telegram,
    AppShare
}

data class InviteTarget(
    val id: String,
    val label: String,
    val route: InviteRoute,
    val component: ComponentName? = null,
    val packageName: String? = component?.packageName,
    val icon: Drawable? = null
)

fun availableInviteTargets(context: Context, contact: ChatInviteContact): List<InviteTarget> {
    val packageManager = context.packageManager
    val targets = mutableListOf<InviteTarget>()

    targets += smsTargets(context, contact)

    val shareIntent = baseShareIntent("")
    val resolvedActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            shareIntent,
            android.content.pm.PackageManager.ResolveInfoFlags.of(android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(shareIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    }

    resolvedActivities
        .asSequence()
        .filter { it.activityInfo.packageName != context.packageName }
        .filter { it.activityInfo.packageName in SUPPORTED_MESSAGING_PACKAGES }
        .distinctBy { it.activityInfo.packageName }
        .map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val route = when {
                contact.internationalPhone == null -> InviteRoute.AppShare
                packageName in WHATSAPP_PACKAGES -> InviteRoute.WhatsApp
                packageName in TELEGRAM_PACKAGES -> InviteRoute.Telegram
                else -> InviteRoute.AppShare
            }
            InviteTarget(
                id = packageName,
                label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty().ifBlank { packageName },
                route = route,
                component = ComponentName(packageName, resolveInfo.activityInfo.name),
                packageName = packageName,
                icon = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
            )
        }
        .sortedWith(compareBy<InviteTarget>({ KNOWN_APP_ORDER[it.packageName] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
        .forEach(targets::add)

    return targets
}

fun launchQuataInvitation(
    context: Context,
    contact: ChatInviteContact,
    target: InviteTarget,
    message: String,
    chooserTitle: String
) {
    val intent = when (target.route) {
        InviteRoute.Sms -> smsInviteIntent(contact)
            .apply { target.component?.let(::setComponent) }
            .putExtra("sms_body", message)
        InviteRoute.WhatsApp -> directAppIntent(
            whatsAppInviteUri(contact.requireInternationalPhone(), message),
            requireNotNull(target.packageName)
        )
        InviteRoute.Telegram -> directAppIntent(
            telegramInviteUri(contact.requireInternationalPhone(), message),
            requireNotNull(target.packageName)
        )
        InviteRoute.AppShare -> shareToComponentIntent(message, requireNotNull(target.component))
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val fallback = target.component?.let { shareToComponentIntent(message, it) }
            ?: genericInviteChooser(context, message, chooserTitle)
        context.startActivity(fallback)
    }
}

fun whatsAppInviteUri(internationalPhone: String, message: String): Uri =
    Uri.parse("https://wa.me/${internationalPhone.filter(Char::isDigit)}?text=${encodeQueryValue(message)}")

fun telegramInviteUri(internationalPhone: String, message: String): Uri =
    Uri.parse("tg://resolve?phone=${internationalPhone.filter(Char::isDigit)}&text=${encodeQueryValue(message)}")

private fun smsInviteIntent(contact: ChatInviteContact): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", contact.phone, null))

private fun smsTargets(context: Context, contact: ChatInviteContact): List<InviteTarget> {
    val packageManager = context.packageManager
    val resolvedActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            smsInviteIntent(contact),
            android.content.pm.PackageManager.ResolveInfoFlags.of(android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(smsInviteIntent(contact), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    }
    val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
    return resolvedActivities
        .filter { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            packageName == defaultPackage || packageName !in SUPPORTED_MESSAGING_PACKAGES
        }
        .distinctBy { it.activityInfo.packageName }
        .sortedByDescending { it.activityInfo.packageName == defaultPackage }
        .map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            InviteTarget(
                id = "sms:$packageName",
                label = if (packageName == defaultPackage) "" else resolveInfo.loadLabel(packageManager).toString(),
                route = InviteRoute.Sms,
                component = ComponentName(packageName, resolveInfo.activityInfo.name),
                packageName = packageName,
                icon = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
            )
        }
}

private fun directAppIntent(uri: Uri, packageName: String): Intent =
    Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)

private fun baseShareIntent(message: String): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, message)
}

private fun shareToComponentIntent(message: String, component: ComponentName): Intent =
    baseShareIntent(message).setComponent(component)

private fun genericInviteChooser(context: Context, message: String, chooserTitle: String): Intent {
    val shareIntent = baseShareIntent(message)
    return Intent.createChooser(shareIntent, chooserTitle).apply {
        putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(context, ShareReceiverActivity::class.java))
        )
    }
}

private fun ChatInviteContact.requireInternationalPhone(): String =
    requireNotNull(internationalPhone) { "An international phone number is required for this invite channel" }

private fun encodeQueryValue(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
private val TELEGRAM_PACKAGES = setOf(
    "org.telegram.messenger",
    "org.telegram.messenger.web",
    "org.thunderdog.challegram"
)
private val KNOWN_APP_ORDER = mapOf(
    "com.whatsapp" to 0,
    "com.whatsapp.w4b" to 1,
    "org.telegram.messenger" to 2,
    "org.telegram.messenger.web" to 3,
    "org.thunderdog.challegram" to 4,
    "org.thoughtcrime.securesms" to 5,
    "com.facebook.orca" to 6,
    "com.viber.voip" to 7,
    "jp.naver.line.android" to 8,
    "com.skype.raider" to 9,
    "com.tencent.mm" to 10,
    "com.kakao.talk" to 11,
    "com.imo.android.imoim" to 12,
    "ch.threema.app" to 13,
    "com.wire" to 14,
    "im.vector.app" to 15,
    "com.google.android.apps.dynamite" to 16,
    "com.microsoft.teams" to 17
)
private val SUPPORTED_MESSAGING_PACKAGES = KNOWN_APP_ORDER.keys
