package com.quata.documentreader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.quata.documentreader.activity.ViewRtf_Activity;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(AndroidJUnit4.class)
public class DocumentReaderWebViewSecurityInstrumentedTest {
    @Test public void rtfContentRendersWithoutFileContentNetworkOrJavaScriptAccess() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File source = new File(context.getCacheDir(), "document-reader-security-test.rtf");
        Files.write(source.toPath(), "{\\rtf1\\ansi Secure reader content}".getBytes(StandardCharsets.UTF_8));
        Intent intent = new Intent(context, ViewRtf_Activity.class).putExtra("path", source.getAbsolutePath()).putExtra("name", source.getName());
        try (ActivityScenario<ViewRtf_Activity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                WebView view = activity.findViewById(R.id.webView);
                assertFalse(view.getSettings().getJavaScriptEnabled());
                assertFalse(view.getSettings().getAllowContentAccess());
                assertFalse(view.getSettings().getAllowFileAccess());
                assertTrue(view.getSettings().getBlockNetworkLoads());
            });
            assertTrue("The converted RTF content was not rendered", waitForRendering(scenario));
        } finally { Files.deleteIfExists(source.toPath()); }
    }

    private boolean waitForRendering(ActivityScenario<ViewRtf_Activity> scenario) {
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean[] rendered = {false};
            scenario.onActivity(activity -> rendered[0] = activity.findViewById(R.id.progressBar).getVisibility() == View.GONE && activity.<WebView>findViewById(R.id.webView).getContentHeight() > 0);
            if (rendered[0]) return true;
            SystemClock.sleep(100);
        }
        return false;
    }
}
