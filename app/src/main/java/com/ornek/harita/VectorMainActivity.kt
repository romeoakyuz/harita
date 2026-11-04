package com.ornek.harita

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

class VectorMainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var styleSatellite = false
    private var locSource: GeoJsonSource? = null
    private var locLayer: SymbolLayer? = null
    private var dotBitmap: Bitmap? = null
    private var arrowBitmap: Bitmap? = null

    private var skipNextResumeRestore = false
    private var lastTouchTime = 0L
    private var arrowMode = false
    private var compassOn = false
    private var lastBearing = -1f
    private var hasFix = false
    private var lastLat = 0.0
    private var lastLon = 0.0

    private lateinit var txtSpeed: TextView
    private lateinit var txtAccuracy: TextView
    private lateinit var txtAltitude: TextView
    private lateinit var btnMyLocationCustom: ImageButton
    private var locationManager: LocationManager? = null

    companion object {
        var vFirstLaunch = true
    }

    private val cm = CompassMode()
    private val compass by lazy {
        CompassHelper(this) { az ->
            if (cm.wanted(arrowMode)) {
                applyCompass(az)
                cm.needle(az)
            }
        }
    }

    // osmdroid zoom -> MapLibre zoom
    private fun mlZoom(osm: Double): Double {
        return (osm - 1.0).coerceIn(0.0, 22.0)
    }

    private fun camTo(lat: Double?, lon: Double?, bearing: Double?, zoom: Double?, ms: Int) {
        val m = map ?: return
        val b = CameraPosition.Builder(m.cameraPosition)
        if (lat != null && lon != null) b.target(LatLng(lat, lon))
        if (bearing != null) b.bearing(bearing)
        if (zoom != null) b.zoom(zoom)
        val upd = CameraUpdateFactory.newCameraPosition(b.build())
        if (ms <= 0) m.moveCamera(upd) else m.animateCamera(upd, ms)
    }

    private fun applyCompass(az: Float) {
        val m = map ?: return
        val la: Double? = if (hasFix) lastLat else null
        val lo: Double? = if (hasFix) lastLon else null
        if (!compassOn) {
            compassOn = true
            camTo(la, lo, az.toDouble(), null, 250)
            return
        }
        var dif = (az - m.cameraPosition.bearing.toFloat()) % 360f
        if (dif > 180f) dif -= 360f
        if (dif < -180f) dif += 360f
        if (Math.abs(dif) >= 1.5f) camTo(la, lo, az.toDouble(), null, 120)
    }

    private fun currentHeading(): Float? {
        return if (arrowMode && lastBearing >= 0f) lastBearing else compass.currentHeading()
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speedKmh = if (location.hasSpeed()) (location.speed * 3.6).toInt() else 0
            compass.updateLocation(location)
            if (location.hasBearing()) lastBearing = location.bearing
            arrowMode = speedKmh > 5
            if (arrowMode) { compassOn = false; cm.set(false) }
            lastLat = location.latitude
            lastLon = location.longitude
            hasFix = true

            if (txtSpeed.visibility == View.VISIBLE) {
                txtSpeed.text = "$speedKmh"
            }
            if (txtAccuracy.visibility == View.VISIBLE) {
                txtAccuracy.text = GpsText.acc(location)
                txtAltitude.text = GpsText.alt(location)
            }

            updateMarker(lastLat, lastLon, if (lastBearing >= 0f) lastBearing else 0f, arrowMode)

            if (cm.follow(arrowMode, lastTouchTime)) {
                if (arrowMode && location.hasBearing()) {
                    camTo(lastLat, lastLon, location.bearing.toDouble(), null, 900)
                } else {
                    camTo(lastLat, lastLon, null, null, 900)
                }
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        setContentView(R.layout.activity_vector_main)

        txtSpeed = makeBox(prefs, "speed_pos_x", "speed_pos_y", 40f, 150f, "0")
        txtAccuracy = makeBox(prefs, "acc_pos_x", "acc_pos_y", 40f, 40f, "-")
        txtAltitude = InfoStack.build(this, txtAccuracy, txtSpeed)
        setupLocationButton(prefs)

        NavHelper.setupBottomNav(this, findViewById(R.id.bottomNav), R.id.nav_map)
        mapView = findViewById(R.id.vectorMapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            loadStyle()
            restoreCamera()
        }

        checkPermissions()

        val action = intent?.getStringExtra("tab_action")
        if (action == "map") {
            skipNextResumeRestore = true
            intent?.removeExtra("tab_action")
        }
    }

    private fun makeBox(prefs: SharedPreferences, kx: String, ky: String, dx: Float, dy: Float, initial: String): TextView {
        val tv = TextView(this)
        tv.text = initial
        tv.textSize = 16f
        tv.textAlignment = View.TEXT_ALIGNMENT_CENTER
        tv.setTextColor(Color.WHITE)
        tv.setBackgroundColor(Color.BLACK)
        tv.setPadding(30, 15, 30, 15)
        tv.visibility = View.GONE
        val container = FrameLayout(this)
        container.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = prefs.getFloat(kx, dx).toInt()
        lp.topMargin = prefs.getFloat(ky, dy).toInt()
        addContentView(container, lp)

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
        return tv
    }

    private fun setupLocationButton(prefs: SharedPreferences) {
        btnMyLocationCustom = ImageButton(this)
        btnMyLocationCustom.setImageResource(R.drawable.ic_location_pin)
        btnMyLocationCustom.setBackgroundResource(R.drawable.bg_rounded_square)
        btnMyLocationCustom.elevation = 8f
        btnMyLocationCustom.setPadding(16, 16, 16, 16)
        val btnX = prefs.getFloat("btn_pos_x", (resources.displayMetrics.widthPixels - 220).toFloat())
        val btnY = prefs.getFloat("btn_pos_y", (resources.displayMetrics.heightPixels - 490).toFloat())
        val btnParams = FrameLayout.LayoutParams(175, 175)
        btnParams.leftMargin = btnX.toInt()
        btnParams.topMargin = btnY.toInt()
        addContentView(btnMyLocationCustom, btnParams)
        cm.btn = btnMyLocationCustom

        var bDX = 0f
        var bDY = 0f
        var startX = 0f
        var startY = 0f
        val clickThreshold = 10f

        btnMyLocationCustom.setOnTouchListener { view, event ->
            val isLocked = prefs.getBoolean("pref_layout_locked", true)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isLocked) {
                        bDX = view.x - event.rawX
                        bDY = view.y - event.rawY
                    }
                    startX = event.rawX
                    startY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isLocked) {
                        view.x = event.rawX + bDX
                        view.y = event.rawY + bDY
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isLocked) {
                        prefs.edit().putFloat("btn_pos_x", view.x).putFloat("btn_pos_y", view.y).apply()
                    }
                    val distance = Math.hypot((event.rawX - startX).toDouble(), (event.rawY - startY).toDouble()).toFloat()
                    if (distance < clickThreshold) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        btnMyLocationCustom.setOnClickListener {
            if (hasFix) {
                val zoom1 = prefs.getFloat("custom_zoom_1", 12.0f).toDouble()
                val h = currentHeading()
                cm.enable(arrowMode); compassOn = true; cm.needle(compass.currentHeading())
                camTo(lastLat, lastLon, h?.toDouble(), mlZoom(zoom1), 1200)
            } else {
                Toast.makeText(this, "Konum aranıyor...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadStyle() {
        val m = map ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val sat = prefs.getBoolean("is_main_satellite", false)
        styleSatellite = sat
        m.setMaxZoomPreference(if (sat) 20.0 else 22.0)
        m.setStyle(VectorStyle.URL) { style ->
            VectorStyle.apply(style, sat)
            setupLocationLayer(style)
        }
    }

    private fun createIcons() {
        val dot = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val c1 = Canvas(dot)
        val p1 = Paint()
        p1.isAntiAlias = true
        p1.color = Color.parseColor("#007AFF")
        p1.style = Paint.Style.FILL
        c1.drawCircle(30f, 30f, 27f, p1)
        p1.style = Paint.Style.STROKE
        p1.color = Color.WHITE
        p1.strokeWidth = 6f
        c1.drawCircle(30f, 30f, 27f, p1)
        dot.density = DisplayMetrics.DENSITY_DEFAULT
        dotBitmap = dot

        val arrow = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val c2 = Canvas(arrow)
        val p2 = Paint()
        p2.isAntiAlias = true
        p2.color = Color.parseColor("#007AFF")
        p2.style = Paint.Style.FILL
        val path = Path()
        path.moveTo(50f, 5f)
        path.lineTo(92f, 95f)
        path.lineTo(50f, 72f)
        path.lineTo(8f, 95f)
        path.close()
        c2.drawPath(path, p2)
        p2.style = Paint.Style.STROKE
        p2.color = Color.WHITE
        p2.strokeWidth = 6f
        c2.drawPath(path, p2)
        arrow.density = DisplayMetrics.DENSITY_DEFAULT
        arrowBitmap = arrow
    }

    private fun setupLocationLayer(style: Style) {
        if (dotBitmap == null) createIcons()
        style.addImage("loc-dot", dotBitmap!!)
        style.addImage("loc-arrow", arrowBitmap!!)
        val src = GeoJsonSource("loc-src")
        style.addSource(src)
        val layer = SymbolLayer("loc-layer", "loc-src")
        layer.setProperties(
            PropertyFactory.iconImage("loc-dot"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconSize(1f / resources.displayMetrics.density),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP)
        )
        style.addLayer(layer)
        locSource = src
        locLayer = layer
        if (hasFix) updateMarker(lastLat, lastLon, if (lastBearing >= 0f) lastBearing else 0f, arrowMode)
    }

    private fun updateMarker(lat: Double, lon: Double, bearing: Float, arrow: Boolean) {
        val src = locSource ?: return
        src.setGeoJson("{\"type\":\"Point\",\"coordinates\":[" + lon + "," + lat + "]}")
        val layer = locLayer ?: return
        if (arrow) {
            layer.setProperties(PropertyFactory.iconImage("loc-arrow"), PropertyFactory.iconRotate(bearing))
        } else {
            layer.setProperties(PropertyFactory.iconImage("loc-dot"), PropertyFactory.iconRotate(0f))
        }
    }

    private fun restoreCamera() {
        val m = map ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (vFirstLaunch) {
            vFirstLaunch = false
            camTo(39.0, 35.0, null, mlZoom(prefs.getFloat("zoom_startup", 3.0f).toDouble()), 0)
        } else if (skipNextResumeRestore) {
            skipNextResumeRestore = false
        } else {
            val cur = m.cameraPosition
            val z = prefs.getFloat("v_last_zoom", cur.zoom.toFloat()).toDouble()
            val la = prefs.getFloat("v_last_lat", (cur.target?.latitude ?: 39.0).toFloat()).toDouble()
            val lo = prefs.getFloat("v_last_lon", (cur.target?.longitude ?: 35.0).toFloat()).toDouble()
            camTo(la, lo, null, z, 0)
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocation()
        }
    }

    private fun startLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            locationManager = lm
            LocationHub.subscribe(this, locationListener)
            LocationHub.lastKnown(this)?.let { lastLat = it.latitude; lastLon = it.longitude; hasFix = true; updateMarker(lastLat, lastLon, if (lastBearing >= 0f) lastBearing else 0f, arrowMode) }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        cm.set(false)
        setIntent(intent)
        val action = intent?.getStringExtra("tab_action")
        if (action == "map") {
            skipNextResumeRestore = true
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            lastTouchTime = System.currentTimeMillis()
            compassOn = false
            camTo(LocationHub.lastKnown(this)?.latitude ?: 39.0, LocationHub.lastKnown(this)?.longitude ?: 35.0, 0.0, mlZoom(prefs.getFloat("zoom_map_tab", 3.0f).toDouble()), 0)
            intent?.removeExtra("tab_action")
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        Orient.apply(this)
        mapView.onResume()
        compass.start()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        txtSpeed.visibility = if (prefs.getBoolean("pref_show_speed", false)) View.VISIBLE else View.GONE
        txtAccuracy.visibility = if (prefs.getBoolean("pref_show_accuracy", false)) View.VISIBLE else View.GONE
        txtAltitude.visibility = txtAccuracy.visibility
        startLocation()
        if (map != null) {
            if (prefs.getBoolean("is_main_satellite", false) != styleSatellite) loadStyle()
            restoreCamera()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        lastTouchTime = System.currentTimeMillis()
        compassOn = false; cm.touch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        compass.stop()
        LocationHub.unsubscribe(locationListener)
        val m = map
        if (m != null) {
            val cp = m.cameraPosition
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit()
                .putFloat("v_last_zoom", cp.zoom.toFloat())
                .putFloat("v_last_lat", (cp.target?.latitude ?: 39.0).toFloat())
                .putFloat("v_last_lon", (cp.target?.longitude ?: 35.0).toFloat())
                .apply()
        }
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        finishAffinity()
    }
}
