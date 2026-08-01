package com.quata.feature.postcomposer.data

import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposerModerationPayloadTest {
    private val actor = ComposerActorSession("profile-7", "Ada Lovelace")

    @Test
    fun textModerationUsesCanonicalPublishedBodyAndActor() {
        val draft = PostComposerDraft(PostComposerType.Text, text = " Hola ", textPatternId = "paper")
        val fields = composerModerationFields(actor, draft, "", "", "ios://post")

        assertEquals("[CANAL:feed]\n[PATRON_TEXTO:paper]\nHola", draft.toRemoteText())
        assertEquals(draft.toRemoteText(), fields["text"])
        assertEquals("Ada Lovelace", fields["display_name"])
        assertEquals("profile-7", fields["profile_id"])
    }

    @Test
    fun imageModerationUsesCanonicalLocationBodyAndMediaMetadata() {
        val draft = PostComposerDraft(PostComposerType.Image, imageUri = "file:///photo.png", locationLabel = "Madrid")
        val fields = composerModerationFields(actor, draft, "photo.png", "image/png", "web://post")

        assertEquals("[CANAL:feed]\n[UBICACION:Madrid]", fields["text"])
        assertEquals("photo.png", fields["image_name"])
        assertEquals("image/png", fields["image_type"])
        assertEquals("web://post", fields["url"])
    }

    @Test
    fun videoModerationUsesCanonicalTitleBody() {
        val draft = PostComposerDraft(PostComposerType.Video, text = "Mi vídeo", videoUri = "file:///clip.mp4")
        val fields = composerModerationFields(actor, draft, "clip.mp4", "video/mp4", "ios://post")

        assertEquals("[CANAL:feed]\n[MEDIA_TITULO:Mi vídeo]", fields["text"])
        assertEquals("clip.mp4", fields["image_name"])
        assertEquals("video/mp4", fields["image_type"])
    }
}
