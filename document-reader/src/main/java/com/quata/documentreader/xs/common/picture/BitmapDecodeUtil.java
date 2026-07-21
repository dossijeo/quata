package com.quata.documentreader.xs.common.picture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
        // Keep this explicit: Play's bytecode analysis requires every Options
        // instance passed to BitmapFactory to define a sampling policy.
        bounds.inSampleSize = 1;
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
        return decodeByteArray(data, offset, length, 0, 0);
    }

    public static Bitmap decodeByteArray(
        byte[] data,
        int offset,
        int length,
        int requestedWidth,
        int requestedHeight
    ) {
        if (data == null || length <= 0 || offset < 0 || offset + length > data.length) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        bounds.inSampleSize = 1;
        BitmapFactory.decodeByteArray(data, offset, length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = Math.max(
            calculateSampleSize(bounds.outWidth, bounds.outHeight),
            calculateTargetSampleSize(bounds.outWidth, bounds.outHeight, requestedWidth, requestedHeight)
        );
        return BitmapFactory.decodeByteArray(data, offset, length, decode);
    }

    public static Bitmap decodeStream(InputStream input) throws IOException {
        if (input == null) return null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        byte[] data = output.toByteArray();
        return decodeByteArray(data, 0, data.length);
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

    static int calculateTargetSampleSize(int width, int height, int requestedWidth, int requestedHeight) {
        if (requestedWidth <= 0 || requestedHeight <= 0) return 1;
        int sample = 1;
        while (width / (sample * 2) >= requestedWidth && height / (sample * 2) >= requestedHeight) {
            sample *= 2;
        }
        return sample;
    }

    private static int normalizeSampleSize(int sampleSize) {
        return sampleSize > 0 ? sampleSize : 1;
    }
}
