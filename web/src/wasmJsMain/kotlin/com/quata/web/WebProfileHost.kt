package com.quata.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.model.CountryPrefix
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.PlatformContact
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PreferenceStore
import com.quata.core.ui.components.QuataSavingButton
import com.quata.core.ui.components.QuataTextField
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileEditConfig
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.SecretQuestionOption
import com.quata.feature.profile.domain.UserProfile
import com.quata.feature.profile.presentation.EmergencyContactsDialogContent
import com.quata.feature.profile.presentation.EmergencyContactsDialogSlots
import com.quata.feature.profile.presentation.EmergencyContactsEditorStrings
import com.quata.feature.profile.presentation.EmergencyContactsHeaderStrings
import com.quata.feature.profile.presentation.EmergencyUserRowContent
import com.quata.feature.profile.presentation.ProfileOverviewAccountCardContent
import com.quata.feature.profile.presentation.ProfilePageLayoutContent
import com.quata.feature.profile.presentation.ProfileUiEvent
import com.quata.feature.profile.presentation.ProfileViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Browser composition for the shared Profile/SOS presentation. Profile mutations are persisted
 * locally through [PreferenceStore]; server-side profile writes remain deliberately outside this
 * launcher until WebPostgrestClient gains an authenticated mutation boundary.
 */
@Composable
fun WebProfileHost(
    repository: WebProfileRepository,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(repository) { ProfileViewModel(repository) }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var isSosOpen by remember { mutableStateOf(false) }
    var contactPickerMessage by remember { mutableStateOf<String?>(null) }
    val profile = state.profile

    Box(modifier.fillMaxSize()) {
        if (profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.isLoading) "Cargando perfil…" else state.errorMessage ?: "No se pudo cargar el perfil.")
            }
        } else {
            ProfilePageLayoutContent(
                isLandscapeLayout = false,
                scrollState = rememberScrollState(),
                content = {
                    Text("Mi perfil", fontWeight = FontWeight.ExtraBold)
                    ProfileOverviewAccountCardContent(
                        avatar = {
                            Text(
                                text = profile.displayName.take(1).uppercase().ifBlank { "Q" },
                                modifier = Modifier.size(56.dp).padding(16.dp),
                                fontWeight = FontWeight.ExtraBold,
                            )
                        },
                        actions = {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(profile.displayName, fontWeight = FontWeight.ExtraBold)
                                Text(profile.neighborhood.ifBlank { "Sin barrio configurado" })
                                OutlinedButton(onClick = { isSosOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Configurar contactos SOS")
                                }
                            }
                        },
                    )
                    QuataTextField(
                        value = profile.displayName,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.NameChanged(it)) },
                        label = "Nombre",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuataTextField(
                        value = profile.neighborhood,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.NeighborhoodChanged(it)) },
                        label = "Barrio",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuataTextField(
                        value = profile.phone,
                        onValueChange = { viewModel.onEvent(ProfileUiEvent.PhoneChanged(it)) },
                        label = "Teléfono",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuataSavingButton(
                        isSaving = state.isSaving,
                        savingText = "Guardando…",
                        actionText = "Guardar cambios",
                        onClick = { viewModel.onEvent(ProfileUiEvent.Save) },
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                contactPickerMessage = repository.importBrowserContacts().fold(
                                    onSuccess = { count -> if (count == 0) "No se seleccionaron contactos." else "$count contactos disponibles para SOS." },
                                    onFailure = { it.message ?: "No se pudieron importar los contactos." },
                                )
                                isSosOpen = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Importar contactos del dispositivo") }
                    contactPickerMessage?.let { Text(it) }
                    state.successMessage?.let { Text(it) }
                    state.errorMessage?.let { Text(it) }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isSosOpen && profile != null) {
            EmergencyContactsDialogContent(
                layoutPadding = PaddingValues(),
                isLandscapeLayout = false,
                isImeVisible = false,
                candidates = state.emergencyCandidates,
                selectedIds = profile.emergencyContactIds,
                message = profile.emergencyMessage,
                isSaving = state.isSaving,
                strings = WebEmergencyStrings,
                onMessageChange = { viewModel.onEvent(ProfileUiEvent.EmergencyMessageChanged(it)) },
                onToggleContact = { viewModel.onEvent(ProfileUiEvent.EmergencyContactToggled(it.id)) },
                onDismiss = { isSosOpen = false },
                onSave = { viewModel.onEvent(ProfileUiEvent.SaveEmergencySettings) },
                slots = EmergencyContactsDialogSlots(
                    contactRow = { contact, selected, toggle ->
                        EmergencyUserRowContent(
                            user = contact,
                            selected = selected,
                            addLabel = "Añadir",
                            removeLabel = "Quitar",
                            avatar = { Text(contact.displayName.take(1).uppercase().ifBlank { "Q" }) },
                            onToggle = toggle,
                        )
                    },
                    messageInput = { fieldModifier, value, onValueChange, minLines, maxLines ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = fieldModifier,
                            minLines = minLines,
                            maxLines = maxLines ?: Int.MAX_VALUE,
                        )
                    },
                ),
            )
        }
    }
}

/** Browser-local ProfileRepository with explicit contact-picker import, ready for server injection. */
class WebProfileRepository(
    private val preferences: PreferenceStore,
    private val contactPicker: ContactPickerService,
) : ProfileRepository {
    private val model = MutableStateFlow(defaultProfileModel())

    override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> = flow {
        refreshPersistedProfile()
        emitAll(model.map { Result.success(it) })
    }

    override suspend fun getProfileEditModel(): Result<ProfileEditModel> = runCatching {
        refreshPersistedProfile()
        model.value
    }

    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> = runCatching {
        val profile = UserProfile(
            displayName = update.displayName,
            neighborhood = update.neighborhood,
            countryCode = update.countryCode,
            phone = update.phone,
            avatarUri = update.avatarUri,
            selectedSecretQuestion = update.secretQuestion,
            emergencyContactIds = update.emergencyContactIds.distinct().take(MaxEmergencyContacts),
            emergencyMessage = update.emergencyMessage,
            emergencyMessageIsDefault = update.emergencyMessageIsDefault,
        )
        persist(profile)
        model.value = model.value.copy(profile = profile)
    }

    override suspend fun saveEmergencySettings(
        contactIds: List<String>,
        message: String,
        messageIsDefault: Boolean,
    ): Result<Unit> = runCatching {
        val profile = model.value.profile.copy(
            emergencyContactIds = contactIds.distinct().take(MaxEmergencyContacts),
            emergencyMessage = message,
            emergencyMessageIsDefault = messageIsDefault,
        )
        persist(profile)
        model.value = model.value.copy(profile = profile)
    }

    suspend fun importBrowserContacts(): Result<Int> = runCatching {
        when (val result = contactPicker.pickContacts()) {
            is PlatformResult.Success -> {
                val imported = result.value.mapIndexedNotNull { index, contact -> contact.toEmergencyCandidate(index) }
                val existing = model.value.config.emergencyCandidates.associateBy(EmergencyContactCandidate::id)
                val merged = (existing.values + imported.filterNot { existing.containsKey(it.id) })
                model.value = model.value.copy(config = model.value.config.copy(emergencyCandidates = merged))
                imported.size
            }
            PlatformResult.Cancelled -> 0
            PlatformResult.Unsupported -> throw UnsupportedOperationException("El selector de contactos no está disponible en este navegador.")
            is PlatformResult.Failure -> throw IllegalStateException(result.reason ?: "contact_picker_failed")
        }
    }

    override fun defaultEmergencyMessage(displayName: String): String =
        "Necesito ayuda. Por favor, contacta conmigo, $displayName."

    override fun changesSavedMessage(): String = "Cambios guardados."

    override fun emergencyContactsSavedMessage(): String = "Configuración SOS guardada."

    private suspend fun refreshPersistedProfile() {
        val current = model.value.profile
        val displayName = preferences.getString(WebProfileNameKey) ?: current.displayName
        val neighborhood = preferences.getString(WebProfileNeighborhoodKey) ?: current.neighborhood
        val phone = preferences.getString(WebProfilePhoneKey) ?: current.phone
        val contacts = preferences.getString(WebProfileEmergencyIdsKey)
            ?.split(',')
            ?.filter(String::isNotBlank)
            .orEmpty()
        val message = preferences.getString(WebProfileEmergencyMessageKey) ?: defaultEmergencyMessage(displayName)
        model.value = model.value.copy(
            profile = current.copy(
                displayName = displayName,
                neighborhood = neighborhood,
                phone = phone,
                emergencyContactIds = contacts,
                emergencyMessage = message,
                emergencyMessageIsDefault = preferences.getString(WebProfileEmergencyIsDefaultKey) != "false",
            ),
        )
    }

    private suspend fun persist(profile: UserProfile) {
        preferences.putString(WebProfileNameKey, profile.displayName)
        preferences.putString(WebProfileNeighborhoodKey, profile.neighborhood)
        preferences.putString(WebProfilePhoneKey, profile.phone)
        preferences.putString(WebProfileEmergencyIdsKey, profile.emergencyContactIds.joinToString(","))
        preferences.putString(WebProfileEmergencyMessageKey, profile.emergencyMessage)
        preferences.putString(WebProfileEmergencyIsDefaultKey, profile.emergencyMessageIsDefault.toString())
    }
}

private fun defaultProfileModel(): ProfileEditModel {
    val displayName = "Mi perfil"
    return ProfileEditModel(
        profile = UserProfile(
            displayName = displayName,
            neighborhood = "",
            countryCode = "240",
            phone = "",
            emergencyMessage = "Necesito ayuda. Por favor, contacta conmigo, $displayName.",
        ),
        config = ProfileEditConfig(
            countryPrefixes = listOf(
                CountryPrefix("240", "+240 - Guinea Ecuatorial"),
                CountryPrefix("34", "+34 - España"),
            ),
            secretQuestions = emptyList<SecretQuestionOption>(),
            emergencyCandidates = emptyList(),
        ),
    )
}

private fun PlatformContact.toEmergencyCandidate(index: Int): EmergencyContactCandidate? {
    val name = displayName?.trim()?.takeIf(String::isNotBlank)
        ?: phones.firstOrNull()?.trim()?.takeIf(String::isNotBlank)
        ?: emails.firstOrNull()?.trim()?.takeIf(String::isNotBlank)
        ?: return null
    val identity = phones.firstOrNull()?.filter(Char::isDigit)
        ?: emails.firstOrNull()?.trim()?.lowercase()
        ?: "$name-$index"
    return EmergencyContactCandidate(
        id = "browser-contact-$identity",
        displayName = name,
        email = emails.firstOrNull().orEmpty(),
        neighborhood = "Contacto del dispositivo",
        phone = phones.firstOrNull().orEmpty(),
    )
}

private val WebEmergencyStrings = EmergencyContactsEditorStrings(
    header = EmergencyContactsHeaderStrings(
        back = "Volver",
        sos = "SOS",
        title = "Contactos de emergencia",
        description = "Elige hasta cinco contactos y personaliza el mensaje de ayuda.",
        contactsTab = "Contactos",
        messageTab = "Mensaje",
    ),
    selectedCount = { "$it seleccionados" },
    networkUsers = "Contactos disponibles",
    searchPlaceholder = "Buscar contacto",
    messageTitle = "Mensaje SOS",
    messageHint = "Escribe el mensaje que recibirán tus contactos.",
    savePortrait = "Guardar SOS",
    saveLandscape = "Guardar",
)

private const val MaxEmergencyContacts = 5
private const val WebProfileNameKey = "web.profile.display_name"
private const val WebProfileNeighborhoodKey = "web.profile.neighborhood"
private const val WebProfilePhoneKey = "web.profile.phone"
private const val WebProfileEmergencyIdsKey = "web.profile.emergency_ids"
private const val WebProfileEmergencyMessageKey = "web.profile.emergency_message"
private const val WebProfileEmergencyIsDefaultKey = "web.profile.emergency_message_is_default"
