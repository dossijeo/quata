package com.quata.feature.externalshare

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareTargetAvailabilityInstrumentedTest {
    @Test
    fun shareTargetVisibilityFollowsAuthenticatedComponentState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageManager = context.packageManager
        val component = ComponentName(context.packageName, "com.quata.ShareReceiverActivity")
        val originalState = packageManager.getComponentEnabledSetting(component)
        try {
            ShareTargetAvailability.setEnabled(context, false)
            assertFalse(packageManager.hasQuataShareTarget("text/plain"))

            ShareTargetAvailability.setEnabled(context, true)
            assertTrue(packageManager.hasQuataShareTarget("text/plain"))
            assertTrue(packageManager.hasQuataShareTarget("image/jpeg"))
            assertTrue(packageManager.hasQuataShareTarget("application/pdf"))
            assertTrue(packageManager.hasQuataShareTarget("image/jpeg", Intent.ACTION_SEND_MULTIPLE))
            assertTrue(packageManager.hasQuataShareTarget("application/pdf", Intent.ACTION_SEND_MULTIPLE))
            assertTrue(packageManager.hasQuataShareTarget("*/*", Intent.ACTION_SEND_MULTIPLE))
            assertFalse(packageManager.hasQuataShareTarget("application/zip"))
        } finally {
            packageManager.setComponentEnabledSetting(
                component,
                originalState,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.hasQuataShareTarget(
        mimeType: String,
        action: String = Intent.ACTION_SEND
    ): Boolean {
        val intent = Intent(action).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        return queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.packageName == "com.quata" && it.activityInfo.name == "com.quata.ShareReceiverActivity" }
    }
}
