package com.quata.feature.profile.presentation

import com.quata.core.model.CountryPrefix
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PlatformContact
import com.quata.core.platform.PlatformResult
import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.auth.presentation.AuthCatalog
import com.quata.feature.auth.presentation.AuthCatalogLocale
import com.quata.feature.profile.data.IosProfilePostgrestGateway
import com.quata.feature.profile.data.IosProfileKeychainSessionProvider
import com.quata.feature.profile.data.IosProfileAvatarUploader
import com.quata.feature.profile.data.IosProfileRuntimeConfiguration
import com.quata.feature.profile.data.IosProfileSessionProvider
import com.quata.feature.profile.data.KmpProfileRepository
import com.quata.feature.profile.data.ProfileAvatarUploader
import com.quata.feature.profile.data.ProfileEmergencyContactsStore
import com.quata.feature.profile.data.ProfileEmergencyMessageStore
import com.quata.feature.profile.data.ProfilePresentationCatalog
import com.quata.feature.profile.data.ProfileSession
import com.quata.feature.profile.data.ProfileSessionProvider
import com.quata.feature.profile.data.RemoteProfileViewerRepository
import com.quata.feature.profile.data.StoredProfileEmergencyMessage
import com.quata.feature.profile.data.profileSecretQuestions
import com.quata.feature.profile.domain.SecretQuestionOption
import platform.Foundation.NSUserDefaults

/**
 * Authenticated iOS composition for the portable Profile/SOS surface.
 *
 * The existing Keychain session and URLSession/PostgREST gateway are reused. Actor-scoped profile
 * and SOS writes use their deployed contracts; selecting a new avatar remains disabled until an
 * iOS storage upload contract has E2E evidence.
 */
class IosProfileSosRuntimeBootstrap(
    configuration: IosProfileRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
    languageTag: String?,
) {
    private val authenticatedSession = IosProfileKeychainSessionProvider(authSession)
    private val sessionProvider = IosProfileSessionAdapter(authSession, authenticatedSession)
    private val remote = IosProfilePostgrestGateway(configuration, authenticatedSession)
    private val repository = KmpProfileRepository(
        remote = remote,
        sessions = sessionProvider,
        avatarUploader = IosProfileAvatarUploader(configuration, authSession),
        emergencyMessages = IosProfileEmergencyMessageDefaults(),
        emergencyContacts = IosProfileEmergencyContactsDefaults(),
        catalog = IosProfilePresentationCatalog(AuthCatalogLocale.fromLanguage(languageTag)),
        sessionReadiness = sessionProvider::requireFreshSession,
    )
    private val memberProfileRepository = RemoteProfileViewerRepository(
        remote = remote,
        sessions = sessionProvider,
    )

    internal fun usesRenewableSession(candidate: IosRenewableAuthSession): Boolean = authSession === candidate

    fun hostDependencies(
        contacts: ContactPickerService,
        permissions: PermissionService,
        onContactsPicked: (List<PlatformContact>) -> Unit,
        onContactPickerResult: (PlatformResult<List<PlatformContact>>) -> Unit,
        onContactsPermissionResult: (com.quata.core.platform.PermissionStatus) -> Unit,
        onClose: () -> Unit,
    ): IosProfileSosHostDependencies = IosProfileSosHostDependencies(
        viewModel = ProfileViewModel(repository),
        strings = IosEmergencyContactsEditorStrings,
        isLandscape = false,
        isImeVisible = false,
        onAvatarAction = {},
        importContactsLabel = "Import contacts",
        requestPermissionsLabel = "Allow contacts access",
        contacts = contacts,
        permissions = permissions,
        onContactsPicked = onContactsPicked,
        onContactPickerResult = onContactPickerResult,
        onContactsPermissionResult = onContactsPermissionResult,
        onClose = onClose,
    )

    /** Factory for the full Cuenta route; callers no longer route Cuenta to SOS only. */
    fun profileHostDependencies(
        onLogout: () -> Unit,
        onDeactivateAccount: () -> Unit,
        onDeleteAccountData: () -> Unit,
        filePicker: com.quata.core.platform.FilePickerService,
        touchFlowEnabled: Boolean,
        themeModeStorageValue: String?,
        onTouchFlowEnabledChange: (Boolean) -> Unit,
        onThemeModeStorageValueChange: (String) -> Unit,
    ): IosProfileHostDependencies = IosProfileHostDependencies(
        repository = repository,
        onLogout = onLogout,
        onDeactivateAccount = onDeactivateAccount,
        onDeleteAccountData = onDeleteAccountData,
        filePicker = filePicker,
        touchFlowEnabled = touchFlowEnabled,
        onTouchFlowEnabledChange = onTouchFlowEnabledChange,
        themeMode = QuataThemeMode.fromStorageValue(themeModeStorageValue),
        onThemeModeChange = { mode -> onThemeModeStorageValueChange(mode.storageValue) },
    )

    /**
     * Uses the same authenticated transport and Keychain-backed identity as Profile/SOS, but
     * exposes only the dedicated read-only member projection. The launcher must not substitute
     * a local Communities model for this route.
     */
    fun memberProfileHostDependencies(
        profileId: String,
        onClose: () -> Unit,
    ): IosMemberProfileHostDependencies = IosMemberProfileHostDependencies(
        profileId = profileId,
        repository = memberProfileRepository,
        onClose = onClose,
    )
}

/** Swift-facing factory: default Kotlin arguments are not reliably exported to Swift. */
fun createIosProfileSosRuntimeBootstrap(
    configuration: IosProfileRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
    languageTag: String?,
): IosProfileSosRuntimeBootstrap = IosProfileSosRuntimeBootstrap(configuration, authSession, languageTag)

/** Public member-profile composition does not read or create a Keychain session. */
fun createIosPublicMemberProfileHostDependencies(
    configuration: IosProfileRuntimeConfiguration,
    profileId: String,
    onClose: () -> Unit,
): IosMemberProfileHostDependencies {
    val anonymousSession = object : ProfileSessionProvider {
        override fun currentSession(): ProfileSession? = null
        override fun updateDisplayName(session: ProfileSession, displayName: String) = Unit
    }
    val anonymousTransportSession = IosProfileSessionProvider { null }
    val remote = IosProfilePostgrestGateway(
        configuration = configuration,
        sessionProvider = anonymousTransportSession,
    )
    return IosMemberProfileHostDependencies(
        profileId = profileId,
        repository = RemoteProfileViewerRepository(remote, anonymousSession),
        onClose = onClose,
    )
}

private class IosProfileSessionAdapter(
    private val authSession: IosRenewableAuthSession,
    private val authenticatedSession: IosProfileKeychainSessionProvider,
) : ProfileSessionProvider {
    override fun currentSession(): ProfileSession? = authSession.restoredSession()
        ?.takeIf { it.userId.isNotBlank() }
        ?.let { ProfileSession(profileId = it.userId, displayName = it.displayName) }

    override fun updateDisplayName(session: ProfileSession, displayName: String) {
        // The authoritative display name is read from the remote profile. Do not rewrite the
        // Keychain identity projection optimistically after an unrelated local failure.
    }

    suspend fun requireFreshSession() {
        val resolved = authenticatedSession.currentSession() ?: error("ios_profile_session_missing")
        val restored = currentSession() ?: error("ios_profile_session_missing")
        check(resolved.profileId == restored.profileId) { "ios_profile_session_actor_mismatch" }
    }
}

internal fun iosProfileAvatarUploadReference(avatarUri: String?): String? {
    val normalized = avatarUri?.trim()?.takeIf(String::isNotBlank) ?: return null
    // Preserve an existing server URL during unrelated edits. Only a new device-local URI
    // requires the unavailable upload boundary.
    if (normalized.startsWith("https://") || normalized.startsWith("http://")) return normalized
    throw UnsupportedOperationException("ios_profile_avatar_upload_not_verified")
}

private class IosProfileEmergencyMessageDefaults(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ProfileEmergencyMessageStore {
    override suspend fun get(profileId: String): StoredProfileEmergencyMessage? {
        val message = defaults.stringForKey("quata.profile.sos.message.$profileId") ?: return null
        return StoredProfileEmergencyMessage(
            message = message,
            isDefault = defaults.boolForKey("quata.profile.sos.message.default.$profileId"),
        )
    }

    override suspend fun save(profileId: String, message: String, isDefault: Boolean) {
        defaults.setObject(message, forKey = "quata.profile.sos.message.$profileId")
        defaults.setBool(isDefault, forKey = "quata.profile.sos.message.default.$profileId")
    }
}

private class IosProfileEmergencyContactsDefaults(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ProfileEmergencyContactsStore {
    override suspend fun get(profileId: String): List<String> =
        (defaults.arrayForKey("quata.profile.sos.contacts.$profileId") as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()

    override suspend fun save(profileId: String, contactIds: List<String>) {
        defaults.setObject(contactIds, forKey = "quata.profile.sos.contacts.$profileId")
    }
}

private class IosProfilePresentationCatalog(private val locale: AuthCatalogLocale) : ProfilePresentationCatalog {
    override fun countryPrefixes(): List<CountryPrefix> = AuthCatalog.countryPrefixes(locale)
    override fun secretQuestions(): List<SecretQuestionOption> = profileSecretQuestions(locale)
    override fun fallbackUserName(): String = "Quata user"
    override fun defaultEmergencyMessage(displayName: String): String = "Emergency message from $displayName"
    override fun changesSavedMessage(): String = "Profile changes saved"
    override fun emergencyContactsSavedMessage(): String = "SOS contacts saved"
}

internal val IosEmergencyContactsEditorStrings = EmergencyContactsEditorStrings(
    header = EmergencyContactsHeaderStrings(
        back = "Back",
        sos = "SOS",
        title = "Emergency contacts",
        contactsTab = "Contacts",
        messageTab = "Message",
        description = "Choose up to five Quata contacts for your SOS message.",
    ),
    selectedCount = { count -> "$count selected" },
    networkUsers = "Quata contacts",
    searchPlaceholder = "Search contacts",
    messageTitle = "Emergency message",
    messageHint = "Write the message your contacts will receive",
    savePortrait = "Save SOS settings",
    saveLandscape = "Save",
)
