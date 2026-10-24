package com.ornek.harita

import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ZoomActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zoom)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        setupZoomControl(
            R.id.seekBarStartup, R.id.txtStartupVal,
            prefs, "zoom_startup", 3.0f, "Değer: "
        )
        setupZoomControl(
            R.id.seekBarCustom1, R.id.txtCustom1Val,
            prefs, "custom_zoom_1", 12.0f, "Değer: "
        )
        setupZoomControl(
            R.id.seekBarCustom2, R.id.txtCustom2Val,
            prefs, "custom_zoom_2", 18.0f, "Değer: "
        )
        setupZoomControl(
            R.id.seekBarMapTab, R.id.txtMapTabVal,
            prefs, "zoom_map_tab", 3.0f, "Değer: "
        )
        setupZoomControl(
            R.id.seekBarRoute, R.id.txtRouteVal,
            prefs, "zoom_route", 15.0f, "Değer: "
        )
    }

    private fun setupZoomControl(seekBarId: Int, textViewId: Int, prefs: android.content.SharedPreferences, key: String, defaultVal: Float, label: String) {
        val seekBar = findViewById<SeekBar>(seekBarId)
        val textView = findViewById<TextView>(textViewId)

        if (seekBar != null && textView != null) {
            val savedVal = prefs.getFloat(key, defaultVal)
            seekBar.max = 22
            seekBar.progress = savedVal.toInt()
            textView.text = "$label${seekBar.progress}.0"

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val z = progress.toFloat()
                        textView.text = "$label$z"
                        prefs.edit().putFloat(key, z).apply()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }
}
