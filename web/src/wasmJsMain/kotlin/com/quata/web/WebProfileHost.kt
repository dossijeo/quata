package com.quata.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.model.CountryPrefix
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.PreferenceStore
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.profile.data.KmpProfileRepository
import com.quata.feature.profile.data.ProfileAvatarUploader
import com.quata.feature.profile.data.ProfileEmergencyContactsStore
import com.quata.feature.profile.data.ProfileEmergencyMessageStore
import com.quata.feature.profile.data.ProfilePresentationCatalog
import com.quata.feature.profile.data.ProfileRemoteGateway
import com.quata.feature.profile.data.ProfileSession
import com.quata.feature.profile.data.ProfileSessionProvider
import com.quata.feature.profile.data.StoredProfileEmergencyMessage
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.SecretQuestionOption
import com.quata.feature.profile.presentation.EmergencyContactsEditorStrings
import com.quata.feature.profile.presentation.EmergencyContactsHeaderStrings
import com.quata.feature.profile.presentation.EmergencyUserRowContent
import com.quata.feature.profile.presentation.ProfileScreenHost
import com.quata.feature.profile.presentation.ProfileScreenSlots
import com.quata.feature.profile.presentation.ProfileScreenStrings
import com.quata.feature.settings.presentation.AppearanceSettingsStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Web mounts the shared Cuenta UI and only provides browser-owned slots. */
@Composable
fun WebProfileHost(
    repository: WebProfileRepository,
    isLoggingOut: Boolean = false,
    onLogout: (() -> Unit)? = null,
    onDeactivateAccount: () -> Unit = {},
    onDeleteAccountData: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isLandscape = rememberQuataWindowLayoutInfo().isLandscape
    ProfileScreenHost(
        repository = repository,
        strings = WebProfileScreenStrings,
        touchFlowEnabled = false,
        onTouchFlowEnabledChange = {},
        themeMode = QuataThemeMode.System,
        onThemeModeChange = {},
        onLogout = { if (!isLoggingOut) onLogout?.invoke() },
        onDeactivateAccount = onDeactivateAccount,
        onDeleteAccountData = onDeleteAccountData,
        modifier = modifier.fillMaxSize(),
        slots = ProfileScreenSlots(
            isLandscapeLayout = { isLandscape },
            avatar = { name, avatarUrl -> BrowserRemoteAvatar(name, name, avatarUrl, false, null, Modifier.size(56.dp)) },
            avatarActions = { _ -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Cambiar foto (pendiente de subida segura)") } },
            emergencyContactRow = { contact, selected, toggle -> EmergencyUserRowContent(contact, selected, "Añadir", "Quitar", avatar = { QuataAvatarFallback(contact.displayName, contact.id) }, onToggle = toggle) },
        ),
    )
}

/** Authenticated remote repository only. There is no browser-local Profile product fallback. */
class WebProfileRepository(
    @Suppress("UNUSED_PARAMETER") preferences: PreferenceStore,
    @Suppress("UNUSED_PARAMETER") contactPicker: ContactPickerService,
    remoteGateway: ProfileRemoteGateway? = null,
    remoteSessionProvider: ProfileSessionProvider? = null,
    private val remoteAvailable: () -> Boolean = { false },
) : ProfileRepository {
    private val remote: ProfileRepository? = if (remoteGateway != null && remoteSessionProvider != null) KmpProfileRepository(
        remote = remoteGateway, sessions = remoteSessionProvider, avatarUploader = WebProfileAvatarUploader,
        emergencyMessages = WebProfileMemoryEmergencyMessageStore(), emergencyContacts = WebProfileMemoryEmergencyContactsStore(), catalog = WebProfileCatalog,
    ) else null

    fun persistenceMode(): WebProfilePersistenceMode = if (remote != null && remoteAvailable()) WebProfilePersistenceMode.Remote else WebProfilePersistenceMode.Unavailable
    private fun selected(): ProfileRepository = when (persistenceMode()) {
        WebProfilePersistenceMode.Remote -> checkNotNull(remote)
        WebProfilePersistenceMode.Unavailable -> UnavailableWebProfileRepository
    }
    override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> = selected().observeProfileEditModel()
    override suspend fun getProfileEditModel(): Result<ProfileEditModel> = selected().getProfileEditModel()
    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> = selected().saveProfile(update)
    override suspend fun saveEmergencySettings(contactIds: List<String>, message: String, messageIsDefault: Boolean): Result<Unit> = selected().saveEmergencySettings(contactIds, message, messageIsDefault)
    override fun defaultEmergencyMessage(displayName: String): String = selected().defaultEmergencyMessage(displayName)
    override fun changesSavedMessage(): String = selected().changesSavedMessage()
    override fun emergencyContactsSavedMessage(): String = selected().emergencyContactsSavedMessage()
}

enum class WebProfilePersistenceMode { Remote, Unavailable }
internal fun webProfilePersistenceMode(hasRemoteRepository: Boolean, hasConfiguredAuthenticatedSession: Boolean): WebProfilePersistenceMode =
    if (hasRemoteRepository && hasConfiguredAuthenticatedSession) WebProfilePersistenceMode.Remote else WebProfilePersistenceMode.Unavailable

private object UnavailableWebProfileRepository : ProfileRepository {
    private fun unavailable() = IllegalStateException("web_profile_remote_session_unavailable")
    override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> = flow { emit(Result.failure(unavailable())) }
    override suspend fun getProfileEditModel(): Result<ProfileEditModel> = Result.failure(unavailable())
    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> = Result.failure(unavailable())
    override suspend fun saveEmergencySettings(contactIds: List<String>, message: String, messageIsDefault: Boolean): Result<Unit> = Result.failure(unavailable())
    override fun defaultEmergencyMessage(displayName: String) = "Necesito ayuda. Por favor, contacta conmigo, $displayName."
    override fun changesSavedMessage() = ""
    override fun emergencyContactsSavedMessage() = ""
}

private object WebProfileAvatarUploader : ProfileAvatarUploader {
    override suspend fun uploadIfNeeded(profileId: String, avatarUri: String?): String? =
        webProfileAvatarUploadReference(avatarUri)
}

internal fun webProfileAvatarUploadReference(avatarUri: String?): String? {
    val normalized = avatarUri?.trim()?.takeIf(String::isNotBlank) ?: return null
    // An already-persisted remote avatar is not a new upload. Passing it through keeps
    // unrelated profile edits usable while the browser picker/upload capability is disabled.
    if (isBrowserAvatarUrl(normalized)) return normalized
    throw UnsupportedOperationException("web_profile_avatar_upload_not_available")
}
private class WebProfileMemoryEmergencyMessageStore : ProfileEmergencyMessageStore {
    private val values = mutableMapOf<String, StoredProfileEmergencyMessage>()
    override fun get(profileId: String) = values[profileId]
    override fun save(profileId: String, message: String, isDefault: Boolean) { values[profileId] = StoredProfileEmergencyMessage(message, isDefault) }
}
private class WebProfileMemoryEmergencyContactsStore : ProfileEmergencyContactsStore {
    private val values = mutableMapOf<String, List<String>>()
    override fun get(profileId: String) = values[profileId].orEmpty()
    override fun save(profileId: String, contactIds: List<String>) { values[profileId] = contactIds }
}
internal class WebProfileSessionProvider(private val authRepository: WebAuthRepository) : ProfileSessionProvider {
    private var displayName = "Usuario"
    override fun currentSession(): ProfileSession? = authRepository.activeProfileSessionOrNull()?.let { ProfileSession(it.userId, displayName) }
    override fun updateDisplayName(session: ProfileSession, displayName: String) { this.displayName = displayName }
}
private object WebProfileCatalog : ProfilePresentationCatalog {
    override fun countryPrefixes() = listOf(CountryPrefix("240", "+240 - Guinea Ecuatorial"), CountryPrefix("34", "+34 - España"))
    override fun secretQuestions(): List<SecretQuestionOption> = emptyList()
    override fun fallbackUserName() = "Usuario"
    override fun defaultEmergencyMessage(displayName: String) = "Necesito ayuda. Por favor, contacta conmigo, $displayName."
    override fun changesSavedMessage() = "Cambios sincronizados con el servidor."
    override fun emergencyContactsSavedMessage() = "Contactos SOS sincronizados con el servidor."
}

private val WebProfileScreenStrings = ProfileScreenStrings(
    "Cargando perfil…", "Mis datos", "Gestión de cuenta", "Gestiona las opciones sensibles de tu cuenta.", "Configurar contactos de emergencia", "Guardar cambios", "Guardando…", "Cerrar sesión", "Nombre", "Barrio", "Teléfono", "Pregunta secreta", "Nueva respuesta secreta", "Volver", "Desactivar cuenta", "Solicitar eliminación de datos", "Esta acción requiere una confirmación adicional.", "Continuar", "Cancelar",
    AppearanceSettingsStrings("Touch Flow", "Tema", "Sistema", "Oscuro", "Claro"),
    EmergencyContactsEditorStrings(EmergencyContactsHeaderStrings("Volver", "SOS", "Contactos de emergencia", "Elige hasta cinco contactos y personaliza el mensaje de ayuda.", "Contactos", "Mensaje"), { "$it seleccionados" }, "Contactos disponibles", "Buscar contacto", "Mensaje SOS", "Escribe el mensaje que recibirán tus contactos.", "Guardar SOS", "Guardar"),
)
