package com.example.harita

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val config = Configuration.getInstance()
        config.userAgentValue = "KisiselRotaUygulamasi/1.0"
        
        val cacheDir = File(applicationContext.cacheDir, "osmdroid")
        cacheDir.mkdirs()
        config.osmdroidBasePath = cacheDir
        config.osmdroidTileCache = File(cacheDir, "tiles")
        
        config.expirationExtendedDuration = 7L * 24L * 60L * 60L * 1000L
        config.tileFileSystemCacheMaxBytes = 500L * 1024 * 1024
        config.tileFileSystemCacheTrimBytes = 400L * 1024 * 1024
        
        config.load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        
        setContent {
            RouteTrackerApp()
        }
    }
}

fun createBlueDot(context: Context): android.graphics.Bitmap {
    val sizePx = (30 * context.resources.displayMetrics.density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
    paint.color = android.graphics.Color.BLUE
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - 6f, paint)
    return bitmap
}

fun createBlueNavArrow(context: Context): android.graphics.Bitmap {
    val sizePx = (42 * context.resources.displayMetrics.density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val path = android.graphics.Path()
    path.moveTo(sizePx / 2f, 0f)
    path.lineTo(sizePx.toFloat(), sizePx.toFloat())
    path.lineTo(sizePx / 2f, sizePx * 0.75f)
    path.lineTo(0f, sizePx.toFloat())
    path.close()
    paint.color = android.graphics.Color.BLUE
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawPath(path, paint)
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 4f
    canvas.drawPath(path, paint)
    return bitmap
}

fun createSolidDot(context: Context, color: Int): android.graphics.drawable.Drawable {
    val sizePx = (24 * context.resources.displayMetrics.density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = color
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 4f
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - 2f, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

fun createRoutePointIcon(context: Context): android.graphics.drawable.Drawable {
    val sizePx = (14 * context.resources.displayMetrics.density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
    paint.color = android.graphics.Color.RED
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 4f
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - 2f, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

fun formatDistance(meters: Float): String {
    return if (meters >= 1000) String.format("%.2f km", meters / 1000f) else "${meters.toInt()} m"
}

@Composable
fun RouteTrackerApp() {
    val navController = rememberNavController()
    var selectedPastRouteIndex by remember { mutableStateOf(-1) }
    var resetMapTrigger by remember { mutableStateOf(0) }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                NavigationBarItem(
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Harita") },
                    label = { Text("Harita") },
                    selected = currentRoute == "map",
                    onClick = { 
                        if (currentRoute != "map") {
                            navController.navigate("map") { 
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            } 
                        }
                        resetMapTrigger++
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                    label = { Text("Ayarlar") },
                    selected = currentRoute == "settings",
                    onClick = { 
                        if (currentRoute != "settings") {
                            navController.navigate("settings") { 
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            } 
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "map",
            modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background),
            enterTransition = { fadeIn(animationSpec = tween(400)) },
            exitTransition = { fadeOut(animationSpec = tween(400)) },
            popEnterTransition = { fadeIn(animationSpec = tween(400)) },
            popExitTransition = { fadeOut(animationSpec = tween(400)) }
        ) {
            composable("map") { 
                MapScreen(selectedPastRouteIndex, resetMapTrigger) { selectedPastRouteIndex = -1 }
            }
            composable("settings") { 
                SettingsScreen(
                    onShowRouteOnMap = { index ->
                        selectedPastRouteIndex = index
                        navController.navigate("map") { 
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToMap = {
                        navController.navigate("map") { 
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ) 
            }
        }
    }
}

@Composable
fun MapScreen(pastRouteIndex: Int, resetMapTrigger: Int, onClearPastRoute: () -> Unit) {
    val context = LocalContext.current
    val mapPrefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)
    
    var isTracking by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(mapPrefs.getString("map_type", "HYBRID") ?: "HYBRID") }
    
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var routePointsFolder by remember { mutableStateOf<FolderOverlay?>(null) }

    var elapsedSeconds by remember { mutableStateOf(0L) }
    var totalDistance by remember { mutableStateOf(0f) }
    var lastLoc by remember { mutableStateOf<Location?>(null) }
    
    var currentAltitude by remember { mutableStateOf(0.0) }
    var currentSpeed by remember { mutableStateOf(0f) }
    var currentAccuracy by remember { mutableStateOf(0f) }
    var currentZoom by remember { mutableStateOf(4.0) }
    var gpsQuality by remember { mutableStateOf("İyi") }

    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            myLocationOverlay?.enableMyLocation()
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    var lastCompassUpdate = 0L
    val sensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                    val now = System.currentTimeMillis()
                    if (now - lastCompassUpdate > 100) {
                        lastCompassUpdate = now
                        if (isTracking && currentSpeed <= 3f) {
                            mapViewInstance?.setMapOrientation(-event.values[0])
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    DisposableEffect(Unit) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(sensorListener) }
    }

    LaunchedEffect(resetMapTrigger) {
        if (resetMapTrigger > 0) {
            mapViewInstance?.controller?.animateTo(GeoPoint(39.0, 35.0))
            mapViewInstance?.controller?.setZoom(4.0)
            mapViewInstance?.invalidate()
        }
    }
    
    fun getBestCurrentLocation(): GeoPoint? {
        try {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) return null

            val useGps = mapPrefs.getBoolean("use_gps", true)
            val useNet = mapPrefs.getBoolean("use_network", true)

            val gpsLoc = if (useGps) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val netLoc = if (useNet) locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null

            val bestLoc: Location? = when {
                gpsLoc != null && netLoc != null -> if (gpsLoc.time > netLoc.time) gpsLoc else netLoc
                else -> gpsLoc ?: netLoc
            }
            if (bestLoc != null) return GeoPoint(bestLoc.latitude, bestLoc.longitude)
        } catch (e: Exception) { e.printStackTrace() }

        myLocationOverlay?.myLocation?.let { return it }
        return null
    }

    val locationListener = remember {
        object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                mapPrefs.edit().putFloat("last_lat", location.latitude.toFloat()).putFloat("last_lon", location.longitude.toFloat()).apply()
                currentAccuracy = if (location.hasAccuracy()) location.accuracy else 0f
                gpsQuality = when {
                    currentAccuracy <= 5f -> "Mükemmel"
                    currentAccuracy <= 15f -> "İyi"
                    currentAccuracy <= 30f -> "Orta"
                    else -> "Zayıf"
                }

                if (isTracking) {
                    val maxAllowedAcc = mapPrefs.getFloat("record_gps_accuracy", 0f)
                    if (maxAllowedAcc > 0f && location.hasAccuracy() && location.accuracy > maxAllowedAcc) {
                        return 
                    }

                    val geo = GeoPoint(location.latitude, location.longitude)

                    if (lastLoc != null) {
                        totalDistance += lastLoc!!.distanceTo(location)
                    }

                    routePolyline?.addPoint(geo)
                    lastLoc = location
                    
                    routePointsFolder?.add(Marker(mapViewInstance).apply {
                        position = geo
                        icon = createRoutePointIcon(context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> true }
                    })

                    mapViewInstance?.controller?.animateTo(geo)
                    if (currentSpeed > 3f && location.hasBearing()) {
                        mapViewInstance?.setMapOrientation(-location.bearing)
                    }
                    mapViewInstance?.invalidate()
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
    }

    LaunchedEffect(Unit) {
        mapType = mapPrefs.getString("map_type", "HYBRID") ?: "HYBRID"
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            if (isTracking) elapsedSeconds++
            
            mapViewInstance?.let { currentZoom = it.zoomLevelDouble }
            
            myLocationOverlay?.lastFix?.let { fix ->
                currentAltitude = fix.altitude
                currentSpeed = if (fix.hasSpeed()) fix.speed * 3.6f else 0f
                if (fix.hasAccuracy()) {
                    currentAccuracy = fix.accuracy
                    gpsQuality = when {
                        currentAccuracy <= 5f -> "Mükemmel"
                        currentAccuracy <= 15f -> "İyi"
                        currentAccuracy <= 30f -> "Orta"
                        else -> "Zayıf"
                    }
                }
            } ?: run {
                currentSpeed = 0f
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { try { locationManager.removeUpdates(locationListener) } catch (e: Exception) {} }
    }

    LaunchedEffect(pastRouteIndex) {
        if (pastRouteIndex >= 0) {
            val jsonStr = mapPrefs.getString("saved_routes", "[]")
            val arr = org.json.JSONArray(jsonStr)
            if (pastRouteIndex < arr.length()) {
                val routeObj = arr.getJSONObject(pastRouteIndex)
                val pts = routeObj.getJSONArray("points")
                
                val polyline = Polyline().apply {
                    outlinePaint.color = android.graphics.Color.RED
                    outlinePaint.strokeWidth = 14f
                }
                val folder = FolderOverlay().apply { name = "route_points" }
                
                for (i in 0 until pts.length()) {
                    val pt = pts.getJSONObject(i)
                    val geo = GeoPoint(pt.getDouble("lat"), pt.getDouble("lon"))
                    polyline.addPoint(geo)
                    folder.add(Marker(mapViewInstance).apply {
                        position = geo
                        icon = createRoutePointIcon(context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ -> true }
                    })
                }
                
                mapViewInstance?.overlays?.removeAll { it is Polyline || it is Marker || (it is FolderOverlay && it.name == "route_points") }
                mapViewInstance?.overlays?.add(polyline)
                mapViewInstance?.overlays?.add(folder)
                
                if (polyline.actualPoints.isNotEmpty()) {
                    mapViewInstance?.overlays?.add(Marker(mapViewInstance).apply { position = polyline.actualPoints.first(); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = createSolidDot(context, android.graphics.Color.GREEN); setOnMarkerClickListener { _, _ -> true } })
                    mapViewInstance?.overlays?.add(Marker(mapViewInstance).apply { position = polyline.actualPoints.last(); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = createSolidDot(context, android.graphics.Color.RED); setOnMarkerClickListener { _, _ -> true } })
                    mapViewInstance?.controller?.animateTo(polyline.actualPoints.first())
                    mapViewInstance?.controller?.setZoom(16.0)
                }
                mapViewInstance?.invalidate()
            }
        }
    }

    val googleRoads = remember {
        object : OnlineTileSourceBase("GoogleRoads", 0, 22, 256, "", arrayOf("https://mt0.google.com/vt/lyrs=m&hl=tr&scale=2&", "https://mt1.google.com/vt/lyrs=m&hl=tr&scale=2&", "https://mt2.google.com/vt/lyrs=m&hl=tr&scale=2&", "https://mt3.google.com/vt/lyrs=m&hl=tr&scale=2&")) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                return baseUrl + "x=$x&y=$y&z=$zoom"
            }
        }
    }

    val googleHybrid = remember {
        object : OnlineTileSourceBase("GoogleHybrid", 0, 22, 256, "", arrayOf("https://mt0.google.com/vt/lyrs=y,h&hl=tr&scale=2&", "https://mt1.google.com/vt/lyrs=y,h&hl=tr&scale=2&", "https://mt2.google.com/vt/lyrs=y,h&hl=tr&scale=2&", "https://mt3.google.com/vt/lyrs=y,h&hl=tr&scale=2&")) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                return baseUrl + "x=$x&y=$y&z=$zoom"
            }
        }
    }

    val widgetsLocked = mapPrefs.getBoolean("widgets_locked", false)
    val showSpeed = mapPrefs.getBoolean("widget_speed", false)
    var speedOffsetX by remember { mutableStateOf(mapPrefs.getInt("widget_speed_x", 16)) }
    var speedOffsetY by remember { mutableStateOf(mapPrefs.getInt("widget_speed_y", 16)) }

    val showAltitude = mapPrefs.getBoolean("widget_altitude", false)
    var altOffsetX by remember { mutableStateOf(mapPrefs.getInt("widget_altitude_x", 16)) }
    var altOffsetY by remember { mutableStateOf(mapPrefs.getInt("widget_altitude_y", 16)) }

    val showAccuracy = mapPrefs.getBoolean("widget_accuracy", false)
    var accOffsetX by remember { mutableStateOf(mapPrefs.getInt("widget_accuracy_x", 16)) }
    var accOffsetY by remember { mutableStateOf(mapPrefs.getInt("widget_accuracy_y", 120)) }

    val showGpsQuality = mapPrefs.getBoolean("widget_gps_quality", false)
    var gpsOffsetX by remember { mutableStateOf(mapPrefs.getInt("widget_gps_quality_x", 16)) }
    var gpsOffsetY by remember { mutableStateOf(mapPrefs.getInt("widget_gps_quality_y", 180)) }

    val showZoom = mapPrefs.getBoolean("widget_zoom", false)
    var zoomOffsetX by remember { mutableStateOf(mapPrefs.getInt("widget_zoom_x", 16)) }
    var zoomOffsetY by remember { mutableStateOf(mapPrefs.getInt("widget_zoom_y", 240)) }

    val showTrackingWidget = mapPrefs.getBoolean("widget_tracking", true)
    var trackingOffsetX by remember { mutableStateOf(mapPrefs.getInt("widget_tracking_x", 120)) }
    var trackingOffsetY by remember { mutableStateOf(mapPrefs.getInt("widget_tracking_y", 16)) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(if (mapType == "ROAD") googleRoads else googleHybrid)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    setTilesScaledToDpi(true)
                    tilesScaleFactor = 1.5f 
                    setMinZoomLevel(2.0)
                    setMaxZoomLevel(22.0)
                    
                    setExpectedCenter(GeoPoint(39.0, 35.0))
                    controller.setZoom(4.0)
                    
                    val rotationGestureOverlay = RotationGestureOverlay(this).apply { isEnabled = true }
                    overlays.add(rotationGestureOverlay)
                    
                    val provider = GpsMyLocationProvider(ctx).apply {
                        val useNet = mapPrefs.getBoolean("use_network", true)
                        if (useNet) {
                            try { addLocationSource(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) {}
                        }
                    }
                    val overlay = MyLocationNewOverlay(provider, this)
                    
                    val blueDot = createBlueDot(ctx)
                    overlay.setDirectionArrow(blueDot, blueDot)
                    overlay.setPersonIcon(blueDot) 
                    overlay.enableMyLocation()
                    
                    overlays.add(overlay)
                    myLocationOverlay = overlay
                    mapViewInstance = this
                }
            },
            update = { view ->
                val targetSource = if (mapType == "ROAD") googleRoads else googleHybrid
                if (view.tileProvider.tileSource != targetSource) {
                    view.setTileSource(targetSource)
                    view.invalidate()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showSpeed) {
            Box(
                modifier = Modifier.offset { IntOffset(speedOffsetX, speedOffsetY) }.pointerInput(widgetsLocked) {
                    if (!widgetsLocked) {
                        detectDragGestures { _, dragAmount ->
                            speedOffsetX += dragAmount.x.roundToInt(); speedOffsetY += dragAmount.y.roundToInt()
                            mapPrefs.edit().putInt("widget_speed_x", speedOffsetX).putInt("widget_speed_y", speedOffsetY).apply()
                        }
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format("%.0f", currentSpeed), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                            Text("km/s", fontSize = 10.sp, color = Color.White)
                        }
                    }
                    if (pastRouteIndex >= 0) {
                        Button(onClick = { 
                            onClearPastRoute()
                            mapViewInstance?.overlays?.removeAll { it is Polyline || it is Marker || (it is FolderOverlay && it.name == "route_points") }
                            mapViewInstance?.invalidate()
                        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Haritayı Temizle") }
                    }
                }
            }
        }

        if (showAltitude) {
            Box(modifier = Modifier.offset { IntOffset(altOffsetX, altOffsetY) }.pointerInput(widgetsLocked) {
                if (!widgetsLocked) detectDragGestures { _, dragAmount -> altOffsetX += dragAmount.x.roundToInt(); altOffsetY += dragAmount.y.roundToInt(); mapPrefs.edit().putInt("widget_altitude_x", altOffsetX).putInt("widget_altitude_y", altOffsetY).apply() }
            }) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) { Text("Rakım: ${currentAltitude.toInt()} m", fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }

        if (showAccuracy) {
            Box(modifier = Modifier.offset { IntOffset(accOffsetX, accOffsetY) }.pointerInput(widgetsLocked) {
                if (!widgetsLocked) detectDragGestures { _, dragAmount -> accOffsetX += dragAmount.x.roundToInt(); accOffsetY += dragAmount.y.roundToInt(); mapPrefs.edit().putInt("widget_accuracy_x", accOffsetX).putInt("widget_accuracy_y", accOffsetY).apply() }
            }) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) { Text("Doğruluk: ±${currentAccuracy.toInt()} m", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }

        if (showGpsQuality) {
            Box(modifier = Modifier.offset { IntOffset(gpsOffsetX, gpsOffsetY) }.pointerInput(widgetsLocked) {
                if (!widgetsLocked) detectDragGestures { _, dragAmount -> gpsOffsetX += dragAmount.x.roundToInt(); gpsOffsetY += dragAmount.y.roundToInt(); mapPrefs.edit().putInt("widget_gps_quality_x", gpsOffsetX).putInt("widget_gps_quality_y", gpsOffsetY).apply() }
            }) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) { Text("GPS: $gpsQuality", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }

        if (showZoom) {
            Box(modifier = Modifier.offset { IntOffset(zoomOffsetX, zoomOffsetY) }.pointerInput(widgetsLocked) {
                if (!widgetsLocked) detectDragGestures { _, dragAmount -> zoomOffsetX += dragAmount.x.roundToInt(); zoomOffsetY += dragAmount.y.roundToInt(); mapPrefs.edit().putInt("widget_zoom_x", zoomOffsetX).putInt("widget_zoom_y", zoomOffsetY).apply() }
            }) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) { Text("Zoom: ${String.format("%.1f", currentZoom)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }

        if (isTracking && showTrackingWidget) {
            Box(modifier = Modifier.offset { IntOffset(trackingOffsetX, trackingOffsetY) }.pointerInput(widgetsLocked) {
                if (!widgetsLocked) detectDragGestures { _, dragAmount -> trackingOffsetX += dragAmount.x.roundToInt(); trackingOffsetY += dragAmount.y.roundToInt(); mapPrefs.edit().putInt("widget_tracking_x", trackingOffsetX).putInt("widget_tracking_y", trackingOffsetY).apply() }
            }) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(6.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Süre: ${formatTime(elapsedSeconds)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Mesafe: ${formatDistance(totalDistance)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { 
                myLocationOverlay?.enableMyLocation()
                val currentLoc = getBestCurrentLocation() ?: myLocationOverlay?.myLocation
                if (currentLoc != null) {
                    val locZoom = mapPrefs.getFloat("zoom_location", 15.0f).toDouble()
                    mapViewInstance?.controller?.setCenter(currentLoc)
                    mapViewInstance?.controller?.animateTo(currentLoc) 
                    mapViewInstance?.controller?.setZoom(locZoom)
                    mapViewInstance?.invalidate()
                } else {
                    Toast.makeText(context, "Konum alınıyor, lütfen bekleyin...", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 100.dp, end = 16.dp)
        ) { Icon(Icons.Default.LocationOn, contentDescription = "Konumuma Git") }

        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!isTracking) {
                Button(
                    onClick = { 
                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (!hasPerm) { 
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            Toast.makeText(context, "Lütfen konum izni verin!", Toast.LENGTH_SHORT).show()
                            return@Button 
                        }
                        
                        myLocationOverlay?.enableMyLocation()
                        val startLoc = getBestCurrentLocation() ?: myLocationOverlay?.myLocation
                        if (startLoc == null) { Toast.makeText(context, "Konum henüz bulunamadı...", Toast.LENGTH_SHORT).show(); return@Button }
                        
                        onClearPastRoute() 
                        isTracking = true
                        elapsedSeconds = 0L
                        totalDistance = 0f
                        lastLoc = startLoc.let { val l = Location(LocationManager.GPS_PROVIDER); l.latitude = it.latitude; l.longitude = it.longitude; l }

                        val navArrow = createBlueNavArrow(context)
                        myLocationOverlay?.setPersonIcon(navArrow)
                        myLocationOverlay?.setDirectionArrow(navArrow, navArrow)

                        mapViewInstance?.overlays?.removeAll { it is Marker || it is Polyline || (it is FolderOverlay && it.name == "route_points") }
                        
                        val folder = FolderOverlay().apply { name = "route_points" }
                        mapViewInstance?.overlays?.add(folder)
                        routePointsFolder = folder

                        val polyline = Polyline().apply { outlinePaint.color = android.graphics.Color.RED; outlinePaint.strokeWidth = 14f; addPoint(startLoc) }
                        mapViewInstance?.overlays?.add(polyline)
                        routePolyline = polyline

                        val startMarker = Marker(mapViewInstance).apply { position = startLoc; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = createSolidDot(context, android.graphics.Color.GREEN); setOnMarkerClickListener { _, _ -> true } }
                        mapViewInstance?.overlays?.add(startMarker)
                        
                        folder.add(Marker(mapViewInstance).apply { position = startLoc; icon = createRoutePointIcon(context); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); setOnMarkerClickListener { _, _ -> true } })
                        
                        val trackZoom = mapPrefs.getFloat("zoom_track", 18.0f).toDouble()
                        mapViewInstance?.controller?.setCenter(startLoc)
                        mapViewInstance?.controller?.animateTo(startLoc)
                        mapViewInstance?.controller?.setZoom(trackZoom) 
                        mapViewInstance?.invalidate()

                        val recFreq = mapPrefs.getLong("record_freq", 1000L)
                        val useGps = mapPrefs.getBoolean("use_gps", true)
                        val useNet = mapPrefs.getBoolean("use_network", true)
                        try { 
                            if (useGps) {
                                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, recFreq, 0f, locationListener) 
                            }
                            if (useNet) {
                                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, recFreq, 0f, locationListener)
                            }
                        } catch (e: SecurityException) { }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.height(50.dp).width(160.dp)
                ) { Text("Başlat", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = { 
                        isTracking = false
                        try { locationManager.removeUpdates(locationListener) } catch (e: Exception) {}
                        mapViewInstance?.setMapOrientation(0f)
                        
                        val blueDot = createBlueDot(context)
                        myLocationOverlay?.setPersonIcon(blueDot)
                        myLocationOverlay?.setDirectionArrow(blueDot, blueDot)

                        val points = routePolyline?.actualPoints
                        val endLoc = myLocationOverlay?.myLocation ?: points?.lastOrNull()
                        if (endLoc != null) {
                            val endMarker = Marker(mapViewInstance).apply { position = endLoc; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = createSolidDot(context, android.graphics.Color.RED); setOnMarkerClickListener { _, _ -> true } }
                            mapViewInstance?.overlays?.add(endMarker)
                            mapViewInstance?.invalidate()
                        }
                        if (!points.isNullOrEmpty()) {
                            val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                            val dateStr = dateFormat.format(java.util.Date())
                            val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)
                            val array = org.json.JSONArray(prefs.getString("saved_routes", "[]"))
                            val newRoute = org.json.JSONObject().apply {
                                put("title", "Rota - $dateStr")
                                put("date", dateStr)
                                val pts = org.json.JSONArray()
                                for (p in points) pts.put(org.json.JSONObject().apply { put("lat", p.latitude); put("lon", p.longitude) })
                                put("points", pts)
                            }
                            array.put(newRoute)
                            prefs.edit().putString("saved_routes", array.toString()).apply()
                            Toast.makeText(context, "Rota kaydedildi!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.height(50.dp).width(160.dp)
                ) { Text("Bitir", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun SettingsScreen(onShowRouteOnMap: (Int) -> Unit, onNavigateToMap: () -> Unit) {
    var currentSubScreen by remember { mutableStateOf("main") }

    BackHandler {
        if (currentSubScreen != "main") currentSubScreen = "main" else onNavigateToMap()
    }

    when (currentSubScreen) {
        "main" -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ayarlar", style = MaterialTheme.typography.headlineMedium)
                Divider()
                ListItem(headlineContent = { Text("Konum Servisleri") }, supportingContent = { Text("GPS ve Şebeke sağlayıcı ayarları") }, modifier = Modifier.clickable { currentSubScreen = "location_services" })
                Divider()
                ListItem(headlineContent = { Text("Rota Kayıt Ayarları") }, supportingContent = { Text("Kayıt sıklığı ve GPS doğruluğu filtresi") }, modifier = Modifier.clickable { currentSubScreen = "route_record_settings" })
                Divider()
                ListItem(headlineContent = { Text("Harita Görünümü") }, supportingContent = { Text("Yol veya Uydu görünümü seçin") }, modifier = Modifier.clickable { currentSubScreen = "map_type" })
                Divider()
                ListItem(headlineContent = { Text("Zoom Ayarları") }, supportingContent = { Text("Açılış, Konum ve Kayıt zoom seviyeleri") }, modifier = Modifier.clickable { currentSubScreen = "zoom_settings" })
                Divider()
                ListItem(headlineContent = { Text("Ana Ekran Araçları") }, supportingContent = { Text("Sürükle-bırak, kilitleme ve araç görünürlükleri") }, modifier = Modifier.clickable { currentSubScreen = "widgets_settings" })
                Divider()
                ListItem(headlineContent = { Text("İzinler") }, supportingContent = { Text("GPS, Pil ve Otomatik Başlatma") }, modifier = Modifier.clickable { currentSubScreen = "permissions" })
                Divider()
                ListItem(headlineContent = { Text("Geçmiş Rotalar") }, supportingContent = { Text("Kaydedilen rotaları yönetin") }, modifier = Modifier.clickable { currentSubScreen = "past_routes" })
            }
        }
        "location_services" -> { LocationServicesScreen(onBack = { currentSubScreen = "main" }) }
        "route_record_settings" -> { RouteRecordSettingsScreen(onBack = { currentSubScreen = "main" }) }
        "map_type" -> { MapTypeScreen(onBack = { currentSubScreen = "main" }, onNavigateToMap = onNavigateToMap) }
        "zoom_settings" -> { ZoomSettingsScreen(onBack = { currentSubScreen = "main" }) }
        "widgets_settings" -> { WidgetsSettingsScreen(onBack = { currentSubScreen = "main" }) }
        "permissions" -> { PermissionsDetailScreen(onBack = { currentSubScreen = "main" }) }
        "past_routes" -> { PastRoutesScreen(onBack = { currentSubScreen = "main" }, onShowRouteOnMap = onShowRouteOnMap) }
    }
}

@Composable
fun LocationServicesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)

    var useGps by remember { mutableStateOf(prefs.getBoolean("use_gps", true)) }
    var useNetwork by remember { mutableStateOf(prefs.getBoolean("use_network", true)) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onBack).fillMaxWidth().padding(vertical = 4.dp)) {
                Text("< Geri", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Konum Servisleri", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { Divider() }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("GPS Uydu Konumu", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Yüksek hassasiyetli uydu konum servisi", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = useGps, onCheckedChange = { useGps = it; prefs.edit().putBoolean("use_gps", it).apply() })
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Şebeke (Network) Konumu", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Baz istasyonu ve Wi-Fi tabanlı kaba konum", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = useNetwork, onCheckedChange = { useNetwork = it; prefs.edit().putBoolean("use_network", it).apply() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteRecordSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)

    val freqOptions = listOf("1 sn", "2 sn", "3 sn", "5 sn", "10 sn")
    val freqValues = listOf(1000L, 2000L, 3000L, 5000L, 10000L)
    var freqIndex by remember { mutableStateOf(freqValues.indexOf(prefs.getLong("record_freq", 1000L)).takeIf { it >= 0 } ?: 0) }

    val accOptions = listOf("Kapalı", "10 m", "30 m", "50 m")
    val accValues = listOf(0f, 10f, 30f, 50f)
    var accIndex by remember { mutableStateOf(accValues.indexOf(prefs.getFloat("record_gps_accuracy", 0f)).takeIf { it >= 0 } ?: 0) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onBack).fillMaxWidth().padding(vertical = 4.dp)) {
                Text("< Geri", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Rota Kayıt Ayarları", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { Divider() }

        item { SettingsDropdown("Kayıt Sıklığı", "GPS cihazı istek sıklığı", freqOptions, freqIndex) { idx -> freqIndex = idx; prefs.edit().putLong("record_freq", freqValues[idx]).apply() } }
        item { SettingsDropdown("GPS Doğruluğu Filtresi", "Doğruluk bu değerin üstüne çıktığında nokta konmaz", accOptions, accIndex) { idx -> accIndex = idx; prefs.edit().putFloat("record_gps_accuracy", accValues[idx]).apply() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(label: String, description: String, options: List<String>, selectedIndex: Int, onOptionSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(description, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = options.getOrElse(selectedIndex) { "" }, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, selectionOption -> DropdownMenuItem(text = { Text(selectionOption) }, onClick = { onOptionSelected(index); expanded = false }) }
            }
        }
    }
}

@Composable
fun WidgetsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)

    var widgetsLocked by remember { mutableStateOf(prefs.getBoolean("widgets_locked", false)) }
    var speedShow by remember { mutableStateOf(prefs.getBoolean("widget_speed", false)) }
    var altShow by remember { mutableStateOf(prefs.getBoolean("widget_altitude", false)) }
    var accShow by remember { mutableStateOf(prefs.getBoolean("widget_accuracy", false)) }
    var gpsShow by remember { mutableStateOf(prefs.getBoolean("widget_gps_quality", false)) }
    var zoomShow by remember { mutableStateOf(prefs.getBoolean("widget_zoom", false)) }
    var trackingShow by remember { mutableStateOf(prefs.getBoolean("widget_tracking", true)) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onBack).fillMaxWidth().padding(vertical = 4.dp)) {
                Text("< Geri", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Ana Ekran Araçları", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { Divider() }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Düzeni Kilitle", fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(if (widgetsLocked) "Araçlar sabitlendi" else "Araçlar serbest (Sürükle bırak)", fontSize = 12.sp) }
                    Switch(checked = widgetsLocked, onCheckedChange = { widgetsLocked = it; prefs.edit().putBoolean("widgets_locked", it).apply() })
                }
            }
        }
        item { Text("Araç Göster/Gizle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { Card(modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Hız Göstergesi", fontWeight = FontWeight.Bold); Switch(checked = speedShow, onCheckedChange = { speedShow = it; prefs.edit().putBoolean("widget_speed", it).apply() }) } } }
        item { Card(modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Rakım Göstergesi", fontWeight = FontWeight.Bold); Switch(checked = altShow, onCheckedChange = { altShow = it; prefs.edit().putBoolean("widget_altitude", it).apply() }) } } }
        item { Card(modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Konum Doğruluğu", fontWeight = FontWeight.Bold); Switch(checked = accShow, onCheckedChange = { accShow = it; prefs.edit().putBoolean("widget_accuracy", it).apply() }) } } }
        item { Card(modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("GPS Kalitesi", fontWeight = FontWeight.Bold); Switch(checked = gpsShow, onCheckedChange = { gpsShow = it; prefs.edit().putBoolean("widget_gps_quality", it).apply() }) } } }
        item { Card(modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Zoom Göstergesi", fontWeight = FontWeight.Bold); Switch(checked = zoomShow, onCheckedChange = { zoomShow = it; prefs.edit().putBoolean("widget_zoom", it).apply() }) } } }
        item { Card(modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Rota Bilgisi (Süre ve Mesafe)", fontWeight = FontWeight.Bold); Switch(checked = trackingShow, onCheckedChange = { trackingShow = it; prefs.edit().putBoolean("widget_tracking", it).apply() }) } } }
    }
}

@Composable
fun ZoomSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)

    var defaultZoom by remember { mutableStateOf(prefs.getFloat("zoom_default", 4.0f)) }
    var locationZoom by remember { mutableStateOf(prefs.getFloat("zoom_location", 15.0f)) }
    var trackZoom by remember { mutableStateOf(prefs.getFloat("zoom_track", 18.0f)) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onBack).fillMaxWidth().padding(vertical = 4.dp)) {
                Text("< Geri", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Zoom Ayarları", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { Divider() }
        item { Column { Text("Normal Açılış Zoom: ${String.format("%.1f", defaultZoom)}", fontWeight = FontWeight.Bold); Slider(value = defaultZoom, onValueChange = { defaultZoom = it }, onValueChangeFinished = { prefs.edit().putFloat("zoom_default", defaultZoom).apply() }, valueRange = 2.0f..20.0f, steps = 18) } }
        item { Column { Text("Konuma Git Butonu Zoom: ${String.format("%.1f", locationZoom)}", fontWeight = FontWeight.Bold); Slider(value = locationZoom, onValueChange = { locationZoom = it }, onValueChangeFinished = { prefs.edit().putFloat("zoom_location", locationZoom).apply() }, valueRange = 2.0f..22.0f, steps = 20) } }
        item { Column { Text("Kayıt Başlat Butonu Zoom: ${String.format("%.1f", trackZoom)}", fontWeight = FontWeight.Bold); Slider(value = trackZoom, onValueChange = { trackZoom = it }, onValueChangeFinished = { prefs.edit().putFloat("zoom_track", trackZoom).apply() }, valueRange = 2.0f..22.0f, steps = 20) } }
    }
}

@Composable
fun MapTypeScreen(onBack: () -> Unit, onNavigateToMap: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)
    var selectedType by remember { mutableStateOf(prefs.getString("map_type", "HYBRID") ?: "HYBRID") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onBack).fillMaxWidth().padding(vertical = 4.dp)) {
                Text("< Geri", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Harita Görünümü", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { Divider() }
        item { ListItem(headlineContent = { Text("Yol Görünümü (Standart)") }, trailingContent = { if (selectedType == "ROAD") Icon(Icons.Default.CheckCircle, "", tint = Color.Green) }, modifier = Modifier.clickable { selectedType = "ROAD"; prefs.edit().putString("map_type", "ROAD").apply(); onNavigateToMap() }) }
        item { ListItem(headlineContent = { Text("Uydu Görünümü (Karma)") }, trailingContent = { if (selectedType == "HYBRID") Icon(Icons.Default.CheckCircle, "", tint = Color.Green) }, modifier = Modifier.clickable { selectedType = "HYBRID"; prefs.edit().putString("map_type", "HYBRID").apply(); onNavigateToMap() }) }
    }
}

@Composable
fun PastRoutesScreen(onBack: () -> Unit, onShowRouteOnMap: (Int) -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)
    val jsonStr = prefs.getString("saved_routes", "[]")
    
    val routeList = remember {
        mutableStateListOf<Pair<String, Int>>().apply {
            try {
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(Pair(obj.getString("title"), obj.getJSONArray("points").length()))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onBack).fillMaxWidth().padding(vertical = 4.dp)) {
                Text("< Geri", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Geçmiş Rotalar", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { Divider() }
        if (routeList.isEmpty()) {
            item { Text("Henüz kaydedilmiş rota bulunmuyor.", color = Color.Gray) }
        } else {
            itemsIndexed(routeList) { index, route ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onShowRouteOnMap(index) }, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(route.first, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("GPS Nokta Sayısı: ${route.second}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        }
                        IconButton(onClick = {
                            val arr = org.json.JSONArray(prefs.getString("saved_routes", "[]"))
                            if (index < arr.length()) {
                                val newArr = org.json.JSONArray()
                                for (i in 0 until arr.length()) { if (i != index) newArr.put(arr.getJSONObject(i)) }
                                prefs.edit().putString("saved_routes", newArr.toString()).apply()
                                routeList.removeAt(index)
                                Toast.makeText(context, "Rota silindi", Toast.LENGTH_SHORT).show()
                            }
                        }) { Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionsDetailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)
    
    var locGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    var notifGranted by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true) }
    var batGranted by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) powerManager.isIgnoringBatteryOptimizations(context.packageName) else true) }
    var autoGranted by remember { mutableStateOf(prefs.getBoolean("autostart_ok", false)) }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { locGranted = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onBack).fillMaxWidth().padding(vertical = 4.dp)) {
                Text("< Geri", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("İzinler Detayı", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { Divider() }
        item { PermissionItem("GPS / Konum İzni", locGranted) { locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { item { PermissionItem("Bildirim İzni", notifGranted) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } } }
        item { PermissionItem("Arka Plan (Pil) İzni", batGranted) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !batGranted) { try { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) } catch (e: Exception) { Toast.makeText(context, "Desteklenmiyor", Toast.LENGTH_SHORT).show() } } } }
        item { PermissionItem("Otomatik Başlatma (Xiaomi)", autoGranted) { try { context.startActivity(Intent().apply { component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") }); prefs.edit().putBoolean("autostart_ok", true).apply(); autoGranted = true } catch (e: Exception) { Toast.makeText(context, "Xiaomi cihaz bulunamadı.", Toast.LENGTH_SHORT).show() } } }
    }
}

@Composable
fun PermissionItem(title: String, isGranted: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { if (isGranted) Icon(Icons.Default.CheckCircle, contentDescription = "Onaylı", tint = Color.Green) else Button(onClick = onClick) { Text("İzin Ver") } },
        modifier = Modifier.clickable(enabled = !isGranted, onClick = onClick)
    )
}
