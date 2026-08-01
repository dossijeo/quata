package com.quata.feature.official.presentation

import com.quata.core.platform.PlatformFile
import com.quata.feature.official.domain.OfficialMediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosOfficialEditorMediaContractTest {
    @Test fun pickerResultMapsToOpaqueHandleWithMetadata() {
        val media = iosOfficialPickedMedia("ios-official-media-7", PlatformFile("file:///tmp/quata_gallery_7.jpg", "notice.jpg", "image/jpeg"), OfficialMediaType.Image)
        assertEquals("ios-official-media-7", media.preparedHandle)
        assertEquals("notice.jpg", media.displayName)
        assertEquals("image/jpeg", media.mimeType)
        assertEquals("local://ios-official-media-7", media.url)
    }

    @Test fun commonEditorCarriesDiscardLifecycleSlotForCancelAndRollback() {
        val slots = OfficialEditorPlatformSlots(richTextEditor = { _, _ -> }, discardMedia = { })
        assertTrue(slots.discardMedia != null)
    }
}
