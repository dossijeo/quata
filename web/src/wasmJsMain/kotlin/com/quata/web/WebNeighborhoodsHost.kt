package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.presentation.NeighborhoodsScreenHost
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
    padding: PaddingValues = PaddingValues(),
) = NeighborhoodsScreenHost(
    repository = repository,
    currentUserId = currentUserId,
    strings = strings.screen,
    avatar = slots.avatar,
    onOpenConversation = onOpenConversation,
    onOpenUserProfile = onOpenUserProfile,
    onAuthRequired = onAuthRequired,
    padding = padding,
)
