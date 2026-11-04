package com.ornek.harita

import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class MapProviderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_provider)

        val rgProvider = findViewById<RadioGroup>(R.id.rgMapProvider)
        val rbGoogle = findViewById<RadioButton>(R.id.rbGoogle)
        val rbOsm = findViewById<RadioButton>(R.id.rbOsm)
        val rbOffline = findViewById<RadioButton>(R.id.rbOffline)
        val rbVector = findViewById<RadioButton>(R.id.rbVector)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentProvider = if (prefs.getString("map_provider", "google") == "vector") "vector" else "google"

        when (currentProvider) {
            "osm" -> rbOsm.isChecked = true
            "offline" -> rbOffline.isChecked = true
            "vector" -> rbVector.isChecked = true
            else -> rbGoogle.isChecked = true
        }

        rgProvider.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.rbOsm -> "osm"
                R.id.rbOffline -> "offline"
                R.id.rbVector -> "vector"
                else -> "google"
            }
            prefs.edit().putString("map_provider", selected).apply()
        }
    }
}
