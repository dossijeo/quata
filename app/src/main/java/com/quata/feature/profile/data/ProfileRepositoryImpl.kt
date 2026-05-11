package com.quata.feature.profile.data

import android.content.Context
import com.quata.R
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.network.supabase.SupabaseProfileDto
import com.quata.core.network.supabase.SupabaseProfileUpdateRequest
import com.quata.core.session.SessionManager
import com.quata.feature.profile.domain.CountryPrefix
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileEditConfig
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.SecretQuestionOption
import com.quata.feature.profile.domain.UserProfile

class ProfileRepositoryImpl(
    private val remote: ProfileRemoteDataSource,
    private val sessionManager: SessionManager,
    private val context: Context
) : ProfileRepository {
    private var mockProfile: UserProfile? = null

    override suspend fun getProfileEditModel(): Result<ProfileEditModel> = runCatching {
        val config = buildProfileConfig(context)
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching ProfileEditModel(
                profile = mockProfile ?: buildMockProfile(),
                config = config
            )
        }

        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val profile = remote.getProfile(session.userId)?.toProfile(session.displayName) ?: buildMockProfile()
        val candidates = remote.getEmergencyCandidates().map { it.toEmergencyCandidate() }
        ProfileEditModel(
            profile = profile,
            config = config.copy(
                emergencyCandidates = candidates.ifEmpty { config.emergencyCandidates }
            )
        )
    }

    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> = runCatching {
        if (!AppConfig.USE_MOCK_BACKEND) {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            remote.saveProfile(session.userId, update.toRemoteRequest())
            return@runCatching
        }

        mockProfile = UserProfile(
            displayName = update.displayName,
            neighborhood = update.neighborhood,
            countryCode = update.countryCode,
            phone = update.phone,
            avatarUri = update.avatarUri,
            selectedSecretQuestion = update.secretQuestion,
            emergencyContactIds = update.emergencyContactIds,
            emergencyMessage = update.emergencyMessage,
            emergencyMessageIsDefault = update.emergencyMessageIsDefault
        )
    }

    private fun buildMockProfile(): UserProfile {
        val session = sessionManager.currentSession()
        val displayName = session?.displayName ?: MockData.currentUser.displayName
        return UserProfile(
            displayName = displayName,
            neighborhood = "La Chana",
            countryCode = "240",
            phone = "680242606",
            avatarUri = null,
            selectedSecretQuestion = "",
            emergencyContactIds = emptyList(),
            emergencyMessage = defaultEmergencyMessage(displayName),
            emergencyMessageIsDefault = true
        )
    }

    private fun SupabaseProfileDto.toProfile(fallbackName: String): UserProfile {
        val displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName
        return UserProfile(
            displayName = displayName,
            neighborhood = neighborhood.orEmpty(),
            countryCode = countryCode?.takeIf { it.isNotBlank() } ?: "240",
            phone = phone.orEmpty(),
            avatarUri = avatarUrl,
            selectedSecretQuestion = secretQuestion.orEmpty(),
            emergencyContactIds = emergencyContactIds.orEmpty(),
            emergencyMessage = emergencyMessage?.takeIf { it.isNotBlank() }
                ?: defaultEmergencyMessage(displayName),
            emergencyMessageIsDefault = emergencyMessageIsDefault ?: emergencyMessage.isNullOrBlank()
        )
    }

    private fun SupabaseProfileDto.toEmergencyCandidate(): EmergencyContactCandidate =
        EmergencyContactCandidate(
            id = id,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: email.orEmpty(),
            email = email.orEmpty(),
            neighborhood = neighborhood.orEmpty(),
            phone = phone.orEmpty()
        )

    private fun ProfileUpdate.toRemoteRequest(): SupabaseProfileUpdateRequest =
        SupabaseProfileUpdateRequest(
            displayName = displayName,
            neighborhood = neighborhood,
            countryCode = countryCode,
            phone = phone,
            avatarUrl = avatarUri,
            secretQuestion = secretQuestion,
            secretAnswer = secretAnswer.takeIf { it.isNotBlank() },
            emergencyContactIds = emergencyContactIds,
            emergencyMessage = emergencyMessage,
            emergencyMessageIsDefault = emergencyMessageIsDefault
        )

    private fun defaultEmergencyMessage(displayName: String): String =
        context.getString(
            R.string.sos_default_message,
            displayName.ifBlank { context.getString(R.string.user_fallback_name) }
        )
}

private fun buildProfileConfig(context: Context): ProfileEditConfig =
    ProfileEditConfig(
        countryPrefixes = context.countryPrefixes(),
        secretQuestions = listOf(
            SecretQuestionOption("", context.getString(R.string.secret_question_keep_current)),
            SecretQuestionOption("madre", context.getString(R.string.secret_question_mother)),
            SecretQuestionOption("barrio", context.getString(R.string.secret_question_neighborhood)),
            SecretQuestionOption("amigo", context.getString(R.string.secret_question_friend)),
            SecretQuestionOption("comida", context.getString(R.string.secret_question_food))
        ),
        emergencyCandidates = listOf(
            EmergencyContactCandidate("u_ji", "JI", "ji@quata.app", "La ferme"),
            EmergencyContactCandidate("u_marcelino", "Marcelino", "marcelino@quata.app", "Molyko"),
            EmergencyContactCandidate("u_obiang", "Obiang", "obiang@quata.app", "Mindoube"),
            EmergencyContactCandidate("u_maribel", "maribelamdemeekandoh", "maribel@quata.app", "Iyubu"),
            EmergencyContactCandidate("u_ana", "Ana", "ana@quata.app", "Sampaka"),
            EmergencyContactCandidate("u_leo", "Leo", "leo@quata.app", "Bikuy"),
            EmergencyContactCandidate("u_sara", "Sara", "sara@quata.app", "Malabo")
        )
    )

private fun Context.countryPrefixes(): List<CountryPrefix> {
    val codes = resources.getStringArray(R.array.country_prefix_codes)
    val labels = resources.getStringArray(R.array.country_prefix_labels)
    require(codes.size == labels.size) { "Country prefix resources must have the same size" }
    return codes.zip(labels) { code, label -> CountryPrefix(code, label) }
}
