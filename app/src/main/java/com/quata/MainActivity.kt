package com.quata

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.quata.core.di.AppContainer
import com.quata.core.platform.MainActivityFilePickerHost
import com.quata.core.platform.MainActivityPermissionHost
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.device.QuataProximityState
import com.quata.core.localization.QuataLanguageManager
import com.quata.core.navigation.AppNavGraph
import com.quata.core.ui.components.QuataSplashScreen
import com.quata.feature.externalshare.ExternalShareIntentParser
import com.quata.feature.externalshare.ExternalShareParseResult
import com.quata.feature.externalshare.ExternalSharePayload
import com.quata.feature.externalshare.ShareTargetAvailability
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val incomingLink = mutableStateOf<Uri?>(null)
    private val incomingShare = mutableStateOf<ExternalSharePayload?>(null)
    private lateinit var appContainer: AppContainer
    private var filePickerHost: MainActivityFilePickerHost? = null
    private var permissionHost: MainActivityPermissionHost? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(QuataLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.navigationBars())

        appContainer = (application as QuataApp).container
        filePickerHost = MainActivityFilePickerHost(this).also(appContainer.filePickerService::attachHost)
        permissionHost = MainActivityPermissionHost(this).also { host ->
            appContainer.permissionService.attachHost(host::request)
        }
        ShareTargetAvailability.setEnabled(this, appContainer.sessionManager.currentSession() != null)
        val launchedFromShare = intent?.action in SHARE_ACTIONS
        handleIncomingIntent(intent)

        setContent {
            val themeMode by appContainer.themePreferences.observeThemeMode()
                .collectAsState(initial = appContainer.themePreferences.themeMode())
            QuataTheme(mode = themeMode) {
                var showSplash by rememberSaveable { mutableStateOf(!launchedFromShare) }
                Box(Modifier.fillMaxSize()) {
                    AppNavGraph(
                        container = appContainer,
                        themeMode = themeMode,
                        incomingLink = incomingLink.value,
                        onIncomingLinkHandled = { incomingLink.value = null },
                        incomingShare = incomingShare.value,
                        onIncomingShareHandled = ::clearIncomingShare
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
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.navigationBars())
        QuataProximityState.start(this)
    }

    override fun onPause() {
        QuataProximityState.stop()
        super.onPause()
    }

    override fun onDestroy() {
        filePickerHost?.let { host ->
            appContainer.filePickerService.detachHost(host)
            host.close()
        }
        filePickerHost = null
        appContainer.permissionService.detachHost()
        permissionHost?.close()
        permissionHost = null
        super.onDestroy()
    }

    private fun handleIncomingIntent(sourceIntent: Intent?) {
        incomingLink.value = sourceIntent?.data?.takeIf { sourceIntent.action == Intent.ACTION_VIEW }
        val shareIntent = sourceIntent?.takeIf {
            it.action in SHARE_ACTIONS
        }
        if (shareIntent == null) {
            incomingShare.value = null
            return
        }
        if (appContainer.sessionManager.currentSession() == null) {
            ShareTargetAvailability.setEnabled(this, false)
            Toast.makeText(this, R.string.share_to_quata_login_required, Toast.LENGTH_LONG).show()
            clearIncomingShare()
            return
        }
        lifecycleScope.launch {
            when (val result = ExternalShareIntentParser.parse(this@MainActivity, shareIntent)) {
                is ExternalShareParseResult.Accepted -> incomingShare.value = result.payload
                ExternalShareParseResult.Empty -> rejectIncomingShare(R.string.share_to_quata_empty)
                ExternalShareParseResult.Unsupported -> rejectIncomingShare(R.string.error_attachment_type_not_allowed)
                ExternalShareParseResult.Unreadable -> rejectIncomingShare(R.string.share_to_quata_read_error)
                ExternalShareParseResult.TooManyFiles -> rejectIncomingShare(R.string.share_to_quata_too_many_files)
            }
        }
    }

    private fun rejectIncomingShare(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
        clearIncomingShare()
    }

    private fun clearIncomingShare() {
        incomingShare.value = null
        if (intent?.action in SHARE_ACTIONS) {
            setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
        }
    }

    private companion object {
        val SHARE_ACTIONS = setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)
    }

}

@Composable
private fun StartupSettingsPrompts(enabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var activeStep by rememberSaveable { mutableStateOf<StartupPermissionStep?>(null) }
    var notificationStepHandled by rememberSaveable { mutableStateOf(false) }
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
        if (!appLinksStepHandled && context.needsAppLinksUserApproval()) {
            activeStep = StartupPermissionStep.AppLinks
        }
    }

    DisposableEffect(lifecycleOwner, enabled) {
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
    AppLinks
}

private fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

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
