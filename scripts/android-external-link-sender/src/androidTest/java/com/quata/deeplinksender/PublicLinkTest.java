package com.quata.deeplinksender;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

/** Separate package/UID. Public resolver delivery only; no product dependency or shell launch. */
@RunWith(AndroidJUnit4.class)
public final class PublicLinkTest {
    /** Environment recovery only: acknowledges the observed Android System UI ANR, not Qüata UI. */
    @Test(timeout = 45000)
    public void acknowledgeSystemUiAnr() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        assertEquals("android", device.getCurrentPackageName());
        assertTrue(device.hasObject(By.pkg("android").text("System UI isn't responding")));
        androidx.test.uiautomator.UiObject2 wait = device.findObject(By.pkg("android").text("Wait"));
        assertNotNull(wait);
        wait.click();
        assertTrue("System UI ANR still visible", device.wait(Until.gone(
                By.pkg("android").text("System UI isn't responding")), 15000));
        // No URL, Intent, activity start or interaction with Qüata is performed.
    }

    private static void pressBack() {
        android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        long downTime = SystemClock.uptimeMillis();
        assertTrue("BACK down rejected", automation.injectInputEvent(new KeyEvent(downTime, downTime,
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD,
                0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD), true));
        assertTrue("BACK up rejected", automation.injectInputEvent(new KeyEvent(downTime, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD,
                0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD), true));
    }

    /** Observes a previously delivered URL; BACK is normal instrumentation input, never a launch. */
    @Test(timeout = 90000)
    public void observeCurrentAndBack() throws Exception {
        Context sender = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bundle args = InstrumentationRegistry.getArguments();
        String run = args.getString("runId", "");
        String resource = args.getString("expectedResource", "");
        String text = args.getString("expectedText", "");
        assertTrue(run.matches("[a-z0-9-]{8,80}"));
        assertTrue(resource.matches("(feed|official|chat)\\.[a-zA-Z0-9.]+"));
        assertFalse(text.isEmpty());
        File directory = new File(sender.getExternalFilesDir(null), run);
        assertFalse(directory.exists());
        assertTrue(directory.mkdirs());
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        JSONObject report = new JSONObject().put("runId", run).put("status", "prepared")
                .put("scope", "Observation and BACK only; no Intent emitted")
                .put("expectedResource", resource).put("expectedText", text);
        try {
            if ("true".equals(args.getString("dismissStartupPrompt"))) {
                assertTrue("Expected startup prompt absent", device.wait(Until.hasObject(By.text("Open Qüata links in the app")), 10000));
                pressBack();
                report.put("startupPromptDismissedWithBack", true);
            }
            assertTrue("Expected detail absent", device.wait(Until.hasObject(By.res(resource)), 30000));
            assertTrue("Expected content absent", device.wait(Until.hasObject(By.textContains(text)), 30000));
            assertEquals("com.quata", device.getCurrentPackageName());
            device.dumpWindowHierarchy(new File(directory, "detail.xml"));
            assertTrue(device.takeScreenshot(new File(directory, "detail.png")));
            pressBack();
            assertTrue("Detail did not close", device.wait(Until.gone(By.res(resource)), 10000));
            long until = SystemClock.elapsedRealtime() + 2000;
            while (SystemClock.elapsedRealtime() < until) {
                assertFalse("Detail reopened after exit", device.hasObject(By.res(resource)));
                SystemClock.sleep(100);
            }
            assertEquals("com.quata", device.getCurrentPackageName());
            device.dumpWindowHierarchy(new File(directory, "back.xml"));
            assertTrue(device.takeScreenshot(new File(directory, "back.png")));
            report.put("status", "passed_pending_visual_review").put("postExitObservationMs", 2000);
        } finally {
            File output = new File(directory, "report.json");
            Files.write(output.toPath(), report.toString(2).getBytes(StandardCharsets.UTF_8));
            Bundle status = new Bundle();
            status.putString("externalLinkReport", output.getAbsolutePath());
            InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
        }
    }

    @Test(timeout = 90000)
    public void deliverPublicLink() throws Exception {
        Context sender = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bundle args = InstrumentationRegistry.getArguments();
        String run = args.getString("runId", "");
        assertTrue("Unique run ID required", run.matches("[a-z0-9-]{8,80}"));
        Uri uri = Uri.parse(args.getString("publicUrl", ""));
        assertEquals("https", uri.getScheme());
        assertEquals("egquata.com", uri.getHost());
        assertNull(uri.getUserInfo());
        String messageId = args.getString("expectedMessageId", "");
        String marker = args.getString("expectedMarker", "");
        if (!messageId.isEmpty()) {
            assertTrue(messageId.matches("[1-9][0-9]{0,15}"));
            assertTrue(marker.matches("[0-9a-f-]{36}"));
            assertTrue(uri.getFragment().matches("chat-sb:[1-9][0-9]{0,15}\\?message=" + messageId));
        }
        assertEquals("com.quata.deeplinksender", sender.getPackageName());
        PackageManager pm = sender.getPackageManager();
        assertNotEquals(sender.getApplicationInfo().uid, pm.getApplicationInfo("com.quata", 0).uid);
        File directory = new File(sender.getExternalFilesDir(null), run);
        assertFalse("Do not overwrite prior evidence", directory.exists());
        assertTrue(directory.mkdirs());
        JSONObject report = new JSONObject().put("runId", run).put("url", uri.toString())
                .put("senderPackage", sender.getPackageName()).put("senderUid", sender.getApplicationInfo().uid)
                .put("scope", "Public resolver delivery probe; destination acceptance requires separate review")
                .put("status", "prepared");
        File reportFile = new File(directory, "report.json");
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, uri)
                    .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            assertNull(view.getPackage());
            assertNull(view.getComponent());
            ResolveInfo resolved = pm.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
            assertNotNull("Public resolver returned no handler", resolved);
            assertEquals("com.quata", resolved.activityInfo.packageName);
            assertEquals("com.quata.MainActivity", resolved.activityInfo.name);
            report.put("resolvedPackage", resolved.activityInfo.packageName)
                    .put("resolvedActivity", resolved.activityInfo.name)
                    .put("action", view.getAction()).put("browsable", true)
                    .put("explicitPackage", JSONObject.NULL).put("explicitComponent", JSONObject.NULL)
                    .put("beforeForegroundPackage", device.getCurrentPackageName())
                    .put("deliveryStartedElapsedMs", SystemClock.elapsedRealtime()).put("status", "delivery_started");
            Files.write(reportFile.toPath(), report.toString(2).getBytes(StandardCharsets.UTF_8));
            // The sender context submits this unchanged implicit Intent exactly once.
            sender.startActivity(view);
            assertTrue("Qüata did not become visible", device.wait(Until.hasObject(By.pkg("com.quata")), 30000));
            if (!messageId.isEmpty()) {
                String focused = "chat.focused-message.visible." + messageId;
                assertTrue("Target message never focused", device.wait(Until.hasObject(By.desc(focused)), 30000));
                report.put("focusedMessageId", messageId).put("focusObservedElapsedMs", SystemClock.elapsedRealtime());
                assertTrue(device.takeScreenshot(new File(directory, "focused.png")));
                device.dumpWindowHierarchy(new File(directory, "focused.xml"));
                assertTrue("Owned message body absent", device.wait(Until.hasObject(By.textContains(marker)), 10000));
                assertTrue("Chat composer absent", device.hasObject(By.res("chat.composer.input")));
                device.dumpWindowHierarchy(new File(directory, "detail.xml"));
                assertTrue(device.takeScreenshot(new File(directory, "detail.png")));
                pressBack();
                assertTrue("Chat did not close", device.wait(Until.gone(By.res("chat.composer.input")), 10000));
                long until = SystemClock.elapsedRealtime() + 2000;
                while (SystemClock.elapsedRealtime() < until) {
                    assertFalse("Chat reopened", device.hasObject(By.res("chat.composer.input")));
                    assertFalse("Message reopened", device.hasObject(By.textContains(marker)));
                    SystemClock.sleep(100);
                }
                assertEquals("com.quata", device.getCurrentPackageName());
                device.dumpWindowHierarchy(new File(directory, "back.xml"));
                assertTrue(device.takeScreenshot(new File(directory, "back.png")));
                report.put("status", "chat_passed_pending_visual_review").put("postExitObservationMs", 2000);
                return;
            }
            SystemClock.sleep(12000);
            device.dumpWindowHierarchy(new File(directory, "hierarchy.xml"));
            assertTrue(device.takeScreenshot(new File(directory, "screen.png")));
            report.put("afterForegroundPackage", device.getCurrentPackageName()).put("status", "delivered_pending_destination_review");
        } finally {
            Files.write(reportFile.toPath(), report.toString(2).getBytes(StandardCharsets.UTF_8));
            Bundle status = new Bundle();
            status.putString("externalLinkReport", reportFile.getAbsolutePath());
            InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
        }
    }
}
