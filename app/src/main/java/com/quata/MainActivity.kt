package com.quata

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.localization.QuataLanguageManager
import com.quata.core.navigation.AppNavGraph
import com.quata.core.ui.components.QuataSplashScreen

class MainActivity : ComponentActivity() {
    private val incomingLink = mutableStateOf<Uri?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(QuataLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar()

        val appContainer = (application as QuataApp).container
        incomingLink.value = intent?.data

        setContent {
            QuataTheme {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    AppNavGraph(
                        container = appContainer,
                        incomingLink = incomingLink.value,
                        onIncomingLinkHandled = { incomingLink.value = null }
                    )
                    AnimatedVisibility(
                        visible = showSplash,
                        exit = fadeOut(animationSpec = tween(durationMillis = 420))
                    ) {
                        QuataSplashScreen(
                            onFinished = { showSplash = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    StartupSettingsPrompts(
                        enabled = !showSplash && appContainer.sessionManager.isLoggedIn()
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingLink.value = intent.data
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun StartupSettingsPrompts(enabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var issue by rememberSaveable { mutableStateOf<BackgroundAccessIssue?>(null) }
    var needsAppLinksApproval by rememberSaveable { mutableStateOf(false) }
    var dismissedIssueName by rememberSaveable { mutableStateOf<String?>(null) }
    var dismissedAppLinksPrompt by rememberSaveable { mutableStateOf(false) }

    fun refreshSettingsIssues() {
        issue = if (enabled) context.backgroundAccessIssue() else null
        needsAppLinksApproval = enabled && context.needsAppLinksUserApproval()
    }

    LaunchedEffect(enabled) {
        refreshSettingsIssues()
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshSettingsIssues()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentIssue = issue?.takeUnless { it.name == dismissedIssueName }
    if (currentIssue != null) {
        BackgroundAccessPrompt(
            issue = currentIssue,
            onDismiss = { dismissedIssueName = currentIssue.name },
            onOpenSettings = {
                dismissedIssueName = currentIssue.name
                context.openBackgroundAccessSettings(currentIssue)
            }
        )
        return
    }

    if (needsAppLinksApproval && !dismissedAppLinksPrompt) {
        AppLinksPrompt(
            onDismiss = { dismissedAppLinksPrompt = true },
            onOpenSettings = {
                dismissedAppLinksPrompt = true
                context.openAppLinksSettings()
            }
        )
    }
}

@Composable
private fun BackgroundAccessPrompt(
    issue: BackgroundAccessIssue,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.background_access_title)) },
        text = {
            Text(
                stringResource(
                    when (issue) {
                        BackgroundAccessIssue.DataSaver -> R.string.background_access_data_body
                        BackgroundAccessIssue.BatteryOptimization -> R.string.background_access_battery_body
                    }
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = onOpenSettings
            ) {
                Text(stringResource(R.string.background_access_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.background_access_not_now))
            }
        }
    )
}

@Composable
private fun AppLinksPrompt(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_links_title)) },
        text = { Text(stringResource(R.string.app_links_body)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.background_access_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.background_access_not_now))
            }
        }
    )
}

private enum class BackgroundAccessIssue {
    DataSaver,
    BatteryOptimization
}

private fun Context.backgroundAccessIssue(): BackgroundAccessIssue? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        if (connectivityManager?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
            return BackgroundAccessIssue.DataSaver
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager?.isIgnoringBatteryOptimizations(appPackageName()) == false) {
            return BackgroundAccessIssue.BatteryOptimization
        }
    }
    return null
}

@SuppressLint("NewApi")
private fun Context.needsAppLinksUserApproval(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val manager = getSystemService(DomainVerificationManager::class.java) ?: return false
    val state = runCatching { manager.getDomainVerificationUserState(appPackageName()) }.getOrNull()
        ?: return false
    if (!state.isLinkHandlingAllowed) return true
    val hostStates = state.hostToStateMap
    val quataHosts = listOf("egquata.com", "www.egquata.com")
    val declaredHosts = quataHosts.filter(hostStates::containsKey)
    if (declaredHosts.isEmpty()) return false
    return declaredHosts.any { host ->
        when (hostStates[host]) {
            DomainVerificationUserState.DOMAIN_STATE_VERIFIED,
            DomainVerificationUserState.DOMAIN_STATE_SELECTED -> false
            else -> true
        }
    }
}

@SuppressLint("BatteryLife")
private fun Context.openBackgroundAccessSettings(issue: BackgroundAccessIssue) {
    val packageUri = Uri.parse("package:${appPackageName()}")
    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    val primaryIntent = when (issue) {
        BackgroundAccessIssue.DataSaver ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, packageUri)
            } else {
                appDetailsIntent
            }
        BackgroundAccessIssue.BatteryOptimization ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
            } else {
                appDetailsIntent
            }
    }
    runCatching { startActivity(primaryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching {
            val fallback = when (issue) {
                BackgroundAccessIssue.BatteryOptimization ->
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                BackgroundAccessIssue.DataSaver ->
                    appDetailsIntent
            }
            startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        .recoverCatching {
            startActivity(appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
}

private fun Context.openAppLinksSettings() {
    val packageUri = Uri.parse("package:${appPackageName()}")
    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    val primaryIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
            data = packageUri
        }
    } else {
        appDetailsIntent
    }
    runCatching { startActivity(primaryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching {
            startActivity(appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
}

private fun Context.appPackageName(): String =
    applicationContext.packageName.takeIf { it.isNotBlank() } ?: BuildConfig.APPLICATION_ID
