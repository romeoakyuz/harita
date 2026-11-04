package com.ornek.harita

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RouteStore {
    fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "rotalar")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun save(ctx: Context, startMs: Long, points: List<GeoPoint>) {
        val sb = StringBuilder()
        for (p in points) sb.append(String.format(Locale.US, "%.6f,%.6f\n", p.latitude, p.longitude))
        File(dir(ctx), "$startMs.rota").writeText(sb.toString())
    }

    fun list(ctx: Context): List<File> {
        val all = dir(ctx).listFiles()?.filter { it.name.endsWith(".rota") } ?: emptyList()
        return all.sortedByDescending { it.name.removeSuffix(".rota").toLongOrNull() ?: 0L }
    }

    fun title(f: File): String {
        val ms = f.name.removeSuffix(".rota").toLongOrNull() ?: return f.name
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))
    }

    fun load(f: File): List<GeoPoint> {
        val out = ArrayList<GeoPoint>()
        for (line in f.readLines()) {
            val s = line.split(",")
            if (s.size == 2) {
                val la = s[0].toDoubleOrNull()
                val lo = s[1].toDoubleOrNull()
                if (la != null && lo != null) out.add(GeoPoint(la, lo))
            }
        }
        return out
    }

    fun dotBitmap(color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawCircle(40f, 40f, 34f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 6f
        c.drawCircle(40f, 40f, 34f, paint)
        return bmp
    }

    fun addDot(ctx: Context, map: MapView, p: GeoPoint, color: Int): Marker {
        val m = Marker(map)
        m.position = p
        m.icon = BitmapDrawable(ctx.resources, dotBitmap(color))
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        m.setOnMarkerClickListener { _, _ -> true }
        map.overlays.add(m)
        return m
    }
}
