package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.feature.official.presentation.OfficialBrowserHostContent

/** Web adapter retains only repository composition and browser navigation ownership. */
@Composable
fun WebOfficialHost(repository: WebOfficialRepository, officialPostId: String?, navigationMessage: String, modifier: Modifier = Modifier) =
    OfficialBrowserHostContent(repository, officialPostId, navigationMessage, modifier)
