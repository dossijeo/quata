package com.quata.deeplinksender;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Observes an already delivered anonymous link. No launch, session injection or backend client. */
@RunWith(AndroidJUnit4.class)
public final class NativeLoginTest {
    private static void editableNodes(android.view.accessibility.AccessibilityNodeInfo node,
            String resource, boolean inside, List<android.view.accessibility.AccessibilityNodeInfo> result) {
        if (node == null) return;
        boolean matched = inside || resource.equals(node.getViewIdResourceName());
        if (matched && node.isEditable() && node.isVisibleToUser()) result.add(node);
        for (int index = 0; index < node.getChildCount(); index++) editableNodes(node.getChild(index), resource, matched, result);
    }

    private static void privateText(android.app.UiAutomation automation, String resource, String value) {
        List<android.view.accessibility.AccessibilityNodeInfo> fields = new java.util.ArrayList<>();
        editableNodes(automation.getRootInActiveWindow(), resource, false, fields);
        assertEquals("Login field ambiguous", 1, fields.size());
        android.view.accessibility.AccessibilityNodeInfo field = fields.get(0);
        // UiObject2.setText logs its argument in UiAutomator 2.3.0. Never use it here.
        assertTrue("Login field not empty", field.getText() == null || field.getText().length() == 0);
        Bundle args = new Bundle();
        args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        assertTrue("Private field action rejected", field.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args));
    }

    private static void save(UiDevice device, File directory, String name) throws Exception {
        device.dumpWindowHierarchy(new File(directory, name + ".xml"));
        assertTrue(device.takeScreenshot(new File(directory, name + ".png")));
    }

    @Test(timeout = 120000)
    public void resumeDeliveredLink() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context sender = instrumentation.getTargetContext();
        String name = InstrumentationRegistry.getArguments().getString("loginSocket", "");
        assertTrue(name.matches("quata-native-login-[0-9a-f-]{36}"));
        JSONObject report = new JSONObject().put("status", "failed").put("phase", "private_input");
        File directory = null;
        try (LocalServerSocket server = new LocalServerSocket(name)) {
            AtomicReference<LocalSocket> accepted = new AtomicReference<>();
            Timer deadline = new Timer(true);
            deadline.schedule(new TimerTask() { public void run() {
                try { if (accepted.get() != null) accepted.get().close(); } catch (Exception ignored) {}
                try { server.close(); } catch (Exception ignored) {}
            } }, 110000);
            try {
                Bundle ready = new Bundle(); ready.putString("nativeLoginSocketReady", name);
                instrumentation.sendStatus(0, ready);
                try (LocalSocket socket = server.accept()) {
                    accepted.set(socket); socket.setSoTimeout(30000);
                    java.io.Reader reader = new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
                    StringBuilder line = new StringBuilder();
                    while (true) {
                        int value = reader.read();
                        assertTrue("Private input incomplete", value >= 0 && line.length() < 4096);
                        if (value == 10) break;
                        line.append((char) value);
                    }
                    JSONObject input = new JSONObject(line.toString());
                    java.util.Set<String> keys = new java.util.HashSet<>(); input.keys().forEachRemaining(keys::add);
                    java.util.Set<String> expectedKeys = new java.util.HashSet<>(java.util.Set.of("runId", "stepId", "countryCode", "phone", "password", "messageId"));
                    boolean cancelFirst = input.has("variant");
                    if (cancelFirst) {
                        assertEquals("cancel-then-feed", input.getString("variant"));
                        expectedKeys.add("variant");
                    }
                    assertEquals(expectedKeys, keys);
                    String run = input.getString("runId"), step = input.getString("stepId");
                    String uuid = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}";
                    assertTrue(run.matches(uuid) && step.matches(uuid));
                    assertTrue("Unsupported country", "240".equals(input.getString("countryCode")));
                    assertTrue(input.getString("phone").matches("[0-9]{8,15}"));
                    assertTrue(input.getString("password").length() >= 12 && input.getString("password").length() <= 128);
                    String message = input.getString("messageId");
                    assertTrue(message.matches("[1-9][0-9]{0,15}"));
                    directory = new File(sender.getExternalFilesDir(null), "native-login-" + step);
                    assertFalse(directory.exists()); assertTrue(directory.mkdirs());
                    report.put("runId", run).put("stepId", step).put("messageId", message);
                    if (cancelFirst) report.put("variant", "cancel-then-feed");
                    UiDevice device = UiDevice.getInstance(instrumentation);
                    android.content.res.Resources resources = sender.getPackageManager().getResourcesForApplication("com.quata");
                    int titleId = resources.getIdentifier("auth_required_title", "string", "com.quata");
                    int loginId = resources.getIdentifier("auth_required_login", "string", "com.quata");
                    assertTrue(titleId != 0 && loginId != 0);
                    report.put("phase", "anonymous_gate");
                    assertTrue(device.wait(Until.hasObject(By.text(resources.getString(titleId))), 15000));
                    assertEquals("com.quata", device.getCurrentPackageName());
                    assertFalse(device.hasObject(By.res("chat.composer.input")));
                    save(device, directory, "gate");
                    if (cancelFirst) {
                        PublicLinkTest.pressBack();
                        assertTrue(device.wait(Until.gone(By.text(resources.getString(titleId))), 10000));
                        assertTrue(device.wait(Until.hasObject(By.desc("navigation.primary.feed")), 10000));
                        UiObject2 like = device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile("feed\\.action\\.like\\..+"))), 30000);
                        assertNotNull("Feed action absent", like);
                        long cancelledUntil = SystemClock.elapsedRealtime() + 2000;
                        while (SystemClock.elapsedRealtime() < cancelledUntil) {
                            assertEquals("com.quata", device.getCurrentPackageName());
                            assertFalse(device.hasObject(By.res("auth.phone")));
                            assertFalse(device.hasObject(By.res("auth.password")));
                            assertFalse(device.hasObject(By.res("chat.composer.input")));
                            assertFalse(device.hasObject(By.desc("chat.focused-message.visible." + message)));
                            assertFalse(device.hasObject(By.text(resources.getString(titleId))));
                            SystemClock.sleep(100);
                        }
                        save(device, directory, "cancelled-feed");
                        like.click(); // Anonymous action requests a new gate without queueing a Like.
                        assertTrue(device.wait(Until.hasObject(By.text(resources.getString(titleId))), 10000));
                        assertFalse(device.hasObject(By.res("chat.composer.input")));
                    }
                    device.findObject(By.text(resources.getString(loginId))).click();
                    assertTrue(device.wait(Until.hasObject(By.res("auth.phone")), 15000));
                    assertTrue("Fixture prefix not selected", device.hasObject(By.text("+240")));
                    report.put("phase", "private_fields");
                    // Accessibility ACTION_SET_TEXT; values never enter argv, reports or assertion messages.
                    privateText(instrumentation.getUiAutomation(), "auth.phone", input.getString("phone"));
                    privateText(instrumentation.getUiAutomation(), "auth.password", input.getString("password"));
                    UiObject2 submit = device.wait(Until.findObject(By.res("auth.submit").enabled(true)), 10000);
                    assertNotNull("Submit absent", submit);
                    assertFalse("Submit not visible", submit.getVisibleBounds().isEmpty());
                    report.put("phase", "submit_started");
                    Files.write(new File(directory, "report.json").toPath(), report.toString(2).getBytes(StandardCharsets.UTF_8));
                    submit.click(); // Exactly one Submit. Failure never retries or captures the credential form.
                    if (cancelFirst) {
                        report.put("phase", "await_feed_after_new_login");
                        long authenticatedDeadline = SystemClock.elapsedRealtime() + 45000;
                        while (SystemClock.elapsedRealtime() < authenticatedDeadline) {
                            assertEquals("com.quata", device.getCurrentPackageName());
                            assertFalse(device.hasObject(By.res("chat.composer.input")));
                            assertFalse(device.hasObject(By.desc("chat.focused-message.visible." + message)));
                            assertFalse(device.hasObject(By.text(resources.getString(titleId))));
                            if (!device.hasObject(By.res("auth.phone")) && !device.hasObject(By.res("auth.password")) &&
                                    device.hasObject(By.res(java.util.regex.Pattern.compile("feed\\.action\\.like\\..+")))) break;
                            SystemClock.sleep(100);
                        }
                    } else {
                        report.put("phase", "await_focus");
                        assertTrue(device.wait(Until.hasObject(By.desc("chat.focused-message.visible." + message)), 45000));
                        assertFalse(device.hasObject(By.res("auth.phone")));
                        assertFalse(device.hasObject(By.res("auth.password")));
                        assertTrue(device.hasObject(By.res("chat.composer.input")));
                        assertTrue(device.hasObject(By.textContains("Deep link " + run)));
                        save(device, directory, "focused");
                        PublicLinkTest.pressBack();
                        assertTrue(device.wait(Until.gone(By.res("chat.composer.input")), 10000));
                        report.put("phase", "await_feed_after_back");
                    }
                    assertFalse("Back returned to Login", device.hasObject(By.res("auth.phone")));
                    assertFalse("Back returned to Login", device.hasObject(By.res("auth.password")));
                    assertTrue(device.wait(Until.hasObject(By.desc("navigation.primary.feed")), 10000));
                    assertTrue(device.wait(Until.hasObject(By.res(java.util.regex.Pattern.compile("feed\\.action\\.like\\..+"))), 30000));
                    long until = SystemClock.elapsedRealtime() + 2000;
                    while (SystemClock.elapsedRealtime() < until) {
                        assertFalse(device.hasObject(By.res("auth.phone")));
                        assertFalse(device.hasObject(By.res("auth.password")));
                        assertFalse(device.hasObject(By.res("chat.composer.input")));
                        assertFalse(device.hasObject(By.desc("chat.focused-message.visible." + message)));
                        if (cancelFirst) {
                            assertEquals("com.quata", device.getCurrentPackageName());
                            assertFalse(device.hasObject(By.text(resources.getString(titleId))));
                            assertTrue(device.hasObject(By.res(java.util.regex.Pattern.compile("feed\\.action\\.like\\..+"))));
                        }
                        SystemClock.sleep(100);
                    }
                    assertEquals("com.quata", device.getCurrentPackageName());
                    save(device, directory, cancelFirst ? "authenticated-feed" : "back");
                    report.put("phase", "complete").put("status", "passed_pending_visual_review").put("submitCount", 1);
                    java.io.Writer writer = new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                    JSONObject receipt = new JSONObject().put("runId", run).put("stepId", step).put("verified", true);
                    if (cancelFirst) receipt.put("variant", "cancel-then-feed");
                    writer.write(receipt.toString() + "\n");
                    writer.flush();
                }
            } finally { deadline.cancel(); }
        } catch (Throwable ignored) {
            // Including UiAutomator failures: no cause, private input, field contents or automatic screenshot.
            throw new AssertionError("native_login_observation_unresolved");
        } finally {
            if (directory != null) Files.write(new File(directory, "report.json").toPath(), report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }
}
