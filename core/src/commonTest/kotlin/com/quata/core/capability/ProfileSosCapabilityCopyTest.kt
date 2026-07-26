package com.quata.core.capability

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileSosCapabilityCopyTest {
    @Test
    fun returnsEnglishCapabilityCopyForEnglishLanguageTags() {
        assertEquals(
            "The contacts picker was unavailable or cancelled.",
            ProfileSosCapabilityCopy.contactsPickerUnavailable("en-GB"),
        )
        assertEquals(
            "Contacts permission was not granted.",
            ProfileSosCapabilityCopy.contactsPermissionNotGranted("en_US"),
        )
    }

    @Test
    fun defaultsToSpanishForUnknownOrMissingLanguageTags() {
        assertEquals(
            "El selector de contactos no est\u00e1 disponible o se cancel\u00f3.",
            ProfileSosCapabilityCopy.contactsPickerUnavailable("ca-ES"),
        )
        assertEquals(
            "No se concedi\u00f3 permiso para acceder a los contactos.",
            ProfileSosCapabilityCopy.contactsPermissionNotGranted(null),
        )
    }
}
