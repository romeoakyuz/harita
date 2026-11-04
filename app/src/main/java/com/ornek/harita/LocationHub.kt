package com.ornek.harita

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat

// Harita ve Rota sekmeleri tek bir konum bağlantısını paylaşır.
// Sekme değişince bağlantı kapanıp yeniden açılmaz, son konum hemen hazırdır.
object LocationHub {
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<LocationListener>()
    private var lm: LocationManager? = null
    private var registered = false
    private var regKey = ""
    private var lastGps: Location? = null
    var last: Location? = null
        private set

    private val stopTask = Runnable { stopNow() }

    private val hub = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.provider == LocationManager.GPS_PROVIDER) {
                lastGps = location
            } else {
                // Şebeke konumu yalnızca GPS 8 sn'dir sessizse geçer
                val g = lastGps
                if (g != null && location.elapsedRealtimeNanos - g.elapsedRealtimeNanos < 8000000000L) return
            }
            last = location
            for (l in ArrayList(listeners)) l.onLocationChanged(location)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun hasPermission(ctx: Context): Boolean {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun register(ctx: Context) {
        if (!hasPermission(ctx)) return
        val app = ctx.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        val useGps = prefs.getBoolean("use_gps_satellite", true)
        val useNet = prefs.getBoolean("use_network_gps", true)
        val key = "$useGps/$useNet"
        val m = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (registered && key == regKey) return
        if (registered) {
            try { lm?.removeUpdates(hub) } catch (e: Exception) {}
            registered = false
        }
        lm = m
        regKey = key
        if (useGps) {
            try { m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, hub) } catch (e: Exception) {}
        }
        if (useNet) {
            try { m.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, hub) } catch (e: Exception) {}
        }
        registered = true
    }

    fun subscribe(ctx: Context, l: LocationListener) {
        handler.removeCallbacks(stopTask)
        if (!listeners.contains(l)) listeners.add(l)
        register(ctx)
    }

    fun unsubscribe(l: LocationListener) {
        listeners.remove(l)
        if (listeners.isEmpty()) {
            handler.removeCallbacks(stopTask)
            handler.postDelayed(stopTask, 10000L)
        }
    }

    private fun stopNow() {
        if (listeners.isNotEmpty()) return
        try { lm?.removeUpdates(hub) } catch (e: Exception) {}
        registered = false
    }

    // Ekran açılır açılmaz gösterilecek konum: önce paylaşılan son konum, yoksa sistemin son bilinen konumu (30 dk'dan yeni)
    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): Location? {
        val l = last
        if (l != null) return l
        if (!hasPermission(ctx)) return null
        val m = ctx.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (p in arrayOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                val x = m.getLastKnownLocation(p)
                if (x != null && System.currentTimeMillis() - x.time < 1800000L) {
                    if (best == null || x.time > best.time) best = x
                }
            } catch (e: Exception) {}
        }
        return best
    }
}
