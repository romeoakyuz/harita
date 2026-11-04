package com.ornek.harita

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

object NavHelper {
    fun mapIntent(a: Activity): Intent {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(a)
        val vector = prefs.getString("map_provider", "google") == "vector"
        return Intent(a, if (vector) VectorMainActivity::class.java else MainActivity::class.java)
    }

    fun routeIntent(a: Activity): Intent {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(a)
        val vector = prefs.getString("map_provider", "google") == "vector"
        return Intent(a, if (vector) VectorRouteActivity::class.java else RouteActivity::class.java)
    }

    fun setupBottomNav(activity: Activity, bottomNav: BottomNavigationView, currentId: Int) {
        bottomNav.setOnItemSelectedListener { item ->
            val intent = when (item.itemId) {
                R.id.nav_settings -> Intent(activity, SettingsActivity::class.java)
                R.id.nav_map -> mapIntent(activity).apply { putExtra("tab_action", "map") }
                R.id.nav_route -> routeIntent(activity)
                else -> return@setOnItemSelectedListener false
            }
            
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)
            
            return@setOnItemSelectedListener false 
        }
    }
}
