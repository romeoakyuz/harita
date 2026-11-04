package com.ornek.harita

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.osmdroid.util.GeoPoint
import java.util.Locale

// Rota kaydının durumu ve mantığı. Ekran kapalıyken veya sekme değişince de sürer.
object RouteRecorder {
    @Volatile
    var recording = false
        private set
    val points = ArrayList<GeoPoint>()
    private var startTime = 0L
    private var distance = 0.0
    private var intervalMs = 5000L
    private var lastRecordTime = 0L
    private var lastFix: GeoPoint? = null

    fun start(ctx: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val mode = prefs.getString("route_mode", "walk") ?: "walk"
        intervalMs = prefs.getInt("route_interval_" + mode, 5) * 1000L
        points.clear()
        startTime = System.currentTimeMillis()
        lastRecordTime = 0L
        distance = 0.0
        lastFix = null
        recording = true
        RouteRecordService.start(ctx)
    }

    fun onLocation(l: Location) {
        if (!recording) return
        if (l.provider != LocationManager.GPS_PROVIDER) return
        val p = GeoPoint(l.latitude, l.longitude)
        lastFix = p
        val now = System.currentTimeMillis()
        if (lastRecordTime != 0L && now - lastRecordTime < intervalMs - 300L) return
        lastRecordTime = now
        if (points.isNotEmpty()) distance += points[points.size - 1].distanceToAsDouble(p)
        points.add(p)
    }

    // Kaydı bitirir; en az 2 nokta varsa Geçmiş Rotalar'a kaydeder ve true döner
    fun stop(ctx: Context): Boolean {
        if (!recording) return false
        recording = false
        RouteRecordService.stop(ctx)
        val last = lastFix
        if (last != null && points.isNotEmpty() && last != points[points.size - 1]) points.add(last)
        if (points.size < 2) return false
        RouteStore.save(ctx, startTime, points)
        return true
    }

    fun info(): String {
        val s = (System.currentTimeMillis() - startTime) / 1000
        val t = String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        val d = if (distance < 1000.0) String.format(Locale.US, "%d m", distance.toInt()) else String.format(Locale.US, "%.2f km", distance / 1000.0)
        return t + "\n" + d
    }
}

// Kayıt sürerken uygulamayı canlı tutan ön plan servisi (bildirimli, ekran kapalıyken de konum alır)
class RouteRecordService : Service() {
    companion object {
        private const val CH = "route_rec"
        private const val NID = 4711

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, RouteRecordService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, RouteRecordService::class.java))
        }
    }

    private var wl: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            RouteRecorder.onLocation(location)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val tick = object : Runnable {
        override fun run() {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NID, buildNotification())
            handler.postDelayed(this, 5000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            RouteRecorder.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NID, n)
        }
        if (!RouteRecorder.recording) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (wl == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val w = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Harita:rota")
            w.acquire(12 * 60 * 60 * 1000L)
            wl = w
        }
        LocationHub.subscribe(this, listener)
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 5000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        LocationHub.unsubscribe(listener)
        val w = wl
        if (w != null && w.isHeld) w.release()
        wl = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CH, "Rota kaydı", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(): Notification {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val vector = prefs.getString("map_provider", "google") == "vector"
        val open = Intent(this, if (vector) VectorRouteActivity::class.java else RouteActivity::class.java)
        open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pf = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openPi = PendingIntent.getActivity(this, 0, open, pf)
        val stop = Intent(this, RouteRecordService::class.java)
        stop.action = "STOP"
        val stopPi = PendingIntent.getService(this, 1, stop, pf)
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Rota kaydediliyor")
            .setContentText(RouteRecorder.info().replace("\n", "  •  "))
            .setContentIntent(openPi)
            .addAction(0, "Durdur", stopPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
