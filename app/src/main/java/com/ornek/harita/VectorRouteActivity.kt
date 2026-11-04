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
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.osmdroid.util.GeoPoint

class VectorRouteActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var styleSatellite = true
    private var locSource: GeoJsonSource? = null
    private var locLayer: SymbolLayer? = null
    private var routeSource: GeoJsonSource? = null
    private var startSource: GeoJsonSource? = null
    private var endSource: GeoJsonSource? = null
    private var dotBitmap: Bitmap? = null
    private var arrowBitmap: Bitmap? = null
    private var playBitmap: Bitmap? = null
    private var stopBitmap: Bitmap? = null

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
    private lateinit var btnRecordRoute: ImageButton
    private lateinit var routeInfoBox: FrameLayout
    private lateinit var txtRouteInfo: TextView
    private var locationManager: LocationManager? = null

    private var isRecording = false
    private var recordIntervalMs = 5000L
    private var recordStartTime = 0L
    private var lastRecordTime = 0L
    private var recordDistance = 0.0
    private var lastFixPoint: GeoPoint? = null
    private val routePoints = RouteRecorder.points
    private var drawnCount = 0
    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null

    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val infoTick = object : Runnable {
        override fun run() {
            if (!isRecording || isDestroyed) return
            updateRouteInfo()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private val hold = LongHold(3000L) { clearRouteFromMap() }
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

            if (isRecording) {
                val before = drawnCount
                if (location.provider == LocationManager.GPS_PROVIDER) recordPointIfDue(GeoPoint(location.latitude, location.longitude))
                if (routePoints.size != before) refreshRoute()
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
        createIcons()

        txtSpeed = makeBox(prefs, "speed_pos_x", "speed_pos_y", 40f, 150f, "0")
        txtAccuracy = makeBox(prefs, "acc_pos_x", "acc_pos_y", 40f, 40f, "-")
        txtAltitude = InfoStack.build(this, txtAccuracy, txtSpeed)
        setupLocationButton(prefs)
        setupRecordButton(prefs)
        setupRouteInfoBox(prefs)

        NavHelper.setupBottomNav(this, findViewById(R.id.bottomNav), R.id.nav_route)
        mapView = findViewById(R.id.vectorMapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            loadStyle()
            applyRouteView()
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
        btnMyLocationCustom.setOnTouchListener { view, event ->
            val isLocked = prefs.getBoolean("pref_layout_locked", true)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isLocked) { bDX = view.x - event.rawX; bDY = view.y - event.rawY }
                    startX = event.rawX
                    startY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isLocked) { view.x = event.rawX + bDX; view.y = event.rawY + bDY; true } else false
                }
                MotionEvent.ACTION_UP -> {
                    if (!isLocked) prefs.edit().putFloat("btn_pos_x", view.x).putFloat("btn_pos_y", view.y).apply()
                    if (Math.hypot((event.rawX - startX).toDouble(), (event.rawY - startY).toDouble()) < 10f) view.performClick()
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

    private fun setupRecordButton(prefs: SharedPreferences) {
        btnRecordRoute = ImageButton(this)
        btnRecordRoute.setImageBitmap(playBitmap)
        btnRecordRoute.setBackgroundResource(R.drawable.bg_rounded_square)
        btnRecordRoute.elevation = 10f
        btnRecordRoute.setPadding(16, 16, 16, 16)
        val recordX = prefs.getFloat("record_pos_x", (resources.displayMetrics.widthPixels - 220).toFloat())
        val recordY = prefs.getFloat("record_pos_y", (resources.displayMetrics.heightPixels - 690).toFloat())
        val recordParams = FrameLayout.LayoutParams(175, 175)
        recordParams.leftMargin = recordX.toInt()
        recordParams.topMargin = recordY.toInt()
        addContentView(btnRecordRoute, recordParams)
        cm.extra = btnRecordRoute

        var rDX = 0f
        var rDY = 0f
        var rStartX = 0f
        var rStartY = 0f
        btnRecordRoute.setOnTouchListener { view, event ->
            val isLocked = prefs.getBoolean("pref_layout_locked", true)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isLocked) { rDX = view.x - event.rawX; rDY = view.y - event.rawY }
                    rStartX = event.rawX
                    rStartY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isLocked) { view.x = event.rawX + rDX; view.y = event.rawY + rDY; true } else false
                }
                MotionEvent.ACTION_UP -> {
                    if (!isLocked) prefs.edit().putFloat("record_pos_x", view.x).putFloat("record_pos_y", view.y).apply()
                    if (Math.hypot((event.rawX - rStartX).toDouble(), (event.rawY - rStartY).toDouble()) < 10f) view.performClick()
                    true
                }
                else -> false
            }
        }

        btnRecordRoute.setOnClickListener {
            if (hold.consume()) return@setOnClickListener
            isRecording = !isRecording
            if (isRecording) {
                routePoints.clear()
                if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 77)
                btnRecordRoute.setImageBitmap(stopBitmap)
                onRecordStart()
                Toast.makeText(this, "Rota Kaydı Başlatıldı", Toast.LENGTH_SHORT).show()
            } else {
                btnRecordRoute.setImageBitmap(playBitmap)
                onRecordStop()
                Toast.makeText(this, "Rota Kaydı Durduruldu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRouteInfoBox(prefs: SharedPreferences) {
        txtRouteInfo = TextView(this)
        txtRouteInfo.text = "00:00:00\n0 m"
        txtRouteInfo.textSize = 16f
        txtRouteInfo.minWidth = 340
        txtRouteInfo.textAlignment = View.TEXT_ALIGNMENT_CENTER
        txtRouteInfo.setTextColor(Color.WHITE)
        txtRouteInfo.setBackgroundResource(R.drawable.bg_rounded_square)
        txtRouteInfo.setPadding(30, 15, 30, 15)
        routeInfoBox = FrameLayout(this)
        routeInfoBox.addView(txtRouteInfo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        routeInfoBox.visibility = View.GONE
        val infoParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        infoParams.leftMargin = prefs.getFloat("route_info_x", (resources.displayMetrics.widthPixels - 360).toFloat()).toInt()
        infoParams.topMargin = prefs.getFloat("route_info_y", 40f).toInt()
        addContentView(routeInfoBox, infoParams)

        var iDX = 0f
        var iDY = 0f
        routeInfoBox.setOnTouchListener { view, event ->
            if (prefs.getBoolean("pref_layout_locked", true)) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { iDX = view.x - event.rawX; iDY = view.y - event.rawY; true }
                MotionEvent.ACTION_MOVE -> { view.x = event.rawX + iDX; view.y = event.rawY + iDY; true }
                MotionEvent.ACTION_UP -> { prefs.edit().putFloat("route_info_x", view.x).putFloat("route_info_y", view.y).apply(); true }
                else -> false
            }
        }
    }

    private fun clearRouteFromMap() {
        if (RouteRecorder.recording) {
            Toast.makeText(this, "Kayıt sürerken rota temizlenemez", Toast.LENGTH_SHORT).show()
            return
        }
        routePoints.clear()
        startPoint = null
        endPoint = null
        refreshRoute()
        Toast.makeText(this, "Haritadaki rota temizlendi", Toast.LENGTH_SHORT).show()
    }

    private fun onRecordStart() {
        RouteRecorder.start(this)
        startPoint = null
        endPoint = null
        refreshRoute()
        updateRouteInfo()
        routeInfoBox.visibility = View.VISIBLE
        uiHandler.removeCallbacks(infoTick)
        uiHandler.postDelayed(infoTick, 1000L)
    }

    private fun syncRecording() {
        if (RouteRecorder.recording) {
            isRecording = true
            btnRecordRoute.setImageBitmap(stopBitmap)
            routeInfoBox.visibility = View.VISIBLE
            if (routePoints.isNotEmpty()) startPoint = routePoints[0]
            endPoint = null
            refreshRoute()
            updateRouteInfo()
            uiHandler.removeCallbacks(infoTick)
            uiHandler.postDelayed(infoTick, 1000L)
        } else if (isRecording) {
            isRecording = false
            btnRecordRoute.setImageBitmap(playBitmap)
            routeInfoBox.visibility = View.GONE
            uiHandler.removeCallbacks(infoTick)
            if (routePoints.size >= 2) endPoint = routePoints[routePoints.size - 1]
            refreshRoute()
        }
    }

    private fun recordPointIfDue(p: GeoPoint) {}

    private fun onRecordStop() {
        uiHandler.removeCallbacks(infoTick)
        routeInfoBox.visibility = View.GONE
        val saved = RouteRecorder.stop(this)
        if (!saved) {
            startPoint = null
            refreshRoute()
            Toast.makeText(this, "Kaydedilecek rota noktası yok", Toast.LENGTH_SHORT).show()
            return
        }
        endPoint = routePoints[routePoints.size - 1]
        refreshRoute()
    }

    private fun updateRouteInfo() {
        txtRouteInfo.text = RouteRecorder.info()
    }

    private val emptyJson = "{\"type\":\"FeatureCollection\",\"features\":[]}"

    private fun pointJson(p: GeoPoint?): String {
        if (p == null) return emptyJson
        return "{\"type\":\"Point\",\"coordinates\":[" + p.longitude + "," + p.latitude + "]}"
    }

    private fun lineJson(): String {
        if (routePoints.size < 2) return emptyJson
        val sb = StringBuilder("{\"type\":\"LineString\",\"coordinates\":[")
        for (i in routePoints.indices) {
            if (i > 0) sb.append(',')
            sb.append('[').append(routePoints[i].longitude).append(',').append(routePoints[i].latitude).append(']')
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun refreshRoute() {
        drawnCount = routePoints.size
        if (RouteRecorder.recording && routePoints.isNotEmpty()) startPoint = routePoints[0]
        routeSource?.setGeoJson(lineJson())
        startSource?.setGeoJson(pointJson(startPoint))
        endSource?.setGeoJson(pointJson(endPoint))
    }

    private fun loadStyle() {
        val m = map ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val sat = prefs.getBoolean("is_route_satellite", true)
        styleSatellite = sat
        m.setMaxZoomPreference(if (sat) 20.0 else 22.0)
        m.setStyle(VectorStyle.URL) { style ->
            VectorStyle.apply(style, sat)
            setupRouteLayers(style)
            setupLocationLayer(style)
        }
    }

    private fun addDotLayer(style: Style, id: String, color: Int): GeoJsonSource {
        val d = resources.displayMetrics.density
        val src = GeoJsonSource(id + "-src")
        style.addSource(src)
        val layer = CircleLayer(id + "-layer", id + "-src")
        layer.setProperties(
            PropertyFactory.circleRadius(34f / d),
            PropertyFactory.circleColor(color),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(6f / d)
        )
        style.addLayer(layer)
        return src
    }

    private fun setupRouteLayers(style: Style) {
        val d = resources.displayMetrics.density
        val rs = GeoJsonSource("route-src")
        style.addSource(rs)
        val line = LineLayer("route-layer", "route-src")
        line.setProperties(
            PropertyFactory.lineColor(Color.parseColor("#2979FF")),
            PropertyFactory.lineWidth(16f / d),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
        style.addLayer(line)
        routeSource = rs
        startSource = addDotLayer(style, "route-start", Color.parseColor("#00C853"))
        endSource = addDotLayer(style, "route-end", Color.parseColor("#FF1744"))
        refreshRoute()
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

        val play = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c3 = Canvas(play)
        val paintBg = Paint()
        paintBg.color = Color.BLACK
        paintBg.style = Paint.Style.FILL
        paintBg.isAntiAlias = true
        c3.drawCircle(64f, 64f, 60f, paintBg)
        val paintOr = Paint()
        paintOr.color = Color.parseColor("#FF8800")
        paintOr.style = Paint.Style.FILL
        paintOr.isAntiAlias = true
        val playPath = Path()
        playPath.moveTo(48f, 36f)
        playPath.lineTo(96f, 64f)
        playPath.lineTo(48f, 92f)
        playPath.close()
        c3.drawPath(playPath, paintOr)
        playBitmap = play

        val stop = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c4 = Canvas(stop)
        c4.drawCircle(64f, 64f, 60f, paintBg)
        c4.drawRect(40f, 40f, 88f, 88f, paintOr)
        stopBitmap = stop
    }

    private fun setupLocationLayer(style: Style) {
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

    // Eski Rota sekmesi gibi: her açılışta Rota yakınlaştırma değeri uygulanır, konum varsa ortalanır
    private fun applyRouteView() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val z = mlZoom(prefs.getFloat("zoom_route", 15.0f).toDouble())
        if (hasFix) camTo(lastLat, lastLon, null, z, 0) else camTo(null, null, null, z, 0)
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
        lastTouchTime = System.currentTimeMillis()
        compassOn = false
        camTo(null, null, 0.0, null, 0)
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
        syncRecording()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        txtSpeed.visibility = if (prefs.getBoolean("pref_show_speed", false)) View.VISIBLE else View.GONE
        txtAccuracy.visibility = if (prefs.getBoolean("pref_show_accuracy", false)) View.VISIBLE else View.GONE
        txtAltitude.visibility = txtAccuracy.visibility
        startLocation()
        if (map != null) {
            if (prefs.getBoolean("is_route_satellite", true) != styleSatellite) loadStyle()
            applyRouteView()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        lastTouchTime = System.currentTimeMillis()
        compassOn = false; cm.touch(ev); hold.touch(ev, btnRecordRoute)
        return super.dispatchTouchEvent(ev)
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        compass.stop()
        LocationHub.unsubscribe(locationListener)
        uiHandler.removeCallbacks(infoTick)
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
        uiHandler.removeCallbacks(infoTick)
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        val intent = NavHelper.mapIntent(this)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
}
