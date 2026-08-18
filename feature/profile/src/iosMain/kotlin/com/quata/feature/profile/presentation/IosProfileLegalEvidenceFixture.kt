package com.quata.feature.profile.presentation

import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.localization.QuataLanguage
import com.quata.core.model.CountryPrefix
import com.quata.core.moderation.LegalDocument
import com.quata.core.moderation.iosLegalDocumentFile
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileEditConfig
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.SecretQuestionOption
import com.quata.feature.profile.domain.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import platform.UIKit.UIViewController

/**
 * iOS UI evidence fixture for Cuenta legal links. It mounts the real shared Profile surface with
 * a local repository so the document opener can be verified without restoring Keychain state.
 */
fun QuataIosProfileLegalEvidenceViewController(
    languageCode: String?,
    onOpened: (String) -> Unit,
    forceSosSaveError: Boolean = false,
): UIViewController = QuataProfileViewController(
    IosProfileHostDependencies(
        repository = IosProfileLegalEvidenceRepository(forceSosSaveError),
        onLogout = {},
        onDeactivateAccount = {},
        onDeleteAccountData = {},
        filePicker = IosProfileLegalEvidenceFilePicker,
        touchFlowEnabled = true,
        onTouchFlowEnabledChange = {},
        themeMode = QuataThemeMode.Light,
        onThemeModeChange = {},
        languageCode = languageCode ?: "es",
        documentOpener = RecordingIosProfileLegalDocumentOpenService(onOpened),
        openLegalDocument = { document, opener ->
            val language = (languageCode ?: "es").toIosLegalEvidenceLanguage()
            iosLegalDocumentFile(document, language)?.let { file ->
                MainScope().launch { opener.open(file) }
            }
        },
    ),
)

private class RecordingIosProfileLegalDocumentOpenService(
    private val onOpened: (String) -> Unit,
) : DocumentOpenService {
    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        onOpened(file.displayName.orEmpty())
        return PlatformResult.Success(Unit)
    }
}

private object IosProfileLegalEvidenceFilePicker : FilePickerService {
    override suspend fun pickFiles(
        acceptedMimeTypes: List<String>,
        allowMultiple: Boolean,
    ): PlatformResult<List<PlatformFile>> = PlatformResult.Unsupported

    override suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> =
        PlatformResult.Unsupported
}

private class IosProfileLegalEvidenceRepository(
    private val forceSosSaveError: Boolean,
) : ProfileRepository {
    private val model = ProfileEditModel(
        profile = UserProfile(
            displayName = "Gabrielo",
            neighborhood = "Bovano",
            countryCode = "240",
            phone = "680242607",
            avatarUri = null,
            selectedSecretQuestion = "",
            emergencyContactIds = emptyList(),
            emergencyMessage = "Avisar a mis contactos de emergencia.",
            emergencyMessageIsDefault = true,
        ),
        config = ProfileEditConfig(
            countryPrefixes = listOf(CountryPrefix("240", "+240 - Guinea Ecuatorial")),
            secretQuestions = listOf(SecretQuestionOption("", "Mantener pregunta actual")),
            emergencyCandidates = (1..6).map { index ->
                EmergencyContactCandidate(
                    id = "sos-fixture-$index",
                    displayName = if (index == 1) "Gabrielu" else "Contacto SOS $index",
                    email = "sos-$index@example.invalid",
                    neighborhood = "Bovano",
                    phone = "+24068024260$index",
                )
            },
        ),
    )

    override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> =
        flowOf(Result.success(model))

    override suspend fun getProfileEditModel(): Result<ProfileEditModel> =
        Result.success(model)

    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> =
        Result.success(Unit)

    override suspend fun saveEmergencySettings(
        contactIds: List<String>,
        message: String,
        messageIsDefault: Boolean,
    ): Result<Unit> =
        if (forceSosSaveError) {
            Result.failure(IllegalStateException("ios_profile_sos_save_failed"))
        } else {
            Result.success(Unit)
        }

    override fun defaultEmergencyMessage(displayName: String): String =
        "Avisar a mis contactos de emergencia."

    override fun changesSavedMessage(): String = "Cambios guardados."

    override fun emergencyContactsSavedMessage(): String = "Contactos guardados."
}

private fun String.toIosLegalEvidenceLanguage(): QuataLanguage = when {
    lowercase().startsWith("es") -> QuataLanguage.Spanish
    lowercase().startsWith("fr") -> QuataLanguage.French
    else -> QuataLanguage.English
}
