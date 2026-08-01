@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.WebElementView
import com.quata.core.model.User
import com.quata.core.ui.richtext.QuataRichTextRenderer
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.presentation.OfficialCardPreview
import com.quata.feature.official.presentation.OfficialEditorMedia
import com.quata.feature.official.presentation.OfficialMediaEditExporter
import kotlinx.browser.document
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.w3c.dom.HTMLElement

/** A DOM contenteditable surface keeps the authored HTML intact instead of reducing it to text. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BrowserOfficialRichBodyEditor(html: String, onChanged: (String) -> Unit, onFullscreenChanged: (Boolean) -> Unit) {
    Column {
        Button(onClick = { browserRichTextCommand("bold") }) { Text("Bold") }
        Button(onClick = { browserRichTextFullscreen(onFullscreenChanged) }) { Text("Fullscreen") }
        WebElementView(
            factory = { (document.createElement("div") as HTMLElement).apply {
                contentEditable = "true"; setAttribute("role", "textbox"); setAttribute("aria-multiline", "true")
                style.minHeight = "180px"; style.padding = "12px"; style.border = "1px solid #777"; style.borderRadius = "8px"
            } },
            update = { element ->
                if (element.innerHTML != html) element.innerHTML = html
                element.oninput = { onChanged(element.innerHTML); null }
            },
            onRelease = { it.oninput = null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
        )
    }
}

internal fun browserOfficialRichTextCancel(): Result<Unit> = runCatching { browserExitFullscreen() }

/** Canvas re-encodes images and MediaRecorder produces an edited WebM video when the browser exposes a codec. */
internal val BrowserOfficialMediaEditor = OfficialMediaEditExporter(
    supportedTypes = setOf(OfficialMediaType.Image, OfficialMediaType.Video),
    editAndExport = { media -> browserOfficialEditMedia(media) },
    cancel = { browserOfficialCancelMediaEdit(); Result.success(Unit) },
)

private suspend fun browserOfficialEditMedia(media: OfficialEditorMedia): Result<OfficialEditorMedia> = runCatching {
    suspendCoroutine { continuation ->
        browserEditOfficialMedia(media.url, media.type.remoteValue,
            onSuccess = { url, mime, name ->
                browserDownloadEditedMedia(url, name)
                continuation.resume(OfficialEditorMedia(url, media.type, displayName = name, mimeType = mime))
            },
            onFailure = { reason -> continuation.resumeWith(Result.failure(IllegalStateException(reason))) },
        )
    }
}

/** A live browser card: the exact edited media and HTML renderer are used by the shared editor. */
internal val BrowserOfficialCardPreview = OfficialCardPreview { draft, modifier ->
    val post = draft.asBrowserPreviewPost()
    Column(modifier = modifier) {
        Text(post.title.ifBlank { "Untitled notice" })
        Text(post.summary)
        post.mediaUrl?.let { BrowserComposerMediaPreview(it, post.mediaType == OfficialMediaType.Video, Modifier.fillMaxWidth()) }
        QuataRichTextRenderer(post.contentHtml, Modifier.fillMaxWidth(), post.contentPlain)
    }
}

private fun OfficialPostDraft.asBrowserPreviewPost() = OfficialPostItem(
    id = "official-editor-preview", author = User(id = "official", email = "", displayName = "Quata", isOfficial = true),
    title = title, summary = summary, contentHtml = contentHtml, contentPlain = contentHtml.replace(Regex("<[^>]*>"), " ").trim(),
    readMoreLabel = readMoreLabel, language = language, translationGroupId = translationGroupId, type = type,
    mediaUrl = mediaUrl, mediaType = mediaType, linkUrl = linkUrl, createdAt = "Now",
)

@JsFun("""command => { const active = document.activeElement; if (active?.isContentEditable) document.execCommand(command, false, null); }""")
private external fun browserRichTextCommand(command: String)

@JsFun("""changed => { const active = document.activeElement; const target = active?.isContentEditable ? active : document.querySelector('[contenteditable=true]'); if (!target?.requestFullscreen) { changed(false); return; } target.requestFullscreen().then(() => changed(true)).catch(() => changed(false)); }""")
private external fun browserRichTextFullscreen(changed: (Boolean) -> Unit)

@JsFun("""() => { if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen(); }""")
private external fun browserExitFullscreen()

@JsFun("""() => { globalThis.__quataOfficialMediaCancel?.(); globalThis.__quataOfficialMediaCancel = null; }""")
private external fun browserOfficialCancelMediaEdit()

/** The exported blob is also offered as a normal browser download; publishing still uses the same URL. */
@JsFun("""(url, name) => { const a = document.createElement('a'); a.href = url; a.download = name; a.style.display = 'none'; document.body?.appendChild(a); a.click(); a.remove(); }""")
private external fun browserDownloadEditedMedia(url: String, name: String)

@JsFun("""(source, kind, success, failure) => {
  let cancelled = false; globalThis.__quataOfficialMediaCancel = () => { cancelled = true; failure('web_media_edit_cancelled'); };
  const done = (url, mime, name) => { if (!cancelled) { globalThis.__quataOfficialMediaCancel = null; success(url, mime, name); } };
  const fail = e => { if (!cancelled) { globalThis.__quataOfficialMediaCancel = null; failure(e?.message || String(e) || 'web_media_edit_failed'); } };
  if (kind === 'image') { const image = new Image(); image.onload = () => { try { const scale = Math.min(1, 1920 / Math.max(image.naturalWidth, image.naturalHeight)); const c = document.createElement('canvas'); c.width = Math.max(1, Math.round(image.naturalWidth * scale)); c.height = Math.max(1, Math.round(image.naturalHeight * scale)); const x = c.getContext('2d'); if (!x) throw Error('web_canvas_unavailable'); x.drawImage(image, 0, 0, c.width, c.height); c.toBlob(b => b ? done(URL.createObjectURL(b), 'image/jpeg', 'edited-image.jpg') : fail('web_image_encode_failed'), 'image/jpeg', .9); } catch(e) { fail(e); } }; image.onerror = () => fail('web_image_decode_failed'); image.src = source; return; }
  const video = document.createElement('video'); video.muted = true; video.playsInline = true; video.preload = 'metadata'; video.onloadedmetadata = () => { try { if (!video.captureStream || !globalThis.MediaRecorder) throw Error('web_video_codec_unavailable'); const scale = Math.min(1, 1280 / Math.max(video.videoWidth, video.videoHeight)); const c = document.createElement('canvas'); c.width = Math.max(1, Math.round(video.videoWidth * scale)); c.height = Math.max(1, Math.round(video.videoHeight * scale)); const stream = c.captureStream(30); const chunks = []; const recorder = new MediaRecorder(stream, MediaRecorder.isTypeSupported?.('video/webm;codecs=vp8') ? {mimeType:'video/webm;codecs=vp8'} : undefined); recorder.ondataavailable = e => { if (e.data.size) chunks.push(e.data); }; recorder.onstop = () => done(URL.createObjectURL(new Blob(chunks, {type:recorder.mimeType || 'video/webm'})), recorder.mimeType || 'video/webm', 'edited-video.webm'); const duration = Math.min(Number.isFinite(video.duration) ? video.duration : 10, 30); const draw = () => { if (cancelled) return; c.getContext('2d').drawImage(video,0,0,c.width,c.height); if (!video.ended) requestAnimationFrame(draw); }; recorder.start(); video.currentTime = 0; video.play().then(() => { draw(); setTimeout(() => { video.pause(); recorder.state !== 'inactive' && recorder.stop(); }, Math.max(250, duration * 1000)); }).catch(fail); } catch(e) { fail(e); } }; video.onerror = () => fail('web_video_decode_failed'); video.src = source; video.load();
}""")
private external fun browserEditOfficialMedia(source: String, kind: String, onSuccess: (String, String, String) -> Unit, onFailure: (String) -> Unit)
