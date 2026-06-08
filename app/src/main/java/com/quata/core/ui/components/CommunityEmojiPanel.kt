package com.quata.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.R
import com.quata.core.designsystem.theme.quataTheme
import java.util.Locale

@Composable
fun CommunityEmojiPanel(
    onEmojiClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialSectionKey: String = "frequent"
) {
    val template = quataTheme()
    val sections = CommunityEmojiCatalog.sections
    var selectedSectionKey by rememberSaveable { mutableStateOf(initialSectionKey) }
    val selectedSection = sections.firstOrNull { it.key == selectedSectionKey } ?: sections.first()

    Surface(
        color = template.colors.surfaceRaised,
        contentColor = template.colors.textPrimary,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.accent.copy(alpha = 0.62f), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(14.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sections) { section ->
                    Surface(
                        color = if (section.key == selectedSectionKey) {
                            template.colors.accent
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.clickable { selectedSectionKey = section.key }
                    ) {
                        Text(
                            text = stringResource(section.labelRes),
                            color = if (section.key == selectedSectionKey) {
                                template.colors.accentContent
                            } else {
                                template.colors.textSecondary
                            },
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedSection.emojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(template.colors.surfaceAlt)
                            .border(1.dp, template.colors.divider, RoundedCornerShape(14.dp))
                            .clickable { onEmojiClick(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun rememberCommunityEmojiPanelDismissState(
    onDismissRequest: () -> Unit
): CommunityEmojiPanelDismissState {
    val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)
    return remember { CommunityEmojiPanelDismissState { latestOnDismissRequest() } }
}

fun Modifier.dismissCommunityEmojiPanelOnOutsideTap(
    isVisible: Boolean,
    state: CommunityEmojiPanelDismissState
): Modifier {
    if (!isVisible) return this
    return this
        .onGloballyPositioned { state.rootCoordinates = it }
        .pointerInput(isVisible, state.panelBounds, state.triggerBounds) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                state.dismissIfOutside(down.position)
            }
        }
}

fun Modifier.trackCommunityEmojiPanelBounds(
    state: CommunityEmojiPanelDismissState
): Modifier = onGloballyPositioned { state.panelBounds = it.boundsInWindow() }

fun Modifier.trackCommunityEmojiTriggerBounds(
    state: CommunityEmojiPanelDismissState
): Modifier = onGloballyPositioned { state.triggerBounds = it.boundsInWindow() }

@Stable
class CommunityEmojiPanelDismissState internal constructor(
    private val onDismissRequest: () -> Unit
) {
    internal var rootCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var panelBounds: Rect? by mutableStateOf(null)
    internal var triggerBounds: Rect? by mutableStateOf(null)

    internal fun dismissIfOutside(positionInRoot: Offset) {
        val windowPosition = rootCoordinates?.localToWindow(positionInRoot) ?: positionInRoot
        val isInsidePanel = panelBounds?.contains(windowPosition) == true
        val isInsideTrigger = triggerBounds?.contains(windowPosition) == true
        if (!isInsidePanel && !isInsideTrigger) {
            onDismissRequest()
        }
    }
}

private object CommunityEmojiCatalog {
    const val FrequentSectionKey = "frequent"

    private val frequentEmojis by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
        "😀", "😁", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎", "🤩", "😇", "🙂", "😉", "😌", "🤗", "😴",
        "🤔", "😅", "😢", "😭", "😤", "😡", "🤯", "🥳", "🤝", "👏", "🙌", "👍", "👎", "🙏", "💪", "🔥",
        "✨", "⭐", "💯", "❤️", "💙", "💚", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹"
        )
    }

    val sections: List<CommunityEmojiSection> by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
        CommunityEmojiSection(R.string.emoji_recent, "recent", frequentEmojis.take(24)),
        CommunityEmojiSection(R.string.emoji_frequent, FrequentSectionKey, frequentEmojis),
        CommunityEmojiSection(
            R.string.emoji_gestures,
            "gestures",
            listOf(
                "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🫶", "🤟", "🤘", "🤙", "👈",
                "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲",
                "🙏", "🤝", "💪", "🦾", "🫵"
            )
        ),
        CommunityEmojiSection(
            R.string.emoji_people,
            "people",
            listOf(
                "👶", "🧒", "👦", "👧", "🧑", "👨", "👩", "👱", "👴", "👵", "🧔", "👮", "🕵️", "💂", "👷",
                "🤴", "👸", "🧕", "👨‍⚕️", "👩‍⚕️", "👨‍🍳", "👩‍🍳", "👨‍🎓", "👩‍🎓", "👨‍🏫", "👩‍🏫",
                "👨‍💻", "👩‍💻", "👨‍🎤", "👩‍🎤", "🧘", "🏃", "🚶", "🧍"
            )
        ),
        CommunityEmojiSection(
            R.string.emoji_animals_nature,
            "animals_nature",
            listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸",
                "🐵", "🙈", "🙉", "🙊", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴",
                "🦄", "🐝", "🪲", "🦋", "🐢", "🐍", "🦎", "🦂", "🦀", "🐙", "🐠", "🐬", "🦭", "🌵", "🌴",
                "🌲", "🌳", "🌸", "🌼", "🌻", "🌞", "🌙", "⭐", "⚡", "☔", "🌈", "🔥", "❄️"
            )
        ),
        CommunityEmojiSection(
            R.string.emoji_food_drink,
            "food_drink",
            listOf(
                "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍒", "🥭", "🍍", "🥥", "🥑",
                "🍅", "🍆", "🥔", "🥕", "🌽", "🌶️", "🥒", "🥬", "🥦", "🧄", "🧅", "🍄", "🥜", "🍞", "🥐",
                "🥖", "🧀", "🍳", "🥓", "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🥗", "🍝", "🍜", "🍣",
                "🍤", "🍩", "🍪", "🎂", "🍫", "☕", "🍵", "🧃", "🥤", "🍺", "🍷", "🍾"
            )
        ),
        CommunityEmojiSection(
            R.string.emoji_objects_symbols,
            "objects_symbols",
            listOf(
                "📱", "💻", "⌚", "📷", "🎥", "📺", "🎮", "🎧", "🧠", "🫀", "💡", "🔦", "📚", "✏️", "📌",
                "📎", "✂️", "🔒", "🔑", "🪙", "💸", "💰", "🧾", "💎", "⚙️", "🧲", "🧪", "🧬", "🚬", "⚰️",
                "🛒", "🧳", "🎁", "🎈", "🎉", "🏆", "⚽", "🏀", "🎯", "🚗", "✈️", "🚀", "🛸", "⏰", "📍",
                "✅", "❌", "⚠️", "❓", "💬", "🗯️"
            )
        ),
        CommunityEmojiSection(
            R.string.emoji_flags,
            "flags",
            listOf(
                "ES", "US", "GB", "FR", "DE", "IT", "PT", "BR", "AR", "CO", "MX", "EC", "PE", "CL", "UY", "PY",
                "BO", "VE", "DO", "CU", "MA", "DZ", "EG", "NG", "ZA", "CM", "GA", "GQ", "JP", "KR", "CN", "IN",
                "AU", "CA"
            ).map(::flagEmoji)
        )
    )
    }

    private fun flagEmoji(countryCode: String): String =
        countryCode
            .uppercase(Locale.US)
            .map { Character.toChars(127397 + it.code).concatToString() }
            .joinToString("")
}

private data class CommunityEmojiSection(
    @param:StringRes val labelRes: Int,
    val key: String,
    val emojis: List<String>
)
