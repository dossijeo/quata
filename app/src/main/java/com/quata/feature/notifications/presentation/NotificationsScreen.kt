package com.quata.feature.notifications.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.quata.R
import com.quata.feature.notifications.domain.NotificationsRepository

/** Android adapter for localized resources and navigation; shared content owns state and clock. */
@Composable
fun NotificationsScreen(
    padding: PaddingValues,
    repository: NotificationsRepository,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val context = LocalContext.current
    val strings = NotificationsStrings(
        title = stringResource(R.string.notifications_title),
        subtitle = stringResource(R.string.notifications_subtitle),
        backContentDescription = stringResource(R.string.common_back),
        relativeTimeCatalog = RelativeTimeCatalog(
            seconds = { context.getString(R.string.time_seconds_ago, it) }, oneMinute = context.getString(R.string.time_one_minute_ago),
            minutes = { context.getString(R.string.time_minutes_ago, it) }, hours = { context.getString(R.string.time_hours_ago, it) },
            days = { context.getString(R.string.time_days_ago, it) }, oneWeek = context.getString(R.string.time_one_week_ago),
            weeks = { context.getString(R.string.time_weeks_ago, it) }, oneMonth = context.getString(R.string.time_one_month_ago),
            months = { context.getString(R.string.time_months_ago, it) }, oneYear = context.getString(R.string.time_one_year_ago), years = { context.getString(R.string.time_years_ago, it) },
        ),
        previewCatalog = ChatPreviewCatalog(
            photo = context.getString(R.string.conversation_preview_photo), video = context.getString(R.string.conversation_preview_video),
            document = context.getString(R.string.conversation_preview_document), voiceNote = context.getString(R.string.conversation_preview_voice_note), file = context.getString(R.string.conversation_preview_file),
        ),
    )

    NotificationsHostContent(
        padding = padding,
        repository = repository,
        strings = strings,
        onBack = onBack,
        onOpenConversation = onOpenConversation,
    )

}
