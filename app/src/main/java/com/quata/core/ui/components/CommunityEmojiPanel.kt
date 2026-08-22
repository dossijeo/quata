package com.quata.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quata.R

/** Android resource adapter only. Rendering, catalog, editing and dismissal live in commonMain. */
@Composable
fun CommunityEmojiPanel(
    onEmojiClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialSectionKey: String = "frequent",
    gridMaxHeight: Dp = 220.dp,
) {
    CommunityEmojiPanelContent(
        sections = communityEmojiSections(
            CommunityEmojiLabels(
                recent = stringResource(R.string.emoji_recent),
                frequent = stringResource(R.string.emoji_frequent),
                gestures = stringResource(R.string.emoji_gestures),
                people = stringResource(R.string.emoji_people),
                animalsNature = stringResource(R.string.emoji_animals_nature),
                foodDrink = stringResource(R.string.emoji_food_drink),
                objectsSymbols = stringResource(R.string.emoji_objects_symbols),
                flags = stringResource(R.string.emoji_flags),
                empty = stringResource(R.string.emoji_empty),
            ),
        ),
        onEmojiClick = onEmojiClick,
        modifier = modifier,
        initialSectionKey = initialSectionKey,
        gridMaxHeight = gridMaxHeight,
    )
}
