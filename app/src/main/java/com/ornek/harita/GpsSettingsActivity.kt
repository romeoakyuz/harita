package com.ornek.harita

import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class GpsSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gps_settings)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val switchGps = findViewById<Switch>(R.id.switchGpsSatellite)
        val switchNetwork = findViewById<Switch>(R.id.switchNetworkGps)

        // Kayıtlı tercihleri yükle (varsayılan: açık)
        switchGps.isChecked = prefs.getBoolean("use_gps_satellite", true)
        switchNetwork.isChecked = prefs.getBoolean("use_network_gps", true)

        switchGps.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_gps_satellite", isChecked).apply()
        }

        switchNetwork.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_network_gps", isChecked).apply()
        }
    }
}
