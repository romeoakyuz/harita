package com.ornek.harita

import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.osmdroid.config.Configuration
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File

class RouteViewActivity : AppCompatActivity() {
    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidGraphicFactory.createInstance(this.application)
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        map = MapView(this)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        map.setTilesScaledToDpi(true)
        map.maxZoomLevel = 22.0
        setContentView(map)

        val provider = "google"
        val isSatellite = prefs.getBoolean("is_route_satellite", true)
        if (provider == "offline") {
            val mapFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "turkey.map")
            if (mapFile.exists()) {
                map.tileProvider = MapsForgeTileProvider(SimpleRegisterReceiver(this), MapsForgeTileSource.createFromFiles(arrayOf(mapFile)), null)
            } else {
                Toast.makeText(this, "turkey.map dosyası bulunamadı!", Toast.LENGTH_LONG).show()
            }
        } else if (provider == "osm") {
            map.setTileSource(if (isSatellite) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
        } else {
            map.setTileSource(if (isSatellite) MapSources.GoogleSatellite else MapSources.GoogleNormal)
        }

        val points = RouteStore.load(File(intent.getStringExtra("file") ?: ""))
        if (points.size < 2) {
            Toast.makeText(this, "Rota verisi okunamadı", Toast.LENGTH_SHORT).show()
            return
        }

        val line = Polyline()
        line.outlinePaint.color = Color.parseColor("#2979FF")
        line.outlinePaint.strokeWidth = 16f
        line.setPoints(points)
        map.overlays.add(line)
        RouteStore.addDot(this, map, points[0], Color.parseColor("#00C853"))
        RouteStore.addDot(this, map, points[points.size - 1], Color.parseColor("#FF1744"))
        val box = BoundingBox.fromGeoPoints(points)
        map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false, 70) }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
