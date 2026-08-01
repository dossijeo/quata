@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.presentation.OfficialEditorPlatformSlots
import com.quata.feature.official.presentation.OfficialPostEditorScreenHost
import com.quata.feature.official.presentation.OfficialPostEditorStrings
import com.quata.feature.official.presentation.OfficialEditorMedia
import com.quata.feature.official.presentation.OfficialLanguageDetector
import com.quata.feature.official.presentation.detectOfficialLanguage
import com.quata.feature.official.presentation.OfficialRichBodyEditor
import com.quata.feature.official.presentation.OfficialEditorCapability
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerSource
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** Browser mount and authorization gate for the common Official editor. */
@Composable
fun WebOfficialEditorHost(
    repository: WebOfficialRepository,
    translator: WebOfficialTranslationService,
    filePicker: FilePickerService,
    onAuthRequired: () -> Unit,
    onBack: () -> Unit,
    onPublished: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository) {
        val profile = repository.refreshCurrentUser().getOrNull()
        allowed = profile?.isOfficial == true || profile?.isAdmin == true
        if (allowed == false) onAuthRequired()
    }
    when (allowed) {
        null -> Text("Loading official editor", modifier = modifier)
        false -> Text("Official authorisation is required", modifier = modifier)
        true -> {
            val imagePicker: @Composable (onPicked: (OfficialEditorMedia) -> Unit, Modifier) -> Unit = { picked, pickerModifier ->
                Button(modifier = pickerModifier, onClick = { scope.launch {
                    filePicker.pick(FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery)).firstReferenceOrNull()
                        ?.let { picked(OfficialEditorMedia(it, OfficialMediaType.Image)) }
                } }) { Text("Image") }
            }
            val videoPicker: @Composable (onPicked: (OfficialEditorMedia) -> Unit, Modifier) -> Unit = { picked, pickerModifier ->
                Button(modifier = pickerModifier, onClick = { scope.launch {
                    filePicker.pick(FilePickerRequest(listOf("video/*"), source = FilePickerSource.Gallery)).firstReferenceOrNull()
                        ?.let { picked(OfficialEditorMedia(it, OfficialMediaType.Video)) }
                } }) { Text("Video") }
            }
            val mediaPreview: @Composable (OfficialEditorMedia, () -> Unit, (() -> Unit)?, Modifier) -> Unit = { media, _, _, previewModifier ->
                BrowserComposerMediaPreview(media.url, media.type == OfficialMediaType.Video, previewModifier)
            }
            OfficialPostEditorScreenHost(
            padding = PaddingValues(),
            language = OfficialPostLanguage.fromAppLanguage(webOfficialLanguageTag()),
            strings = OfficialPostEditorStrings.forLanguage(webOfficialLanguageTag()),
            slots = OfficialEditorPlatformSlots(
                richTextEditor = OfficialRichBodyEditor(
                    content = { html, onChanged, onFullscreenChanged -> BrowserOfficialRichBodyEditor(html, onChanged, onFullscreenChanged) },
                    cancel = ::browserOfficialRichTextCancel,
                ),
                imagePicker = imagePicker,
                videoPicker = videoPicker,
                mediaPreview = mediaPreview,
                mediaEditor = OfficialEditorCapability.Available(BrowserOfficialMediaEditor),
                cardPreview = OfficialEditorCapability.Available(BrowserOfficialCardPreview),
            ),
            onSubmit = { drafts -> repository.createPosts(drafts).map { it?.id } },
            onPublished = onPublished,
            onBack = onBack,
            newTranslationGroupId = ::webOfficialTranslationGroupId,
            translator = translator,
            languageDetector = OfficialLanguageDetector { Result.success(detectOfficialLanguage(it)) },
            modifier = modifier,
            )
        }
    }
}

/** RFC-4122 v4; fail closed on browsers without Web Crypto rather than inventing a weak group id. */
private fun webOfficialTranslationGroupId(): String = js("(() => { const c = globalThis.crypto; if (!c) throw new Error('web_crypto_unavailable'); if (c.randomUUID) return c.randomUUID(); const b = new Uint8Array(16); c.getRandomValues(b); b[6] = (b[6] & 15) | 64; b[8] = (b[8] & 63) | 128; const h = [...b].map(x => x.toString(16).padStart(2, '0')).join(''); return h.slice(0,8)+'-'+h.slice(8,12)+'-'+h.slice(12,16)+'-'+h.slice(16,20)+'-'+h.slice(20); })()")
