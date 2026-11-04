package com.ornek.harita

import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class ScreenToolsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_tools)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        val switchSpeed = findViewById<Switch>(R.id.switchSpeed)
        val switchAccuracy = findViewById<Switch>(R.id.switchAccuracy)
        val switchLayoutLock = findViewById<Switch>(R.id.switchLayoutLock)

        switchSpeed.isChecked = prefs.getBoolean("pref_show_speed", false)
        switchAccuracy.isChecked = prefs.getBoolean("pref_show_accuracy", false)
        switchLayoutLock.isChecked = prefs.getBoolean("pref_layout_locked", true) // Varsayılan kilitli

        switchSpeed.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_show_speed", isChecked).apply()
        }

        switchAccuracy.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_show_accuracy", isChecked).apply()
        }

        switchLayoutLock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_layout_locked", isChecked).apply()
        }
    }
}
