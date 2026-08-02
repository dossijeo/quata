package com.quata.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material3.DropdownMenuItem as MaterialDropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.model.CountryPrefix
import com.quata.core.platform.CameraCaptureRequest
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PreferenceStore
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.auth.presentation.AuthCatalog
import com.quata.feature.auth.presentation.AuthCatalogLocale
import com.quata.feature.profile.data.KmpProfileRepository
import com.quata.feature.profile.data.ProfileAvatarUploader
import com.quata.feature.profile.data.ProfileEmergencyContactsStore
import com.quata.feature.profile.data.ProfileEmergencyMessageStore
import com.quata.feature.profile.data.ProfilePresentationCatalog
import com.quata.feature.profile.data.ProfileRemoteGateway
import com.quata.feature.profile.data.ProfileSession
import com.quata.feature.profile.data.ProfileSessionProvider
import com.quata.feature.profile.data.StoredProfileEmergencyMessage
import com.quata.feature.profile.data.profileSecretQuestions
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Web mounts the shared Cuenta UI and only provides browser-owned slots. */
@Composable
internal fun WebProfileHost(
    repository: WebProfileRepository,
    platformServices: WebPlatformServices,
    avatarReferences: WebProfileAvatarReferenceRegistry,
    touchFlowEnabled: Boolean,
    themeMode: QuataThemeMode,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (QuataThemeMode) -> Unit,
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
        touchFlowEnabled = touchFlowEnabled,
        onTouchFlowEnabledChange = onTouchFlowEnabledChange,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onLogout = { if (!isLoggingOut) onLogout?.invoke() },
        onDeactivateAccount = onDeactivateAccount,
        onDeleteAccountData = onDeleteAccountData,
        modifier = modifier.fillMaxSize(),
        slots = ProfileScreenSlots(
            isLandscapeLayout = { isLandscape },
            avatar = { name, avatarUrl -> BrowserRemoteAvatar(name, name, avatarUrl, false, null, Modifier.size(56.dp), allowOwnedBlobReference = true) },
            avatarActions = { change -> WebProfileAvatarActions(platformServices, avatarReferences, change) },
            emergencyContactRow = { contact, selected, toggle -> EmergencyUserRowContent(contact, selected, "Añadir", "Quitar", avatar = { BrowserRemoteAvatar(contact.displayName, contact.id, contact.avatarUrl, false, null, Modifier.size(46.dp)) }, onToggle = toggle) },
        ),
    )
}

/** Browser-owned gallery/camera chooser; the visible control is the Android Profile control. */
@Composable
private fun WebProfileAvatarActions(
    platformServices: WebPlatformServices,
    references: WebProfileAvatarReferenceRegistry,
    onAvatarChanged: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var pendingReference by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // This Main scope outlives the composable only until its final release completes. Registry
    // removal makes replacement/upload/disposal idempotent; finally cancels the scope itself.
    val releaseScope = remember { MainScope() }
    val latestPendingReference by rememberUpdatedState(pendingReference)

    DisposableEffect(Unit) {
        onDispose {
            releaseScope.launch {
                try {
                    references.release(latestPendingReference)
                } finally {
                    releaseScope.cancel()
                }
            }
        }
    }

    suspend fun openEditor(file: com.quata.core.platform.PlatformFile, fromCamera: Boolean) {
        references.release(pendingReference)
        if (fromCamera) references.ownCamera(file) else references.ownGallery(file)
        pendingReference = file.reference
        error = null
    }

    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
        CompactIcon(Icons.Filled.PhotoCamera, null)
        Spacer(Modifier.width(4.dp))
        Text("Cambiar foto de perfil")
    }
    if (menuOpen) Popup(
        popupPositionProvider = CenteredAvatarActionMenuPosition,
        onDismissRequest = { menuOpen = false },
    ) {
        Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 6.dp) {
            Column {
        DropdownMenuItem(text = { Text("Elegir de galería") }, onClick = {
            menuOpen = false
            scope.launch {
                when (val result = platformServices.filePicker.pick(FilePickerRequest(listOf("image/*"), source = FilePickerSource.Gallery))) {
                    is PlatformResult.Success -> result.value.firstOrNull()?.let { openEditor(it, fromCamera = false) }
                        ?: run { error = "No se seleccionó ninguna foto." }
                    PlatformResult.Cancelled -> Unit
                    PlatformResult.Unsupported -> error = "La galería no está disponible en este navegador."
                    is PlatformResult.Failure -> error = "No se pudo seleccionar la foto."
                }
            }
        })
        DropdownMenuItem(
            text = { Text("Hacer foto") },
            leadingIcon = { CompactIcon(Icons.Filled.PhotoCamera, null) },
            onClick = {
            menuOpen = false
            scope.launch {
                when (val result = platformServices.cameraCapture.capturePhoto(CameraCaptureRequest("quata-avatar.jpg"))) {
                    is PlatformResult.Success -> openEditor(result.value, fromCamera = true)
                    PlatformResult.Cancelled -> Unit
                    PlatformResult.Unsupported -> error = "La cámara no está disponible en este navegador."
                    is PlatformResult.Failure -> error = "No se pudo capturar la foto."
                }
            }
        })
            }
        }
    }
    pendingReference?.let { reference ->
        WebAvatarImageEditor(
            sourceReference = reference,
            initialTransform = references.editorTransform(reference),
            onDismiss = {
                scope.launch { references.release(reference) }
                pendingReference = null
            },
            onConfirm = { transform ->
                references.saveEditorTransform(reference, transform)
                pendingReference = null
                onAvatarChanged(reference)
            },
        )
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

/**
 * Anchors the action menu to the centre of the actual control, rather than assuming a desktop
 * account-panel offset. On a narrow viewport it is clamped to the visible window and flips above
 * its control when there is no room below.
 */
private object CenteredAvatarActionMenuPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        return webCenteredAvatarActionMenuOffset(anchorBounds, windowSize, popupContentSize)
    }
}

/** Pure placement seam: exact centre on desktop, clamp/flip only when a mobile viewport needs it. */
internal fun webCenteredAvatarActionMenuOffset(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupContentSize: IntSize,
): IntOffset {
    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
    val desiredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
    val belowY = anchorBounds.bottom
    val y = if (belowY + popupContentSize.height <= windowSize.height) belowY
    else (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
    return IntOffset(desiredX.coerceIn(0, maxX), y)
}

/** Keeps both avatar source choices visually equivalent without relying on browser menu chrome. */
@Composable
private fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
) = MaterialDropdownMenuItem(
    text = text,
    onClick = onClick,
    leadingIcon = { CompactIcon(Icons.Filled.PermMedia, null) },
)

@Composable
private fun DropdownMenuItem(
    text: @Composable () -> Unit,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
) = MaterialDropdownMenuItem(
    text = text,
    onClick = onClick,
    leadingIcon = leadingIcon,
)

/** Authenticated remote repository only. There is no browser-local Profile product fallback. */
class WebProfileRepository(
    preferences: PreferenceStore,
    @Suppress("UNUSED_PARAMETER") contactPicker: ContactPickerService,
    remoteGateway: ProfileRemoteGateway? = null,
    remoteSessionProvider: ProfileSessionProvider? = null,
    avatarUploader: ProfileAvatarUploader = UnavailableWebProfileAvatarUploader,
    private val remoteAvailable: () -> Boolean = { false },
) : ProfileRepository {
    private val remote: ProfileRepository? = if (remoteGateway != null && remoteSessionProvider != null) KmpProfileRepository(
        remote = remoteGateway, sessions = remoteSessionProvider, avatarUploader = avatarUploader,
        emergencyMessages = WebProfilePreferenceEmergencyMessageStore(preferences),
        emergencyContacts = WebProfilePreferenceEmergencyContactsStore(preferences),
        catalog = WebProfileCatalog,
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

private object UnavailableWebProfileAvatarUploader : ProfileAvatarUploader {
    override suspend fun uploadIfNeeded(profileId: String, avatarUri: String?): String? =
        webProfileAvatarUploadReference(avatarUri)
}
internal class WebProfilePreferenceEmergencyMessageStore(private val preferences: PreferenceStore) : ProfileEmergencyMessageStore {
    override suspend fun get(profileId: String): StoredProfileEmergencyMessage? {
        val message = preferences.getString(webProfileSosKey(profileId, "message")) ?: return null
        val isDefault = preferences.getString(webProfileSosKey(profileId, "is_default"))?.toBooleanStrictOrNull() ?: true
        return StoredProfileEmergencyMessage(message, isDefault)
    }

    override suspend fun save(profileId: String, message: String, isDefault: Boolean) {
        preferences.putString(webProfileSosKey(profileId, "message"), message)
        preferences.putString(webProfileSosKey(profileId, "is_default"), isDefault.toString())
    }
}

internal class WebProfilePreferenceEmergencyContactsStore(private val preferences: PreferenceStore) : ProfileEmergencyContactsStore {
    override suspend fun get(profileId: String): List<String> = preferences
        .getString(webProfileSosKey(profileId, "contacts"))
        ?.split(WebProfileContactSeparator)
        ?.filter(String::isNotBlank)
        ?.distinct()
        ?.take(5)
        .orEmpty()

    override suspend fun save(profileId: String, contactIds: List<String>) {
        preferences.putString(
            webProfileSosKey(profileId, "contacts"),
            contactIds.map(String::trim).filter(String::isNotBlank).distinct().take(5)
                .joinToString(WebProfileContactSeparator),
        )
    }
}

private fun webProfileSosKey(profileId: String, field: String): String =
    "web.profile.sos.${profileId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.$field"

private const val WebProfileContactSeparator = "\u001F"
internal class WebProfileSessionProvider(private val authRepository: WebAuthRepository) : ProfileSessionProvider {
    private var displayName = "Usuario"
    override fun currentSession(): ProfileSession? = authRepository.activeProfileSessionOrNull()?.let { ProfileSession(it.userId, displayName) }
    override fun updateDisplayName(session: ProfileSession, displayName: String) { this.displayName = displayName }
}
private object WebProfileCatalog : ProfilePresentationCatalog {
    private fun locale() = AuthCatalogLocale.fromLanguage(webProfileLanguageTag())
    override fun countryPrefixes() = AuthCatalog.countryPrefixes(locale())
    override fun secretQuestions(): List<SecretQuestionOption> = profileSecretQuestions(locale())
    override fun fallbackUserName() = "Usuario"
    override fun defaultEmergencyMessage(displayName: String) = "Necesito ayuda. Por favor, contacta conmigo, $displayName."
    override fun changesSavedMessage() = "Cambios sincronizados con el servidor."
    override fun emergencyContactsSavedMessage() = "Contactos SOS sincronizados con el servidor."
}

internal fun webProfileLanguageTag(): String? = js("globalThis.navigator?.language || globalThis.document?.documentElement?.lang || 'es'")

private val WebProfileScreenStrings = ProfileScreenStrings(
    "Cargando perfil…", "Mis datos", "Gestión de cuenta", "Gestiona las opciones sensibles de tu cuenta.", "Configurar contactos de emergencia", "Guardar cambios", "Guardando…", "Cerrar sesión", "Nombre", "Barrio", "Teléfono", "Nueva contraseña", "Pregunta secreta", "Nueva respuesta secreta", "Volver", "Desactivar cuenta", "Solicitar eliminación de datos", "Esta acción requiere una confirmación adicional.", "Continuar", "Cancelar",
    AppearanceSettingsStrings("Activar Qüata TouchFlow", "Modo de color", "Sistema", "Modo Oscuro", "Modo Claro"),
    EmergencyContactsEditorStrings(EmergencyContactsHeaderStrings("Volver", "SOS", "Contactos de emergencia", "Elige hasta cinco contactos y personaliza el mensaje de ayuda.", "Contactos", "Mensaje"), { "$it seleccionados" }, "Contactos disponibles", "Buscar contacto", "Mensaje SOS", "Escribe el mensaje que recibirán tus contactos.", "Guardar SOS", "Guardar"),
    "El cambio de contraseña se realiza desde «Olvidé mi contraseña» hasta que exista un contrato autenticado de actualización.",
    "No se pudo cargar el perfil.",
    "Reintentar",
)
