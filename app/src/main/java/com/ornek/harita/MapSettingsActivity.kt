package com.ornek.harita

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MapSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_settings)

        findViewById<LinearLayout>(R.id.layoutMapProvider).setOnClickListener {
            startActivity(Intent(this, MapProviderActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.layoutViewMode).setOnClickListener {
            startActivity(Intent(this, ViewSettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.layoutZoom).setOnClickListener {
            startActivity(Intent(this, ZoomActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.layoutGpsSelect).setOnClickListener {
            startActivity(Intent(this, GpsSettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.layoutScreenTools).setOnClickListener {
            startActivity(Intent(this, ScreenToolsActivity::class.java))
        }
    }
}
