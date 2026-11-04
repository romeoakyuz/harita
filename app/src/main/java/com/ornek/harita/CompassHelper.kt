package com.ornek.harita

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class CompassHelper(context: Context, private val onHeading: (Float) -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rot = FloatArray(9)
    private var sx = 0f
    private var sy = 0f
    private var hasValue = false
    private var sent = false
    private var lastSent = 0f
    private var lastTime = 0L
    private var declination = 0f
    private var declTime = 0L

    fun start() {
        hasValue = false
        sent = false
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    fun updateLocation(l: Location) {
        val now = System.currentTimeMillis()
        if (declTime != 0L && now - declTime < 60000L) return
        declTime = now
        declination = GeomagneticField(l.latitude.toFloat(), l.longitude.toFloat(), l.altitude.toFloat(), now).declination
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        SensorManager.getRotationMatrixFromVector(rot, event.values)
        val w = if (rot[8] > 0f) rot[8] else 0f
        var vx = w * rot[1] + (1f - w) * (-rot[2])
        var vy = w * rot[4] + (1f - w) * (-rot[5])
        val n = sqrt(vx * vx + vy * vy)
        if (n < 0.1f) return
        vx /= n
        vy /= n
        if (!hasValue) {
            sx = vx
            sy = vy
            hasValue = true
        } else {
            val dot = sx * vx + sy * vy
            val cr = sx * vy - sy * vx
            val dd = Math.toDegrees(abs(atan2(cr, dot)).toDouble())
            val a = if (dd > 30.0) 0.6f else if (dd > 10.0) 0.35f else 0.15f
            sx += a * (vx - sx)
            sy += a * (vy - sy)
        }
        val now = System.currentTimeMillis()
        if (now - lastTime < 50L) return
        var az = Math.toDegrees(atan2(sx, sy).toDouble()).toFloat() + declination
        az = ((az % 360f) + 360f) % 360f
        lastTime = now
        lastSent = az
        sent = true
        onHeading(az)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun currentHeading(): Float? {
        if (!hasValue) return null
        var az = Math.toDegrees(atan2(sx, sy).toDouble()).toFloat() + declination
        az = ((az % 360f) + 360f) % 360f
        return az
    }
}
