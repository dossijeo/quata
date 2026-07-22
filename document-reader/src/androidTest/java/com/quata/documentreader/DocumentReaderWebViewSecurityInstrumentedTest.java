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
import com.quata.documentreader.util.RtfHtmlDataType;
import com.quata.documentreader.util.RtfReader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(AndroidJUnit4.class)
public class DocumentReaderWebViewSecurityInstrumentedTest {

    @Test
    public void rtfContentRendersWithRestrictedWebView() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File source = new File(context.getCacheDir(), "document-reader-security-test.rtf");
        Files.write(source.toPath(), "{\\rtf1\\ansi Secure reader content}".getBytes(StandardCharsets.UTF_8));
        RtfReader reader = new RtfReader();
        reader.parse(source);
        assertTrue(new RtfHtmlDataType().format(reader.root, true).contains("Secure reader content"));

        Intent intent = new Intent(context, ViewRtf_Activity.class)
                .putExtra("path", source.getAbsolutePath())
                .putExtra("name", source.getName());

        try (ActivityScenario<ViewRtf_Activity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                WebView webView = activity.findViewById(R.id.webView);
                assertFalse(webView.getSettings().getJavaScriptEnabled());
                assertFalse(webView.getSettings().getAllowContentAccess());
                assertFalse(webView.getSettings().getAllowFileAccess());
                assertTrue(webView.getSettings().getBlockNetworkLoads());
            });

            assertTrue(
                    "The converted RTF content was not rendered",
                    waitForRendering(scenario)
            );
        } finally {
            Files.deleteIfExists(source.toPath());
        }
    }

    private boolean waitForRendering(ActivityScenario<ViewRtf_Activity> scenario) {
        for (int attempt = 0; attempt < 20; attempt++) {
            boolean[] rendered = {false};
            scenario.onActivity(activity -> rendered[0] =
                    activity.findViewById(R.id.progressBar).getVisibility() == View.GONE
                            && activity.<WebView>findViewById(R.id.webView).getContentHeight() > 0);
            if (rendered[0]) {
                return true;
            }
            SystemClock.sleep(100);
        }
        return false;
    }
}
