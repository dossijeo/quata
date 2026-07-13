package com.quata.core.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

object QuataProximityState : SensorEventListener {
    @Volatile
    private var near: Boolean = false
    @Volatile
    private var rawNear: Boolean = false
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var appContext: Context? = null
    private var started = false
    private val handler = Handler(Looper.getMainLooper())
    private var confirmNearRunnable: Runnable? = null

    fun start(context: Context) {
        if (started) return
        val appContext = context.applicationContext
        this.appContext = appContext
        val manager = appContext.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        sensorManager = manager
        proximitySensor = sensor
        started = true
        if (sensor != null) {
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        cancelNearConfirmation()
        sensorManager?.unregisterListener(this)
        sensorManager = null
        proximitySensor = null
        near = false
        rawNear = false
        appContext = null
        started = false
        releaseProximityWakeLock()
    }

    fun isNear(): Boolean = near

    override fun onSensorChanged(event: SensorEvent) {
        val sensor = proximitySensor ?: return
        val distance = event.values.firstOrNull() ?: return
        val isRawNear = distance < sensor.maximumRange.coerceAtMost(PROXIMITY_NEAR_THRESHOLD_CM)
        if (isRawNear == rawNear) return
        rawNear = isRawNear
        if (isRawNear) {
            scheduleNearConfirmation()
        } else {
            cancelNearConfirmation()
            near = false
            releaseProximityWakeLock()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun acquireProximityWakeLock(context: Context) {
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return
        if (!powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        val lock = wakeLock ?: powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "Quata:ProximityScreenOff"
        ).also { wakeLock = it }
        if (!lock.isHeld) {
            lock.acquire()
        }
    }

    private fun scheduleNearConfirmation() {
        if (confirmNearRunnable != null) return
        val runnable = Runnable {
            confirmNearRunnable = null
            if (!rawNear || !started) return@Runnable
            near = true
            appContext?.let(::acquireProximityWakeLock)
        }
        confirmNearRunnable = runnable
        handler.postDelayed(runnable, PROXIMITY_CONFIRMATION_DELAY_MILLIS)
    }

    private fun cancelNearConfirmation() {
        confirmNearRunnable?.let(handler::removeCallbacks)
        confirmNearRunnable = null
    }

    private fun releaseProximityWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
        wakeLock = null
    }

    private const val PROXIMITY_NEAR_THRESHOLD_CM = 5f
    private const val PROXIMITY_CONFIRMATION_DELAY_MILLIS = 3_000L
}
