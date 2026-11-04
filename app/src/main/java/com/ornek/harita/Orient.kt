package com.ornek.harita

import android.app.Activity
import android.content.pm.ActivityInfo
import android.preference.PreferenceManager

// Ekran yönü: "portrait" (dikey, yatay dönmez) veya "auto" (telefonun otomatik döndürme ayarına uyar)
object Orient {
    fun apply(act: Activity) {
        val p = PreferenceManager.getDefaultSharedPreferences(act.applicationContext).getString("pref_orientation", "auto")
        act.requestedOrientation = if (p == "portrait") ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
