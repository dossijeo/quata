package com.quata.feature.profile.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.quata.MainActivity
import com.quata.QuataApp
import com.quata.data.supabase.SupabaseCacheMode
import com.quata.feature.postcomposer.imageeditor.PostImageEditorRootTestTag
import com.quata.feature.postcomposer.imageeditor.PostImageEditorRotateTestTag
import com.quata.feature.postcomposer.imageeditor.PostImageEditorSaveTestTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class ProfileAvatarRealInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val app: QuataApp = ApplicationProvider.getApplicationContext()
    private val device = UiDevice.getInstance(instrumentation)
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun authenticatedUserChangesProfileAvatarFromCommonAccount() = runBlocking {
        val credentialsFile = optionalArgument("quataAccountAvatarCredentialsFile")
        assumeTrue(
            "ACCOUNT-AVATAR-ANDROID-REAL-001 is opt-in and requires local credentials.",
            !credentialsFile.isNullOrBlank() && optionalArgument("quataAccountAvatarEvidence") == "1",
        )
        val credentials = credentialsFromFile(credentialsFile.orEmpty())

        suppressStartupPrompts()
        app.container.authRepository.login(credentials.countryCode, credentials.phone, credentials.password)
            .getOrThrow()
        val session = app.container.sessionManager.currentSession()
        assertTrue(
            "Android must hold a real Supabase-authenticated session before changing avatar.",
            session?.isSupabaseAuthenticated() == true,
        )
        val profileId = session?.userId ?: error("android_account_avatar_session_missing")
        val originalAvatar = fetchAvatar(profileId)
        val fixtureUri = createAvatarEvidenceUri()
        val screenshots = mutableListOf<String>()
        var uploadedAvatar: String? = null
        var uploadedStoragePath: String? = null
        var publicProbe: PublicProbe? = null
        var cleanupProfileRestored = false
        var cleanupStorageDeleted = false

        try {
            ActivityScenario.launch<MainActivity>(mainIntent(fixtureUri)).use {
                compose.waitUntil(45_000) {
                    runCatching {
                        compose.onNodeWithTag(ProfileAvatarChangeTestTag, useUnmergedTree = true)
                            .fetchSemanticsNode()
                    }.isSuccess
                }
                screenshots += saveScreenshot("android-account-avatar-profile-opened")

                compose.onNodeWithTag(ProfileAvatarChangeTestTag, useUnmergedTree = true)
                    .performScrollTo()
                    .performClick()
                compose.onNodeWithTag(ProfileAvatarGalleryTestTag, useUnmergedTree = true)
                    .performClick()
                compose.waitUntil(20_000) {
                    runCatching {
                        compose.onNodeWithTag(PostImageEditorRootTestTag, useUnmergedTree = true)
                            .fetchSemanticsNode()
                    }.isSuccess
                }
                screenshots += saveScreenshot("android-account-avatar-editor-opened")

                compose.onAllNodesWithTag(PostImageEditorRotateTestTag, useUnmergedTree = true)
                    .filterToOne(hasClickAction())
                    .performClick()
                compose.onAllNodesWithTag(PostImageEditorSaveTestTag, useUnmergedTree = true)
                    .filterToOne(hasClickAction())
                    .performClick()
                compose.waitUntil(30_000) {
                    runCatching {
                        compose.onNodeWithTag(PostImageEditorRootTestTag, useUnmergedTree = true)
                            .fetchSemanticsNode()
                    }.isFailure
                }
                screenshots += saveScreenshot("android-account-avatar-editor-saved-preview")

                clickSaveProfile()
                uploadedAvatar = waitForChangedAvatar(profileId, originalAvatar)
                uploadedStoragePath = uploadedAvatar?.avatarStoragePath(profileId)
                publicProbe = probePublicAvatar(uploadedAvatar.orEmpty())
                assertTrue("android_account_avatar_public_probe_200", publicProbe?.ok == true)
                screenshots += saveScreenshot("android-account-avatar-profile-saved")
            }
        } finally {
            app.container.supabaseCommunityApi.updateProfile(profileId, mapOf("avatar_url" to originalAvatar))
            cleanupProfileRestored = waitForAvatar(profileId, originalAvatar)
            uploadedStoragePath?.let { path ->
                runCatching { app.container.supabaseCommunityApi.deletePostImageObject(path) }
                    .onSuccess { cleanupStorageDeleted = true }
            }
            writeReport(
                profileId = profileId,
                originalAvatar = originalAvatar,
                uploadedAvatar = uploadedAvatar,
                uploadedStoragePath = uploadedStoragePath,
                publicProbe = publicProbe,
                cleanupProfileRestored = cleanupProfileRestored,
                cleanupStorageDeleted = cleanupStorageDeleted || uploadedStoragePath == null,
                screenshots = screenshots,
            )
        }

        assertTrue("android_account_avatar_profile_restored", cleanupProfileRestored)
        assertTrue("android_account_avatar_storage_deleted", cleanupStorageDeleted)
        assertEquals("android_account_avatar_restored_avatar", originalAvatar, fetchAvatar(profileId))
    }

    private suspend fun fetchAvatar(profileId: String): String? =
        app.container.supabaseCommunityApi
            .getProfiles(ids = listOf(profileId), cacheMode = SupabaseCacheMode.NETWORK_ONLY)
            .firstOrNull()
            ?.avatar_url

    private suspend fun waitForChangedAvatar(profileId: String, originalAvatar: String?): String {
        repeat(30) {
            val avatar = fetchAvatar(profileId)
            if (!avatar.isNullOrBlank() && avatar != originalAvatar) return avatar
            delay(1_000)
        }
        error("android_account_avatar_remote_update_timeout")
    }

    private suspend fun waitForAvatar(profileId: String, expectedAvatar: String?): Boolean {
        repeat(20) {
            if (fetchAvatar(profileId) == expectedAvatar) return true
            delay(500)
        }
        return false
    }

    private fun mainIntent(evidenceImageUri: String): Intent =
        Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.quata.extra.SKIP_SPLASH_FOR_EVIDENCE", true)
            .putExtra("com.quata.extra.START_DESTINATION_FOR_EVIDENCE", "profile")
            .putExtra("com.quata.extra.ACCOUNT_AVATAR_EVIDENCE_IMAGE_URI", evidenceImageUri)

    private fun clickSaveProfile() {
        val saveButton = compose.onAllNodesWithTag(ProfileSaveChangesTestTag, useUnmergedTree = true)
            .filterToOne(hasClickAction())
        runCatching { saveButton.performScrollTo() }
        saveButton.performClick()
    }

    private fun createAvatarEvidenceUri(): String {
        val file = File(targetContext.filesDir, "account-avatar-evidence-source.png")
        Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (x in 0 until bitmap.width) {
                for (y in 0 until bitmap.height) {
                    val color = if (x < bitmap.width / 2) {
                        Color.rgb(239, 68, 68)
                    } else if (y < bitmap.height / 2) {
                        Color.rgb(17, 24, 39)
                    } else {
                        Color.rgb(250, 204, 21)
                    }
                    bitmap.setPixel(x, y, color)
                }
            }
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "android_account_avatar_fixture_encode_failed"
                }
            }
            bitmap.recycle()
        }
        return Uri.fromFile(file).toString()
    }

    private fun String.avatarStoragePath(profileId: String): String =
        substringAfter("/storage/v1/object/public/community-posts/", missingDelimiterValue = "")
            .takeIf { it.startsWith("avatars/$profileId/") && ".." !in it }
            ?: error("android_account_avatar_storage_path_invalid")

    private fun probePublicAvatar(url: String): PublicProbe {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        return try {
            val status = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            PublicProbe(ok = status in 200..299 && contentType.startsWith("image/"), status = status, contentType = contentType)
        } finally {
            connection.disconnect()
        }
    }

    private fun saveScreenshot(name: String): String {
        val file = File(evidenceDir(), "$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        if (bitmap == null) {
            check(device.takeScreenshot(file)) { "android_screenshot_failed:$name" }
        } else {
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "android_screenshot_encode_failed:$name"
                }
            }
        }
        return file.name
    }

    private fun writeReport(
        profileId: String,
        originalAvatar: String?,
        uploadedAvatar: String?,
        uploadedStoragePath: String?,
        publicProbe: PublicProbe?,
        cleanupProfileRestored: Boolean,
        cleanupStorageDeleted: Boolean,
        screenshots: List<String>,
    ) {
        val passed = !uploadedAvatar.isNullOrBlank() &&
            uploadedAvatar != originalAvatar &&
            publicProbe?.ok == true &&
            cleanupProfileRestored &&
            cleanupStorageDeleted
        File(evidenceDir(), "android-account-avatar-evidence.json").writeText(
            JSONObject()
                .put("check", "ACCOUNT-AVATAR-ANDROID-REAL-001")
                .put("status", if (passed) "passed" else "failed")
                .put("profileId", profileId)
                .put("originalAvatarPresent", !originalAvatar.isNullOrBlank())
                .put("uploadedAvatarChanged", !uploadedAvatar.isNullOrBlank() && uploadedAvatar != originalAvatar)
                .put("uploadedStoragePath", uploadedStoragePath)
                .put(
                    "publicProbe",
                    JSONObject()
                        .put("ok", publicProbe?.ok == true)
                        .put("status", publicProbe?.status)
                        .put("contentType", publicProbe?.contentType),
                )
                .put(
                    "cleanup",
                    JSONObject()
                        .put("profileRestored", cleanupProfileRestored)
                        .put("storageDeleted", cleanupStorageDeleted)
                        .put("storagePath", uploadedStoragePath),
                )
                .put(
                    "accountAvatarSteps",
                    JSONArray(
                        listOf(
                            "avatar_selected",
                            "avatar_editor_confirmed",
                            "avatar_uploaded",
                            "avatar_persisted",
                            "avatar_rollback_verified",
                            "avatar_cleanup_verified",
                        ),
                    ),
                )
                .put("screenshots", JSONArray(screenshots))
                .put("evidenceDirectory", evidenceDir().absolutePath)
                .toString(2) + "\n",
        )
    }

    private fun evidenceDir(): File =
        File(targetContext.filesDir, "account-avatar-evidence")
            .also { dir -> check(dir.exists() || dir.mkdirs()) { "android_evidence_directory_create_failed:${dir.absolutePath}" } }

    private fun suppressStartupPrompts() {
        targetContext.getSharedPreferences("quata_startup_permission_prompts", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("app_links_prompt_seen", true)
            .commit()
    }

    private fun optionalArgument(name: String): String? =
        arguments.getString(name)?.trim()?.takeIf(String::isNotEmpty)

    private fun credentialsFromFile(path: String): EvidenceCredentials {
        val file = if (path.startsWith("app-internal:")) {
            File(targetContext.filesDir, path.removePrefix("app-internal:"))
        } else {
            File(path)
        }
        val json = JSONObject(file.readText())
        return EvidenceCredentials(
            countryCode = json.getString("country_code"),
            phone = json.getString("phone"),
            password = json.getString("password"),
        )
    }

    private data class PublicProbe(
        val ok: Boolean,
        val status: Int,
        val contentType: String,
    )

    private data class EvidenceCredentials(
        val countryCode: String,
        val phone: String,
        val password: String,
    )
}
