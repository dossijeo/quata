package com.quata.feature.externalshare

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object ShareTargetAvailability {
    fun setEnabled(context: Context, enabled: Boolean) {
        val component = ComponentName(context.packageName, SHARE_ACTIVITY_ALIAS)
        val desiredState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (context.packageManager.getComponentEnabledSetting(component) == desiredState) return
        context.packageManager.setComponentEnabledSetting(
            component,
            desiredState,
            PackageManager.DONT_KILL_APP
        )
    }

    private const val SHARE_ACTIVITY_ALIAS = "com.quata.ShareReceiverActivity"
}
