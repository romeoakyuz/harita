package com.ornek.harita

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RouteRecordSettingsActivity : AppCompatActivity() {
    private val values = intArrayOf(1, 3, 5, 10)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_record_settings)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val cardWalk = findViewById<View>(R.id.cardWalk)
        val cardCar = findViewById<View>(R.id.cardCar)
        val rbWalk = findViewById<RadioButton>(R.id.rbModeWalk)
        val rbCar = findViewById<RadioButton>(R.id.rbModeCar)
        fun showMode(m: String) {
            rbWalk.isChecked = (m == "walk")
            rbCar.isChecked = (m == "car")
            cardWalk.setBackgroundResource(if (m == "walk") R.drawable.bg_route_card_active else R.drawable.bg_route_card)
            cardCar.setBackgroundResource(if (m == "car") R.drawable.bg_route_card_active else R.drawable.bg_route_card)
        }
        fun pick(m: String) {
            prefs.edit().putString("route_mode", m).apply()
            showMode(m)
        }
        showMode(prefs.getString("route_mode", "walk") ?: "walk")
        cardWalk.setOnClickListener { pick("walk") }
        rbWalk.setOnClickListener { pick("walk") }
        cardCar.setOnClickListener { pick("car") }
        rbCar.setOnClickListener { pick("car") }

        setupChips(prefs, "walk", intArrayOf(R.id.chipWalk1, R.id.chipWalk3, R.id.chipWalk5, R.id.chipWalk10))
        setupChips(prefs, "car", intArrayOf(R.id.chipCar1, R.id.chipCar3, R.id.chipCar5, R.id.chipCar10))
    }

    private fun setupChips(prefs: SharedPreferences, mode: String, ids: IntArray) {
        val chips = ids.map { findViewById<TextView>(it) }
        val normalColor = chips[0].currentTextColor
        fun show(sec: Int) {
            for (i in chips.indices) {
                val on = (values[i] == sec)
                chips[i].isSelected = on
                chips[i].setTextColor(if (on) Color.WHITE else normalColor)
            }
        }
        var cur = prefs.getInt("route_interval_$mode", 5)
        if (values.indexOf(cur) < 0) cur = 5
        show(cur)
        for (i in chips.indices) {
            chips[i].setOnClickListener {
                prefs.edit().putInt("route_interval_$mode", values[i]).apply()
                show(values[i])
            }
        }
    }
}
