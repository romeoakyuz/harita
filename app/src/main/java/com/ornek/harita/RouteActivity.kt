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
    
    private lateinit var txtSpeed: TextView
    private lateinit var txtAccuracy: TextView
    private lateinit var txtAltitude: TextView
    private lateinit var btnMyLocationCustom: ImageButton
    private lateinit var btnRecordRoute: ImageButton
    
    private var isRecording = false
    private var recordIntervalMs = 5000L
    private var recordStartTime = 0L
    private var lastRecordTime = 0L
    private var recordDistance = 0.0
    private var lastFixPoint: GeoPoint? = null
    private var startMarker: org.osmdroid.views.overlay.Marker? = null
    private var endMarker: org.osmdroid.views.overlay.Marker? = null
    private lateinit var routeInfoBox: FrameLayout
    private lateinit var txtRouteInfo: TextView
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val infoTick = object : Runnable {
        override fun run() {
            if (!isRecording || isDestroyed) return
            updateRouteInfo()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private fun setupRouteInfoBox(prefs: android.content.SharedPreferences) {
        txtRouteInfo = TextView(this).apply {
            text = "00:00:00\n0 m"
            textSize = 16f
            minWidth = 340
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_rounded_square)
            setPadding(30, 15, 30, 15)
        }
        routeInfoBox = FrameLayout(this).apply {
            addView(txtRouteInfo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            visibility = View.GONE
        }
        val infoParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = prefs.getFloat("route_info_x", (resources.displayMetrics.widthPixels - 360).toFloat()).toInt()
            topMargin = prefs.getFloat("route_info_y", 40f).toInt()
        }
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
        routePolyline.setPoints(routePoints)
        startMarker?.let { map.overlays.remove(it) }
        endMarker?.let { map.overlays.remove(it) }
        startMarker = null
        endMarker = null
        map.invalidate()
        Toast.makeText(this, "Haritadaki rota temizlendi", Toast.LENGTH_SHORT).show()
    }

    private fun onRecordStart() {
        RouteRecorder.start(this)
        startMarker?.let { map.overlays.remove(it) }
        endMarker?.let { map.overlays.remove(it) }
        startMarker = null
        endMarker = null
        updateRouteInfo()
        routeInfoBox.visibility = View.VISIBLE
        uiHandler.removeCallbacks(infoTick)
        uiHandler.postDelayed(infoTick, 1000L)
        map.invalidate()
    }

    private fun syncRecording() {
        if (RouteRecorder.recording) {
            isRecording = true
            btnRecordRoute.setImageBitmap(stopBitmap)
            routeInfoBox.visibility = View.VISIBLE
            routePolyline.setPoints(routePoints)
            if (routePoints.isNotEmpty() && startMarker == null) startMarker = RouteStore.addDot(this, map, routePoints[0], Color.parseColor("#00C853"))
            updateRouteInfo()
            uiHandler.removeCallbacks(infoTick)
            uiHandler.postDelayed(infoTick, 1000L)
            map.invalidate()
        } else if (isRecording) {
            isRecording = false
            btnRecordRoute.setImageBitmap(playBitmap)
            routeInfoBox.visibility = View.GONE
            uiHandler.removeCallbacks(infoTick)
            routePolyline.setPoints(routePoints)
            if (routePoints.size >= 2 && endMarker == null) endMarker = RouteStore.addDot(this, map, routePoints[routePoints.size - 1], Color.parseColor("#FF1744"))
            map.invalidate()
        }
    }

    private fun recordPointIfDue(p: GeoPoint) {
        if (routePoints.isNotEmpty() && startMarker == null) startMarker = RouteStore.addDot(this, map, routePoints[0], Color.parseColor("#00C853"))
    }

    private fun onRecordStop() {
        uiHandler.removeCallbacks(infoTick)
        routeInfoBox.visibility = View.GONE
        val saved = RouteRecorder.stop(this)
        routePolyline.setPoints(routePoints)
        if (!saved) {
            startMarker?.let { map.overlays.remove(it) }
            startMarker = null
            map.invalidate()
            Toast.makeText(this, "Kaydedilecek rota noktası yok", Toast.LENGTH_SHORT).show()
            return
        }
        endMarker = RouteStore.addDot(this, map, routePoints[routePoints.size - 1], Color.parseColor("#FF1744"))
        map.invalidate()
    }

    private fun updateRouteInfo() {
        txtRouteInfo.text = RouteRecorder.info()
    }
    private var lastTouchTime = System.currentTimeMillis()
    private var arrowMode = false
    private var compassOn = false
    private fun applyCompass(az: Float) {
        if (!compassOn) {
            compassOn = true
            if (::locationOverlay.isInitialized && locationOverlay.myLocation != null) {
                map.controller.animateTo(locationOverlay.myLocation)
            }
        }
        val target = 360f - az
        var dif = (target - map.mapOrientation) % 360f
        if (dif > 180f) dif -= 360f
        if (dif < -180f) dif += 360f
        if (Math.abs(dif) >= 1.5f) map.mapOrientation = target
    }
    private var lastBearing = -1f
    private fun faceHeading() {
        val h: Float? = if (arrowMode && lastBearing >= 0f) lastBearing else compass.currentHeading()
        if (h != null) map.mapOrientation = 360f - h
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
    private val routePolyline = Polyline()
    private val routePoints = RouteRecorder.points
    
    private var locationManager: LocationManager? = null
    private lateinit var dotBitmap: Bitmap
    private lateinit var arrowBitmap: Bitmap
    private lateinit var playBitmap: Bitmap
    private lateinit var stopBitmap: Bitmap

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speedKmh = if (location.hasSpeed()) (location.speed * 3.6).toInt() else 0
            compass.updateLocation(location)
            if (location.hasBearing()) lastBearing = location.bearing
            arrowMode = speedKmh > 5
            if (arrowMode) { compassOn = false; cm.set(false) }
            val currentPoint = GeoPoint(location.latitude, location.longitude)
            
            if (txtSpeed.visibility == View.VISIBLE) {
                txtSpeed.text = "$speedKmh"
            }
            if (txtAccuracy.visibility == View.VISIBLE) {
                txtAccuracy.text = GpsText.acc(location)
                txtAltitude.text = GpsText.alt(location)
            }

            if (isRecording) {
                if (location.provider == LocationManager.GPS_PROVIDER) recordPointIfDue(currentPoint)
                routePolyline.setPoints(routePoints)
                map.invalidate()
            }

            if (speedKmh > 5) {
                if (::arrowBitmap.isInitialized) {
                    locationOverlay.setDirectionIcon(arrowBitmap)
                    locationOverlay.setPersonIcon(arrowBitmap)
                }
                if (System.currentTimeMillis() - lastTouchTime >= 5000L) map.controller.animateTo(currentPoint)
                if (!locationOverlay.isFollowLocationEnabled && System.currentTimeMillis() - lastTouchTime >= 5000L) {
                    locationOverlay.enableFollowLocation()
                }
                if (location.hasBearing() && System.currentTimeMillis() - lastTouchTime >= 5000L) {
                    map.mapOrientation = 360f - location.bearing
                }
            } else {
                if (::dotBitmap.isInitialized) {
                    locationOverlay.setDirectionIcon(dotBitmap)
                    locationOverlay.setPersonIcon(dotBitmap)
                }
                if (cm.follow(arrowMode, lastTouchTime)) map.controller.animateTo(currentPoint)
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
            topMargin = prefs.getFloat("speed_pos_y", 150f).toInt()
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
            topMargin = prefs.getFloat("acc_pos_y", 40f).toInt()
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

        routePolyline.outlinePaint.color = Color.parseColor("#2979FF")
        routePolyline.outlinePaint.strokeWidth = 16f
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
        txtAltitude = InfoStack.build(this, txtAccuracy, txtSpeed)

        btnMyLocationCustom = ImageButton(this).apply {
            setImageResource(R.drawable.ic_location_pin)
            setBackgroundResource(R.drawable.bg_rounded_square)
            elevation = 8f
            setPadding(16, 16, 16, 16)
        }
        val btnParams = FrameLayout.LayoutParams(175, 175).apply {
            leftMargin = prefs.getFloat("btn_pos_x", (resources.displayMetrics.widthPixels - 220).toFloat()).toInt()
            topMargin = prefs.getFloat("btn_pos_y", (resources.displayMetrics.heightPixels - 490).toFloat()).toInt()
        }
        addContentView(btnMyLocationCustom, btnParams)
        cm.btn = btnMyLocationCustom

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
                val zoom1 = prefs.getFloat("custom_zoom_1", 12.0f).toDouble()
                faceHeading()
                cm.enable(arrowMode); compassOn = true; cm.needle(compass.currentHeading())
                map.controller.animateTo(locationOverlay.myLocation, zoom1, 1200L)
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
        val recordX = prefs.getFloat("record_pos_x", (resources.displayMetrics.widthPixels - 220).toFloat())
        val recordY = prefs.getFloat("record_pos_y", (resources.displayMetrics.heightPixels - 690).toFloat())
        val recordParams = FrameLayout.LayoutParams(175, 175).apply {
            leftMargin = recordX.toInt()
            topMargin = recordY.toInt()
        }
        addContentView(btnRecordRoute, recordParams)
        cm.extra = btnRecordRoute
        setupRouteInfoBox(prefs)

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
            if (hold.consume()) return@setOnClickListener
            isRecording = !isRecording
            if (isRecording) {
                routePoints.clear()
                if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 77)
                routePolyline.setPoints(routePoints)
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

    private fun createIcons() {
        dotBitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val canvas1 = Canvas(dotBitmap)
        val paintDot = Paint().apply { color = Color.parseColor("#007AFF"); style = Paint.Style.FILL; isAntiAlias = true }
        canvas1.drawCircle(30f, 30f, 27f, paintDot)
        paintDot.style = Paint.Style.STROKE; paintDot.color = Color.WHITE; paintDot.strokeWidth = 6f
        canvas1.drawCircle(30f, 30f, 27f, paintDot)

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
        Orient.apply(this)
        map.onResume()
        compass.start()
        syncRecording()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        txtSpeed.visibility = if (prefs.getBoolean("pref_show_speed", false)) View.VISIBLE else View.GONE
        txtAccuracy.visibility = if (prefs.getBoolean("pref_show_accuracy", false)) View.VISIBLE else View.GONE
        txtAltitude.visibility = txtAccuracy.visibility

        map.controller.setZoom(prefs.getFloat("zoom_route", 15.0f).toDouble())

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            LocationHub.subscribe(this, locationListener)
        }

        map.post {
            if (::locationOverlay.isInitialized && locationOverlay.myLocation != null) {
                map.controller.animateTo(locationOverlay.myLocation)
            }
        }
        
        val provider = "google"
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        cm.set(false)
        map.mapOrientation = 0f
        lastTouchTime = System.currentTimeMillis()
        compassOn = false
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        lastTouchTime = System.currentTimeMillis()
        compassOn = false; cm.touch(ev); hold.touch(ev, btnRecordRoute)
        return super.dispatchTouchEvent(ev)
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        compass.stop()
        LocationHub.unsubscribe(locationListener)
        uiHandler.removeCallbacks(infoTick)
    }

    override fun onBackPressed() {
        val intent = NavHelper.mapIntent(this)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
}
