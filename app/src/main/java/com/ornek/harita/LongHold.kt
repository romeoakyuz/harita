package com.ornek.harita

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

// Bir butona belirli süre basılı tutmayı algılar (parmak kayarsa iptal olur)
class LongHold(private val ms: Long, private val onFire: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var task: Runnable? = null
    private var fired = false
    private var x0 = 0f
    private var y0 = 0f

    fun touch(ev: MotionEvent?, target: View?) {
        if (ev == null) return
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancel()
                fired = false
                if (target == null) return
                val r = Rect()
                if (target.getGlobalVisibleRect(r) && r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    x0 = ev.rawX
                    y0 = ev.rawY
                    val t = Runnable {
                        fired = true
                        target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onFire()
                    }
                    task = t
                    handler.postDelayed(t, ms)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (Math.hypot((ev.rawX - x0).toDouble(), (ev.rawY - y0).toDouble()) > 30.0) cancel()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancel()
        }
    }

    private fun cancel() {
        task?.let { handler.removeCallbacks(it) }
        task = null
    }

    // Uzun basış tetiklendiyse true döner (ardından gelen tıklama yok sayılmalı)
    fun consume(): Boolean {
        val f = fired
        fired = false
        return f
    }
}
