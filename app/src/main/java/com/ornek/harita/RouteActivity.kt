package com.ornek.harita

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.osmdroid.config.Configuration
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

class RouteActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private var locationClickCount = 0
    
    private lateinit var txtSpeed: TextView
    private lateinit var txtAccuracy: TextView
    private lateinit var btnMyLocationCustom: ImageButton
    private lateinit var btnRecordRoute: ImageButton
    
    private var isRecording = false
    private val routePolyline = Polyline()
    private val routePoints = ArrayList<GeoPoint>()
    
    private var locationManager: LocationManager? = null
    private lateinit var dotBitmap: Bitmap
    private lateinit var arrowBitmap: Bitmap
    private lateinit var playBitmap: Bitmap
    private lateinit var stopBitmap: Bitmap

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speedKmh = if (location.hasSpeed()) (location.speed * 3.6).toInt() else 0
            val currentPoint = GeoPoint(location.latitude, location.longitude)
            
            if (txtSpeed.visibility == View.VISIBLE) {
                txtSpeed.text = "$speedKmh"
            }
            if (txtAccuracy.visibility == View.VISIBLE) {
                txtAccuracy.text = if (location.hasAccuracy()) "±${location.accuracy.toInt()}m" else "-"
            }

            if (isRecording) {
                routePoints.add(currentPoint)
                routePolyline.setPoints(routePoints)
                map.invalidate()
            }

            if (speedKmh > 5) {
                if (::arrowBitmap.isInitialized) {
                    locationOverlay.setDirectionIcon(arrowBitmap)
                    locationOverlay.setPersonIcon(arrowBitmap)
                }
                map.controller.animateTo(currentPoint)
                if (!locationOverlay.isFollowLocationEnabled) {
                    locationOverlay.enableFollowLocation()
                }
                if (location.hasBearing()) {
                    map.mapOrientation = 360f - location.bearing
                }
            } else {
                if (::dotBitmap.isInitialized) {
                    locationOverlay.setDirectionIcon(dotBitmap)
                    locationOverlay.setPersonIcon(dotBitmap)
                }
                if (locationOverlay.isFollowLocationEnabled) {
                    locationOverlay.disableFollowLocation()
                }
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))
        setContentView(R.layout.activity_route)
        
        NavHelper.setupBottomNav(this, findViewById(R.id.bottomNav), R.id.nav_route)
        
        findViewById<ImageButton>(R.id.btnMyLocation).visibility = View.GONE

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // 1. HIZ GÖSTERGESİ (Sürüklenebilir)
        txtSpeed = TextView(this).apply {
            text = "0"
            textSize = 16f 
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setPadding(30, 15, 30, 15)
            visibility = View.GONE
        }
        val speedContainer = FrameLayout(this).apply {
            addView(txtSpeed, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val speedParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = prefs.getFloat("speed_pos_x", 40f).toInt()
            topMargin = prefs.getFloat("speed_pos_y", 40f).toInt()
        }
        addContentView(speedContainer, speedParams)

        var sDX = 0f
        var sDY = 0f
        speedContainer.setOnTouchListener { view, event ->
            if (prefs.getBoolean("pref_layout_locked", true)) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { sDX = view.x - event.rawX; sDY = view.y - event.rawY; true }
                MotionEvent.ACTION_MOVE -> { view.x = event.rawX + sDX; view.y = event.rawY + sDY; true }
                MotionEvent.ACTION_UP -> { prefs.edit().putFloat("speed_pos_x", view.x).putFloat("speed_pos_y", view.y).apply(); true }
                else -> false
            }
        }

        // 2. DOĞRULUK GÖSTERGESİ (Sürüklenebilir)
        txtAccuracy = TextView(this).apply {
            text = "-"
            textSize = 16f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setPadding(30, 15, 30, 15)
            visibility = View.GONE
        }
        val accContainer = FrameLayout(this).apply {
            addView(txtAccuracy, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val accParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = prefs.getFloat("acc_pos_x", 40f).toInt()
            topMargin = prefs.getFloat("acc_pos_y", 120f).toInt()
        }
        addContentView(accContainer, accParams)

        var aDX = 0f
        var aDY = 0f
        accContainer.setOnTouchListener { view, event ->
            if (prefs.getBoolean("pref_layout_locked", true)) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { aDX = view.x - event.rawX; aDY = view.y - event.rawY; true }
                MotionEvent.ACTION_MOVE -> { view.x = event.rawX + aDX; view.y = event.rawY + aDY; true }
                MotionEvent.ACTION_UP -> { prefs.edit().putFloat("acc_pos_x", view.x).putFloat("acc_pos_y", view.y).apply(); true }
                else -> false
            }
        }

        map = findViewById(R.id.mapView)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        map.setTilesScaledToDpi(true)
        map.maxZoomLevel = 22.0
        map.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        routePolyline.outlinePaint.color = Color.parseColor("#FF8800")
        routePolyline.outlinePaint.strokeWidth = 10f
        map.overlays.add(routePolyline)

        val rotationOverlay = RotationGestureOverlay(map)
        rotationOverlay.isEnabled = true
        map.overlays.add(rotationOverlay)

        createIcons()

        val provider = GpsMyLocationProvider(this)
        locationOverlay = MyLocationNewOverlay(provider, map)
        locationOverlay.setPersonIcon(dotBitmap)
        locationOverlay.setDirectionIcon(dotBitmap)
        locationOverlay.setPersonAnchor(0.5f, 0.5f)
        locationOverlay.setDirectionAnchor(0.5f, 0.5f)
        locationOverlay.enableMyLocation()
        map.overlays.add(locationOverlay)

        // 3. KONUM BUTONU (Sürüklenebilir - 175x175)
        btnMyLocationCustom = ImageButton(this).apply {
            setImageResource(R.drawable.ic_location_pin)
            setBackgroundResource(R.drawable.bg_rounded_square)
            elevation = 8f
            setPadding(16, 16, 16, 16)
        }
        val btnParams = FrameLayout.LayoutParams(175, 175).apply {
            leftMargin = prefs.getFloat("btn_pos_x", (resources.displayMetrics.widthPixels - 220).toFloat()).toInt()
            topMargin = prefs.getFloat("btn_pos_y", (resources.displayMetrics.heightPixels - 380).toFloat()).toInt()
        }
        addContentView(btnMyLocationCustom, btnParams)

        var bDX = 0f
        var bDY = 0f
        var bStartX = 0f
        var bStartY = 0f
        btnMyLocationCustom.setOnTouchListener { view, event ->
            val isLocked = prefs.getBoolean("pref_layout_locked", true)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isLocked) { bDX = view.x - event.rawX; bDY = view.y - event.rawY }
                    bStartX = event.rawX; bStartY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isLocked) { view.x = event.rawX + bDX; view.y = event.rawY + bDY; true } else false
                }
                MotionEvent.ACTION_UP -> {
                    if (!isLocked) prefs.edit().putFloat("btn_pos_x", view.x).putFloat("btn_pos_y", view.y).apply()
                    if (Math.hypot((event.rawX - bStartX).toDouble(), (event.rawY - bStartY).toDouble()) < 10f) view.performClick()
                    true
                }
                else -> false
            }
        }

        btnMyLocationCustom.setOnClickListener {
            if (::locationOverlay.isInitialized && locationOverlay.myLocation != null) {
                locationClickCount++
                val zoom1 = prefs.getFloat("custom_zoom_1", 12.0f).toDouble()
                val zoom2 = prefs.getFloat("custom_zoom_2", 18.0f).toDouble()
                map.controller.animateTo(locationOverlay.myLocation)
                map.controller.setZoom(if (locationClickCount % 2 == 1) zoom1 else zoom2)
            } else {
                Toast.makeText(this, "Konum aranıyor...", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. ROTA KAYIT BUTONU (Sürüklenebilir - 175x175)
        btnRecordRoute = ImageButton(this).apply {
            setImageBitmap(playBitmap)
            setBackgroundResource(R.drawable.bg_rounded_square)
            elevation = 10f
            setPadding(16, 16, 16, 16)
        }
        val recordX = prefs.getFloat("record_pos_x", 60f)
        val recordY = prefs.getFloat("record_pos_y", 250f)
        val recordParams = FrameLayout.LayoutParams(175, 175).apply {
            leftMargin = recordX.toInt()
            topMargin = recordY.toInt()
        }
        addContentView(btnRecordRoute, recordParams)

        var rDX = 0f
        var rDY = 0f
        var rStartX = 0f
        var rStartY = 0f
        btnRecordRoute.setOnTouchListener { view, event ->
            val isLocked = prefs.getBoolean("pref_layout_locked", true)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isLocked) { rDX = view.x - event.rawX; rDY = view.y - event.rawY }
                    rStartX = event.rawX; rStartY = event.rawY
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
            isRecording = !isRecording
            if (isRecording) {
                routePoints.clear()
                routePolyline.setPoints(routePoints)
                btnRecordRoute.setImageBitmap(stopBitmap)
                Toast.makeText(this, "Rota Kaydı Başlatıldı", Toast.LENGTH_SHORT).show()
            } else {
                btnRecordRoute.setImageBitmap(playBitmap)
                Toast.makeText(this, "Rota Kaydı Durduruldu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createIcons() {
        dotBitmap = Bitmap.createBitmap(75, 75, Bitmap.Config.ARGB_8888)
        val canvas1 = Canvas(dotBitmap)
        val paintDot = Paint().apply { color = Color.parseColor("#007AFF"); style = Paint.Style.FILL; isAntiAlias = true }
        canvas1.drawCircle(37.5f, 37.5f, 33f, paintDot)
        paintDot.style = Paint.Style.STROKE; paintDot.color = Color.WHITE; paintDot.strokeWidth = 6f
        canvas1.drawCircle(37.5f, 37.5f, 33f, paintDot)

        arrowBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas2 = Canvas(arrowBitmap)
        val paintArrow = Paint().apply { color = Color.parseColor("#007AFF"); style = Paint.Style.FILL; isAntiAlias = true }
        val path = Path().apply { moveTo(50f, 5f); lineTo(92f, 95f); lineTo(50f, 72f); lineTo(8f, 95f); close() }
        canvas2.drawPath(path, paintArrow)
        paintArrow.style = Paint.Style.STROKE; paintArrow.color = Color.WHITE; paintArrow.strokeWidth = 6f
        canvas2.drawPath(path, paintArrow)

        playBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvasPlay = Canvas(playBitmap)
        val paintBg = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL; isAntiAlias = true }
        canvasPlay.drawCircle(64f, 64f, 60f, paintBg)
        val paintPlay = Paint().apply { color = Color.parseColor("#FF8800"); style = Paint.Style.FILL; isAntiAlias = true }
        val playPath = Path().apply {
            moveTo(48f, 36f)
            lineTo(96f, 64f)
            lineTo(48f, 92f)
            close()
        }
        canvasPlay.drawPath(playPath, paintPlay)

        stopBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvasStop = Canvas(stopBitmap)
        canvasStop.drawCircle(64f, 64f, 60f, paintBg)
        val paintStop = Paint().apply { color = Color.parseColor("#FF8800"); style = Paint.Style.FILL; isAntiAlias = true }
        canvasStop.drawRect(40f, 40f, 88f, 88f, paintStop)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        txtSpeed.visibility = if (prefs.getBoolean("pref_show_speed", false)) View.VISIBLE else View.GONE
        txtAccuracy.visibility = if (prefs.getBoolean("pref_show_accuracy", false)) View.VISIBLE else View.GONE

        map.controller.setZoom(prefs.getFloat("zoom_route", 15.0f).toDouble())

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
        }

        map.post {
            if (::locationOverlay.isInitialized && locationOverlay.myLocation != null) {
                map.controller.animateTo(locationOverlay.myLocation)
            }
        }
        
        val provider = prefs.getString("map_provider", "google")
        val isSatellite = prefs.getBoolean("is_route_satellite", true)
        
        if (provider == "offline") {
            val mapFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "turkey.map")
            if (mapFile.exists()) {
                map.tileProvider = MapsForgeTileProvider(SimpleRegisterReceiver(this), MapsForgeTileSource.createFromFiles(arrayOf(mapFile)), null)
            } else {
                Toast.makeText(this, "turkey.map dosyası bulunamadı!", Toast.LENGTH_LONG).show()
            }
        } else {
            if (map.tileProvider !is MapTileProviderBasic) {
                map.tileProvider = MapTileProviderBasic(applicationContext)
            }
            if (provider == "osm") {
                map.setTileSource(if (isSatellite) TileSourceFactory.USGS_SAT else TileSourceFactory.MAPNIK)
            } else {
                map.setTileSource(if (isSatellite) MapSources.GoogleSatellite else MapSources.GoogleNormal)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        locationManager?.removeUpdates(locationListener)
    }

    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
}
