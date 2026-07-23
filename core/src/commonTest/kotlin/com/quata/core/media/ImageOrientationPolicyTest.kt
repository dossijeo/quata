package com.quata.core.media

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageOrientationPolicyTest {
    @Test fun samplingKeepsPowersOfTwo() {
        assertEquals(4, imageSampleSize(4000, 3000, 1200))
        assertEquals(1, imageSampleSize(800, 600, 1200))
    }

    @Test fun transposeKeepsSharedTransformDefinition() {
        assertEquals(ImageTransform(rotationDegrees = 90f, flipHorizontally = true), ImageOrientation.Transpose.transform())
    }
}
