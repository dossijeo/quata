package com.quata.feature.profile.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.quata.feature.profile.domain.EmergencyContactCandidate

/** Localized copy and formatting owned by the platform launcher/resources. */
data class EmergencyContactsEditorStrings(
    val header: EmergencyContactsHeaderStrings,
    val selectedCount: @Composable (Int) -> String,
    val networkUsers: String,
    val searchPlaceholder: String,
    val messageTitle: String,
    val messageHint: String,
    val savePortrait: String,
    val saveLandscape: String,
)

/**
 * Full portable SOS-contact editor.
 *
 * Contact discovery, permission prompts, back dispatch and the IME visibility signal remain
 * with the host. The host also supplies avatar-backed rows and the text input, while this
 * content owns the responsive hierarchy, tabs, filtering, selection presentation and focus
 * positioning.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun EmergencyContactsEditorContent(
    layoutPadding: PaddingValues,
    isLandscapeLayout: Boolean,
    isImeVisible: Boolean,
    candidates: List<EmergencyContactCandidate>,
    selectedIds: List<String>,
    message: String,
    isSaving: Boolean,
    strings: EmergencyContactsEditorStrings,
    onMessageChange: (String) -> Unit,
    onToggleContact: (EmergencyContactCandidate) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    userRow: @Composable (EmergencyContactCandidate, Boolean, () -> Unit) -> Unit,
    messageInput: @Composable (Modifier, String, (String) -> Unit, Int, Int?) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf(EmergencyContactsTab.Contacts) }
    val messageScrollState = rememberScrollState()
    val contactsListState = rememberLazyListState()
    val messageBringIntoViewRequester = remember { BringIntoViewRequester() }
    var isMessageFocused by remember { mutableStateOf(false) }
    val selectedIdSet = selectedIds.toSet()
    val visibleUsers = filterEmergencyContactCandidates(candidates, selectedIdSet, query)

    LaunchedEffect(isMessageFocused, isImeVisible) {
        if (isMessageFocused && isImeVisible) messageBringIntoViewRequester.bringIntoView()
    }
    LaunchedEffect(isImeVisible, selectedTab) {
        if (!isImeVisible && selectedTab == EmergencyContactsTab.Contacts) {
            contactsListState.scrollToItem(0)
        }
    }

    EmergencyContactsDialogFrameContent(
        layoutPadding = layoutPadding,
        isLandscapeLayout = isLandscapeLayout,
    ) {
        if (isLandscapeLayout) {
            EmergencyContactsLandscapeEditorLayoutContent(
                topBar = {
                    EmergencyContactsLandscapeTopBarContent(
                        backLabel = strings.header.back,
                        sosLabel = strings.header.sos,
                        title = strings.header.title,
                        saveLabel = strings.saveLandscape,
                        isSaving = isSaving,
                        onDismiss = onDismiss,
                        onSave = onSave,
                    )
                },
                contacts = { modifier ->
                    EmergencyContactsLandscapeContactsSectionContent(
                        title = strings.header.contactsTab,
                        selectedCountLabel = strings.selectedCount(selectedIds.size),
                        modifier = modifier,
                        searchInput = {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(strings.searchPlaceholder) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                            )
                        },
                        users = { usersModifier ->
                            LazyColumn(
                                modifier = usersModifier,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(visibleUsers, key = EmergencyContactCandidate::id) { user ->
                                    userRow(user, user.id in selectedIdSet) { onToggleContact(user) }
                                }
                            }
                        },
                    )
                },
                message = { modifier ->
                    Column(modifier.verticalScroll(messageScrollState)) {
                        EmergencyContactsLandscapeMessageIntroContent(
                            tabLabel = strings.header.messageTab,
                            description = strings.header.description,
                        )
                        EmergencyContactsLandscapeMessagePanelContent(
                            title = strings.messageTitle,
                            hint = strings.messageHint,
                            input = {
                                messageInput(
                                    Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(messageBringIntoViewRequester)
                                        .onFocusChanged { isMessageFocused = it.isFocused },
                                    message,
                                    onMessageChange,
                                    4,
                                    5,
                                )
                            },
                        )
                    }
                },
            )
        } else {
            EmergencyContactsPortraitEditorLayoutContent(
                body = { modifier ->
                    when (selectedTab) {
                        EmergencyContactsTab.Contacts -> EmergencyContactsSelectionContent(
                            candidates = candidates,
                            selectedIds = selectedIdSet,
                            query = query,
                            onQueryChange = { query = it },
                            listState = contactsListState,
                            showHeader = !isImeVisible,
                            headerStrings = strings.header,
                            searchPlaceholder = strings.searchPlaceholder,
                            selectedCountLabel = strings.selectedCount(selectedIds.size),
                            networkUsersLabel = strings.networkUsers,
                            onTabSelected = { selectedTab = it },
                            onDismiss = onDismiss,
                            userRow = { user, selected ->
                                userRow(user, selected) { onToggleContact(user) }
                            },
                            modifier = modifier,
                        )
                        EmergencyContactsTab.Message -> EmergencyContactsMessageContent(
                            scrollState = messageScrollState,
                            showHeader = !isImeVisible,
                            headerStrings = strings.header,
                            title = strings.messageTitle,
                            hint = strings.messageHint,
                            onTabSelected = { selectedTab = it },
                            onDismiss = onDismiss,
                            messageInput = {
                                messageInput(
                                    Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(messageBringIntoViewRequester)
                                        .onFocusChanged { isMessageFocused = it.isFocused },
                                    message,
                                    onMessageChange,
                                    8,
                                    null,
                                )
                            },
                            modifier = modifier,
                        )
                    }
                },
                saveAction = {
                    if (!isImeVisible) {
                        Spacer(Modifier.height(18.dp))
                        EmergencyContactsPortraitSaveButtonContent(
                            label = strings.savePortrait,
                            isSaving = isSaving,
                            onSave = onSave,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                },
            )
        }
    }
}
