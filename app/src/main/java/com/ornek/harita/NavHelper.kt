package com.ornek.harita

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

object NavHelper {
    fun setupBottomNav(activity: Activity, bottomNav: BottomNavigationView, currentId: Int) {
        bottomNav.setOnItemSelectedListener { item ->
            val intent = when (item.itemId) {
                R.id.nav_settings -> Intent(activity, SettingsActivity::class.java)
                R.id.nav_map -> Intent(activity, MainActivity::class.java).apply { putExtra("tab_action", "map") }
                R.id.nav_route -> Intent(activity, RouteActivity::class.java)
                else -> return@setOnItemSelectedListener false
            }
            
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)
            
            return@setOnItemSelectedListener false 
        }
    }
}
