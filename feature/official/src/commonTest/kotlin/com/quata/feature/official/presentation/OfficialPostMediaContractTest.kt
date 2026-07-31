package com.quata.feature.official.presentation

import com.quata.feature.official.domain.OfficialMediaType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfficialPostMediaContractTest {
    @Test fun video_has_the_shared_inline_play_affordance() {
        officialInlineMediaContract(OfficialMediaType.Video).also { contract ->
            assertTrue(contract.showPlayButton)
            assertTrue(contract.requiresStillThumbnail)
        }
    }

    @Test fun image_has_no_video_play_affordance() {
        officialInlineMediaContract(OfficialMediaType.Image).also { contract ->
            assertFalse(contract.showPlayButton)
            assertFalse(contract.requiresStillThumbnail)
        }
    }
}
