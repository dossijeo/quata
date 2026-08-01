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

private fun webOfficialTranslationGroupId(): String = js("globalThis.crypto?.randomUUID?.() || ('official-' + Date.now() + '-' + Math.random().toString(16).slice(2))")
