package com.ornek.harita

import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class AppearanceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appearance)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val switchMainNormal = findViewById<Switch>(R.id.switchMainNormal)
        val switchMainSat = findViewById<Switch>(R.id.switchMainSatellite)
        val switchRouteNormal = findViewById<Switch>(R.id.switchRouteNormal)
        val switchRouteSat = findViewById<Switch>(R.id.switchRouteSatellite)

        val isMainSat = prefs.getBoolean("is_main_satellite", false)
        val isRouteSat = prefs.getBoolean("is_route_satellite", true)

        switchMainSat.isChecked = isMainSat
        switchMainNormal.isChecked = !isMainSat

        switchRouteSat.isChecked = isRouteSat
        switchRouteNormal.isChecked = !isRouteSat

        switchMainNormal.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchMainSat.isChecked = false
                prefs.edit().putBoolean("is_main_satellite", false).apply()
            }
        }

        switchMainSat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchMainNormal.isChecked = false
                prefs.edit().putBoolean("is_main_satellite", true).apply()
            }
        }

        switchRouteNormal.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchRouteSat.isChecked = false
                prefs.edit().putBoolean("is_route_satellite", false).apply()
            }
        }

        switchRouteSat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                switchRouteNormal.isChecked = false
                prefs.edit().putBoolean("is_route_satellite", true).apply()
            }
        }
    }
}
