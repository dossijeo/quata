@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.presentation.OfficialEditorPlatformSlots
import com.quata.feature.official.presentation.OfficialPostEditorRoot
import com.quata.feature.official.presentation.OfficialPostEditorStrings

/** Browser mount and authorization gate for the common Official editor. */
@Composable
fun WebOfficialEditorHost(
    repository: WebOfficialRepository,
    onAuthRequired: () -> Unit,
    onBack: () -> Unit,
    onPublished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(repository) {
        val profile = repository.refreshCurrentUser().getOrNull()
        allowed = profile?.isOfficial == true || profile?.isAdmin == true
        if (allowed == false) onAuthRequired()
    }
    when (allowed) {
        null -> Text("Loading official editor", modifier = modifier)
        false -> Text("Official authorisation is required", modifier = modifier)
        true -> OfficialPostEditorRoot(
            padding = PaddingValues(),
            language = OfficialPostLanguage.fromAppLanguage(webOfficialLanguageTag()),
            strings = OfficialPostEditorStrings.forLanguage(webOfficialLanguageTag()),
            slots = OfficialEditorPlatformSlots(
                // This target currently has no portable WYSIWYG bridge; the real HTML field is
                // intentionally labelled as such instead of faking formatting controls.
                richTextEditor = { html, onChanged -> OutlinedTextField(value = html, onValueChange = onChanged, label = { Text("HTML") }) },
            ),
            onSubmit = { drafts -> repository.createPosts(drafts).map { onPublished() } },
            onBack = onBack,
            newTranslationGroupId = ::webOfficialTranslationGroupId,
            modifier = modifier,
        )
    }
}

/** RFC-4122 v4; fail closed on browsers without Web Crypto rather than inventing a weak group id. */
private fun webOfficialTranslationGroupId(): String = js("(() => { const c = globalThis.crypto; if (!c) throw new Error('web_crypto_unavailable'); if (c.randomUUID) return c.randomUUID(); const b = new Uint8Array(16); c.getRandomValues(b); b[6] = (b[6] & 15) | 64; b[8] = (b[8] & 63) | 128; const h = [...b].map(x => x.toString(16).padStart(2, '0')).join(''); return h.slice(0,8)+'-'+h.slice(8,12)+'-'+h.slice(12,16)+'-'+h.slice(16,20)+'-'+h.slice(20); })()")
