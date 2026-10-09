package com.example.harita

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

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

fun createSmallMarkerIcon(context: Context, resId: Int, color: Int): android.graphics.drawable.Drawable {
    val drawable = ContextCompat.getDrawable(context, resId)?.mutate()!!
    drawable.setTint(color)
    val sizePx = (28 * context.resources.displayMetrics.density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
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
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                NavigationBarItem(
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Harita") },
                    label = { Text("Harita") },
                    selected = currentRoute == "map",
                    onClick = { navController.navigate("map") { popUpTo(0) } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ayarlar") },
                    label = { Text("Ayarlar") },
                    selected = currentRoute == "settings",
                    onClick = { navController.navigate("settings") { popUpTo(0) } }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "map",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("map") { 
                MapScreen(selectedPastRouteIndex) { selectedPastRouteIndex = -1 }
            }
            composable("settings") { 
                SettingsScreen(
                    onShowRouteOnMap = { index ->
                        selectedPastRouteIndex = index
                        navController.navigate("map") { popUpTo(0) }
                    },
                    onNavigateToMap = {
                        navController.navigate("map") { popUpTo(0) }
                    }
                ) 
            }
        }
    }
}

@Composable
fun MapScreen(pastRouteIndex: Int, onClearPastRoute: () -> Unit) {
    val context = LocalContext.current
    val mapPrefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)
    val coroutineScope = rememberCoroutineScope()
    
    var isTracking by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(mapPrefs.getString("map_type", "ROAD") ?: "ROAD") }
    
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }

    var elapsedSeconds by remember { mutableStateOf(0L) }
    var totalDistance by remember { mutableStateOf(0f) }
    var lastLoc by remember { mutableStateOf<android.location.Location?>(null) }
    
    var currentAltitude by remember { mutableStateOf(0.0) }
    var currentSpeed by remember { mutableStateOf(0f) }
    
    var isMapVisible by remember { mutableStateOf(true) }

    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager }
    
    val locationListener = remember {
        object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                mapPrefs.edit()
                    .putFloat("last_lat", location.latitude.toFloat())
                    .putFloat("last_lon", location.longitude.toFloat())
                    .apply()

                if (isTracking) {
                    val geo = GeoPoint(location.latitude, location.longitude)
                    routePolyline?.addPoint(geo)
                    
                    lastLoc?.let { totalDistance += it.distanceTo(location) }
                    lastLoc = location

                    mapViewInstance?.controller?.setCenter(geo)
                    if (location.hasBearing()) {
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
        mapType = mapPrefs.getString("map_type", "ROAD") ?: "ROAD"
    }

    LaunchedEffect(isTracking) {
        while (true) {
            delay(1000L)
            if (isTracking) elapsedSeconds++
            
            myLocationOverlay?.lastFix?.let { fix ->
                currentAltitude = fix.altitude
                currentSpeed = if (fix.hasSpeed()) fix.speed * 3.6f else 0f
            } ?: run {
                currentSpeed = 0f
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationManager.removeUpdates(locationListener)
        }
    }

    LaunchedEffect(pastRouteIndex) {
        if (pastRouteIndex >= 0) {
            val jsonStr = mapPrefs.getString("saved_routes", "[]")
            val arr = org.json.JSONArray(jsonStr)
            if (pastRouteIndex < arr.length()) {
                val routeObj = arr.getJSONObject(pastRouteIndex)
                val pts = routeObj.getJSONArray("points")
                val polyline = Polyline().apply {
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 14f
                }
                for (i in 0 until pts.length()) {
                    val pt = pts.getJSONObject(i)
                    polyline.addPoint(GeoPoint(pt.getDouble("lat"), pt.getDouble("lon")))
                }
                mapViewInstance?.overlays?.removeAll { it is Polyline || it is Marker }
                mapViewInstance?.overlays?.add(polyline)
                mapViewInstance?.invalidate()
                
                if (polyline.actualPoints.isNotEmpty()) {
                    mapViewInstance?.controller?.setCenter(polyline.actualPoints.first())
                    mapViewInstance?.controller?.setZoom(16.0)
                }
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

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isMapVisible,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
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
                        
                        val isFirstLaunch = mapPrefs.getBoolean("is_first_launch", true)
                        val defaultZoom = mapPrefs.getFloat("zoom_default", 4.0f).toDouble()
                        val centerLat = if (isFirstLaunch) 39.0 else mapPrefs.getFloat("last_lat", 39.0f).toDouble()
                        val centerLon = if (isFirstLaunch) 35.0 else mapPrefs.getFloat("last_lon", 35.0f).toDouble()
                        
                        setExpectedCenter(GeoPoint(centerLat, centerLon))
                        controller.setZoom(defaultZoom)
                        
                        if (isFirstLaunch) {
                            mapPrefs.edit().putBoolean("is_first_launch", false).apply()
                        }
                        
                        val rotationGestureOverlay = RotationGestureOverlay(this).apply { isEnabled = true }
                        overlays.add(rotationGestureOverlay)
                        
                        val provider = GpsMyLocationProvider(ctx)
                        val overlay = MyLocationNewOverlay(provider, this)
                        overlay.enableMyLocation()
                        
                        overlay.runOnFirstFix {
                            post {
                                overlay.myLocation?.let {
                                    mapPrefs.edit()
                                        .putFloat("last_lat", it.latitude.toFloat())
                                        .putFloat("last_lon", it.longitude.toFloat())
                                        .apply()
                                }
                            }
                        }
                        
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
        }

        // Sol Üst: Yuvarlak Hız Göstergesi ve Temizle Butonu
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp, start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.0f", currentSpeed),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "km/s",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (pastRouteIndex >= 0) {
                Button(
                    onClick = { 
                        onClearPastRoute()
                        mapViewInstance?.overlays?.removeAll { it is Polyline || (it is Marker && it.title != "Start" && it.title != "Stop") }
                        mapViewInstance?.invalidate()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Haritayı Temizle")
                }
            }
        }

        // Sağ Üst: Yalnızca Canlı Rakım Göstergesi
        Card(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                Text("Rakım: ${currentAltitude.toInt()} m", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (isTracking) {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Süre: ${formatTime(elapsedSeconds)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Mesafe: ${formatDistance(totalDistance)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // Konumuma Git Butonu (İlk basışta anında ve hatasız ortalama garantili)
        FloatingActionButton(
            onClick = { 
                myLocationOverlay?.enableMyLocation()
                val currentLoc = myLocationOverlay?.myLocation ?: run {
                    try {
                        val loc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                            ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                        if (loc != null) GeoPoint(loc.latitude, loc.longitude) else null
                    } catch (e: SecurityException) { null }
                }

                if (currentLoc != null) {
                    coroutineScope.launch {
                        isMapVisible = false 
                        delay(150L)          
                        
                        val locZoom = mapPrefs.getFloat("zoom_location", 15.0f).toDouble()
                        mapViewInstance?.controller?.setCenter(currentLoc) 
                        mapViewInstance?.controller?.setZoom(locZoom)
                        mapViewInstance?.invalidate()
                        
                        delay(50L)
                        isMapVisible = true  
                    }
                } else {
                    Toast.makeText(context, "Konum aranıyor, GPS açık olduğundan emin olun...", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 100.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Konumuma Git")
        }

        // Tek Buton (Başlat / Bitir)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (!isTracking) {
                Button(
                    onClick = { 
                        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (!hasPerm) {
                            Toast.makeText(context, "Önce İzinler bölümünden Konum izni verin!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        myLocationOverlay?.enableMyLocation()
                        val startLoc = myLocationOverlay?.myLocation ?: try {
                            val loc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                            if (loc != null) GeoPoint(loc.latitude, loc.longitude) else null
                        } catch (e: SecurityException) { null }

                        if (startLoc == null) {
                            Toast.makeText(context, "Konum henüz bulunamadı...", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        onClearPastRoute() 
                        isTracking = true
                        elapsedSeconds = 0L
                        totalDistance = 0f
                        lastLoc = null

                        try { lastLoc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) } catch (e: SecurityException) { }

                        mapViewInstance?.overlays?.removeAll { it is Marker || it is Polyline }

                        val polyline = Polyline().apply { outlinePaint.color = android.graphics.Color.RED; outlinePaint.strokeWidth = 14f; addPoint(startLoc) }
                        mapViewInstance?.overlays?.add(polyline)
                        routePolyline = polyline

                        val startMarker = Marker(mapViewInstance).apply { position = startLoc; title = "Start"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); icon = createSmallMarkerIcon(context, android.R.drawable.presence_online, android.graphics.Color.GREEN) }
                        mapViewInstance?.overlays?.add(startMarker)
                        startMarker.showInfoWindow()
                        
                        coroutineScope.launch {
                            isMapVisible = false
                            delay(150L)
                            val trackZoom = mapPrefs.getFloat("zoom_track", 18.0f).toDouble()
                            mapViewInstance?.controller?.setCenter(startLoc)
                            mapViewInstance?.controller?.setZoom(trackZoom) 
                            mapViewInstance?.invalidate()
                            delay(50L)
                            isMapVisible = true
                        }

                        try { locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 2000L, 2f, locationListener) } catch (e: SecurityException) { }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(50.dp).width(160.dp)
                ) {
                    Text("Başlat", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { 
                        isTracking = false
                        locationManager.removeUpdates(locationListener)
                        mapViewInstance?.setMapOrientation(0f)

                        val points = routePolyline?.actualPoints
                        val endLoc = myLocationOverlay?.myLocation ?: points?.lastOrNull()
                        
                        if (endLoc != null) {
                            val endMarker = Marker(mapViewInstance).apply { position = endLoc; title = "Stop"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); icon = createSmallMarkerIcon(context, android.R.drawable.presence_busy, android.graphics.Color.RED) }
                            mapViewInstance?.overlays?.add(endMarker)
                            endMarker.showInfoWindow()
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(50.dp).width(160.dp)
                ) {
                    Text("Bitir", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onShowRouteOnMap: (Int) -> Unit, onNavigateToMap: () -> Unit) {
    var currentSubScreen by remember { mutableStateOf("main") }

    BackHandler {
        if (currentSubScreen != "main") {
            currentSubScreen = "main"
        } else {
            onNavigateToMap()
        }
    }

    when (currentSubScreen) {
        "main" -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ayarlar", style = MaterialTheme.typography.headlineMedium)
                Divider()
                ListItem(headlineContent = { Text("Harita Görünümü") }, supportingContent = { Text("Yol veya Uydu görünümü seçin") }, modifier = Modifier.clickable { currentSubScreen = "map_type" })
                Divider()
                ListItem(headlineContent = { Text("Zoom Ayarları") }, supportingContent = { Text("Açılış, Konum ve Kayıt zoom seviyeleri") }, modifier = Modifier.clickable { currentSubScreen = "zoom_settings" })
                Divider()
                ListItem(headlineContent = { Text("İzinler") }, supportingContent = { Text("GPS, Pil ve Otomatik Başlatma") }, modifier = Modifier.clickable { currentSubScreen = "permissions" })
                Divider()
                ListItem(headlineContent = { Text("Geçmiş Rotalar") }, supportingContent = { Text("Kaydedilen rotaları yönetin") }, modifier = Modifier.clickable { currentSubScreen = "past_routes" })
            }
        }
        "map_type" -> { MapTypeScreen(onBack = { currentSubScreen = "main" }, onNavigateToMap = onNavigateToMap) }
        "zoom_settings" -> { ZoomSettingsScreen(onBack = { currentSubScreen = "main" }) }
        "permissions" -> { PermissionsDetailScreen(onBack = { currentSubScreen = "main" }) }
        "past_routes" -> { PastRoutesScreen(onBack = { currentSubScreen = "main" }, onShowRouteOnMap = onShowRouteOnMap) }
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

        item {
            Column {
                Text("Normal Açılış Zoom: ${String.format("%.1f", defaultZoom)}", fontWeight = FontWeight.Bold)
                Slider(
                    value = defaultZoom,
                    onValueChange = { defaultZoom = it },
                    onValueChangeFinished = { prefs.edit().putFloat("zoom_default", defaultZoom).apply() },
                    valueRange = 2.0f..20.0f,
                    steps = 18
                )
            }
        }

        item {
            Column {
                Text("Konuma Git Butonu Zoom: ${String.format("%.1f", locationZoom)}", fontWeight = FontWeight.Bold)
                Slider(
                    value = locationZoom,
                    onValueChange = { locationZoom = it },
                    onValueChangeFinished = { prefs.edit().putFloat("zoom_location", locationZoom).apply() },
                    valueRange = 2.0f..22.0f,
                    steps = 20
                )
            }
        }

        item {
            Column {
                Text("Kayıt Başlat Butonu Zoom: ${String.format("%.1f", trackZoom)}", fontWeight = FontWeight.Bold)
                Slider(
                    value = trackZoom,
                    onValueChange = { trackZoom = it },
                    onValueChangeFinished = { prefs.edit().putFloat("zoom_track", trackZoom).apply() },
                    valueRange = 2.0f..22.0f,
                    steps = 20
                )
            }
        }
    }
}

@Composable
fun MapTypeScreen(onBack: () -> Unit, onNavigateToMap: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)
    var selectedType by remember { mutableStateOf(prefs.getString("map_type", "ROAD") ?: "ROAD") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onBack).fillMaxWidth().padding(vertical = 4.dp)) {
                Text("< Geri", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Harita Görünümü", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { Divider() }
        item {
            ListItem(
                headlineContent = { Text("Yol Görünümü (Standart)") },
                trailingContent = { if (selectedType == "ROAD") Icon(Icons.Default.CheckCircle, "", tint = Color.Green) },
                modifier = Modifier.clickable {
                    selectedType = "ROAD"
                    prefs.edit().putString("map_type", "ROAD").apply()
                    onNavigateToMap()
                }
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Uydu Görünümü (Karma)") },
                trailingContent = { if (selectedType == "HYBRID") Icon(Icons.Default.CheckCircle, "", tint = Color.Green) },
                modifier = Modifier.clickable {
                    selectedType = "HYBRID"
                    prefs.edit().putString("map_type", "HYBRID").apply()
                    onNavigateToMap()
                }
            )
        }
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
                                for (i in 0 until arr.length()) {
                                    if (i != index) newArr.put(arr.getJSONObject(i))
                                }
                                prefs.edit().putString("saved_routes", newArr.toString()).apply()
                                routeList.removeAt(index)
                                Toast.makeText(context, "Rota silindi", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item { PermissionItem("Bildirim İzni", notifGranted) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
        }
        item {
            PermissionItem("Arka Plan (Pil) İzni", batGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !batGranted) {
                    try { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) } 
                    catch (e: Exception) { Toast.makeText(context, "Desteklenmiyor", Toast.LENGTH_SHORT).show() }
                }
            }
        }
        item {
            PermissionItem("Otomatik Başlatma (Xiaomi)", autoGranted) {
                try {
                    context.startActivity(Intent().apply { component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") })
                    prefs.edit().putBoolean("autostart_ok", true).apply()
                    autoGranted = true
                } catch (e: Exception) { Toast.makeText(context, "Xiaomi cihaz bulunamadı.", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}

@Composable
fun PermissionItem(title: String, isGranted: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            if (isGranted) Icon(Icons.Default.CheckCircle, contentDescription = "Onaylı", tint = Color.Green)
            else Button(onClick = onClick) { Text("İzin Ver") }
        },
        modifier = Modifier.clickable(enabled = !isGranted, onClick = onClick)
    )
}
