package com.ornek.harita

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton

// Konum butonundaki pusula simgesi: halka sabit, ibre kuzeyi gösterir
class CompassIconDrawable : Drawable() {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val north = Paint(Paint.ANTI_ALIAS_FLAG)
    private val south = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hub = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var rot = 0f

    init {
        ring.style = Paint.Style.STROKE
        ring.color = 0xFFCCCCCC.toInt()
        north.style = Paint.Style.FILL
        north.color = 0xFFFF3B30.toInt()
        south.style = Paint.Style.FILL
        south.color = 0xFFEEEEEE.toInt()
        hub.style = Paint.Style.FILL
        hub.color = 0xFF2C2C2C.toInt()
    }

    fun setNorth(deg: Float) {
        rot = deg
        invalidateSelf()
    }

    override fun getIntrinsicWidth(): Int = 112
    override fun getIntrinsicHeight(): Int = 112

    override fun draw(c: Canvas) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = Math.min(b.width(), b.height()) / 2f
        ring.strokeWidth = r * 0.09f
        c.drawCircle(cx, cy, r * 0.9f, ring)
        c.save()
        c.rotate(rot, cx, cy)
        val len = r * 0.72f
        val w = r * 0.26f
        path.reset()
        path.moveTo(cx, cy - len)
        path.lineTo(cx + w, cy)
        path.lineTo(cx - w, cy)
        path.close()
        c.drawPath(path, north)
        path.reset()
        path.moveTo(cx, cy + len)
        path.lineTo(cx + w, cy)
        path.lineTo(cx - w, cy)
        path.close()
        c.drawPath(path, south)
        c.drawCircle(cx, cy, r * 0.07f, hub)
        c.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// Pusula yalnızca konum butonuyla açılır; haritaya dokununca kapanır
class CompassMode {
    var btn: ImageButton? = null
    var extra: View? = null
    var active = false
        private set
    private var holdUntil = 0L
    private var firstFix = true
    private val icon = CompassIconDrawable()

    // Pusula ile dönme/ortalama şu an yapılmalı mı (konum yuvarlakken)
    fun wanted(arrow: Boolean): Boolean {
        return !arrow && active && System.currentTimeMillis() >= holdUntil
    }

    // Konuma ortalama: ok modunda eski 5 sn kuralı, yuvarlakken ilk konum veya pusula açıkken
    fun follow(arrow: Boolean, lastTouch: Long): Boolean {
        if (arrow) return System.currentTimeMillis() - lastTouch >= 5000L
        if (firstFix) {
            firstFix = false
            return true
        }
        return wanted(false)
    }

    fun enable(arrow: Boolean) {
        if (arrow) return
        holdUntil = System.currentTimeMillis() + 1300L
        set(true)
    }

    fun set(on: Boolean) {
        if (active == on) return
        active = on
        val b = btn ?: return
        if (on) b.setImageDrawable(icon) else b.setImageResource(R.drawable.ic_location_pin)
    }

    fun needle(az: Float?) {
        if (active && az != null) icon.setNorth(-az)
    }

    fun touch(ev: MotionEvent?) {
        if (!active || ev == null || ev.actionMasked != MotionEvent.ACTION_DOWN) return
        val x = ev.rawX.toInt()
        val y = ev.rawY.toInt()
        val r = Rect()
        for (v in arrayOf(btn, extra)) {
            if (v != null && v.getGlobalVisibleRect(r) && r.contains(x, y)) return
        }
        set(false)
    }
}
