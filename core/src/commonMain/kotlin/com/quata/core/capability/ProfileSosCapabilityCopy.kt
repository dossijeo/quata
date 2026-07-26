package com.quata.core.capability

/**
 * Small shared copy surface for capability notices emitted by Web and iOS Profile/SOS hosts.
 *
 * This is intentionally separate from Android resources and from feature copy. Unknown language
 * tags use Spanish, matching the existing common capability-copy fallback.
 */
object ProfileSosCapabilityCopy {
    fun contactsPickerUnavailable(languageTag: String?): String = forLanguageTag(languageTag).contactsPickerUnavailable

    fun contactsPermissionNotGranted(languageTag: String?): String = forLanguageTag(languageTag).contactsPermissionNotGranted

    fun selectedDeviceContactsNotMatched(languageTag: String?): String =
        forLanguageTag(languageTag).selectedDeviceContactsNotMatched

    private fun forLanguageTag(languageTag: String?): ProfileSosCapabilityText = when (
        languageTag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase()
    ) {
        "en" -> EnglishProfileSosCapabilityText
        "es" -> SpanishProfileSosCapabilityText
        else -> SpanishProfileSosCapabilityText
    }
}

private interface ProfileSosCapabilityText {
    val contactsPickerUnavailable: String
    val contactsPermissionNotGranted: String
    val selectedDeviceContactsNotMatched: String
}

private object SpanishProfileSosCapabilityText : ProfileSosCapabilityText {
    override val contactsPickerUnavailable: String = "El selector de contactos no est\u00e1 disponible o se cancel\u00f3."
    override val contactsPermissionNotGranted: String = "No se concedi\u00f3 permiso para acceder a los contactos."
    override val selectedDeviceContactsNotMatched: String =
        "Los contactos seleccionados del dispositivo todav\u00eda no se pueden asociar a perfiles de Quata en iOS."
}

private object EnglishProfileSosCapabilityText : ProfileSosCapabilityText {
    override val contactsPickerUnavailable: String = "The contacts picker was unavailable or cancelled."
    override val contactsPermissionNotGranted: String = "Contacts permission was not granted."
    override val selectedDeviceContactsNotMatched: String =
        "Selected device contacts cannot yet be matched to Quata profiles on iOS."
}
