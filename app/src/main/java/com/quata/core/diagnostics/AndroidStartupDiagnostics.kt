package com.quata.core.diagnostics

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.quata.BuildConfig

/**
 * Small, debug-only startup markers intended to delimit Android process startup in logcat and
 * Perfetto. Markers deliberately contain neither account, intent nor network data.
 */
object AndroidStartupDiagnostics {
    private const val TAG = "QuataStartup"

    fun begin(phase: String): Long? {
        if (!BuildConfig.DEBUG) return null
        val startedAt = SystemClock.elapsedRealtime()
        Trace.beginSection("QuataStartup:$phase")
        Log.d(TAG, "begin phase=$phase elapsedMs=$startedAt")
        return startedAt
    }

    fun end(phase: String, startedAt: Long?) {
        if (!BuildConfig.DEBUG || startedAt == null) return
        val finishedAt = SystemClock.elapsedRealtime()
        Log.d(
            TAG,
            "end phase=$phase elapsedMs=$finishedAt durationMs=${finishedAt - startedAt}",
        )
        Trace.endSection()
    }

    fun mark(phase: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "mark phase=$phase elapsedMs=${SystemClock.elapsedRealtime()}")
        }
    }

    fun startedAt(): Long? = if (BuildConfig.DEBUG) SystemClock.elapsedRealtime() else null

    fun completed(phase: String, startedAt: Long?) {
        if (!BuildConfig.DEBUG || startedAt == null) return
        val finishedAt = SystemClock.elapsedRealtime()
        Log.d(
            TAG,
            "complete phase=$phase elapsedMs=$finishedAt durationMs=${finishedAt - startedAt}",
        )
    }
}
