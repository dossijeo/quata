package com.quata.feature.official.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.quata.R
import com.quata.feature.official.domain.OfficialReadMoreOption

internal data class OfficialReadMoreUiOption(
    val option: OfficialReadMoreOption,
    @param:StringRes val labelRes: Int
)

internal val officialReadMoreUiOptions = listOf(
    OfficialReadMoreUiOption(OfficialReadMoreOption.ReadMore, R.string.official_read_more),
    OfficialReadMoreUiOption(OfficialReadMoreOption.MoreInformation, R.string.official_read_more_more_information),
    OfficialReadMoreUiOption(OfficialReadMoreOption.ContinueReading, R.string.official_read_more_continue_reading),
    OfficialReadMoreUiOption(OfficialReadMoreOption.Details, R.string.official_read_more_details)
)

@Composable
internal fun localizedOfficialReadMoreLabel(storedValue: String): String {
    val option = OfficialReadMoreOption.fromStored(storedValue) ?: OfficialReadMoreOption.ReadMore
    val labelRes = officialReadMoreUiOptions.firstOrNull { it.option == option }?.labelRes
        ?: R.string.official_read_more
    return stringResource(labelRes)
}
