package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.quata.core.designsystem.theme.quataTheme
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.presentation.NeighborhoodsScreenHost
import com.quata.feature.neighborhoods.presentation.NeighborhoodsViewModel
import com.quata.feature.neighborhoods.presentation.CommunityProfileRoot
import com.quata.feature.neighborhoods.presentation.NeighborhoodsScreenStrings
import com.quata.feature.neighborhoods.presentation.defaultNeighborhoodsScreenStrings

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private val browserNeighborhoodsLanguage: String = js("globalThis.navigator?.language || 'en'")

fun browserNeighborhoodsStrings(): WebNeighborhoodsStrings =
    WebNeighborhoodsStrings(defaultNeighborhoodsScreenStrings(browserNeighborhoodsLanguage))

data class WebNeighborhoodsStrings(val screen: NeighborhoodsScreenStrings)

class WebNeighborhoodsSlots(
    val avatar: @Composable (NeighborhoodUser, Boolean, () -> Unit) -> Unit,
)

/** Thin browser wrapper: state, gates and directory/member navigation are common. */
@Composable
fun WebNeighborhoodsHost(
    repository: NeighborhoodRepository,
    currentUserId: String?,
    strings: WebNeighborhoodsStrings,
    slots: WebNeighborhoodsSlots,
    onOpenConversation: (String) -> Unit,
    onAuthRequired: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onDismissProfile: () -> Unit = {},
    profileId: String? = null,
    padding: PaddingValues = PaddingValues(),
) {
    if (profileId != null) {
        WebCommunityProfileRoute(repository, profileId, onDismissProfile)
    } else NeighborhoodsScreenHost(
    repository = repository,
    currentUserId = currentUserId,
    strings = strings.screen,
    avatar = slots.avatar,
    onOpenConversation = onOpenConversation,
    onOpenUserProfile = onOpenUserProfile,
    onAuthRequired = onAuthRequired,
    padding = padding,
)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WebCommunityProfileRoute(
    repository: NeighborhoodRepository,
    profileId: String,
    onDismiss: () -> Unit,
) {
    val model = remember(repository) { NeighborhoodsViewModel(repository) }
    val state by model.uiState.collectAsState()
    DisposableEffect(model) { onDispose(model::close) }
    LaunchedEffect(profileId) { model.openUserProfile(profileId) }
    val profile = state.selectedProfile ?: return
    val theme = quataTheme()
    CommunityProfileRoot(
        profileId = profile.user.id,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.colors.background,
        contentColor = theme.colors.textPrimary,
        onDismiss = { model.closeUserProfile(); onDismiss() },
    ) { navigation, dispatch ->
        Column {
            Text(profile.user.displayName)
            Text("${profile.user.postsCount} posts · ${profile.user.followersCount} followers")
            val peopleList = navigation.peopleList
            if (peopleList != null) {
                val people = if (peopleList.name == "Followers") profile.followers else profile.following
                people.forEach { Text(it.displayName) }
                Text("Back", modifier = androidx.compose.ui.Modifier) // root state is preserved for host actions
            } else {
                Text("Posts", modifier = androidx.compose.ui.Modifier)
                profile.posts.forEach { Text(it.text) }
            }
        }
    }
}
