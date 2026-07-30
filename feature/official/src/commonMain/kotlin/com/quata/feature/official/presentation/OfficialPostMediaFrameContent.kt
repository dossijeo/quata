package com.quata.feature.official.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.feature.official.domain.OfficialMediaType

/**
 * The media affordance is deliberately shared; hosts only supply a decoder surface.
 *
 * A video host must provide a still image (not a player surface). This is the Web integration
 * seam for its canvas frame decoder: until it supplies that image the common frame intentionally
 * renders no textual or synthetic fallback.
 */
data class OfficialInlineMediaContract(
    val showPlayButton: Boolean,
    val requiresStillThumbnail: Boolean,
)

fun officialInlineMediaContract(mediaType: OfficialMediaType?): OfficialInlineMediaContract =
    OfficialInlineMediaContract(
        showPlayButton = mediaType == OfficialMediaType.Video,
        requiresStillThumbnail = mediaType == OfficialMediaType.Video,
    )

/**
 * Portable Official feed/detail media frame. Platform hosts supply image/video rendering while
 * this component owns only the visual container and navigation gesture.
 */
@Composable
fun OfficialPostMediaFrameContent(
    onOpenMedia: () -> Unit,
    showPlayButton: Boolean = false,
    media: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpenMedia),
        contentAlignment = Alignment.Center,
    ) {
        media(Modifier.fillMaxSize())
        if (showPlayButton) OfficialPostMediaPlayButton()
    }
}

/** Kept in common code so native thumbnail decoders cannot drift from the Official affordance. */
@Composable
private fun OfficialPostMediaPlayButton() {
    Surface(
        color = Color.Black.copy(alpha = 0.38f),
        contentColor = Color.White,
        shape = RoundedCornerShape(31.dp),
        modifier = Modifier.size(62.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}
