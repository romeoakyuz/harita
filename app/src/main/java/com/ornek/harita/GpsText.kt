package com.ornek.harita

import android.location.Location
import android.os.Build

// Üst sol bilgi kutularındaki yazılar: "Gps 10m" ve "Rakım 123m"
object GpsText {
    fun acc(l: Location): String {
        return "Gps " + (if (l.hasAccuracy()) l.accuracy.toInt().toString() + "m" else "-")
    }

    fun alt(l: Location): String {
        var a = "-"
        if (Build.VERSION.SDK_INT >= 34 && l.hasMslAltitude()) {
            a = l.mslAltitudeMeters.toInt().toString() + "m"
        } else if (l.hasAltitude()) {
            a = l.altitude.toInt().toString() + "m"
        }
        return "Rakım " + a
    }

    fun line(l: Location): String {
        return acc(l) + "   " + alt(l)
    }
}
