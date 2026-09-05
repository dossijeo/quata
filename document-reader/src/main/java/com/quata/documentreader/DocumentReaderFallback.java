package com.quata.documentreader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

public final class DocumentReaderFallback {
    private DocumentReaderFallback() {
    }

    public static void failOrOpenChooser(Activity activity) {
        if (!openSystemChooser(activity)) {
            Toast.makeText(activity, R.string.quata_document_reader_unsupported, Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    public static boolean openSystemChooser(Activity activity) {
        Uri uri = fallbackUri(activity);
        if (uri == null) {
            return false;
        }
        String scheme = normalizedScheme(uri);
        if (!"content".equals(scheme) && !"file".equals(scheme)) {
            return false;
        }
        String mimeType = activity.getIntent().getStringExtra(QuataDocumentReader.EXTRA_MIME_TYPE);
        if (mimeType == null || mimeType.trim().isEmpty()) {
            mimeType = "*/*";
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        if ("content".equals(scheme)) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        String title = activity.getIntent().getStringExtra(QuataDocumentReader.EXTRA_FILE_NAME);
        if (title == null || title.trim().isEmpty()) {
            title = activity.getIntent().getStringExtra("name");
        }
        if (title == null || title.trim().isEmpty()) {
            title = "document";
        }
        try {
            activity.startActivity(Intent.createChooser(intent, title));
            activity.finish();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Uri fallbackUri(Activity activity) {
        String explicit = activity.getIntent().getStringExtra(QuataDocumentReader.EXTRA_FALLBACK_URI);
        if (explicit != null && !explicit.trim().isEmpty()) {
            return Uri.parse(explicit);
        }
        String path = activity.getIntent().getStringExtra("path");
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        return Uri.fromFile(new File(path));
    }

    private static String normalizedScheme(Uri uri) {
        String scheme = uri.getScheme();
        return scheme == null ? "" : scheme.toLowerCase(Locale.US);
    }
}
