package com.example.harita

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Osmdroid yapılandırması
        Configuration.getInstance().load(
            applicationContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
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
                    icon = { Icon(Icons.Default.Map, contentDescription = "Harita") },
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
    // İstediğiniz gibi: Başlangıçta uydu (USGS), butona basınca normal (MAPNIK) görünüm
    var isSatellite by remember { mutableStateOf(true) }
    var isTracking by remember { mutableStateOf(false) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.USGS_SAT) // Uydu görünümü başlangıç
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setPoint(GeoPoint(39.9207, 32.8541)) // Ankara merkezli başlangıç
                    mapViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Görünüm Değiştirme Butonu (Uydu <-> Normal)
        Button(
            onClick = { 
                isSatellite = !isSatellite
                mapViewInstance?.let { map ->
                    if (isSatellite) {
                        map.setTileSource(TileSourceFactory.USGS_SAT)
                    } else {
                        map.setTileSource(TileSourceFactory.MAPNIK) // Standart OpenStreetMap normal görünüm
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Text(if (isSatellite) "Normale Geç" else "Uyduya Geç")
        }

        // Başlat / Bitir Butonları
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { isTracking = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isTracking
            ) {
                Text("Başlat")
            }
            
            Button(
                onClick = { isTracking = false },
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium)
        Divider()
        
        ListItem(
            headlineContent = { Text("Yer İşaretleri") },
            modifier = Modifier.clickable { }
        )
        ListItem(
            headlineContent = { Text("Geçmiş Rotalar") },
            modifier.clickable { }
        )
        ListItem(
            headlineContent = { Text("Rota Ayarları") },
            modifier = Modifier.clickable { }
        )
    }
}
