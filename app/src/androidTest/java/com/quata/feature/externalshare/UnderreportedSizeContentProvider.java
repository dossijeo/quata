package com.quata.feature.externalshare;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

public final class UnderreportedSizeContentProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        String[] columns = projection != null
                ? projection
                : new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
                row[index] = "underreported.txt";
            } else if (OpenableColumns.SIZE.equals(columns[index])) {
                row[index] = 1L;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = new File(availableContext().getCacheDir(), "underreported-external-share.txt");
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
            randomAccessFile.setLength(ExternalShareIntentParser.MAX_SHARED_FILE_BYTES + 1);
        } catch (Exception error) {
            FileNotFoundException wrapped = new FileNotFoundException("underreported_provider_file_failed");
            wrapped.initCause(error);
            throw wrapped;
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private android.content.Context availableContext() throws FileNotFoundException {
        android.content.Context context = getContext();
        if (context == null) throw new FileNotFoundException("underreported_provider_context_missing");
        return context;
    }
}
