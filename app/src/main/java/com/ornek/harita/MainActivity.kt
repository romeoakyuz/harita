package com.ornek.harita

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private var skipNextResumeRestore = false
    private var lastTouchTime = 0L
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
    private val cm = CompassMode()
    private val compass by lazy {
        CompassHelper(this) { az ->
            if (cm.wanted(arrowMode)) {
                applyCompass(az)
                cm.needle(az)
            }
        }
    }
    
    private lateinit var txtSpeed: TextView
    private lateinit var txtAccuracy: TextView
    private lateinit var txtAltitude: TextView
    private lateinit var btnMyLocationCustom: ImageButton
    
    private var locationManager: LocationManager? = null
    private lateinit var dotBitmap: Bitmap
    private lateinit var arrowBitmap: Bitmap
    
    companion object {
        var isFirstLaunch = true
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speedKmh = if (location.hasSpeed()) (location.speed * 3.6).toInt() else 0
            compass.updateLocation(location)
            if (location.hasBearing()) lastBearing = location.bearing
            arrowMode = speedKmh > 5
            if (arrowMode) { compassOn = false; cm.set(false) }
            
            if (txtSpeed.visibility == View.VISIBLE) {
                txtSpeed.text = "$speedKmh"
            }
            if (txtAccuracy.visibility == View.VISIBLE) {
                txtAccuracy.text = GpsText.acc(location)
                txtAltitude.text = GpsText.alt(location)
            }

            if (speedKmh > 5) {
                if (::arrowBitmap.isInitialized) {
                    locationOverlay.setDirectionIcon(arrowBitmap)
                    locationOverlay.setPersonIcon(arrowBitmap)
                }
                if (System.currentTimeMillis() - lastTouchTime >= 5000L) map.controller.animateTo(GeoPoint(location.latitude, location.longitude))
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
                if (cm.follow(arrowMode, lastTouchTime)) map.controller.animateTo(GeoPoint(location.latitude, location.longitude))
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
        if (PreferenceManager.getDefaultSharedPreferences(this).getString("map_provider", "google") == "vector") {
            startActivity(Intent(this, VectorMainActivity::class.java))
            finish()
            return
        }
        AndroidGraphicFactory.createInstance(this.application)
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        Configuration.getInstance().load(applicationContext, prefs)

        setContentView(R.layout.activity_main)

        val xmlBtn = findViewById<ImageButton>(R.id.btnMyLocation)
        xmlBtn.visibility = View.GONE

        // 1. HIZ GÖSTERGESİ (Ayrı Sürüklenebilir)
        txtSpeed = TextView(this).apply {
            text = "0"
            textSize = 16f 
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(30, 15, 30, 15)
            visibility = View.GONE
        }
        val speedX = prefs.getFloat("speed_pos_x", 40f)
        val speedY = prefs.getFloat("speed_pos_y", 150f)
        val speedContainer = FrameLayout(this).apply {
            addView(txtSpeed, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val speedParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = speedX.toInt()
            topMargin = speedY.toInt()
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

        // 2. DOĞRULUK GÖSTERGESİ (Ayrı Sürüklenebilir)
        txtAccuracy = TextView(this).apply {
            text = "-"
            textSize = 16f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(30, 15, 30, 15)
            visibility = View.GONE
        }
        val accX = prefs.getFloat("acc_pos_x", 40f)
        val accY = prefs.getFloat("acc_pos_y", 40f)
        val accContainer = FrameLayout(this).apply {
            addView(txtAccuracy, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val accParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = accX.toInt()
            topMargin = accY.toInt()
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

        // 3. KONUM BUTONU (Doğrudan Sürüklenebilir & Tıklanabilir)
        txtAltitude = InfoStack.build(this, txtAccuracy, txtSpeed)

        btnMyLocationCustom = ImageButton(this).apply {
            setImageResource(R.drawable.ic_location_pin)
            setBackgroundResource(R.drawable.bg_rounded_square)
            elevation = 8f
            setPadding(16, 16, 16, 16)
        }
        val btnX = prefs.getFloat("btn_pos_x", (resources.displayMetrics.widthPixels - 220).toFloat())
        val btnY = prefs.getFloat("btn_pos_y", (resources.displayMetrics.heightPixels - 490).toFloat())
        
        val btnParams = FrameLayout.LayoutParams(175, 175).apply {
            leftMargin = btnX.toInt()
            topMargin = btnY.toInt()
        }
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
            if (::locationOverlay.isInitialized && locationOverlay.myLocation != null) {
                val zoom1 = prefs.getFloat("custom_zoom_1", 12.0f).toDouble()
                val targetZoom = zoom1
                
                faceHeading()
                cm.enable(arrowMode); compassOn = true; cm.needle(compass.currentHeading())
                map.controller.animateTo(locationOverlay.myLocation, targetZoom, 1200L)
                // map.mapOrientation = 0f -> Kuzeye dönme engellendi, mevcut açı korunuyor
            } else {
                Toast.makeText(this, "Konum aranıyor...", Toast.LENGTH_SHORT).show()
            }
        }

        NavHelper.setupBottomNav(this, findViewById(R.id.bottomNav), R.id.nav_map)
        map = findViewById(R.id.mapView)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false) 
        map.setTilesScaledToDpi(true)
        map.maxZoomLevel = 22.0
        map.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        val rotationOverlay = RotationGestureOverlay(map)
        rotationOverlay.isEnabled = true
        map.overlays.add(rotationOverlay)

        checkPermissions()
        
        val action = intent?.getStringExtra("tab_action")
        if (action == "map") {
            skipNextResumeRestore = true
            intent?.removeExtra("tab_action")
        }
    }
    
    private fun createIcons() {
        dotBitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        val canvas1 = Canvas(dotBitmap)
        val paintDot = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#007AFF")
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        canvas1.drawCircle(30f, 30f, 27f, paintDot)
        paintDot.style = android.graphics.Paint.Style.STROKE
        paintDot.color = android.graphics.Color.WHITE
        paintDot.strokeWidth = 6f
        canvas1.drawCircle(30f, 30f, 27f, paintDot)

        arrowBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas2 = Canvas(arrowBitmap)
        val paintArrow = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#007AFF")
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        val path = android.graphics.Path().apply {
            moveTo(50f, 5f)
            lineTo(92f, 95f)
            lineTo(50f, 72f)
            lineTo(8f, 95f)
            close()
        }
        canvas2.drawPath(path, paintArrow)
        
        paintArrow.style = android.graphics.Paint.Style.STROKE
        paintArrow.color = android.graphics.Color.WHITE
        paintArrow.strokeWidth = 6f
        canvas2.drawPath(path, paintArrow)
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        } else {
            setupLocationOverlay()
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
            map.post {
                val mapTabZoom = prefs.getFloat("zoom_map_tab", 3.0f).toDouble()
                map.mapOrientation = 0f
                lastTouchTime = System.currentTimeMillis()
                compassOn = false
                map.controller.setZoom(mapTabZoom)
                map.controller.setCenter(LocationHub.lastKnown(this)?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(39.0, 35.0))
            }
            intent?.removeExtra("tab_action")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupLocationOverlay()
        }
    }

    private fun setupLocationOverlay() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useGps = prefs.getBoolean("use_gps_satellite", true)
        val useNetwork = prefs.getBoolean("use_network_gps", true)

        val provider = GpsMyLocationProvider(this)
        if (useGps) provider.addLocationSource(LocationManager.GPS_PROVIDER)
        if (useNetwork) provider.addLocationSource(LocationManager.NETWORK_PROVIDER)

        createIcons()

        locationOverlay = MyLocationNewOverlay(provider, map)
        
        locationOverlay.setPersonIcon(dotBitmap)
        locationOverlay.setDirectionIcon(dotBitmap)
        
        locationOverlay.setPersonAnchor(0.5f, 0.5f)
        locationOverlay.setDirectionAnchor(0.5f, 0.5f)

        locationOverlay.enableMyLocation()
        map.overlays.add(locationOverlay)
    }

    override fun onResume() {
        super.onResume()
        Orient.apply(this)
        map.onResume()
        compass.start()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        val showSpeed = prefs.getBoolean("pref_show_speed", false)
        val showAcc = prefs.getBoolean("pref_show_accuracy", false)
        
        txtSpeed.visibility = if (showSpeed) View.VISIBLE else View.GONE
        txtAccuracy.visibility = if (showAcc) View.VISIBLE else View.GONE
        txtAltitude.visibility = txtAccuracy.visibility
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            LocationHub.subscribe(this, locationListener)
        }
        
        val provider = "google"
        val isSatellite = prefs.getBoolean("is_main_satellite", false)
        
        if (provider == "offline") {
            val mapFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "turkey.map")
            if (mapFile.exists()) {
                val forgeSource = MapsForgeTileSource.createFromFiles(arrayOf(mapFile))
                val forgeProvider = MapsForgeTileProvider(SimpleRegisterReceiver(this), forgeSource, null)
                map.tileProvider = forgeProvider
            } else {
                Toast.makeText(this, "turkey.map dosyası bulunamadı!", Toast.LENGTH_SHORT).show()
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

        map.post {
            if (isFirstLaunch) {
                val startupZoom = prefs.getFloat("zoom_startup", 3.0f).toDouble()
                map.controller.setZoom(startupZoom)
                map.controller.setCenter(GeoPoint(39.0, 35.0))
                isFirstLaunch = false
            } else if (skipNextResumeRestore) {
                skipNextResumeRestore = false
            } else {
                val lastZoom = prefs.getFloat("last_zoom", map.zoomLevelDouble.toFloat()).toDouble()
                val lastLat = prefs.getFloat("last_lat", 39.0f).toDouble()
                val lastLon = prefs.getFloat("last_lon", 35.0f).toDouble()
                map.controller.setZoom(lastZoom)
                map.controller.setCenter(GeoPoint(lastLat, lastLon))
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        lastTouchTime = System.currentTimeMillis()
        compassOn = false; cm.touch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        compass.stop()
        LocationHub.unsubscribe(locationListener)
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit()
            .putFloat("last_zoom", map.zoomLevelDouble.toFloat())
            .putFloat("last_lat", map.mapCenter.latitude.toFloat())
            .putFloat("last_lon", map.mapCenter.longitude.toFloat())
            .apply()
    }

    override fun onBackPressed() {
        finishAffinity() 
    }
}
