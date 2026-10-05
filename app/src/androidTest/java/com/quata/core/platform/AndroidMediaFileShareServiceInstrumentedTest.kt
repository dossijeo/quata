package com.quata.core.platform

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMediaFileShareServiceInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dismissingTheRealChooserReturnsCancelledRatherThanFalseSuccess() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val file = File(instrumentation.targetContext.cacheDir, "media-share-${System.nanoTime()}.mp4")
            .apply { writeBytes(byteArrayOf(0, 1, 2, 3)) }
        try {
            compose.setContent {
                val share = rememberAndroidMediaFileShareService()
                val scope = rememberCoroutineScope()
                var result by remember { mutableStateOf("idle") }
                Button(
                    modifier = Modifier.testTag("media-share.launch"),
                    onClick = {
                        scope.launch {
                            result = when (
                                share.share(
                                    SharePayload(
                                        files = listOf(
                                            PlatformFile(file.toURI().toString(), file.name, "video/mp4", file.length()),
                                        ),
                                    ),
                                )
                            ) {
                                is PlatformResult.Success -> "success"
                                is PlatformResult.Failure -> "failure"
                                PlatformResult.Cancelled -> "cancelled"
                                PlatformResult.Unsupported -> "unsupported"
                            }
                        }
                    },
                ) { Text("Share") }
                Text(result, Modifier.testTag("media-share.result"))
            }

            compose.onNodeWithTag("media-share.launch").performClick()
            assertTrue(
                "The native Android chooser must become the foreground surface.",
                device.wait(Until.gone(By.pkg(instrumentation.targetContext.packageName)), 8_000),
            )
            device.pressBack()
            val deadline = SystemClock.uptimeMillis() + 8_000
            while (device.currentPackageName != instrumentation.targetContext.packageName &&
                SystemClock.uptimeMillis() < deadline
            ) SystemClock.sleep(100)
            assertTrue(device.currentPackageName == instrumentation.targetContext.packageName)
            compose.waitUntil(5_000) {
                runCatching {
                    compose.onNodeWithTag("media-share.result").assertTextEquals("cancelled")
                }.isSuccess
            }
            compose.onNodeWithTag("media-share.result").assertTextEquals("cancelled")
        } finally {
            file.delete()
            if (device.currentPackageName != instrumentation.targetContext.packageName) device.pressBack()
        }
    }
}
