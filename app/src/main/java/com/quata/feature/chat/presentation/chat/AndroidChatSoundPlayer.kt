package com.quata.feature.chat.presentation.chat

import android.content.Context
import android.media.MediaPlayer
import com.quata.R

/** Plays the same packaged cues used by the original Android conversation detail. */
internal fun Context.playChatSound(event: ChatSoundEvent) {
    val resource = when (event) {
        ChatSoundEvent.MessageSent -> R.raw.sent
        ChatSoundEvent.MessageReceived -> R.raw.notification
    }
    val player = runCatching { MediaPlayer.create(this, resource) }.getOrNull() ?: return
    player.setOnCompletionListener { completedPlayer -> completedPlayer.release() }
    player.setOnErrorListener { errorPlayer, _, _ -> errorPlayer.release(); true }
    runCatching { player.start() }.onFailure { player.release() }
}
