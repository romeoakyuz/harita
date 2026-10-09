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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.compose.*
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Configuration.getInstance().userAgentValue = "KisiselRotaUygulamasi/1.0"
        Configuration.getInstance().load(
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

@Composable
fun RouteTrackerApp() {
    val navController = rememberNavController()
    
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
            composable("map") { MapScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}

@Composable
fun MapScreen() {
    val context = LocalContext.current
    var isTracking by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf("ROAD") }
    
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }

    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager }
    
    val locationListener = remember {
        object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                if (isTracking) {
                    val geo = GeoPoint(location.latitude, location.longitude)
                    routePolyline?.addPoint(geo)
                    mapViewInstance?.controller?.animateTo(geo)
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

    DisposableEffect(Unit) {
        onDispose {
            locationManager.removeUpdates(locationListener)
        }
    }

    // Maksimum Zoom Seviyesi 22'ye çıkarıldı
    val googleRoads = remember {
        object : OnlineTileSourceBase(
            "GoogleRoads", 0, 22, 256, "", 
            arrayOf("https://mt0.google.com/vt/lyrs=m&hl=tr&", "https://mt1.google.com/vt/lyrs=m&hl=tr&", "https://mt2.google.com/vt/lyrs=m&hl=tr&", "https://mt3.google.com/vt/lyrs=m&hl=tr&")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                return baseUrl + "x=$x&y=$y&z=$zoom"
            }
        }
    }

    val googleHybrid = remember {
        object : OnlineTileSourceBase(
            "GoogleHybrid", 0, 22, 256, "", 
            arrayOf("https://mt0.google.com/vt/lyrs=y&hl=tr&", "https://mt1.google.com/vt/lyrs=y&hl=tr&", "https://mt2.google.com/vt/lyrs=y&hl=tr&", "https://mt3.google.com/vt/lyrs=y&hl=tr&")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                return baseUrl + "x=$x&y=$y&z=$zoom"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(if (mapType == "ROAD") googleRoads else googleHybrid)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    
                    setMinZoomLevel(4.0)
                    setMaxZoomLevel(22.0) // Harita zoom kısıtlaması kaldırıldı
                    controller.setZoom(9.0)
                    
                    val rotationGestureOverlay = RotationGestureOverlay(this).apply {
                        isEnabled = true
                    }
                    overlays.add(rotationGestureOverlay)
                    
                    val provider = GpsMyLocationProvider(ctx)
                    val overlay = MyLocationNewOverlay(provider, this)
                    overlay.enableMyLocation()
                    
                    overlay.runOnFirstFix {
                        post {
                            overlay.myLocation?.let {
                                controller.animateTo(it)
                                controller.setZoom(14.0)
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

        Button(
            onClick = { mapType = if (mapType == "ROAD") "HYBRID" else "ROAD" },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(if (mapType == "ROAD") "Uydu Görünümü" else "Yol Görünümü")
        }

        FloatingActionButton(
            onClick = { 
                myLocationOverlay?.let { overlay ->
                    val myLoc = overlay.myLocation
                    if (myLoc != null) {
                        mapViewInstance?.controller?.animateTo(myLoc)
                        mapViewInstance?.controller?.setZoom(19.0)
                    } else {
                        Toast.makeText(context, "Konum aranıyor, GPS açık olduğundan emin olun...", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 100.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Konumuma Git")
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { 
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!hasPerm) {
                        Toast.makeText(context, "Önce Ayarlar > İzinler bölümünden Konum izni verin!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    val startLoc = myLocationOverlay?.myLocation
                    if (startLoc == null) {
                        Toast.makeText(context, "Konum henüz bulunamadı, lütfen bekleyin.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isTracking = true
                    mapViewInstance?.overlays?.removeAll { it is Marker || it is Polyline }

                    val polyline = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.RED
                        outlinePaint.strokeWidth = 12f
                        addPoint(startLoc)
                    }
                    mapViewInstance?.overlays?.add(polyline)
                    routePolyline = polyline

                    val startMarker = Marker(mapViewInstance).apply {
                        position = startLoc
                        title = "Start"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createSmallMarkerIcon(context, android.R.drawable.presence_online, android.graphics.Color.GREEN)
                    }
                    mapViewInstance?.overlays?.add(startMarker)
                    startMarker.showInfoWindow()
                    
                    mapViewInstance?.controller?.animateTo(startLoc)
                    mapViewInstance?.controller?.setZoom(19.0)
                    mapViewInstance?.invalidate()

                    try {
                        locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 2000L, 2f, locationListener)
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isTracking
            ) {
                Text("Başlat")
            }
            
            Button(
                onClick = { 
                    isTracking = false
                    locationManager.removeUpdates(locationListener)
                    mapViewInstance?.setMapOrientation(0f)

                    val points = routePolyline?.actualPoints
                    val endLoc = myLocationOverlay?.myLocation ?: points?.lastOrNull()
                    
                    if (endLoc != null) {
                        val endMarker = Marker(mapViewInstance).apply {
                            position = endLoc
                            title = "Stop"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = createSmallMarkerIcon(context, android.R.drawable.presence_busy, android.graphics.Color.RED)
                        }
                        mapViewInstance?.overlays?.add(endMarker)
                        endMarker.showInfoWindow()
                        mapViewInstance?.invalidate()
                    }

                    if (!points.isNullOrEmpty()) {
                        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                        val dateStr = dateFormat.format(java.util.Date())
                        
                        val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)
                        val jsonStr = prefs.getString("saved_routes", "[]")
                        val array = org.json.JSONArray(jsonStr)
                        
                        val newRoute = org.json.JSONObject().apply {
                            put("title", "Rota - $dateStr")
                            put("date", dateStr)
                            val pts = org.json.JSONArray()
                            for (p in points) {
                                pts.put(org.json.JSONObject().apply {
                                    put("lat", p.latitude)
                                    put("lon", p.longitude)
                                })
                            }
                            put("points", pts)
                        }
                        array.put(newRoute)
                        prefs.edit().putString("saved_routes", array.toString()).apply()
                        Toast.makeText(context, "Rota kaydedildi: $dateStr", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = isTracking
            ) {
                Text("Bitir")
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var currentSubScreen by remember { mutableStateOf("main") }

    when (currentSubScreen) {
        "main" -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Ayarlar", style = MaterialTheme.typography.headlineMedium)
                Divider()
                ListItem(headlineContent = { Text("İzinler") }, supportingContent = { Text("GPS, Pil ve Otomatik Başlatma detayları") }, modifier = Modifier.clickable { currentSubScreen = "permissions" })
                Divider()
                ListItem(headlineContent = { Text("Yer İşaretleri") }, modifier = Modifier.clickable { })
                ListItem(headlineContent = { Text("Geçmiş Rotalar") }, supportingContent = { Text("Kaydedilen rotaları görüntüle") }, modifier = Modifier.clickable { currentSubScreen = "past_routes" })
                Divider()
                ListItem(headlineContent = { Text("Rota Ayarları") }, modifier = Modifier.clickable { })
            }
        }
        "permissions" -> { PermissionsDetailScreen(onBack = { currentSubScreen = "main" }) }
        "past_routes" -> { PastRoutesScreen(onBack = { currentSubScreen = "main" }) }
    }
}

@Composable
fun PastRoutesScreen(onBack: () -> Unit) {
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
            items(routeList) { route ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(route.first, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("GPS Nokta Sayısı: ${route.second}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
        item {
            PermissionItem("GPS / Konum İzni", locGranted) {
                locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                PermissionItem("Bildirim İzni", notifGranted) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        item {
            PermissionItem("Arka Plan (Pil) İzni", batGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !batGranted) {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Desteklenmiyor", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        item {
            PermissionItem("Otomatik Başlatma (Xiaomi)", autoGranted) {
                try {
                    val intent = Intent().apply {
                        component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    }
                    context.startActivity(intent)
                    prefs.edit().putBoolean("autostart_ok", true).apply()
                    autoGranted = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Xiaomi cihaz bulunamadı.", Toast.LENGTH_SHORT).show()
                    prefs.edit().putBoolean("autostart_ok", true).apply()
                    autoGranted = true
                }
            }
        }
    }
}

@Composable
fun PermissionItem(title: String, isGranted: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            if (isGranted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Onaylı", tint = Color.Green)
            } else {
                Button(onClick = onClick) {
                    Text("İzin Ver")
                }
            }
        },
        modifier = Modifier.clickable(enabled = !isGranted, onClick = onClick)
    )
}
