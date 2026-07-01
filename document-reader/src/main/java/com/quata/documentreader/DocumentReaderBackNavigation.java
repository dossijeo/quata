package com.quata.documentreader;

import android.app.Activity;

import androidx.activity.ComponentActivity;

public final class DocumentReaderBackNavigation {
    private DocumentReaderBackNavigation() {
    }

    public static void navigateBack(Activity activity) {
        if (activity instanceof ComponentActivity) {
            ((ComponentActivity) activity).getOnBackPressedDispatcher().onBackPressed();
        } else if (activity != null) {
            activity.finish();
        }
    }
}
