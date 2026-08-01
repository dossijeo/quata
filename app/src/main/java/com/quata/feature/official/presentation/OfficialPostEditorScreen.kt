package com.quata.feature.official.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.core.language.QuataLanguageIdentifier
import com.quata.core.translation.QuataDeepLLanguage
import com.quata.core.translation.QuataOfficialDeepLTranslator
import com.quata.core.ui.richtext.QuataRichTextEditorBox
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialRepository
import java.util.UUID

/** Android is now only a host: the state, form flow and reset semantics live in commonMain. */
@Composable
fun OfficialPostEditorRoute(
    padding: PaddingValues,
    repository: OfficialRepository,
    onPublished: (String?) -> Unit,
    onFullscreenEditorVisibilityChange: (Boolean) -> Unit = {},
    viewModel: OfficialFeedAndroidViewModel = viewModel(factory = OfficialFeedAndroidViewModel.factory(repository)),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { onFullscreenEditorVisibilityChange(true) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onFullscreenEditorVisibilityChange(false) } }
    val currentUser = state.currentUser
    if (currentUser?.isOfficial != true && currentUser?.isAdmin != true) {
        Text("Official authorisation is required")
        return
    }
    BackHandler { onPublished(null) }
    OfficialPostEditorRoot(
        padding = padding,
        language = OfficialPostLanguage.Spanish,
        strings = OfficialPostEditorStrings.forLanguage("es"),
        slots = OfficialEditorPlatformSlots(
            richTextEditor = { html, onChanged ->
                // Existing Android WYSIWYG implementation remains the real rich-text adapter.
                QuataRichTextEditorBox(initialHtml = html, onHtmlChange = onChanged, modifier = Modifier)
            },
        ),
        translator = OfficialDraftTranslator { draft, target ->
            runCatching {
                val source = when (draft.language) {
                    OfficialPostLanguage.Spanish -> QuataDeepLLanguage.Spanish
                    OfficialPostLanguage.English -> QuataDeepLLanguage.English
                    OfficialPostLanguage.French -> QuataDeepLLanguage.French
                }
                val destination = when (target) {
                    OfficialPostLanguage.Spanish -> QuataDeepLLanguage.Spanish
                    OfficialPostLanguage.English -> QuataDeepLLanguage.English
                    OfficialPostLanguage.French -> QuataDeepLLanguage.French
                }
                draft.copy(
                    title = QuataOfficialDeepLTranslator.shared.translateText(draft.title, source, destination),
                    summary = QuataOfficialDeepLTranslator.shared.translateText(draft.summary, source, destination),
                    contentHtml = QuataOfficialDeepLTranslator.shared.translateText(draft.contentHtml, source, destination),
                    language = target,
                )
            }
        },
        newTranslationGroupId = { UUID.randomUUID().toString() },
        onSubmit = { drafts: List<OfficialPostDraft> ->
            repository.createPosts(drafts).map { created -> onPublished(created?.id) }
        },
        onBack = { onPublished(null) },
    )
}
