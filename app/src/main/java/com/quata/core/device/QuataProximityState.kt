package com.quata.core.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager

object QuataProximityState : SensorEventListener {
    @Volatile
    private var near: Boolean = false
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    fun start(context: Context) {
        if (started) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        sensorManager = manager
        proximitySensor = sensor
        started = true
        if (sensor != null) {
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        acquireProximityWakeLock(appContext)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        proximitySensor = null
        near = false
        started = false
        releaseProximityWakeLock()
    }

    fun isNear(): Boolean = near

    override fun onSensorChanged(event: SensorEvent) {
        val sensor = proximitySensor ?: return
        val distance = event.values.firstOrNull() ?: return
        near = distance < sensor.maximumRange.coerceAtMost(PROXIMITY_NEAR_THRESHOLD_CM)
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

    private fun releaseProximityWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
        wakeLock = null
    }

    private const val PROXIMITY_NEAR_THRESHOLD_CM = 5f
}
