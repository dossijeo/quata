package com.quata.documentreader.xs.common.picture;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BitmapDecodeUtilTest {
    @Test
    public void keepsImagesInsideMemoryAndDimensionLimits() {
        assertEquals(1, BitmapDecodeUtil.calculateSampleSize(1920, 1080));
        assertEquals(2, BitmapDecodeUtil.calculateSampleSize(6000, 4000));
        assertEquals(4, BitmapDecodeUtil.calculateSampleSize(12000, 9000));
    }

    @Test
    public void usesPowerOfTwoSamplingForExtremePanoramas() {
        assertEquals(4, BitmapDecodeUtil.calculateSampleSize(12000, 1000));
    }

    @Test
    public void samplesTowardRequestedDisplaySizeWithoutUndershootingIt() {
        assertEquals(1, BitmapDecodeUtil.calculateTargetSampleSize(1920, 1080, 1280, 720));
        assertEquals(2, BitmapDecodeUtil.calculateTargetSampleSize(4000, 3000, 1200, 900));
        assertEquals(4, BitmapDecodeUtil.calculateTargetSampleSize(8000, 6000, 1200, 900));
    }
}
