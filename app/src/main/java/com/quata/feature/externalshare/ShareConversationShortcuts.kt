package com.quata.feature.externalshare

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.quata.ShareReceiverActivity
import com.quata.core.model.Conversation
import com.quata.core.navigation.AppDestinations
import com.quata.core.ui.components.avatarFallbackColorArgb
import com.quata.core.ui.components.avatarInitials
import com.quata.feature.chat.presentation.chatDisplayTitle

object ShareConversationShortcuts {
    suspend fun publish(
        context: Context,
        conversations: List<Conversation>,
        currentUserId: String
    ) {
        val recentConversations = conversations.asSequence()
            .filter(::isEligible)
            .sortedByDescending { it.updatedAtMillis ?: Long.MIN_VALUE }
            .take(MAX_RECENT_CONVERSATIONS)
            .toList()
        val shortcuts = buildList {
            recentConversations.forEachIndexed { rank, conversation ->
                conversation.toShortcut(context, rank, currentUserId)?.let(::add)
            }
        }
        val existingIds = publishedShortcutIds(context)
        val newIds = shortcuts.mapTo(mutableSetOf()) { it.id }
        val obsoleteIds = existingIds - newIds
        if (ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) && obsoleteIds.isNotEmpty()) {
            ShortcutManagerCompat.removeLongLivedShortcuts(context, obsoleteIds.toList())
        }
    }

    fun clear(context: Context) {
        val ids = publishedShortcutIds(context).toList()
        if (ids.isEmpty()) return
        ShortcutManagerCompat.removeDynamicShortcuts(context, ids)
        ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
    }

    fun conversationIdFromShortcut(shortcutId: String?): String? = shortcutId
        ?.takeIf { it.startsWith(SHORTCUT_PREFIX) }
        ?.removePrefix(SHORTCUT_PREFIX)
        ?.takeIf { it.isNotBlank() }

    private suspend fun Conversation.toShortcut(
        context: Context,
        rank: Int,
        currentUserId: String
    ): ShortcutInfoCompat? {
        val displayTitle = chatDisplayTitle().trim().takeIf { it.isNotBlank() } ?: return null
        val conversationKey = id
        val avatarStableId = if (!isGroup) {
            participantIds.firstOrNull { it != currentUserId } ?: conversationKey
        } else {
            conversationKey
        }
        return ShortcutInfoCompat.Builder(context, shortcutId(conversationKey))
            .setShortLabel(displayTitle.take(MAX_SHORT_LABEL_LENGTH))
            .setLongLabel(displayTitle)
            .setIcon(conversationIcon(context, displayTitle, avatarUrl, avatarStableId))
            .setIntent(
                Intent(context, ShareReceiverActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(EXTRA_CONVERSATION_ID, conversationKey)
                }
            )
            .setCategories(setOf(SHARE_CATEGORY))
            .setPerson(
                Person.Builder()
                    .setKey(conversationKey)
                    .setName(displayTitle)
                    .build()
            )
            .setLongLived(true)
            .setRank(rank)
            .addCapabilityBinding("actions.intent.SEND_MESSAGE")
            .build()
    }

    private suspend fun conversationIcon(
        context: Context,
        displayTitle: String,
        avatarUrl: String?,
        avatarStableId: String
    ): IconCompat {
        val avatar = avatarUrl?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(ICON_SIZE_PX)
                    .allowHardware(false)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
                context.imageLoader.execute(request).drawable
                    ?.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
            }.getOrNull()
        }
        return IconCompat.createWithAdaptiveBitmap(
            avatar ?: initialsBitmap(displayTitle, avatarStableId)
        )
    }

    private fun initialsBitmap(displayTitle: String, avatarStableId: String): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(avatarFallbackColorArgb(avatarStableId))
        val initials = avatarInitials(displayTitle)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = ICON_SIZE_PX * if (initials.length > 1) 0.34f else 0.46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metrics = paint.fontMetrics
        val baseline = ICON_SIZE_PX / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(initials, ICON_SIZE_PX / 2f, baseline, paint)
        return bitmap
    }

    private fun isEligible(conversation: Conversation): Boolean =
        conversation.isVisible &&
            !conversation.isEmergency &&
            conversation.id != AppDestinations.FavoriteMessagesConversationId &&
            (conversation.participantIds.isEmpty() || conversation.participantIds.distinct().size > 1) &&
            conversation.chatDisplayTitle().isNotBlank()

    private fun publishedShortcutIds(context: Context): Set<String> =
        ShortcutManagerCompat.getDynamicShortcuts(context)
            .mapNotNullTo(mutableSetOf()) { shortcut ->
                shortcut.id.takeIf { it.startsWith(SHORTCUT_PREFIX) }
            }

    fun shortcutId(conversationId: String): String = SHORTCUT_PREFIX + conversationId

    const val SHARE_CATEGORY = "com.quata.category.SHARE_CONVERSATION"
    const val EXTRA_CONVERSATION_ID = "com.quata.extra.CONVERSATION_ID"
    private const val SHORTCUT_PREFIX = "quata-share-conversation:"
    private const val MAX_RECENT_CONVERSATIONS = 3
    private const val MAX_SHORT_LABEL_LENGTH = 40
    private const val ICON_SIZE_PX = 192
}
