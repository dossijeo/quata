package com.quata.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest

data class QuataNetworkImageState(
    val isNetworkAvailable: Boolean = true,
    val reconnectToken: Long = 0L
)

val LocalQuataNetworkImageState = compositionLocalOf { QuataNetworkImageState() }

@Composable
fun rememberCachedRemoteImageRequest(url: String?): Any? {
    val cleanUrl = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val context = LocalContext.current
    val networkState = LocalQuataNetworkImageState.current
    return remember(context, cleanUrl, networkState.isNetworkAvailable, networkState.reconnectToken) {
        ImageRequest.Builder(context)
            .data(cleanUrl)
            .memoryCacheKey(cleanUrl)
            .diskCacheKey(cleanUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
