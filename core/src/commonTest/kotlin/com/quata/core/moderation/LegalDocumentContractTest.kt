package com.quata.core.moderation

import com.quata.core.localization.QuataLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class LegalDocumentContractTest {
    @Test
    fun documentsResolveBundledAssetsAndPublicUrls() {
        assertEquals("privacy_es.docx", LegalDocument.Privacy.assetName(QuataLanguage.Spanish))
        assertEquals("privacy_fr.docx", LegalDocument.Privacy.assetName(QuataLanguage.French))
        assertEquals("privacy_en.docx", LegalDocument.Privacy.assetName(QuataLanguage.English))
        assertEquals("child_safety_es.docx", LegalDocument.ChildSafety.assetName(QuataLanguage.Spanish))
        assertEquals("child_safety_fr.docx", LegalDocument.ChildSafety.assetName(QuataLanguage.French))
        assertEquals("child_safety_en.docx", LegalDocument.ChildSafety.assetName(QuataLanguage.English))

        assertEquals(LegalLinks.Privacy, LegalDocument.Privacy.publicUrl())
        assertEquals(LegalLinks.ChildSafety, LegalDocument.ChildSafety.publicUrl())
    }

    @Test
    fun labelsAreSharedAcrossHosts() {
        val spanish = legalDocumentLabels(QuataLanguage.Spanish)
        val french = legalDocumentLabels(QuataLanguage.French)
        val english = legalDocumentLabels(QuataLanguage.English)

        assertEquals("Política de privacidad", LegalDocument.Privacy.label(spanish))
        assertEquals("Seguridad infantil y normas de la comunidad", LegalDocument.ChildSafety.label(spanish))
        assertEquals("Politique de confidentialité", LegalDocument.Privacy.label(french))
        assertEquals("Sécurité des enfants et règles de la communauté", LegalDocument.ChildSafety.label(french))
        assertEquals("Privacy policy", LegalDocument.Privacy.label(english))
        assertEquals("Child safety and community standards", LegalDocument.ChildSafety.label(english))
    }
}
