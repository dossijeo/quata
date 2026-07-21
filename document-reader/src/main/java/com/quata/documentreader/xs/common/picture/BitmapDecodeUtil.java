package com.quata.documentreader.xs.common.picture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Memory-bounded synchronous decoding for images embedded in local office documents. */
public final class BitmapDecodeUtil {
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXEL_COUNT = 8L * 1024L * 1024L;

    private BitmapDecodeUtil() {}

    public static Bitmap decodeFile(String path) {
        return decodeFile(path, null);
    }

    public static Bitmap decodeFile(String path, BitmapFactory.Options requestedOptions) {
        if (path == null) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options decode = requestedOptions != null
            ? requestedOptions
            : new BitmapFactory.Options();
        decode.inJustDecodeBounds = false;
        decode.inSampleSize = Math.max(normalizeSampleSize(decode.inSampleSize), calculateSampleSize(bounds.outWidth, bounds.outHeight));
        return BitmapFactory.decodeFile(path, decode);
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        if (data == null || length <= 0 || offset < 0 || offset + length > data.length) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, offset, length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight);
        return BitmapFactory.decodeByteArray(data, offset, length, decode);
    }

    static int calculateSampleSize(int width, int height) {
        int sample = 1;
        while (width / sample > MAX_DIMENSION
            || height / sample > MAX_DIMENSION
            || ((long) width * (long) height) / ((long) sample * (long) sample) > MAX_PIXEL_COUNT) {
            sample *= 2;
        }
        return sample;
    }

    private static int normalizeSampleSize(int sampleSize) {
        return sampleSize > 0 ? sampleSize : 1;
    }
}
