import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import { join } from "node:path";
import test from "node:test";

const root = new URL("..", import.meta.url);

async function source(path) {
  return readFile(new URL(path, root), "utf8");
}

async function kotlinAndSwiftFiles(dir) {
  const absolute = new URL(dir, root);
  const entries = await readdir(absolute, { withFileTypes: true });
  const files = await Promise.all(entries.map(async (entry) => {
    const child = join(dir, entry.name).replaceAll("\\", "/");
    if (entry.isDirectory()) return kotlinAndSwiftFiles(child);
    return /\.(kt|swift)$/.test(entry.name) ? [child] : [];
  }));
  return files.flat();
}

const [
  packageJson,
  inventory,
  verticalPlan,
  appNavGraph,
  androidHost,
  androidTranslatorClient,
  webHost,
  iosHost,
  chatScreenHost,
  conversationDetail,
  deepLinkFocus,
  selectedActions,
  selectedActionBar,
  titleBar,
  favoriteHeader,
  chatTranslatorOverlay,
  groupManagement,
  sosLocation,
  attachmentQuickPanel,
  pendingAttachmentOverlay,
  documentAttachment,
  audioAttachmentPlayer,
  chatBrowserHostContent,
  communityProfileHost,
  communityProfileHeader,
  profilePrimaryActions,
  communityProfileSheet,
  viewModel,
] = await Promise.all([
  source("package.json"),
  source("docs/SCREEN_MIGRATION_INVENTORY_V2.md"),
  source("docs/CHAT_MULTIPLATFORM_VERTICAL_PLAN.md"),
  source("app/src/main/java/com/quata/core/navigation/AppNavGraph.kt"),
  source("app/src/main/java/com/quata/feature/chat/presentation/chat/AndroidChatProductScreen.kt"),
  source("app/src/main/java/com/quata/core/language/QuataTranslatorClient.kt"),
  source("web/src/wasmJsMain/kotlin/com/quata/web/WebChatHost.kt"),
  source("feature/chat/src/iosMain/kotlin/com/quata/feature/chat/presentation/chat/QuataChatViewController.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatScreenHost.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatConversationDetailContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatMessageDeepLinkFocus.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatComposerAndActionsContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatSelectedMessageActionBarContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatConversationTitleBarContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/FavoriteMessagesHeaderContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatTranslatorOverlayContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatGroupManagementContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatSosLocationContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatAttachmentQuickPanelContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatPendingAttachmentOverlayContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatDocumentAttachmentContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatAudioAttachmentPlayerContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatBrowserHostContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileScreenHost.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileHeaderContent.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/ProfilePrimaryActions.kt"),
  source("feature/neighborhoods/src/commonMain/kotlin/com/quata/feature/neighborhoods/presentation/CommunityProfileSheetContent.kt"),
  source("feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatViewModel.kt"),
]);

test("CHAT-COMMON-ROOT-001 is part of mandatory fast and Wave2 contracts", () => {
  const scripts = JSON.parse(packageJson).scripts;
  assert.match(scripts["test:ci-fast-contracts"], /scripts\/chat-common-root-contract\.test\.mjs/);
  assert.match(scripts["test:web-wave2-contracts"], /scripts\/chat-common-root-contract\.test\.mjs/);
});

test("Android, Wasm and iOS product routes mount ChatProductHostContent", () => {
  assert.match(appNavGraph, /import com\.quata\.feature\.chat\.presentation\.chat\.AndroidChatProductScreen/);
  assert.match(appNavGraph, /AndroidChatProductScreen\([\s\S]*?focusedMessageId = chatFocusedMessageId[\s\S]*?onFocusedMessageHandled = \{ chatFocusedMessageId = null \}/);
  assert.doesNotMatch(appNavGraph, /com\.quata\.feature\.chat\.presentation\.chat\.ChatScreen\b/);

  assert.match(androidHost, /fun AndroidChatProductScreen\(/);
  assert.match(androidHost, /ChatProductHostContent\(/);
  assert.match(androidHost, /conversationModel = viewModel\.commonModel/);

  assert.match(webHost, /fun WebChatHost\(/);
  assert.match(webHost, /ChatProductHostContent\(/);
  assert.match(webHost, /conversationList = \{ listModifier ->[\s\S]*?ConversationsScreenHost\(/);
  assert.match(webHost, /onOpenFavorites = \{ onOpenConversation\(AppDestinations\.FavoriteMessagesConversationId\) \}/);

  assert.match(iosHost, /fun QuataChatViewController\(dependencies: IosChatHostDependencies\)/);
  assert.match(iosHost, /ChatProductHostContent\(/);
  assert.match(iosHost, /conversationList = \{ listModifier ->[\s\S]*?ConversationsScreenHost\(/);
  assert.match(iosHost, /onOpenFavorites = \{ dependencies\.onOpenConversation\(AppDestinations\.FavoriteMessagesConversationId\) \}/);
});

test("platform product sources do not route through the legacy browser-style wrapper", async () => {
  const files = (
    await Promise.all([
      kotlinAndSwiftFiles("app/src/main/java"),
      kotlinAndSwiftFiles("web/src/wasmJsMain"),
      kotlinAndSwiftFiles("feature/chat/src/iosMain"),
      kotlinAndSwiftFiles("iosApp/iosApp"),
    ])
  ).flat();
  const offenders = [];
  for (const file of files) {
    const text = await source(file);
    if (/ChatBrowserHostContent\(/.test(text)) offenders.push(file);
  }
  assert.deepEqual(offenders, [], "legacy ChatBrowserHostContent must not be a platform product route");
});

test("common chat root owns read states, retry, history paging and one-shot focused message handling", () => {
  assert.match(chatScreenHost, /ChatProductScaffold\(/);
  assert.match(chatScreenHost, /ChatReadFailureContent\(/);
  assert.match(chatScreenHost, /model\.retryMessageLoading\(\)/);
  assert.match(chatScreenHost, /model\.loadOlderMessages\(\)/);
  assert.match(chatScreenHost, /focusedMessageId = focusedMessage\?\.id/);
  assert.match(chatScreenHost, /deepLinkRequest = ChatMessageDeepLinkRequest\.NoTarget[\s\S]*?onFocusedMessageHandled\(\)/);

  assert.match(conversationDetail, /item\(key = "chat-initial-loading"\)/);
  assert.match(conversationDetail, /item\(key = "chat-history-loading"\)/);
  assert.match(conversationDetail, /listState\.scrollToItem\(index\)/);
  assert.match(conversationDetail, /visibleItemsInfo\.any \{ item -> item\.key == focusedMessage\.composeKey\(\) \}/);
  assert.match(conversationDetail, /private const val FocusedMessageHighlightMillis = 8_000L/);
  assert.match(conversationDetail, /delay\(FocusedMessageHighlightMillis\)[\s\S]*?onFocusedMessageHandled\(\)/);
  assert.match(conversationDetail, /firstVisible <= 2 && !isLoadingOlderMessages\)[\s\S]*?onLoadOlderMessages\(\)/);
  assert.match(conversationDetail, /testTag = if \(isSelected\) "chat\.message\.\$\{message\.id\}\.selected" else "chat\.message\.\$\{message\.id\}"/);
  assert.match(conversationDetail, /role = Role\.Button/);
  assert.match(conversationDetail, /contentDescription = message\.accessibleActionLabel\(\)/);
  assert.match(conversationDetail, /private fun Message\.accessibleActionLabel\(\): String/);
  assert.match(conversationDetail, /if \(isSelected\) \{[\s\S]*?Box\([\s\S]*?testTag = "chat\.message\.\$\{message\.id\}\.selected"/);
  assert.match(conversationDetail, /stateDescription = if \(isSelected\) "selected" else "not selected"/);

  assert.match(deepLinkFocus, /hasMoreHistory -> ChatMessageDeepLinkRequest\.LoadingOlder/);
  assert.match(deepLinkFocus, /else -> ChatMessageDeepLinkRequest\.Unavailable/);
  assert.match(deepLinkFocus, /retryChatMessageDeepLinkRequest/);
});

test("common chat action chrome owns mute and tombstone action guards", () => {
  assert.match(groupManagement, /ChatGroupMenuOptionsTestTag = "chat\.menu\.options"/);
  assert.match(groupManagement, /testTag = ChatGroupMenuOptionsTestTag/);
  assert.match(groupManagement, /ChatUiEvent\.ConversationMutedChanged\(conversation\?\.isMuted != true\)/);
  assert.match(groupManagement, /conversation\?\.isMuted == true\) strings\.reactivateNotifications else strings\.muteConversation/);
  assert.match(groupManagement, /chat\.menu\.mute/);
  assert.match(groupManagement, /chat\.menu\.unmute/);

  for (const tag of ["copy", "reply", "forward", "edit", "report", "favorite", "delete"]) {
    assert.match(selectedActions, new RegExp(`testTag = "chat\\.action\\.${tag}"`));
  }
  assert.match(selectedActions, /if \(!message\.isDeleted\) \{[\s\S]*?chat\.action\.copy[\s\S]*?chat\.action\.reply[\s\S]*?chat\.action\.forward/);
  assert.match(selectedActions, /if \(!message\.isDeleted\) CompactIconButton\([\s\S]*?testTag = "chat\.action\.favorite"/);

  assert.match(viewModel, /selectedMessage\(\)\?\.takeIf \{ !it\.isLocalEcho && !it\.isDeleted \}/);
  assert.match(viewModel, /selectedMessage\(\)\?\.takeIf \{ it\.isMine && !it\.isDeleted && !it\.isLocalEcho \}/);
  assert.match(viewModel, /selectedMessage\(\)\?\.takeIf \{ !it\.isMine && !it\.isDeleted && !it\.isLocalEcho \}/);
});

test("common chat group management exposes stable cross-platform evidence anchors", () => {
  const menuAnchors = {
    Options: "chat.menu.options",
    Mute: "chat.menu.mute",
    Unmute: "chat.menu.unmute",
    AllowInvites: "chat.group.menu.allowInvites",
    AddParticipants: "chat.group.menu.addParticipants",
    Leave: "chat.group.menu.leave",
    Delete: "chat.group.menu.delete",
  };
  for (const [constant, tag] of Object.entries(menuAnchors)) {
    assert.match(groupManagement, new RegExp(`ChatGroupMenu${constant}TestTag = "${tag.replaceAll(".", "\\.")}"`));
  }
  assert.match(groupManagement, /testTag = ChatGroupMenuOptionsTestTag/);
  assert.match(groupManagement, /testTag = if \(conversation\?\.isMuted == true\) ChatGroupMenuUnmuteTestTag else ChatGroupMenuMuteTestTag/);
  for (const constant of ["AllowInvites", "AddParticipants", "Leave", "Delete"]) {
    assert.match(groupManagement, new RegExp(`testTag = ChatGroupMenu${constant}TestTag`));
  }

  for (const [constant, tag] of Object.entries({
    MemberRow: "chat.group.member.",
    MemberManage: "chat.group.member.manage.",
    MemberPromoteDemote: "chat.group.member.role.",
    MemberBlock: "chat.group.member.block.",
    MemberRemove: "chat.group.member.remove.",
  })) {
    assert.match(groupManagement, new RegExp(`ChatGroup${constant}TestTagPrefix = "${tag.replaceAll(".", "\\.")}"`));
    assert.match(groupManagement, new RegExp(`testTag = ChatGroup${constant}TestTagPrefix \\+ member\\.id`));
  }
  assert.match(groupManagement, /import androidx\.compose\.ui\.text\.style\.TextOverflow/);
  assert.match(groupManagement, /maxLines = 1,\s+overflow = TextOverflow\.Ellipsis/);

  const pickerAnchors = {
    Root: "chat.group.participants.root",
    Search: "chat.group.participants.search",
    LoadMore: "chat.group.participants.loadMore",
    Confirm: "chat.group.participants.confirm",
    Cancel: "chat.group.participants.cancel",
  };
  for (const [constant, tag] of Object.entries(pickerAnchors)) {
    assert.match(groupManagement, new RegExp(`ChatGroupParticipantPicker${constant}TestTag = "${tag.replaceAll(".", "\\.")}"`));
    assert.match(groupManagement, new RegExp(`testTag = ChatGroupParticipantPicker${constant}TestTag`));
  }
  assert.match(groupManagement, /ChatGroupParticipantPickerCandidateTestTagPrefix = "chat\.group\.participants\.candidate\."/);
  assert.match(groupManagement, /testTag = ChatGroupParticipantPickerCandidateTestTagPrefix \+ candidate\.profileId/);

  for (const event of [
    "MemberInvitesChanged",
    "OpenAddParticipants",
    "LeaveConversation",
    "DeleteConversation",
    "PromoteModerator",
    "DemoteModerator",
    "BlockParticipant",
    "RemoveParticipant",
    "ParticipantSearchChanged",
    "ParticipantSelectionToggled",
    "AddSelectedParticipants",
  ]) {
    assert.match(groupManagement, new RegExp(`ChatUiEvent\\.${event}`));
  }
});

test("common SOS location messages expose map/open evidence anchors", () => {
  for (const [constant, tag] of Object.entries({
    Root: "chat.sos.location.root",
    MapPreview: "chat.sos.location.mapPreview",
    Unavailable: "chat.sos.location.unavailable",
    OpenMaps: "chat.sos.location.openMaps",
  })) {
    assert.match(sosLocation, new RegExp(`ChatSosLocation${constant}TestTag = "${tag.replaceAll(".", "\\.")}"`));
    assert.match(sosLocation, new RegExp(`testTag = ChatSosLocation${constant}TestTag`));
  }
  assert.match(sosLocation, /onOpenMaps: \(String\) -> Unit/);
  assert.match(sosLocation, /modifier = Modifier\.clickable \{ onOpenMaps\(url\) \}\.semantics \{[\s\S]*?testTag = ChatSosLocationOpenMapsTestTag[\s\S]*?contentDescription = openMapsLabel[\s\S]*?role = Role\.Button/);
  assert.match(chatBrowserHostContent, /ChatSosLocationContent\(/);
  assert.match(chatBrowserHostContent, /onOpenMaps = onOpenExternalLink/);
  assert.match(chatBrowserHostContent, /resolveChatSosPresentation/);
});

test("common chat headers and selected-message menu use one opaque surface color", () => {
  assert.match(titleBar, /internal fun chatHeaderSurfaceColor\(\) = quataTheme\(\)\.colors\.surface/);
  assert.match(titleBar, /val headerSurfaceColor = chatHeaderSurfaceColor\(\)/);
  assert.match(titleBar, /color = headerSurfaceColor/);
  assert.match(chatScreenHost, /CompositionLocalProvider\(LocalQuataTranslatableTextRegistry provides translatorRegistry\) \{\s*Box\(\s*modifier = Modifier\s*\.weight\(1f\)\s*\.imePadding\(\)/);
  assert.match(chatScreenHost, /ChatConversationDetailContent\([\s\S]*?modifier = Modifier\.fillMaxSize\(\)/);
  assert.doesNotMatch(chatScreenHost, /ChatConversationDetailContent\([\s\S]*?modifier = Modifier\.weight\(1f\)\.imePadding\(\)/);
  assert.match(selectedActionBar, /val surfaceColor = chatHeaderSurfaceColor\(\)/);
  assert.match(selectedActionBar, /Surface\([\s\S]*?color = surfaceColor/);
  assert.match(selectedActionBar, /Box\(Modifier\.fillMaxWidth\(\)\.background\(surfaceColor\)\)/);
  assert.match(selectedActionBar, /\.background\(surfaceColor\)/);
  assert.match(selectedActionBar, /color = surfaceColor/);
  assert.match(groupManagement, /private fun ChatOpaqueOptionsMenuContent\(/);
  assert.match(groupManagement, /containerColor = chatHeaderSurfaceColor\(\)/);
  assert.equal((groupManagement.match(/DropdownMenu\(/g) ?? []).length, 1);
  assert.equal((groupManagement.match(/ChatOpaqueOptionsMenuContent\(/g) ?? []).length, 3);
  assert.equal((groupManagement.match(/<ChatOpaqueOptionsMenuContent\(/g) ?? []).length, 0);
  assert.match(favoriteHeader, /color = chatHeaderSurfaceColor\(\)/);
  for (const sourceText of [selectedActionBar, groupManagement, favoriteHeader]) assert.doesNotMatch(sourceText, /colors\.surface\.copy\(alpha\s*=/);
  assert.doesNotMatch(androidHost, /colors\.surface\.copy\(alpha\s*=/);
  assert.doesNotMatch(titleBar, /colors\.surface\.copy\(alpha\s*=/);
});

test("common chat composer exposes stable cross-platform evidence anchors", () => {
  const expectedTags = {
    Root: "root",
    Input: "input",
    Send: "send",
    Emoji: "emoji",
    Attach: "attach",
    Camera: "camera",
    RecordAudio: "recordAudio",
    EditingBanner: "editing",
    ReplyBanner: "reply",
  };
  for (const [constant, tag] of Object.entries(expectedTags)) {
    assert.match(selectedActions, new RegExp(`ChatComposer${constant}TestTag = "chat\\.composer\\.${tag}"`));
    assert.match(selectedActions, new RegExp(`testTag = ChatComposer${constant}TestTag`));
  }
  assert.match(selectedActions, /messageInputOverride\?\.invoke\([\s\S]*?taggedInputModifier/);
  assert.match(selectedActions, /sendButtonOverride\?\.let[\s\S]*?Modifier\.semantics \{ testTag = ChatComposerSendTestTag \}/);
  assert.match(selectedActions, /ChatComposerModeBannerContent\([\s\S]*?ChatComposerEditingBannerTestTag/);
  assert.match(selectedActions, /ChatComposerModeBannerContent\([\s\S]*?ChatComposerReplyBannerTestTag/);
  assert.match(webHost, /messageInputOverride = \{ value, onChange, onSubmit, modifier, leadingIcon, trailingIcon ->[\s\S]*?WebNativeInput\([\s\S]*?onSubmit = onSubmit/);
  assert.doesNotMatch(webHost, /sendButtonOverride = \{/);
  assert.doesNotMatch(webHost, /WebNativeButton\("Enviar"/);
  assert.match(iosHost, /ChatProductHostContent\([\s\S]*?audioRecordingConfiguration = dependencies\.audioRecordingConfiguration/);
});

test("common chat forward picker exposes stable cross-platform evidence anchors", () => {
  const expectedTags = {
    Root: "root",
    Search: "search",
    LoadMore: "loadMore",
    Send: "send",
    Cancel: "cancel",
  };
  for (const [constant, tag] of Object.entries(expectedTags)) {
    assert.match(selectedActions, new RegExp(`ChatForwardPicker${constant}TestTag = "chat\\.forward\\.${tag}"`));
    assert.match(selectedActions, new RegExp(`testTag = ChatForwardPicker${constant}TestTag`));
  }
  assert.match(selectedActions, /ChatForwardPickerCandidateTestTagPrefix = "chat\.forward\.candidate\."/);
  assert.match(selectedActions, /testTag = ChatForwardPickerCandidateTestTagPrefix \+ candidate\.profileId/);
  assert.match(selectedActions, /ChatUiEvent\.OpenForwardDialog/);
  assert.match(selectedActions, /ChatUiEvent\.SendForward/);
});

test("common chat attachments and audio expose stable cross-platform evidence anchors", () => {
  const quickPanelAnchors = {
    QuickPanel: "chat.attachment.quickPanel",
    PickFile: "chat.attachment.pick.file",
    PickGallery: "chat.attachment.pick.gallery",
  };
  for (const [constant, tag] of Object.entries(quickPanelAnchors)) {
    assert.match(attachmentQuickPanel, new RegExp(`ChatAttachment${constant}TestTag = "${tag.replaceAll(".", "\\.")}"`));
    assert.match(attachmentQuickPanel, new RegExp(`testTag = ChatAttachment${constant}TestTag`));
  }

  const pendingAnchors = {
    Overlay: "chat.attachment.pending",
    Clear: "chat.attachment.pending.clear",
  };
  for (const [constant, tag] of Object.entries(pendingAnchors)) {
    assert.match(pendingAttachmentOverlay, new RegExp(`ChatPendingAttachment${constant}TestTag = "${tag.replaceAll(".", "\\.")}"`));
    assert.match(pendingAttachmentOverlay, new RegExp(`testTag = ChatPendingAttachment${constant}TestTag`));
  }

  assert.match(documentAttachment, /ChatDocumentAttachmentTestTag = "chat\.attachment\.document"/);
  assert.match(documentAttachment, /testTag = ChatDocumentAttachmentTestTag/);

  const audioAnchors = {
    Player: "chat.attachment.audio.player",
    Toggle: "chat.attachment.audio.toggle",
    Progress: "chat.attachment.audio.progress",
  };
  for (const [constant, tag] of Object.entries(audioAnchors)) {
    assert.match(audioAttachmentPlayer, new RegExp(`ChatAudioAttachment${constant}TestTag = "${tag.replaceAll(".", "\\.")}"`));
    assert.match(audioAttachmentPlayer, new RegExp(`testTag = ChatAudioAttachment${constant}TestTag`));
  }

  assert.match(chatBrowserHostContent, /ChatMediaAttachmentContent\(/);
  assert.match(chatBrowserHostContent, /ChatDocumentAttachmentContent\(/);
  assert.match(chatBrowserHostContent, /ChatAudioAttachmentPlayerContent\(/);
  assert.match(chatBrowserHostContent, /ChatUiEvent\.AttachmentSelected/);
  assert.match(chatBrowserHostContent, /FilePickerSource\.Documents/);
  assert.match(chatBrowserHostContent, /FilePickerSource\.Gallery/);
  assert.match(iosHost, /audioRecordingConfiguration = dependencies\.audioRecordingConfiguration/);
});

test("CHAT-TRANSLATION uses the common overlay and stable evidence anchors on every platform", () => {
  assert.match(chatScreenHost, /FangTranslatorTriggerContent\([\s\S]*?modifier = Modifier\.semantics \{ testTag = ChatTranslatorTriggerTestTag \}/);
  assert.match(chatScreenHost, /slots\.onOpenTranslator\?\.invoke\(\) \?: run \{ translatorActive = true \}/);
  assert.match(chatScreenHost, /ChatTranslatorOverlayContent\(/);

  for (const [constant, tag] of Object.entries({
    Trigger: "chat.translator.trigger",
    Overlay: "chat.translator.overlay",
    Exit: "chat.translator.exit",
    Instruction: "chat.translator.instruction",
  })) {
    assert.match(chatTranslatorOverlay, new RegExp(`ChatTranslator${constant}TestTag = "${tag.replaceAll(".", "\\.")}"`));
  }
  assert.match(chatTranslatorOverlay, /ChatTranslatorMessageTestTagPrefix = "chat\.translator\.message\."/);
  assert.match(chatTranslatorOverlay, /testTag = ChatTranslatorMessageTestTagPrefix \+ box\.id/);
  assert.match(chatTranslatorOverlay, /val label: String get\(\) = "\$\{source\.shortCode\(\)\}->\$\{target\.shortCode\(\)\}"/);

  assert.match(androidHost, /FangChatTranslationGateway\(QuataCachedTranslator\.get\(context\)\)/);
  assert.doesNotMatch(androidHost, /LocalQuataTranslatorModeController|QuataTranslatorOverlaySource\.Chat|onOpenTranslator = \{/);
  assert.match(androidTranslatorClient, /readTimeout\(90, TimeUnit\.SECONDS\)/);
  assert.match(androidTranslatorClient, /callTimeout\(120, TimeUnit\.SECONDS\)/);
  assert.match(webHost, /FangChatTranslationGateway\(FangTranslationService\(transport = BrowserTranslationHttpTransport\(\)\)\)/);
  assert.match(iosHost, /FangChatTranslationGateway\(FangTranslationService\(transport = IosTranslationHttpTransport\(\)\)\)/);
});

test("common chat profile entry exposes stable cross-platform evidence anchors", () => {
  assert.match(chatBrowserHostContent, /ChatProfileMemberAvatarTestTagPrefix = "chat\.profile\.member\."/);
  assert.match(chatBrowserHostContent, /ChatProfileMessageAvatarTestTagPrefix = "chat\.profile\.message\."/);
  assert.match(chatBrowserHostContent, /testTag = ChatProfileMemberAvatarTestTagPrefix \+ member\.id/);
  assert.match(chatBrowserHostContent, /testTag = ChatProfileMessageAvatarTestTagPrefix \+ message\.senderId/);
  assert.match(chatBrowserHostContent, /onOpenUserProfile\(member\.id\)/);
  assert.match(chatBrowserHostContent, /onOpenUserProfile\(message\.senderId\)/);

  assert.match(communityProfileHost, /PublicProfileRootTestTag = "public-profile\.root"/);
  assert.match(communityProfileHost, /PublicProfileBackTestTag = "public-profile\.back"/);
  assert.match(communityProfileHost, /PublicProfileUserTestTagPrefix = "public-profile\.user\."/);
  assert.match(communityProfileHost, /PublicProfileHeaderTestTagPrefix = PublicProfileUserTestTagPrefix/);
  [
    "Avatar",
    "Name",
    "Neighborhood",
    "PostsKpi",
    "FollowersKpi",
    "FollowingKpi",
  ].forEach((constant) => {
    assert.match(communityProfileHost, new RegExp(`PublicProfile${constant}TestTagPrefix = "public-profile\\.`));
    assert.match(communityProfileHost, new RegExp(`PublicProfile${constant}TestTagPrefix \\+ profile\\.user\\.id`));
  });
  assert.match(communityProfileHost, /testTag = PublicProfileRootTestTag/);
  assert.match(communityProfileHost, /testTag = PublicProfileBackTestTag/);
  assert.match(communityProfileHost, /testTag = PublicProfileHeaderTestTagPrefix \+ profile\.user\.id/);
  assert.match(communityProfileHeader, /displayNameModifier: Modifier = Modifier/);
  assert.match(communityProfileHeader, /neighborhoodModifier: Modifier = Modifier/);
  assert.match(communityProfileHeader, /Text\(displayName, modifier = displayNameModifier/);
  assert.match(communityProfileHeader, /Text\(neighborhood, modifier = neighborhoodModifier/);
  assert.match(communityProfileHost, /PublicProfileHeaderTestTagPrefix = PublicProfileUserTestTagPrefix/);
  assert.match(communityProfileHost, /userId = profile\.user\.id/);
  assert.match(profilePrimaryActions, /PublicProfileFollowActionTestTagPrefix = "public-profile\.follow\."/);
  assert.match(profilePrimaryActions, /PublicProfileFollowLoadingTestTagPrefix = "public-profile\.follow\.loading\."/);
  assert.match(profilePrimaryActions, /PublicProfileChatActionTestTagPrefix = "public-profile\.chat\."/);
  assert.match(profilePrimaryActions, /testTag = PublicProfileFollowActionTestTagPrefix \+ userId/);
  assert.match(profilePrimaryActions, /testTag = PublicProfileFollowLoadingTestTagPrefix \+ userId/);
  assert.match(profilePrimaryActions, /testTag = PublicProfileChatActionTestTagPrefix \+ userId/);
  assert.match(communityProfileSheet, /modifier: Modifier = Modifier/);
  assert.match(communityProfileSheet, /ModalBottomSheet\([\s\S]*?modifier = modifier/);
});

test("SCR-CHAT inventory reflects the real common-root state without declaring final GO", () => {
  const scrChat = inventory.split(/\r?\n/).find((line) => line.startsWith("| `SCR-CHAT` |"));
  const chatFavorites = inventory.split(/\r?\n/).find((line) => line.startsWith("| `CHAT-FAVORITES` |"));
  assert.ok(scrChat, "SCR-CHAT row must exist");
  assert.ok(chatFavorites, "CHAT-FAVORITES row must exist");
  assert.match(scrChat, /\*\*COMÚN con límites\.\*\*/);
  assert.match(scrChat, /`ChatProductHostContent`\/`ChatScreenHost` se consume en Android, Wasm e iOS/);
  assert.doesNotMatch(scrChat, /FALLBACK|PARCIAL/);
  assert.match(scrChat, /no declarar GO/);
  assert.match(chatFavorites, /FavoriteMessagesConversationId/);
  assert.match(chatFavorites, /Android, Wasm e iOS/);
  assert.match(inventory, /\| `CHAT-MESSAGES` \|[\s\S]*?#226 \(`702aad06`\)/);
  assert.match(inventory, /\| `CHAT-FOCUSED-MESSAGE` \|[\s\S]*?contrato común de foco/);
  assert.doesNotMatch(inventory, /Web\/iOS aún conservan `ChatBrowserHostContent`/);

  assert.match(verticalPlan, /Android, Wasm e iOS consumen ya `ChatProductHostContent`\/`ChatScreenHost`/);
  assert.match(verticalPlan, /`SCR-CHAT` permanece \*\*COMÚN con límites\*\*/);
});
