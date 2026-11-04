package com.ornek.harita

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.preference.PreferenceManager
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

// Rakım, Gps ve Hız kutuları: konum butonu gibi köşeleri yuvarlak, 3 haneye göre dar, ayrı ayrı sürüklenebilir.
// Konum kaydedilmemişse ekranın tam sol üstünde üst üste (Rakım, Gps, Hız) boşluksuz durur.
object InfoStack {
    fun build(act: Activity, acc: TextView, spd: TextView): TextView {
        val prefs = PreferenceManager.getDefaultSharedPreferences(act.applicationContext)

        // Bir kerelik: eski kayıtlı konumları sıfırla ki hepsi sol üste gelsin
        if (!prefs.getBoolean("info_boxes_v3", false)) {
            prefs.edit().remove("acc_pos_x").remove("acc_pos_y").remove("speed_pos_x").remove("speed_pos_y").remove("alt_pos_x").remove("alt_pos_y").putBoolean("info_boxes_v3", true).apply()
        }

        val alt = TextView(act)
        alt.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        alt.textAlignment = View.TEXT_ALIGNMENT_CENTER
        alt.setTextColor(Color.WHITE)
        alt.visibility = View.GONE
        style(alt)
        style(acc)
        style(spd)

        // Kutu yüksekliği (üçü de aynı)
        alt.text = "Rakım -"
        alt.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val h = alt.measuredHeight

        // Her kutu 3 haneli sayıyı alacak kadar geniş; daha uzun yazı gelirse kutu kendiliğinden uzar
        fit(alt, "Rakım 888m")
        fit(acc, "Gps 888m")
        fit(spd, "888")

        addBox(act, prefs, alt, "alt_pos_x", "alt_pos_y", 0f, 0f)
        restack(acc, prefs, "acc_pos_x", "acc_pos_y", 0f, h.toFloat())
        restack(spd, prefs, "speed_pos_x", "speed_pos_y", 0f, 2f * h)
        return alt
    }

    private fun style(tv: TextView) {
        tv.textSize = 15f
        tv.setBackgroundResource(R.drawable.bg_rounded_square)
        tv.setPadding(10, 0, 10, 0)
        tv.includeFontPadding = false
    }

    private fun fit(tv: TextView, sample: String) {
        tv.minWidth = Math.ceil(tv.paint.measureText(sample).toDouble()).toInt() + tv.paddingLeft + tv.paddingRight
    }

    private fun addBox(act: Activity, prefs: SharedPreferences, tv: TextView, kx: String, ky: String, dx: Float, dy: Float) {
        val container = FrameLayout(act)
        container.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = prefs.getFloat(kx, dx).toInt()
        lp.topMargin = prefs.getFloat(ky, dy).toInt()
        act.addContentView(container, lp)

        var dX = 0f
        var dY = 0f
        container.setOnTouchListener { view, event ->
            if (prefs.getBoolean("pref_layout_locked", true)) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dX = view.x - event.rawX; dY = view.y - event.rawY; true }
                MotionEvent.ACTION_MOVE -> { view.x = event.rawX + dX; view.y = event.rawY + dY; true }
                MotionEvent.ACTION_UP -> { prefs.edit().putFloat(kx, view.x).putFloat(ky, view.y).apply(); true }
                else -> false
            }
        }
    }

    // Kullanıcı bu kutuyu henüz yerleştirmediyse varsayılan yerine koy
    private fun restack(tv: TextView, prefs: SharedPreferences, kx: String, ky: String, dx: Float, dy: Float) {
        if (prefs.contains(kx) || prefs.contains(ky)) return
        val c = tv.parent as? View ?: return
        val lp = c.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.leftMargin = dx.toInt()
        lp.topMargin = dy.toInt()
        c.layoutParams = lp
    }
}
