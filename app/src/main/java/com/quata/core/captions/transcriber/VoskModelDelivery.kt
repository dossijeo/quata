package com.quata.core.captions.transcriber

import android.content.Context
import com.quata.R
import com.quata.core.localization.QuataLanguage
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallHelper
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

enum class VoskModelLanguage(
    val languageCode: String,
    val moduleName: String,
    val resourceName: String,
    val titleRes: Int
) {
    ENGLISH(
        languageCode = "en",
        moduleName = "vosk_model_en",
        resourceName = "vosk_model_en",
        titleRes = R.string.caption_model_language_english
    ),
    SPANISH(
        languageCode = "es",
        moduleName = "vosk_model_es",
        resourceName = "vosk_model_es",
        titleRes = R.string.caption_model_language_spanish
    ),
    FRENCH(
        languageCode = "fr",
        moduleName = "vosk_model_fr",
        resourceName = "vosk_model_fr",
        titleRes = R.string.caption_model_language_french
    );

    val resourcePackageName: String
        get() = "com.quata.$moduleName"

    companion object {
        fun from(language: QuataLanguage): VoskModelLanguage =
            when (language) {
                QuataLanguage.Spanish -> SPANISH
                QuataLanguage.French -> FRENCH
                QuataLanguage.English -> ENGLISH
            }

        fun from(locale: Locale): VoskModelLanguage =
            when (locale.language.lowercase(Locale.US)) {
                SPANISH.languageCode -> SPANISH
                FRENCH.languageCode -> FRENCH
                else -> ENGLISH
            }
    }
}

data class VoskModelDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytesToDownload: Long
) {
    val fraction: Float?
        get() = totalBytesToDownload
            .takeIf { it > 0L }
            ?.let { (bytesDownloaded.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
}

class VoskModelNotInstalledException(
    val language: VoskModelLanguage
) : CaptionTranscriptionException("Vosk model module ${language.moduleName} is not installed.")

class VoskModelDeliveryManager(context: Context) {
    private val appContext = context.applicationContext
    private val splitInstallManager = SplitInstallManagerFactory.create(appContext)

    fun isInstalled(language: VoskModelLanguage): Boolean {
        VoskModelSplitSupport.refresh(appContext)
        return splitInstallManager.installedModules.contains(language.moduleName) ||
            VoskModelSplitSupport.isProcessConfirmed(language) ||
            VoskModelSplitSupport.installedSplitNames(appContext).contains(language.moduleName) ||
            VoskModelSplitSupport.installedSplitSourceDirs(appContext, language).isNotEmpty()
    }

    suspend fun install(
        language: VoskModelLanguage,
        onProgress: (VoskModelDownloadProgress) -> Unit = {}
    ) {
        if (isInstalled(language)) {
            VoskModelSplitSupport.markInstalled(language)
            VoskModelSplitSupport.refresh(appContext)
            pruneOtherModels(language)
            return
        }

        val request = SplitInstallRequest.newBuilder()
            .addModule(language.moduleName)
            .build()

        suspendCancellableCoroutine { continuation ->
            var sessionId: Int? = null
            val listener = object : SplitInstallStateUpdatedListener {
                override fun onStateUpdate(state: SplitInstallSessionState) {
                    if (sessionId != null && state.sessionId() != sessionId) return
                    when (state.status()) {
                        SplitInstallSessionStatus.DOWNLOADING,
                        SplitInstallSessionStatus.INSTALLING -> {
                            onProgress(
                                VoskModelDownloadProgress(
                                    bytesDownloaded = state.bytesDownloaded(),
                                    totalBytesToDownload = state.totalBytesToDownload()
                                )
                            )
                        }
                        SplitInstallSessionStatus.INSTALLED -> {
                            splitInstallManager.unregisterListener(this)
                            VoskModelSplitSupport.markInstalled(language)
                            VoskModelSplitSupport.refresh(appContext)
                            pruneOtherModels(language)
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        SplitInstallSessionStatus.FAILED -> {
                            splitInstallManager.unregisterListener(this)
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    CaptionTranscriptionException("Could not download the captions model (${state.errorCode()}).")
                                )
                            }
                        }
                        SplitInstallSessionStatus.CANCELED,
                        SplitInstallSessionStatus.CANCELING -> {
                            splitInstallManager.unregisterListener(this)
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    CaptionTranscriptionException("Captions model download was cancelled.")
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }

            splitInstallManager.registerListener(listener)
            splitInstallManager.startInstall(request)
                .addOnSuccessListener { id -> sessionId = id }
                .addOnFailureListener { error ->
                    splitInstallManager.unregisterListener(listener)
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

            continuation.invokeOnCancellation {
                splitInstallManager.unregisterListener(listener)
                sessionId?.let(splitInstallManager::cancelInstall)
            }
        }
    }

    private fun pruneOtherModels(keep: VoskModelLanguage) {
        val removableModules = VoskModelLanguage.entries
            .filterNot { it == keep }
            .map { it.moduleName }
            .filter { moduleName ->
                splitInstallManager.installedModules.contains(moduleName) ||
                    VoskModelSplitSupport.installedSplitNames(appContext).contains(moduleName)
            }
        if (removableModules.isNotEmpty()) {
            splitInstallManager.deferredUninstall(removableModules)
        }

        VoskModelLanguage.entries
            .filterNot { it == keep }
            .forEach { language ->
                candidateModelDirectories(language).forEach { directory ->
                    runCatching { directory.deleteRecursively() }
                }
            }
    }

    private fun candidateModelDirectories(language: VoskModelLanguage): List<File> {
        val filesRoot = File(appContext.filesDir, "vosk")
        val externalRoot = appContext.getExternalFilesDir(null)?.let { File(it, "vosk") }
        val names = when (language) {
            VoskModelLanguage.FRENCH -> listOf("fr", "vosk-model-small-fr-0.22")
            VoskModelLanguage.SPANISH -> listOf("es", "vosk-model-small-es-0.42")
            VoskModelLanguage.ENGLISH -> listOf("en", "vosk-model-small-en-us-0.15")
        }
        return buildList {
            names.forEach { add(File(filesRoot, it)) }
            externalRoot?.let { root -> names.forEach { add(File(root, it)) } }
        }
    }
}

internal object VoskModelSplitSupport {
    private val processConfirmedModules: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun markInstalled(language: VoskModelLanguage) {
        processConfirmedModules += language.moduleName
    }

    fun isProcessConfirmed(language: VoskModelLanguage): Boolean =
        processConfirmedModules.contains(language.moduleName)

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        runCatching { SplitCompat.install(appContext) }
        runCatching { SplitInstallHelper.updateAppInfo(appContext) }
    }

    fun installedSplitNames(context: Context): Set<String> =
        runCatching {
            context.packageManager
                .getApplicationInfo(context.packageName, 0)
                .splitNames
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())

    fun installedSplitSourceDirs(context: Context, language: VoskModelLanguage): List<File> =
        runCatching {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            val splitNames = appInfo.splitNames.orEmpty()
            appInfo.splitSourceDirs
                ?.mapIndexedNotNull { index, sourceDir ->
                    val splitName = splitNames.getOrNull(index)
                    val matchesName = splitName == language.moduleName
                    val matchesPath = sourceDir.contains(language.moduleName, ignoreCase = true) ||
                        sourceDir.contains("split_${language.moduleName}", ignoreCase = true)
                    if (matchesName || matchesPath) File(sourceDir) else null
                }
                .orEmpty()
        }.getOrDefault(emptyList())
}
