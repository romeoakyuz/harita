package com.ornek.harita

import org.json.JSONArray
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

object VectorStyle {
    const val URL = "https://tiles.openfreemap.org/styles/liberty"
    private const val EXTRA = 5.0

    fun apply(style: Style, satellite: Boolean) {
        enlargePlaceLabels(style, 1.3)
        boldPlaceLabels(style)
        if (satellite) {
            applySatellite(style)
            whitePlaceLabels(style)
        }
    }

    private fun applySatellite(style: Style) {
        val firstId = style.layers[0].id
        for (layer in style.layers) {
            if (layer is FillLayer || layer is BackgroundLayer || layer is FillExtrusionLayer) {
                layer.setProperties(PropertyFactory.visibility(Property.NONE))
            }
        }
        val tiles = TileSet("2.2.0", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}")
        style.addSource(RasterSource("sat-src", tiles, 256))
        style.addLayerBelow(RasterLayer("sat-layer", "sat-src"), firstId)
    }

    private fun whitePlaceLabels(style: Style) {
        for (layer in style.layers) {
            if (layer !is SymbolLayer) continue
            if (layer.sourceLayer != "place") continue
            layer.setProperties(
                PropertyFactory.textColor(android.graphics.Color.WHITE),
                PropertyFactory.textHaloWidth(0f),
                PropertyFactory.textHaloBlur(0f)
            )
        }
    }

    private fun boldPlaceLabels(style: Style) {
        var bold: Array<String>? = null
        for (layer in style.layers) {
            if (layer !is SymbolLayer) continue
            val f: Array<String>? = try { layer.textFont.value } catch (e: Exception) { null }
            if (f != null && f.size == 1 && f[0].endsWith("Bold")) {
                bold = f
                break
            }
        }
        if (bold == null) return
        val lit = JSONArray()
        lit.put("literal")
        lit.put(JSONArray(bold.toList()))
        val ex = Expression.raw(lit.toString())
        for (layer in style.layers) {
            if (layer !is SymbolLayer) continue
            if (layer.sourceLayer != "place") continue
            layer.setProperties(PropertyFactory.textFont(ex))
        }
    }

    private fun enlargePlaceLabels(style: Style, f: Double) {
        for (layer in style.layers) {
            if (layer !is SymbolLayer) continue
            if (layer.sourceLayer != "place") continue
            try {
                val pv = layer.textSize
                val ex = pv.expression
                if (ex != null) {
                    val arr = toJson(ex.toArray()) as JSONArray
                    layer.setProperties(PropertyFactory.textSize(Expression.raw(scaleExpr(arr, f).toString())))
                } else {
                    val v = pv.value
                    if (v != null) layer.setProperties(PropertyFactory.textSize(v * f.toFloat() + EXTRA.toFloat()))
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun toJson(o: Any?): Any? {
        if (o is Array<*>) {
            val a = JSONArray()
            for (e in o) a.put(toJson(e))
            return a
        }
        return o
    }

    private fun mul(v: Any?, f: Double): Any? {
        if (v is Number) return v.toDouble() * f + EXTRA
        val a = JSONArray()
        a.put("*")
        a.put(f)
        a.put(v)
        val w = JSONArray()
        w.put("+")
        w.put(EXTRA)
        w.put(a)
        return w
    }

    private fun scaleExpr(src: JSONArray, f: Double): JSONArray {
        val op = src.optString(0)
        var start = -1
        if (op == "interpolate" || op == "interpolate-hcl" || op == "interpolate-lab") start = 4
        else if (op == "step") start = 2
        if (start < 0) return mul(src, f) as JSONArray
        val out = JSONArray(src.toString())
        var i = start
        while (i < out.length()) {
            out.put(i, mul(out.get(i), f))
            i += 2
        }
        return out
    }
}
