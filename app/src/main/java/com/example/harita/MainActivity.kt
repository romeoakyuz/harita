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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Configuration.getInstance().userAgentValue = "HaritaApp/1.0"
        Configuration.getInstance().load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        )
        
        setContent {
            RouteTrackerApp()
        }
    }
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    // Kesintisiz çalışan hazır yüksek çözünürlüklü uydu katmanı
                    setTileSource(TileSourceFactory.WIREFRAME.name?.let { TileSourceFactory.USGS_SAT } ?: TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    controller.setZoom(19.0)
                    
                    val provider = GpsMyLocationProvider(ctx)
                    val overlay = MyLocationNewOverlay(provider, this)
                    overlay.enableMyLocation()
                    
                    overlay.runOnFirstFix {
                        post {
                            overlay.myLocation?.let {
                                controller.animateTo(it)
                                controller.setZoom(19.0)
                            }
                        }
                    }
                    
                    overlays.add(overlay)
                    myLocationOverlay = overlay
                    mapViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Konumuma Git Butonu
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

        // Başlat / Bitir Butonları
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { 
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!hasPerm) {
                        Toast.makeText(context, "Önce Ayarlar'dan Konum izni verin!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    val startLoc = myLocationOverlay?.myLocation
                    if (startLoc == null) {
                        Toast.makeText(context, "Konum henüz bulunamadı, lütfen bekleyin.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isTracking = true
                    mapViewInstance?.overlays?.removeAll { it is Marker || it is Polyline }

                    // Kırmızı Çizgi
                    val polyline = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.RED
                        outlinePaint.strokeWidth = 12f
                        addPoint(startLoc)
                    }
                    mapViewInstance?.overlays?.add(polyline)
                    routePolyline = polyline

                    // Başlangıç İşaretçisi (Yeşil)
                    val startMarker = Marker(mapViewInstance).apply {
                        position = startLoc
                        title = "Başlangıç"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = context.getDrawable(android.R.drawable.presence_online)?.mutate()?.apply {
                            setTint(android.graphics.Color.GREEN)
                        }
                    }
                    mapViewInstance?.overlays?.add(startMarker)
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

                    val endLoc = myLocationOverlay?.myLocation ?: routePolyline?.actualPoints?.lastOrNull()
                    if (endLoc != null) {
                        // Bitiş İşaretçisi (Kırmızı)
                        val endMarker = Marker(mapViewInstance).apply {
                            position = endLoc
                            title = "Bitiş"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = context.getDrawable(android.R.drawable.presence_busy)?.mutate()?.apply {
                                setTint(android.graphics.Color.RED)
                            }
                        }
                        mapViewInstance?.overlays?.add(endMarker)
                        mapViewInstance?.invalidate()
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
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val prefs = context.getSharedPreferences("harita_prefs", Context.MODE_PRIVATE)

    var locGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    var notifGranted by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true) }
    var batGranted by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) powerManager.isIgnoringBatteryOptimizations(context.packageName) else true) }
    var autoGranted by remember { mutableStateOf(prefs.getBoolean("autostart_ok", false)) }

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { locGranted = it }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Ayarlar", style = MaterialTheme.typography.headlineMedium) }
        item { Divider() }
        
        // --- İZİNLER BÖLÜMÜ ---
        item { Text("İzinler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        
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

        item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }
        item { Text("Diğer Menüler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        
        item { ListItem(headlineContent = { Text("Yer İşaretleri") }, modifier = Modifier.clickable { }) }
        item { ListItem(headlineContent = { Text("Geçmiş Rotalar") }, modifier = Modifier.clickable { }) }
        item { ListItem(headlineContent = { Text("Rota Ayarları") }, modifier = Modifier.clickable { }) }
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
