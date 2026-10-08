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
import androidx.navigation.compose.*
import com.google.maps.android.compose.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var mapProperties by remember { 
        mutableStateOf(MapProperties(mapType = MapType.SATELLITE)) 
    }
    var isTracking by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            properties = mapProperties,
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        )
        
        Button(
            onClick = { 
                mapProperties = if (mapProperties.mapType == MapType.SATELLITE) {
                    MapProperties(mapType = MapType.NORMAL)
                } else {
                    MapProperties(mapType = MapType.SATELLITE)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Text(if (mapProperties.mapType == MapType.SATELLITE) "Normale Geç" else "Uyduya Geç")
        }

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
            modifier = Modifier.clickable { }
        )
        ListItem(
            headlineContent = { Text("Rota Ayarları") },
            modifier = Modifier.clickable { }
        )
    }
}
