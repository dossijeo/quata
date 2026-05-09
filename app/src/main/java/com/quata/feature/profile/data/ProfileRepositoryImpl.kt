package com.quata.feature.profile.data

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
    private val sessionManager: SessionManager
) : ProfileRepository {
    private var mockProfile: UserProfile? = null

    override suspend fun getProfileEditModel(): Result<ProfileEditModel> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching ProfileEditModel(
                profile = mockProfile ?: buildMockProfile(),
                config = mockProfileConfig
            )
        }

        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val profile = remote.getProfile(session.userId)?.toProfile(session.displayName) ?: buildMockProfile()
        val candidates = remote.getEmergencyCandidates().map { it.toEmergencyCandidate() }
        ProfileEditModel(
            profile = profile,
            config = mockProfileConfig.copy(
                emergencyCandidates = candidates.ifEmpty { mockProfileConfig.emergencyCandidates }
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
        "🚨 SOS REAL: ${displayName.ifBlank { "Usuario" }} necesita ayuda urgente. Eres uno de sus contactos de emergencia en QUATA."
}

private val mockProfileConfig = ProfileEditConfig(
    countryPrefixes = listOf(
        CountryPrefix("240", "+240 - Guinea Ecuatorial"),
        CountryPrefix("1", "+1 - Estados Unidos / Canada"),
        CountryPrefix("7", "+7 - Rusia / Kazajistan"),
        CountryPrefix("20", "+20 - Egipto"),
        CountryPrefix("27", "+27 - Sudafrica"),
        CountryPrefix("30", "+30 - Grecia"),
        CountryPrefix("31", "+31 - Paises Bajos"),
        CountryPrefix("32", "+32 - Belgica"),
        CountryPrefix("33", "+33 - Francia"),
        CountryPrefix("34", "+34 - Espana"),
        CountryPrefix("36", "+36 - Hungria"),
        CountryPrefix("39", "+39 - Italia"),
        CountryPrefix("40", "+40 - Rumania"),
        CountryPrefix("41", "+41 - Suiza"),
        CountryPrefix("43", "+43 - Austria"),
        CountryPrefix("44", "+44 - Reino Unido"),
        CountryPrefix("45", "+45 - Dinamarca"),
        CountryPrefix("46", "+46 - Suecia"),
        CountryPrefix("47", "+47 - Noruega"),
        CountryPrefix("48", "+48 - Polonia"),
        CountryPrefix("49", "+49 - Alemania"),
        CountryPrefix("51", "+51 - Peru"),
        CountryPrefix("52", "+52 - Mexico"),
        CountryPrefix("53", "+53 - Cuba"),
        CountryPrefix("54", "+54 - Argentina"),
        CountryPrefix("55", "+55 - Brasil"),
        CountryPrefix("56", "+56 - Chile"),
        CountryPrefix("57", "+57 - Colombia"),
        CountryPrefix("58", "+58 - Venezuela"),
        CountryPrefix("60", "+60 - Malasia"),
        CountryPrefix("61", "+61 - Australia"),
        CountryPrefix("62", "+62 - Indonesia"),
        CountryPrefix("63", "+63 - Filipinas"),
        CountryPrefix("64", "+64 - Nueva Zelanda"),
        CountryPrefix("65", "+65 - Singapur"),
        CountryPrefix("66", "+66 - Tailandia"),
        CountryPrefix("81", "+81 - Japon"),
        CountryPrefix("82", "+82 - Corea del Sur"),
        CountryPrefix("84", "+84 - Vietnam"),
        CountryPrefix("86", "+86 - China"),
        CountryPrefix("90", "+90 - Turquia"),
        CountryPrefix("91", "+91 - India"),
        CountryPrefix("92", "+92 - Pakistan"),
        CountryPrefix("93", "+93 - Afganistan"),
        CountryPrefix("94", "+94 - Sri Lanka"),
        CountryPrefix("95", "+95 - Myanmar"),
        CountryPrefix("98", "+98 - Iran"),
        CountryPrefix("211", "+211 - Sudan del Sur"),
        CountryPrefix("212", "+212 - Marruecos"),
        CountryPrefix("213", "+213 - Argelia"),
        CountryPrefix("216", "+216 - Tunez"),
        CountryPrefix("218", "+218 - Libia"),
        CountryPrefix("220", "+220 - Gambia"),
        CountryPrefix("221", "+221 - Senegal"),
        CountryPrefix("222", "+222 - Mauritania"),
        CountryPrefix("223", "+223 - Mali"),
        CountryPrefix("224", "+224 - Guinea"),
        CountryPrefix("225", "+225 - Costa de Marfil"),
        CountryPrefix("226", "+226 - Burkina Faso"),
        CountryPrefix("227", "+227 - Niger"),
        CountryPrefix("228", "+228 - Togo"),
        CountryPrefix("229", "+229 - Benin"),
        CountryPrefix("230", "+230 - Mauricio"),
        CountryPrefix("231", "+231 - Liberia"),
        CountryPrefix("232", "+232 - Sierra Leona"),
        CountryPrefix("233", "+233 - Ghana"),
        CountryPrefix("234", "+234 - Nigeria"),
        CountryPrefix("235", "+235 - Chad"),
        CountryPrefix("236", "+236 - Republica Centroafricana"),
        CountryPrefix("237", "+237 - Camerun"),
        CountryPrefix("238", "+238 - Cabo Verde"),
        CountryPrefix("239", "+239 - Santo Tome y Principe"),
        CountryPrefix("241", "+241 - Gabon"),
        CountryPrefix("242", "+242 - Republica del Congo"),
        CountryPrefix("243", "+243 - Republica Democratica del Congo"),
        CountryPrefix("244", "+244 - Angola"),
        CountryPrefix("245", "+245 - Guinea-Bisau"),
        CountryPrefix("246", "+246 - Territorio Britanico del Oceano Indico"),
        CountryPrefix("248", "+248 - Seychelles"),
        CountryPrefix("249", "+249 - Sudan"),
        CountryPrefix("250", "+250 - Ruanda"),
        CountryPrefix("251", "+251 - Etiopia"),
        CountryPrefix("252", "+252 - Somalia"),
        CountryPrefix("253", "+253 - Yibuti"),
        CountryPrefix("254", "+254 - Kenia"),
        CountryPrefix("255", "+255 - Tanzania"),
        CountryPrefix("256", "+256 - Uganda"),
        CountryPrefix("257", "+257 - Burundi"),
        CountryPrefix("258", "+258 - Mozambique"),
        CountryPrefix("260", "+260 - Zambia"),
        CountryPrefix("261", "+261 - Madagascar"),
        CountryPrefix("262", "+262 - Reunion / Mayotte"),
        CountryPrefix("263", "+263 - Zimbabue"),
        CountryPrefix("264", "+264 - Namibia"),
        CountryPrefix("265", "+265 - Malaui"),
        CountryPrefix("266", "+266 - Lesoto"),
        CountryPrefix("267", "+267 - Botsuana"),
        CountryPrefix("268", "+268 - Esuatini"),
        CountryPrefix("269", "+269 - Comoras"),
        CountryPrefix("290", "+290 - Santa Elena"),
        CountryPrefix("291", "+291 - Eritrea"),
        CountryPrefix("297", "+297 - Aruba"),
        CountryPrefix("298", "+298 - Islas Feroe"),
        CountryPrefix("299", "+299 - Groenlandia"),
        CountryPrefix("350", "+350 - Gibraltar"),
        CountryPrefix("351", "+351 - Portugal"),
        CountryPrefix("352", "+352 - Luxemburgo"),
        CountryPrefix("353", "+353 - Irlanda"),
        CountryPrefix("354", "+354 - Islandia"),
        CountryPrefix("355", "+355 - Albania"),
        CountryPrefix("356", "+356 - Malta"),
        CountryPrefix("357", "+357 - Chipre"),
        CountryPrefix("358", "+358 - Finlandia"),
        CountryPrefix("359", "+359 - Bulgaria"),
        CountryPrefix("370", "+370 - Lituania"),
        CountryPrefix("371", "+371 - Letonia"),
        CountryPrefix("372", "+372 - Estonia"),
        CountryPrefix("373", "+373 - Moldavia"),
        CountryPrefix("374", "+374 - Armenia"),
        CountryPrefix("375", "+375 - Bielorrusia"),
        CountryPrefix("376", "+376 - Andorra"),
        CountryPrefix("377", "+377 - Monaco"),
        CountryPrefix("378", "+378 - San Marino"),
        CountryPrefix("380", "+380 - Ucrania"),
        CountryPrefix("381", "+381 - Serbia"),
        CountryPrefix("382", "+382 - Montenegro"),
        CountryPrefix("383", "+383 - Kosovo"),
        CountryPrefix("385", "+385 - Croacia"),
        CountryPrefix("386", "+386 - Eslovenia"),
        CountryPrefix("387", "+387 - Bosnia y Herzegovina"),
        CountryPrefix("389", "+389 - Macedonia del Norte"),
        CountryPrefix("420", "+420 - Republica Checa"),
        CountryPrefix("421", "+421 - Eslovaquia"),
        CountryPrefix("423", "+423 - Liechtenstein"),
        CountryPrefix("500", "+500 - Islas Malvinas"),
        CountryPrefix("501", "+501 - Belice"),
        CountryPrefix("502", "+502 - Guatemala"),
        CountryPrefix("503", "+503 - El Salvador"),
        CountryPrefix("504", "+504 - Honduras"),
        CountryPrefix("505", "+505 - Nicaragua"),
        CountryPrefix("506", "+506 - Costa Rica"),
        CountryPrefix("507", "+507 - Panama"),
        CountryPrefix("508", "+508 - San Pedro y Miquelon"),
        CountryPrefix("509", "+509 - Haiti"),
        CountryPrefix("590", "+590 - Guadalupe / San Martin"),
        CountryPrefix("591", "+591 - Bolivia"),
        CountryPrefix("592", "+592 - Guyana"),
        CountryPrefix("593", "+593 - Ecuador"),
        CountryPrefix("594", "+594 - Guayana Francesa"),
        CountryPrefix("595", "+595 - Paraguay"),
        CountryPrefix("596", "+596 - Martinica"),
        CountryPrefix("597", "+597 - Surinam"),
        CountryPrefix("598", "+598 - Uruguay"),
        CountryPrefix("599", "+599 - Curazao / Caribe Neerlandes"),
        CountryPrefix("670", "+670 - Timor Oriental"),
        CountryPrefix("672", "+672 - Territorios Australes"),
        CountryPrefix("673", "+673 - Brunei"),
        CountryPrefix("674", "+674 - Nauru"),
        CountryPrefix("675", "+675 - Papua Nueva Guinea"),
        CountryPrefix("676", "+676 - Tonga"),
        CountryPrefix("677", "+677 - Islas Salomon"),
        CountryPrefix("678", "+678 - Vanuatu"),
        CountryPrefix("679", "+679 - Fiyi"),
        CountryPrefix("680", "+680 - Palaos"),
        CountryPrefix("681", "+681 - Wallis y Futuna"),
        CountryPrefix("682", "+682 - Islas Cook"),
        CountryPrefix("683", "+683 - Niue"),
        CountryPrefix("685", "+685 - Samoa"),
        CountryPrefix("686", "+686 - Kiribati"),
        CountryPrefix("687", "+687 - Nueva Caledonia"),
        CountryPrefix("688", "+688 - Tuvalu"),
        CountryPrefix("689", "+689 - Polinesia Francesa"),
        CountryPrefix("690", "+690 - Tokelau"),
        CountryPrefix("691", "+691 - Micronesia"),
        CountryPrefix("692", "+692 - Islas Marshall"),
        CountryPrefix("850", "+850 - Corea del Norte"),
        CountryPrefix("852", "+852 - Hong Kong"),
        CountryPrefix("853", "+853 - Macao"),
        CountryPrefix("855", "+855 - Camboya"),
        CountryPrefix("856", "+856 - Laos"),
        CountryPrefix("880", "+880 - Banglades"),
        CountryPrefix("886", "+886 - Taiwan"),
        CountryPrefix("960", "+960 - Maldivas"),
        CountryPrefix("961", "+961 - Libano"),
        CountryPrefix("962", "+962 - Jordania"),
        CountryPrefix("963", "+963 - Siria"),
        CountryPrefix("964", "+964 - Irak"),
        CountryPrefix("965", "+965 - Kuwait"),
        CountryPrefix("966", "+966 - Arabia Saudita"),
        CountryPrefix("967", "+967 - Yemen"),
        CountryPrefix("968", "+968 - Oman"),
        CountryPrefix("970", "+970 - Palestina"),
        CountryPrefix("971", "+971 - Emiratos Arabes Unidos"),
        CountryPrefix("972", "+972 - Israel"),
        CountryPrefix("973", "+973 - Barein"),
        CountryPrefix("974", "+974 - Catar"),
        CountryPrefix("975", "+975 - Butan"),
        CountryPrefix("976", "+976 - Mongolia"),
        CountryPrefix("977", "+977 - Nepal"),
        CountryPrefix("992", "+992 - Tayikistan"),
        CountryPrefix("993", "+993 - Turkmenistan"),
        CountryPrefix("994", "+994 - Azerbaiyan"),
        CountryPrefix("995", "+995 - Georgia"),
        CountryPrefix("996", "+996 - Kirguistan"),
        CountryPrefix("998", "+998 - Uzbekistan")
    ),
    secretQuestions = listOf(
        SecretQuestionOption("", "Mantener pregunta secreta actual"),
        SecretQuestionOption("madre", "¿Como se llama tu madre?"),
        SecretQuestionOption("barrio", "¿En que barrio creciste?"),
        SecretQuestionOption("amigo", "¿Nombre de tu mejor amigo?"),
        SecretQuestionOption("comida", "¿Tu comida favorita?")
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
