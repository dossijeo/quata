package com.quata.feature.profile.presentation

import com.quata.core.model.CountryPrefix
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PlatformContact
import com.quata.core.platform.PlatformResult
import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.profile.data.IosProfilePostgrestGateway
import com.quata.feature.profile.data.IosProfileRuntimeConfiguration
import com.quata.feature.profile.data.KmpProfileRepository
import com.quata.feature.profile.data.ProfileAvatarUploader
import com.quata.feature.profile.data.ProfileEmergencyContactsStore
import com.quata.feature.profile.data.ProfileEmergencyMessageStore
import com.quata.feature.profile.data.ProfilePresentationCatalog
import com.quata.feature.profile.data.ProfileSession
import com.quata.feature.profile.data.ProfileSessionProvider
import com.quata.feature.profile.data.RemoteProfileViewerRepository
import com.quata.feature.profile.data.StoredProfileEmergencyMessage
import com.quata.feature.profile.domain.SecretQuestionOption
import platform.Foundation.NSUserDefaults

/**
 * Authenticated iOS composition for the portable Profile/SOS surface.
 *
 * The existing Keychain session and URLSession/PostgREST gateway are reused. Profile writes and
 * avatar uploads remain deliberately unsupported until their iOS RLS/storage contracts have E2E
 * evidence; the shared ViewModel exposes that failure instead of persisting a false local success.
 */
class IosProfileSosRuntimeBootstrap(
    configuration: IosProfileRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
) {
    private val sessionProvider = IosProfileSessionAdapter(authSession)
    private val remote = IosProfilePostgrestGateway(configuration, authSession)
    private val repository = KmpProfileRepository(
        remote = remote,
        sessions = sessionProvider,
        avatarUploader = IosUnsupportedProfileAvatarUploader,
        emergencyMessages = IosProfileEmergencyMessageDefaults(),
        emergencyContacts = IosProfileEmergencyContactsDefaults(),
        catalog = IosProfilePresentationCatalog,
    )
    private val memberProfileRepository = RemoteProfileViewerRepository(
        remote = remote,
        sessions = sessionProvider,
    )

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
): IosProfileSosRuntimeBootstrap = IosProfileSosRuntimeBootstrap(configuration, authSession)

/** Public member-profile composition: only `community_profiles` is readable without Keychain. */
fun createIosPublicMemberProfileHostDependencies(
    configuration: IosProfileRuntimeConfiguration,
    profileId: String,
    onClose: () -> Unit,
): IosMemberProfileHostDependencies {
    val anonymousSession = object : ProfileSessionProvider {
        override fun currentSession(): ProfileSession? = null
    }
    val remote = IosProfilePostgrestGateway(
        configuration = configuration,
        sessionProvider = anonymousSession,
        allowAnonymousCommunityProfileReads = true,
    )
    return IosMemberProfileHostDependencies(
        profileId = profileId,
        repository = RemoteProfileViewerRepository(remote, anonymousSession),
        onClose = onClose,
    )
}

private class IosProfileSessionAdapter(
    private val authSession: IosRenewableAuthSession,
) : ProfileSessionProvider {
    override fun currentSession(): ProfileSession? = authSession.restoredSession()
        ?.takeIf { it.userId.isNotBlank() }
        ?.let { ProfileSession(profileId = it.userId, displayName = it.displayName) }

    override fun updateDisplayName(session: ProfileSession, displayName: String) {
        // The remote profile mutation is currently unsupported on iOS. Do not rewrite Keychain
        // identity optimistically; a future verified mutation path may update it after success.
    }
}

private object IosUnsupportedProfileAvatarUploader : ProfileAvatarUploader {
    override suspend fun uploadIfNeeded(profileId: String, avatarUri: String?): String? {
        if (avatarUri.isNullOrBlank()) return null
        throw UnsupportedOperationException("ios_profile_avatar_upload_not_verified")
    }
}

private class IosProfileEmergencyMessageDefaults(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ProfileEmergencyMessageStore {
    override fun get(profileId: String): StoredProfileEmergencyMessage? {
        val message = defaults.stringForKey("quata.profile.sos.message.$profileId") ?: return null
        return StoredProfileEmergencyMessage(
            message = message,
            isDefault = defaults.boolForKey("quata.profile.sos.message.default.$profileId"),
        )
    }

    override fun save(profileId: String, message: String, isDefault: Boolean) {
        defaults.setObject(message, forKey = "quata.profile.sos.message.$profileId")
        defaults.setBool(isDefault, forKey = "quata.profile.sos.message.default.$profileId")
    }
}

private class IosProfileEmergencyContactsDefaults(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ProfileEmergencyContactsStore {
    override fun get(profileId: String): List<String> =
        (defaults.arrayForKey("quata.profile.sos.contacts.$profileId") as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()

    override fun save(profileId: String, contactIds: List<String>) {
        defaults.setObject(contactIds, forKey = "quata.profile.sos.contacts.$profileId")
    }
}

private object IosProfilePresentationCatalog : ProfilePresentationCatalog {
    override fun countryPrefixes(): List<CountryPrefix> = listOf(CountryPrefix(code = "240", label = "Equatorial Guinea (+240)"))
    override fun secretQuestions(): List<SecretQuestionOption> = emptyList()
    override fun fallbackUserName(): String = "Quata user"
    override fun defaultEmergencyMessage(displayName: String): String = "Emergency message from $displayName"
    override fun changesSavedMessage(): String = "Profile changes saved"
    override fun emergencyContactsSavedMessage(): String = "SOS contacts saved"
}

private val IosEmergencyContactsEditorStrings = EmergencyContactsEditorStrings(
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
