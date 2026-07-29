package com.quata.web

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.WebElementView
import com.quata.core.model.Post
import com.quata.feature.feed.presentation.FeedMediaUnavailablePlaceholderContent
import kotlinx.browser.document
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLVideoElement

/** Browser media slot for feed URLs with a representation supported by native HTML elements. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowserFeedMediaContent(post: Post, unavailableMessage: String) {
    val videoUrl = post.videoUrl?.takeIf(::isBrowserVideoUrl)
    val imageUrl = post.imageUrl?.takeIf(::isBrowserImageUrl)
    when {
        videoUrl != null -> WebElementView(
            factory = {
                (document.createElement("video") as HTMLVideoElement).apply {
                    controls = true
                    preload = "metadata"
                    muted = true
                    setAttribute("playsinline", "")
                    style.width = "100%"
                    style.height = "100%"
                    style.objectFit = "cover"
                }
            },
            update = { element -> element.src = videoUrl },
            modifier = Modifier.fillMaxWidth().height(360.dp),
        )
        imageUrl != null -> WebElementView(
            factory = {
                (document.createElement("img") as HTMLImageElement).apply {
                    alt = post.text.take(120)
                    setAttribute("loading", "lazy")
                    style.width = "100%"
                    style.height = "100%"
                    style.objectFit = "cover"
                }
            },
            update = { element -> element.src = imageUrl },
            modifier = Modifier.fillMaxWidth().height(360.dp),
        )
        post.imageUrl != null || post.videoUrl != null -> FeedMediaUnavailablePlaceholderContent(
            message = unavailableMessage,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun isBrowserImageUrl(url: String): Boolean =
    url.isHttpMediaUrl() && url.substringBefore('?').lowercase().endsWithAny(".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif")

private fun isBrowserVideoUrl(url: String): Boolean =
    url.isHttpMediaUrl() && url.substringBefore('?').lowercase().endsWithAny(".mp4", ".webm", ".ogv", ".ogg")

private fun String.isHttpMediaUrl(): Boolean = startsWith("https://") || startsWith("http://")

private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any(::endsWith)
