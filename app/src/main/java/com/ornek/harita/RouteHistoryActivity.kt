package com.ornek.harita

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class RouteHistoryActivity : AppCompatActivity() {
    private val files = ArrayList<File>()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val rowPad = (12 * density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "Geçmiş Rotalar"
        title.textSize = 20f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(0, 0, 0, (20 * density).toInt())
        root.addView(title)

        val empty = TextView(this)
        empty.text = "Kayıtlı rota yok"
        empty.textSize = 14f
        root.addView(empty)

        files.addAll(RouteStore.list(this))
        adapter = object : BaseAdapter() {
            override fun getCount(): Int = files.size
            override fun getItem(position: Int): Any = files[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = LinearLayout(this@RouteHistoryActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, rowPad, 0, rowPad)

                val name = TextView(this@RouteHistoryActivity)
                name.text = RouteStore.title(files[position])
                name.textSize = 16f
                row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                val del = TextView(this@RouteHistoryActivity)
                del.text = "Sil"
                del.textSize = 14f
                del.setTypeface(del.typeface, Typeface.BOLD)
                del.setTextColor(Color.parseColor("#E53935"))
                del.setPadding(rowPad * 2, rowPad, rowPad * 2, rowPad)
                del.setOnClickListener { confirmDelete(position) }
                row.addView(del)
                return row
            }
        }

        val list = ListView(this)
        list.adapter = adapter
        list.emptyView = empty
        list.setOnItemClickListener { _, _, pos, _ ->
            val i = Intent(this, RouteViewActivity::class.java)
            i.putExtra("file", files[pos].absolutePath)
            startActivity(i)
        }
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun confirmDelete(position: Int) {
        if (position < 0 || position >= files.size) return
        val f = files[position]
        AlertDialog.Builder(this)
            .setTitle("Rotayı sil")
            .setMessage(RouteStore.title(f) + " tarihli rota silinsin mi?")
            .setPositiveButton("Sil") { _, _ ->
                f.delete()
                files.remove(f)
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("İptal", null)
            .show()
    }
}
