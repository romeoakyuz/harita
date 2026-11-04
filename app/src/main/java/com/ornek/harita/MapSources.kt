package com.ornek.harita

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

object MapSources {
    // Google Normal Harita
    val GoogleNormal = object : OnlineTileSourceBase("GoogleNormal", 0, 22, 256, "", arrayOf("https://mt0.google.com/vt/lyrs=m&hl=tr&")) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return baseUrl + "x=" + MapTileIndex.getX(pMapTileIndex) + "&y=" + MapTileIndex.getY(pMapTileIndex) + "&z=" + MapTileIndex.getZoom(pMapTileIndex)
        }
    }
    
    // Google İsimli Uydu Haritası (Karma/Hybrid)
    val GoogleSatellite = object : OnlineTileSourceBase("GoogleSatellite", 0, 22, 256, "", arrayOf("https://mt0.google.com/vt/lyrs=y&hl=tr&")) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return baseUrl + "x=" + MapTileIndex.getX(pMapTileIndex) + "&y=" + MapTileIndex.getY(pMapTileIndex) + "&z=" + MapTileIndex.getZoom(pMapTileIndex)
        }
    }
}
