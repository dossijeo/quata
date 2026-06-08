package com.quata

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
            val themeMode by appContainer.themePreferences.observeThemeMode()
                .collectAsState(initial = appContainer.themePreferences.themeMode())
            QuataTheme(mode = themeMode) {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    AppNavGraph(
                        container = appContainer,
                        themeMode = themeMode,
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
                        enabled = !showSplash
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
    var activeStep by rememberSaveable { mutableStateOf<StartupPermissionStep?>(null) }
    var activeBackgroundIssue by rememberSaveable { mutableStateOf<BackgroundAccessIssue?>(null) }
    var notificationStepHandled by rememberSaveable { mutableStateOf(false) }
    var backgroundStepHandled by rememberSaveable { mutableStateOf(false) }
    var appLinksStepHandled by rememberSaveable { mutableStateOf(false) }
    var waitingForExternalSettings by rememberSaveable { mutableStateOf(false) }
    var refreshTick by rememberSaveable { mutableStateOf(0) }

    fun advanceStartupPermissionFlow() {
        activeStep = null
        refreshTick += 1
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationStepHandled = true
        advanceStartupPermissionFlow()
    }

    fun resolveNextStartupPermissionStep() {
        if (!enabled || waitingForExternalSettings || activeStep != null) return
        if (!notificationStepHandled && context.needsNotificationPermission()) {
            activeStep = StartupPermissionStep.Notifications
            return
        }
        val backgroundIssue = context.backgroundAccessIssue()
        if (!backgroundStepHandled && backgroundIssue != null) {
            activeBackgroundIssue = backgroundIssue
            activeStep = StartupPermissionStep.BackgroundAccess
            return
        }
        if (!appLinksStepHandled && context.needsAppLinksUserApproval()) {
            activeStep = StartupPermissionStep.AppLinks
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (waitingForExternalSettings) {
                    waitingForExternalSettings = false
                }
                refreshTick += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(enabled, refreshTick, waitingForExternalSettings, activeStep) {
        if (!enabled) {
            activeStep = null
            return@LaunchedEffect
        }
        resolveNextStartupPermissionStep()
    }

    LaunchedEffect(activeStep) {
        if (activeStep == StartupPermissionStep.Notifications) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val currentIssue = activeBackgroundIssue.takeIf { activeStep == StartupPermissionStep.BackgroundAccess }
    if (currentIssue != null) {
        BackgroundAccessPrompt(
            issue = currentIssue,
            onDismiss = {
                backgroundStepHandled = true
                advanceStartupPermissionFlow()
            },
            onOpenSettings = {
                backgroundStepHandled = true
                waitingForExternalSettings = true
                advanceStartupPermissionFlow()
                context.openBackgroundAccessSettings(currentIssue)
            }
        )
        return
    }

    if (activeStep == StartupPermissionStep.AppLinks) {
        AppLinksPrompt(
            onDismiss = {
                context.markAppLinksPromptSeen()
                appLinksStepHandled = true
                advanceStartupPermissionFlow()
            },
            onOpenSettings = {
                context.markAppLinksPromptSeen()
                appLinksStepHandled = true
                waitingForExternalSettings = true
                advanceStartupPermissionFlow()
                context.openAppLinksSettings()
            }
        )
    }
}

private enum class StartupPermissionStep {
    Notifications,
    BackgroundAccess,
    AppLinks
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

private fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

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
    val hasSelectedHost = declaredHosts.any { host ->
        hostStates[host] == DomainVerificationUserState.DOMAIN_STATE_SELECTED
    }
    val hasVerifiedHost = declaredHosts.any { host ->
        hostStates[host] == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
    }
    val hasUnapprovedHost = declaredHosts.any { host ->
        when (hostStates[host]) {
            DomainVerificationUserState.DOMAIN_STATE_VERIFIED,
            DomainVerificationUserState.DOMAIN_STATE_SELECTED -> false
            else -> true
        }
    }
    return hasUnapprovedHost || (!hasSelectedHost && hasVerifiedHost && !hasSeenAppLinksPrompt())
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

private fun Context.hasSeenAppLinksPrompt(): Boolean =
    getSharedPreferences(STARTUP_PERMISSION_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_APP_LINKS_PROMPT_SEEN, false)

private fun Context.markAppLinksPromptSeen() {
    getSharedPreferences(STARTUP_PERMISSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_APP_LINKS_PROMPT_SEEN, true)
        .apply()
}

private fun Context.appPackageName(): String =
    applicationContext.packageName.takeIf { it.isNotBlank() } ?: BuildConfig.APPLICATION_ID

private const val STARTUP_PERMISSION_PREFS = "quata_startup_permission_prompts"
private const val KEY_APP_LINKS_PROMPT_SEEN = "app_links_prompt_seen"
