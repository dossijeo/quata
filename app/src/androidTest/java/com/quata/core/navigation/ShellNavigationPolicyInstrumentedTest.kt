package com.quata.core.navigation

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.quata.MainActivity
import com.quata.QuataApp
import com.quata.R
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ShellNavigationPolicyInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val arguments = InstrumentationRegistry.getArguments()

    @Before
    fun ensureAnonymousSession() {
        runBlocking { app.container.authRepository.logout() }
    }

    @Test
    fun anonymousPublicPrimaryRoutesRemainInsideSharedShell() {
        ActivityScenario.launch<MainActivity>(startIntent(AppDestinations.Feed.route)).use {
            waitForPrimaryNavigation(AppDestinations.Feed.route)
            clickPrimaryNavigation(AppDestinations.Neighborhoods.route)
            waitForPrimaryNavigation(AppDestinations.Neighborhoods.route)
            assertAuthDialogMissing("neighborhoods")
            saveScreenshot("android-shell-nav-public-neighborhoods")

            clickPrimaryNavigation(AppDestinations.Official.route)
            waitForPrimaryNavigation(AppDestinations.Official.route)
            assertAuthDialogMissing("official")
            saveScreenshot("android-shell-nav-public-official")
        }

        writeReport(
            fileName = "android-shell-nav-public-evidence.json",
            steps = listOf(
                "anonymous_feed_launched_with_shared_navigation_anchor",
                "anonymous_neighborhoods_route_selected_without_auth_prompt",
                "anonymous_official_route_selected_without_auth_prompt",
            ),
            screenshots = listOf(
                "android-shell-nav-public-neighborhoods.png",
                "android-shell-nav-public-official.png",
            ),
        )
    }

    @Test
    fun anonymousPrivatePrimaryRoutesOpenAuthRequiredPrompt() {
        ActivityScenario.launch<MainActivity>(startIntent(AppDestinations.Feed.route)).use {
            waitForPrimaryNavigation(AppDestinations.Feed.route)

            clickPrimaryNavigation(AppDestinations.Conversations.route)
            waitForAuthDialog("conversations")
            saveScreenshot("android-shell-nav-private-conversations")
        }

        ActivityScenario.launch<MainActivity>(startIntent(AppDestinations.Feed.route)).use {
            waitForPrimaryNavigation(AppDestinations.Feed.route)

            clickPrimaryNavigation(AppDestinations.Profile.route)
            waitForAuthDialog("profile")
            saveScreenshot("android-shell-nav-private-profile")
        }

        writeReport(
            fileName = "android-shell-nav-private-evidence.json",
            steps = listOf(
                "anonymous_conversations_primary_route_requested_auth_prompt",
                "anonymous_profile_primary_route_requested_auth_prompt",
            ),
            screenshots = listOf(
                "android-shell-nav-private-conversations.png",
                "android-shell-nav-private-profile.png",
            ),
        )
    }

    @Test
    fun authenticatedPrimaryRoutesRemainSelectedAcrossForegroundAndActivityRecreation() = runBlocking {
        val credentialsFile = optionalArgument("quataShellNavigationCredentialsFile")
        assumeTrue(
            "FLOW-SHELL-NAV-ANDROID-RESTORATION-001 is opt-in and requires local credentials.",
            !credentialsFile.isNullOrBlank() && optionalArgument("quataShellNavigationRestorationEvidence") == "1",
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())
        suppressStartupPrompts()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password).getOrThrow()
        val initialSession = app.container.sessionManager.currentSession()
        assertTrue("android_shell_navigation_real_session_missing", initialSession?.isSupabaseAuthenticated() == true)
        val steps = mutableListOf<String>()
        val screenshots = mutableListOf<String>()

        primaryNavigationDestinations.forEachIndexed { index, destination ->
            val route = destination.route
            val launchRoute = primaryNavigationDestinations[(index + 1) % primaryNavigationDestinations.size].route
            ActivityScenario.launch<MainActivity>(startIntent(launchRoute)).use { scenario ->
                waitForSelectedPrimaryNavigation(launchRoute)
                clickPrimaryNavigation(route)
                waitForSelectedPrimaryNavigation(route)
                steps += "${route}_selected_after_navigation_from_$launchRoute"

                scenario.moveToState(Lifecycle.State.STARTED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                waitForSelectedPrimaryNavigation(route)
                steps += "${route}_preserved_after_foreground"

                scenario.recreate()
                waitForSelectedPrimaryNavigation(route)
                steps += "${route}_preserved_after_recreation"
                val screenshot = "android-shell-nav-restored-$route"
                saveScreenshot(screenshot)
                screenshots += "$screenshot.png"
            }
        }

        val finalSession = app.container.sessionManager.currentSession()
        assertTrue("android_shell_navigation_session_lost", finalSession?.isSupabaseAuthenticated() == true)
        assertEquals("android_shell_navigation_actor_changed", initialSession?.userId, finalSession?.userId)
        writeRestorationReport(initialSession?.userId.orEmpty(), steps, screenshots)
    }

    private fun startIntent(route: String): Intent =
        Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", route)

    private fun waitForPrimaryNavigation(route: String) {
        val tag = primaryNavigationTag(route)
        check(device.wait(Until.hasObject(By.desc(tag)), 20_000)) {
            "android_shell_navigation_anchor_missing:$tag"
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun waitForSelectedPrimaryNavigation(route: String) {
        val tag = primaryNavigationTag(route)
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMillis = 30_000)
        runCatching {
            compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsSelected()
        }.getOrElse { failure ->
            val selectedRoutes = primaryNavigationDestinations.mapNotNull { destination ->
                destination.route.takeIf {
                    runCatching {
                        compose.onNodeWithTag(primaryNavigationTag(it), useUnmergedTree = true).assertIsSelected()
                    }.isSuccess
                }
            }
            throw AssertionError(
                "android_shell_navigation_selected_route_mismatch:expected=$route:actual=${selectedRoutes.joinToString()}",
                failure,
            )
        }
    }

    private fun clickPrimaryNavigation(route: String) {
        val tag = primaryNavigationTag(route)
        val node = device.wait(Until.findObject(By.desc(tag)), 20_000)
            ?: error("android_shell_navigation_click_anchor_missing:$tag")
        node.click()
    }

    private fun waitForAuthDialog(context: String) {
        val title = targetContext.getString(R.string.auth_required_title)
        check(device.wait(Until.hasObject(By.text(title)), 20_000)) {
            "android_auth_required_prompt_missing:$context"
        }
    }

    private fun assertAuthDialogMissing(context: String) {
        val title = targetContext.getString(R.string.auth_required_title)
        check(!device.hasObject(By.text(title))) {
            "android_auth_required_prompt_unexpected:$context"
        }
    }

    private fun primaryNavigationTag(route: String): String =
        "navigation.primary.$route"

    private fun saveScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("android_screenshot_failed:$name")
        val file = File(evidenceDir(), "$name.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "android_screenshot_encode_failed:$name"
            }
        }
    }

    private fun writeReport(fileName: String, steps: List<String>, screenshots: List<String>) {
        File(evidenceDir(), fileName).writeText(
            JSONObject()
                .put("check", "FLOW-SHELL-NAV-ANDROID-001")
                .put("status", "passed")
                .put("steps", JSONArray(steps))
                .put("semanticAnchors", JSONArray(listOf(
                    primaryNavigationTag(AppDestinations.Feed.route),
                    primaryNavigationTag(AppDestinations.Neighborhoods.route),
                    primaryNavigationTag(AppDestinations.Official.route),
                    primaryNavigationTag(AppDestinations.Conversations.route),
                    primaryNavigationTag(AppDestinations.Profile.route),
                    targetContext.getString(R.string.auth_required_title),
                )))
                .put("screenshots", JSONArray(screenshots))
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun writeRestorationReport(profileId: String, steps: List<String>, screenshots: List<String>) {
        File(evidenceDir(), "android-shell-nav-restoration-evidence.json").writeText(
            JSONObject()
                .put("check", "FLOW-SHELL-NAV-ANDROID-RESTORATION-001")
                .put("status", "passed")
                .put("actorProfileIdSha256", sha256(profileId))
                .put("routes", JSONArray(primaryNavigationDestinations.map { it.route }))
                .put("steps", JSONArray(steps))
                .put("authenticatedActorPreserved", true)
                .put("screenshots", JSONArray(screenshots))
                .toString(2) + "\n",
        )
    }

    private fun suppressStartupPrompts() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                targetContext.packageName,
                android.Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        targetContext.getSharedPreferences("quata_startup_permission_prompts", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("app_links_prompt_seen", true).commit()
    }

    private fun optionalArgument(name: String): String? =
        arguments.getString(name)?.trim()?.takeIf(String::isNotEmpty)

    private fun credentialsFromFile(path: String): Credentials {
        val file = if (path.startsWith("app-internal:")) {
            File(targetContext.filesDir, path.removePrefix("app-internal:"))
        } else {
            File(path)
        }
        val json = JSONObject(file.readText())
        return Credentials(json.getString("country_code"), json.getString("phone"), json.getString("password"))
    }

    private fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private data class Credentials(val countryCode: String, val phone: String, val password: String)

    private fun evidenceDir(): File =
        (targetContext.getExternalFilesDir("shell-nav-evidence")
            ?: File(targetContext.filesDir, "shell-nav-evidence"))
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed" } }
}
