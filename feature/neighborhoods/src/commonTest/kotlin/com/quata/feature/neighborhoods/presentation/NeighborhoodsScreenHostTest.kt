package com.quata.feature.neighborhoods.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import com.quata.core.model.PostComment

class NeighborhoodsScreenHostTest {
    @Test fun `anonymous users can browse but private actions require auth`() {
        assertFalse(canPerformNeighborhoodPrivateAction(null))
        assertFalse(canPerformNeighborhoodPrivateAction("  "))
        assertTrue(canPerformNeighborhoodPrivateAction("profile-id"))
    }

    @Test fun `catalog covers English Spanish and French`() {
        assertTrue(defaultNeighborhoodsScreenStrings("en-US").list.title == "Communities")
        assertTrue(defaultNeighborhoodsScreenStrings("es-ES").list.title == "Comunidades")
        assertTrue(defaultNeighborhoodsScreenStrings("fr-FR").list.title == "Communautés")
        assertEquals("Retry", defaultCommunityProfileStrings("en-US").runtime.retry)
        val spanishRuntime = defaultCommunityProfileStrings("es-ES").runtime
        assertEquals("Cargando perfil…", spanishRuntime.loadingProfile)
        assertEquals("Reintentar", spanishRuntime.retry)
        assertEquals("Archivo", spanishRuntime.genericFile)
        assertEquals("Cargar vídeo", spanishRuntime.loadVideo)
        assertEquals("No se pudo abrir el archivo", spanishRuntime.attachmentOpenFailed)
        assertEquals("Réessayer", defaultCommunityProfileStrings("fr-FR").runtime.retry)
        assertEquals("Charger la vidéo", defaultCommunityProfileStrings("fr-FR").runtime.loadVideo)
    }

    @Test fun `relative activity labels cover Android calendar buckets`() {
        val now = 400L * 86_400_000L
        assertTrue(neighborhoodTimeLabel(now - 86_400_000L, now, "es-ES") == "Ayer")
        assertTrue(neighborhoodTimeLabel(now - 3L * 86_400_000L, now, "en-US") == "3 days ago")
        assertTrue(neighborhoodTimeLabel(now - 14L * 86_400_000L, now, "fr-FR") == "Il y a 2 semaines")
        assertTrue(neighborhoodTimeLabel(now - 60L * 86_400_000L, now, "es") == "Hace 2 meses")
        assertTrue(neighborhoodTimeLabel(now - 365L * 86_400_000L, now, "en") == "1 year ago")
    }

    @Test fun `wall-only community keeps chat action enabled`() {
        val wallOnly = com.quata.feature.neighborhoods.domain.NeighborhoodCommunity("Centro", emptyList(), "wall:123", null, null, 4)
        assertTrue(canOpenNeighborhoodChat(wallOnly, null))
    }

    @Test fun `profile attachment visuals cover media documents and generic files`() {
        fun attachment(name: String, mime: String?) = com.quata.feature.neighborhoods.domain.ProfileAttachment("id-$name", name, "https://example.test/$name", mime, null, "Ana")
        assertEquals(ProfileAttachmentVisualKind.Image, attachment("photo.heic", null).visualKind())
        assertEquals(ProfileAttachmentVisualKind.Video, attachment("clip.bin", "video/mp4").visualKind())
        assertEquals(ProfileAttachmentVisualKind.Audio, attachment("voice.m4a", null).visualKind())
        assertEquals(ProfileAttachmentVisualKind.Document, attachment("report.xlsx", null).visualKind())
        assertEquals(ProfileAttachmentVisualKind.File, attachment("archive.zip", "application/zip").visualKind())
    }

    @Test fun `comment draft clears only for a new matching backend comment`() {
        val existing = PostComment("old", "Me", "same", "now", authorId = "me")
        val confirmed = PostComment("backend-id", "Me", "hello", "now", authorId = "me")
        assertFalse(isRemoteProfileCommentConfirmation(listOf(existing), "me", "hello", listOf("old")))
        assertFalse(isRemoteProfileCommentConfirmation(listOf(confirmed), "other", "hello", listOf("old")))
        assertTrue(isRemoteProfileCommentConfirmation(listOf(existing, confirmed), "me", "hello", listOf("old")))
    }
}
