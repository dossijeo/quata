package com.quata.feature.auth.presentation

import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.presentation.login.LoginFormStrings
import com.quata.feature.auth.presentation.recovery.ForgotPasswordFormStrings
import com.quata.feature.auth.presentation.register.RegisterSecretQuestion

/**
 * Canonical portable copy for Auth forms. Android, Web and iOS consume this catalogue instead
 * of maintaining their own phone-prefix and recovery-question datasets.
 *
 * Android selects the same English, Spanish or French copy that was previously stored in its
 * resources. Hosts can inject another [AuthCatalogLocale] without changing Auth presentation.
 */
enum class AuthCatalogLocale {
    English,
    Spanish,
    French;

    companion object {
        fun fromLanguage(language: String?): AuthCatalogLocale = when (language?.lowercase()) {
            "es" -> Spanish
            "fr" -> French
            else -> English
        }
    }
}

data class AuthCatalogCopy(
    val loginSubtitle: String,
    val login: LoginFormStrings,
    val recoverySubtitle: String,
    val recovery: ForgotPasswordFormStrings,
    val recoveryQuestionWaiting: String,
    val recoveryQuestionLoading: String,
    val passwordUpdatedMessage: String,
    val register: AuthRegisterCatalogCopy,
    val secretQuestions: List<RegisterSecretQuestion>,
    val profileKeepCurrentSecretQuestion: String,
)

data class AuthRegisterCatalogCopy(
    val title: String,
    val displayName: String,
    val neighborhood: String,
    val secretAnswer: String,
    val creating: String,
    val createAccount: String,
    val back: String,
)

/**
 * The one portable Auth source. The country rows are migrated verbatim from Android's former
 * `country_prefix_codes` and `country_prefix_labels` resources, preserving order and labels.
 */
object AuthCatalog {
    val countryPrefixes: List<CountryPrefix> by lazy {
        countryPrefixes(AuthCatalogLocale.English)
    }

    fun countryPrefixes(locale: AuthCatalogLocale): List<CountryPrefix> =
        countryPrefixRows(locale).lineSequence()
            .filter(String::isNotBlank)
            .map { row ->
                val separator = row.indexOf('|')
                require(separator > 0) { "Invalid Auth country-prefix row" }
                CountryPrefix(
                    code = row.substring(0, separator),
                    label = row.substring(separator + 1),
                )
            }
            .toList()

    fun copy(locale: AuthCatalogLocale = AuthCatalogLocale.English): AuthCatalogCopy = when (locale) {
        AuthCatalogLocale.English -> EnglishCopy
        AuthCatalogLocale.Spanish -> SpanishCopy
        AuthCatalogLocale.French -> FrenchCopy
    }

    private val EnglishCopy = authCopy(
        loginSubtitle = "Connect, post, and chat",
        phone = "Phone",
        password = "Password",
        signingIn = "Signing in…",
        signIn = "Sign in",
        forgotPassword = "Forgot my password",
        createAccount = "Create account",
        mockNotice = "Mock mode is active by default. Configure AppConfig to use the real backend.",
        searchPrefix = "Search prefix",
        recoverySubtitle = "Recover password",
        yourPhone = "Your phone",
        yourSecretQuestion = "Your secret question",
        yourSecretAnswer = "Your secret answer",
        newPassword = "New password",
        saving = "Saving…",
        updatePassword = "Update password",
        back = "Back",
        recoveryQuestionWaiting = "Enter a registered phone",
        recoveryQuestionLoading = "Loading secret question…",
        passwordUpdatedMessage = "Password updated",
        registerTitle = "Create your account",
        displayName = "Name",
        neighborhood = "Your district and community",
        creating = "Creating…",
        keepCurrentSecretQuestion = "Keep current secret question",
        selectSecretQuestion = "Select a secret question",
        mother = "What is your mother's name?",
        neighborhoodQuestion = "Which district did you grow up in?",
        friend = "Name of your best friend?",
        food = "Your favorite food?",
    )

    private val SpanishCopy = authCopy(
        loginSubtitle = "Conecta, publica y conversa",
        phone = "Teléfono",
        password = "Contraseña",
        signingIn = "Entrando…",
        signIn = "Entrar",
        forgotPassword = "Olvidé mi contraseña",
        createAccount = "Crear cuenta",
        mockNotice = "Modo mock activo por defecto. Configura AppConfig para usar el backend real.",
        searchPrefix = "Buscar prefijo",
        recoverySubtitle = "Recuperar contraseña",
        yourPhone = "Tu teléfono",
        yourSecretQuestion = "Tu pregunta secreta",
        yourSecretAnswer = "Tu respuesta secreta",
        newPassword = "Nueva contraseña",
        saving = "Guardando…",
        updatePassword = "Actualizar contraseña",
        back = "Volver",
        recoveryQuestionWaiting = "Introduce un teléfono registrado",
        recoveryQuestionLoading = "Cargando pregunta secreta…",
        passwordUpdatedMessage = "Contraseña actualizada",
        registerTitle = "Crea tu cuenta",
        displayName = "Nombre",
        neighborhood = "Tu barrio y comunidad",
        creating = "Creando…",
        keepCurrentSecretQuestion = "Mantener pregunta secreta actual",
        selectSecretQuestion = "Selecciona una pregunta secreta",
        mother = "¿Cómo se llama tu madre?",
        neighborhoodQuestion = "¿En qué barrio creciste?",
        friend = "¿Nombre de tu mejor amigo?",
        food = "¿Tu comida favorita?",
    )

    private val FrenchCopy = authCopy(
        loginSubtitle = "Connecte, publie et discute",
        phone = "Telephone",
        password = "Mot de passe",
        signingIn = "Connexion…",
        signIn = "Entrer",
        forgotPassword = "Mot de passe oublie",
        createAccount = "Creer compte",
        mockNotice = "Mode mock actif par defaut. Configure AppConfig pour utiliser le backend reel.",
        searchPrefix = "Chercher prefixe",
        recoverySubtitle = "Recuperer le mot de passe",
        yourPhone = "Ton telephone",
        yourSecretQuestion = "Ta question secrete",
        yourSecretAnswer = "Ta reponse secrete",
        newPassword = "Nouveau mot de passe",
        saving = "Enregistrement…",
        updatePassword = "Actualiser le mot de passe",
        back = "Retour",
        recoveryQuestionWaiting = "Saisis un telephone enregistre",
        recoveryQuestionLoading = "Chargement de la question secrete…",
        passwordUpdatedMessage = "Mot de passe actualise",
        registerTitle = "Cree ton compte",
        displayName = "Nom",
        neighborhood = "Ton quartier et ta communaute",
        creating = "Creation…",
        keepCurrentSecretQuestion = "Garder la question secrete actuelle",
        selectSecretQuestion = "Selectionne une question secrete",
        mother = "Quel est le nom de ta mere ?",
        neighborhoodQuestion = "Dans quel quartier as-tu grandi ?",
        friend = "Nom de ton meilleur ami ?",
        food = "Ton plat prefere ?",
    )
}

private fun countryPrefixRows(locale: AuthCatalogLocale): String = when (locale) {
    AuthCatalogLocale.Spanish -> SpanishCountryPrefixRows
    AuthCatalogLocale.English,
    AuthCatalogLocale.French,
    -> EnglishCountryPrefixRows
}

@Suppress("LongParameterList")
private fun authCopy(
    loginSubtitle: String,
    phone: String,
    password: String,
    signingIn: String,
    signIn: String,
    forgotPassword: String,
    createAccount: String,
    mockNotice: String,
    searchPrefix: String,
    recoverySubtitle: String,
    yourPhone: String,
    yourSecretQuestion: String,
    yourSecretAnswer: String,
    newPassword: String,
    saving: String,
    updatePassword: String,
    back: String,
    recoveryQuestionWaiting: String,
    recoveryQuestionLoading: String,
    passwordUpdatedMessage: String,
    registerTitle: String,
    displayName: String,
    neighborhood: String,
    creating: String,
    keepCurrentSecretQuestion: String,
    selectSecretQuestion: String,
    mother: String,
    neighborhoodQuestion: String,
    friend: String,
    food: String,
): AuthCatalogCopy = AuthCatalogCopy(
    loginSubtitle = loginSubtitle,
    login = LoginFormStrings(
        phone = phone,
        password = password,
        signingIn = signingIn,
        signIn = signIn,
        forgotPassword = forgotPassword,
        createAccount = createAccount,
        searchPrefix = searchPrefix,
        mockNotice = mockNotice,
    ),
    recoverySubtitle = recoverySubtitle,
    recovery = ForgotPasswordFormStrings(
        phone = yourPhone,
        searchPrefix = searchPrefix,
        secretQuestion = yourSecretQuestion,
        secretAnswer = yourSecretAnswer,
        newPassword = newPassword,
        saving = saving,
        updatePassword = updatePassword,
        back = back,
    ),
    recoveryQuestionWaiting = recoveryQuestionWaiting,
    recoveryQuestionLoading = recoveryQuestionLoading,
    passwordUpdatedMessage = passwordUpdatedMessage,
    register = AuthRegisterCatalogCopy(
        title = registerTitle,
        displayName = displayName,
        neighborhood = neighborhood,
        secretAnswer = yourSecretAnswer,
        creating = creating,
        createAccount = createAccount,
        back = back,
    ),
    secretQuestions = listOf(
        RegisterSecretQuestion("", selectSecretQuestion),
        RegisterSecretQuestion("madre", mother),
        RegisterSecretQuestion("barrio", neighborhoodQuestion),
        RegisterSecretQuestion("amigo", friend),
        RegisterSecretQuestion("comida", food),
    ),
    profileKeepCurrentSecretQuestion = keepCurrentSecretQuestion,
)

private const val EnglishCountryPrefixRows = """
240|+240 — Equatorial Guinea
1|+1 — United States / Canada
7|+7 — Russia / Kazakhstan
20|+20 — Egypt
27|+27 — South Africa
30|+30 — Greece
31|+31 — Netherlands
32|+32 — Belgium
33|+33 — France
34|+34 — Spain
36|+36 — Hungary
39|+39 — Italy
40|+40 — Romania
41|+41 — Switzerland
43|+43 — Austria
44|+44 — United Kingdom
45|+45 — Denmark
46|+46 — Sweden
47|+47 — Norway
48|+48 — Poland
49|+49 — Germany
51|+51 — Peru
52|+52 — Mexico
53|+53 — Cuba
54|+54 — Argentina
55|+55 — Brazil
56|+56 — Chile
57|+57 — Colombia
58|+58 — Venezuela
60|+60 — Malaysia
61|+61 — Australia
62|+62 — Indonesia
63|+63 — Philippines
64|+64 — New Zealand
65|+65 — Singapore
66|+66 — Thailand
81|+81 — Japan
82|+82 — South Korea
84|+84 — Vietnam
86|+86 — China
90|+90 — Turkey
91|+91 — India
92|+92 — Pakistan
93|+93 — Afghanistan
94|+94 — Sri Lanka
95|+95 — Myanmar
98|+98 — Iran
211|+211 — South Sudan
212|+212 — Morocco
213|+213 — Algeria
216|+216 — Tunisia
218|+218 — Libya
220|+220 — Gambia
221|+221 — Senegal
222|+222 — Mauritania
223|+223 — Mali
224|+224 — Guinea
225|+225 — Ivory Coast
226|+226 — Burkina Faso
227|+227 — Niger
228|+228 — Togo
229|+229 — Benin
230|+230 — Mauritius
231|+231 — Liberia
232|+232 — Sierra Leone
233|+233 — Ghana
234|+234 — Nigeria
235|+235 — Chad
236|+236 — Central African Republic
237|+237 — Cameroon
238|+238 — Cape Verde
239|+239 — Sao Tome and Principe
241|+241 — Gabon
242|+242 — Republic of the Congo
243|+243 — Democratic Republic of the Congo
244|+244 — Angola
245|+245 — Guinea-Bissau
246|+246 — British Indian Ocean Territory
248|+248 — Seychelles
249|+249 — Sudan
250|+250 — Rwanda
251|+251 — Ethiopia
252|+252 — Somalia
253|+253 — Djibouti
254|+254 — Kenya
255|+255 — Tanzania
256|+256 — Uganda
257|+257 — Burundi
258|+258 — Mozambique
260|+260 — Zambia
261|+261 — Madagascar
262|+262 — Réunion / Mayotte
263|+263 — Zimbabwe
264|+264 — Namibia
265|+265 — Malawi
266|+266 — Lesotho
267|+267 — Botswana
268|+268 — Eswatini
269|+269 — Comoros
290|+290 — Saint Helena
291|+291 — Eritrea
297|+297 — Aruba
298|+298 — Faroe Islands
299|+299 — Greenland
350|+350 — Gibraltar
351|+351 — Portugal
352|+352 — Luxembourg
353|+353 — Ireland
354|+354 — Iceland
355|+355 — Albania
356|+356 — Malta
357|+357 — Cyprus
358|+358 — Finland
359|+359 — Bulgaria
370|+370 — Lithuania
371|+371 — Latvia
372|+372 — Estonia
373|+373 — Moldova
374|+374 — Armenia
375|+375 — Belarus
376|+376 — Andorra
377|+377 — Monaco
378|+378 — San Marino
380|+380 — Ukraine
381|+381 — Serbia
382|+382 — Montenegro
383|+383 — Kosovo
385|+385 — Croatia
386|+386 — Slovenia
387|+387 — Bosnia and Herzegovina
389|+389 — North Macedonia
420|+420 — Czech Republic
421|+421 — Slovakia
423|+423 — Liechtenstein
500|+500 — Falkland Islands
501|+501 — Belize
502|+502 — Guatemala
503|+503 — El Salvador
504|+504 — Honduras
505|+505 — Nicaragua
506|+506 — Costa Rica
507|+507 — Panama
508|+508 — Saint Pierre and Miquelon
509|+509 — Haiti
590|+590 — Guadeloupe / Saint Martin
591|+591 — Bolivia
592|+592 — Guyana
593|+593 — Ecuador
594|+594 — French Guiana
595|+595 — Paraguay
596|+596 — Martinique
597|+597 — Suriname
598|+598 — Uruguay
599|+599 — Curaçao / Caribbean Netherlands
670|+670 — East Timor
672|+672 — Australian External Territories
673|+673 — Brunei
674|+674 — Nauru
675|+675 — Papua New Guinea
676|+676 — Tonga
677|+677 — Solomon Islands
678|+678 — Vanuatu
679|+679 — Fiji
680|+680 — Palau
681|+681 — Wallis and Futuna
682|+682 — Cook Islands
683|+683 — Niue
685|+685 — Samoa
686|+686 — Kiribati
687|+687 — New Caledonia
688|+688 — Tuvalu
689|+689 — French Polynesia
690|+690 — Tokelau
691|+691 — Micronesia
692|+692 — Marshall Islands
850|+850 — North Korea
852|+852 — Hong Kong
853|+853 — Macau
855|+855 — Cambodia
856|+856 — Laos
880|+880 — Bangladesh
886|+886 — Taiwan
960|+960 — Maldives
961|+961 — Lebanon
962|+962 — Jordan
963|+963 — Syria
964|+964 — Iraq
965|+965 — Kuwait
966|+966 — Saudi Arabia
967|+967 — Yemen
968|+968 — Oman
970|+970 — Palestine
971|+971 — United Arab Emirates
972|+972 — Israel
973|+973 — Bahrain
974|+974 — Qatar
975|+975 — Bhutan
976|+976 — Mongolia
977|+977 — Nepal
992|+992 — Tajikistan
993|+993 — Turkmenistan
994|+994 — Azerbaijan
995|+995 — Georgia
996|+996 — Kyrgyzstan
998|+998 — Uzbekistan
"""

private const val SpanishCountryPrefixRows = """
240|+240 — Guinea Ecuatorial
1|+1 — Estados Unidos / Canadá
7|+7 — Rusia / Kazajistán
20|+20 — Egipto
27|+27 — Sudáfrica
30|+30 — Grecia
31|+31 — Países Bajos
32|+32 — Bélgica
33|+33 — Francia
34|+34 — España
36|+36 — Hungría
39|+39 — Italia
40|+40 — Rumanía
41|+41 — Suiza
43|+43 — Austria
44|+44 — Reino Unido
45|+45 — Dinamarca
46|+46 — Suecia
47|+47 — Noruega
48|+48 — Polonia
49|+49 — Alemania
51|+51 — Perú
52|+52 — México
53|+53 — Cuba
54|+54 — Argentina
55|+55 — Brasil
56|+56 — Chile
57|+57 — Colombia
58|+58 — Venezuela
60|+60 — Malasia
61|+61 — Australia
62|+62 — Indonesia
63|+63 — Filipinas
64|+64 — Nueva Zelanda
65|+65 — Singapur
66|+66 — Tailandia
81|+81 — Japón
82|+82 — Corea del Sur
84|+84 — Vietnam
86|+86 — China
90|+90 — Turquía
91|+91 — India
92|+92 — Pakistán
93|+93 — Afganistán
94|+94 — Sri Lanka
95|+95 — Myanmar
98|+98 — Irán
211|+211 — Sudán del Sur
212|+212 — Marruecos
213|+213 — Argelia
216|+216 — Túnez
218|+218 — Libia
220|+220 — Gambia
221|+221 — Senegal
222|+222 — Mauritania
223|+223 — Mali
224|+224 — Guinea
225|+225 — Costa de Marfil
226|+226 — Burkina Faso
227|+227 — Níger
228|+228 — Togo
229|+229 — Benín
230|+230 — Mauricio
231|+231 — Liberia
232|+232 — Sierra Leona
233|+233 — Ghana
234|+234 — Nigeria
235|+235 — Chad
236|+236 — República Centroafricana
237|+237 — Camerún
238|+238 — Cabo Verde
239|+239 — Santo Tomé y Príncipe
241|+241 — Gabón
242|+242 — República del Congo
243|+243 — República Democrática del Congo
244|+244 — Angola
245|+245 — Guinea-Bisáu
246|+246 — Territorio Británico del Océano Índico
248|+248 — Seychelles
249|+249 — Sudán
250|+250 — Ruanda
251|+251 — Etiopía
252|+252 — Somalia
253|+253 — Yibuti
254|+254 — Kenia
255|+255 — Tanzania
256|+256 — Uganda
257|+257 — Burundi
258|+258 — Mozambique
260|+260 — Zambia
261|+261 — Madagascar
262|+262 — Reunión / Mayotte
263|+263 — Zimbabue
264|+264 — Namibia
265|+265 — Malaui
266|+266 — Lesoto
267|+267 — Botsuana
268|+268 — Esuatini
269|+269 — Comoras
290|+290 — Santa Elena
291|+291 — Eritrea
297|+297 — Aruba
298|+298 — Islas Feroe
299|+299 — Groenlandia
350|+350 — Gibraltar
351|+351 — Portugal
352|+352 — Luxemburgo
353|+353 — Irlanda
354|+354 — Islandia
355|+355 — Albania
356|+356 — Malta
357|+357 — Chipre
358|+358 — Finlandia
359|+359 — Bulgaria
370|+370 — Lituania
371|+371 — Letonia
372|+372 — Estonia
373|+373 — Moldavia
374|+374 — Armenia
375|+375 — Bielorrusia
376|+376 — Andorra
377|+377 — Mónaco
378|+378 — San Marino
380|+380 — Ucrania
381|+381 — Serbia
382|+382 — Montenegro
383|+383 — Kosovo
385|+385 — Croacia
386|+386 — Eslovenia
387|+387 — Bosnia y Herzegovina
389|+389 — Macedonia del Norte
420|+420 — República Checa
421|+421 — Eslovaquia
423|+423 — Liechtenstein
500|+500 — Islas Malvinas
501|+501 — Belice
502|+502 — Guatemala
503|+503 — El Salvador
504|+504 — Honduras
505|+505 — Nicaragua
506|+506 — Costa Rica
507|+507 — Panamá
508|+508 — San Pedro y Miquelón
509|+509 — Haití
590|+590 — Guadalupe / San Martín
591|+591 — Bolivia
592|+592 — Guyana
593|+593 — Ecuador
594|+594 — Guayana Francesa
595|+595 — Paraguay
596|+596 — Martinica
597|+597 — Surinam
598|+598 — Uruguay
599|+599 — Curazao / Caribe Neerlandés
670|+670 — Timor Oriental
672|+672 — Territorios Australes
673|+673 — Brunéi
674|+674 — Nauru
675|+675 — Papúa Nueva Guinea
676|+676 — Tonga
677|+677 — Islas Salomón
678|+678 — Vanuatu
679|+679 — Fiyi
680|+680 — Palaos
681|+681 — Wallis y Futuna
682|+682 — Islas Cook
683|+683 — Niue
685|+685 — Samoa
686|+686 — Kiribati
687|+687 — Nueva Caledonia
688|+688 — Tuvalu
689|+689 — Polinesia Francesa
690|+690 — Tokelau
691|+691 — Micronesia
692|+692 — Islas Marshall
850|+850 — Corea del Norte
852|+852 — Hong Kong
853|+853 — Macao
855|+855 — Camboya
856|+856 — Laos
880|+880 — Bangladés
886|+886 — Taiwán
960|+960 — Maldivas
961|+961 — Líbano
962|+962 — Jordania
963|+963 — Siria
964|+964 — Irak
965|+965 — Kuwait
966|+966 — Arabia Saudita
967|+967 — Yemen
968|+968 — Omán
970|+970 — Palestina
971|+971 — Emiratos Árabes Unidos
972|+972 — Israel
973|+973 — Baréin
974|+974 — Catar
975|+975 — Bután
976|+976 — Mongolia
977|+977 — Nepal
992|+992 — Tayikistán
993|+993 — Turkmenistán
994|+994 — Azerbaiyán
995|+995 — Georgia
996|+996 — Kirguistán
998|+998 — Uzbekistán
"""
