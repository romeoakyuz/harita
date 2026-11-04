package com.ornek.harita

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {
    private lateinit var btnNotif: Button
    private lateinit var btnBattery: Button
    private lateinit var btnAutoStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        btnNotif = findViewById(R.id.btnNotifPerm)
        btnBattery = findViewById(R.id.btnBatteryPerm)
        btnAutoStart = findViewById(R.id.btnAutoStartPerm)

        btnNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            }
        }
        btnBattery.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
        btnAutoStart.setOnClickListener {
            try {
                val intent = Intent()
                intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        btnNotif.text = "Bildirim İzni " + if (notifGranted) "✅" else "❌"

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        btnBattery.text = "Pil Kısıtlaması Yok " + if (pm.isIgnoringBatteryOptimizations(packageName)) "✅" else "❌"
        btnAutoStart.text = "Otomatik Başlatma Ayarı ⚙️"
    }
}
