package com.ornek.harita

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<LinearLayout>(R.id.layoutAppPermissions).setOnClickListener {
            startActivity(Intent(this, AppPermissionsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.layoutMapSettings).setOnClickListener {
            startActivity(Intent(this, MapSettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.layoutRouteSettings).setOnClickListener {
            Toast.makeText(this, "Rota Kayıt Ayarları yakında...", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.layoutPastRoutes).setOnClickListener {
            Toast.makeText(this, "Geçmiş Rotalar yakında...", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.layoutBookmarks).setOnClickListener {
            Toast.makeText(this, "Yer İşaretleri yakında...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
}
