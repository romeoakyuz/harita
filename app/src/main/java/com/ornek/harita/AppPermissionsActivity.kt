package com.ornek.harita

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AppPermissionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_permissions)

        findViewById<LinearLayout>(R.id.layoutNotification).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Bu cihazda desteklenmiyor", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<LinearLayout>(R.id.layoutBattery).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Pil ayarlarına ulaşılamadı", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<LinearLayout>(R.id.layoutAutoStart).setOnClickListener {
            Toast.makeText(this, "Xiaomi/Özel arayüz otomatik başlatma ayarlarına yönlendiriliyorsunuz", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent().apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
    }
}
