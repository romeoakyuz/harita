package com.harita.gps

import android.content.Context
import android.location.Location
import androidx.preference.PreferenceManager

class GpsKayitFiltresi(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    // 1. Kayıt Sıklığı (Saniye)
    val kayitSikligiSn: Long
        get() = prefs.getString("kayit_sikligi", "1")?.toLongOrNull() ?: 1L

    // 2. Minimum Mesafe (Metre)
    val minMesafeMetre: Float
        get() = prefs.getString("min_mesafe", "10")?.toFloatOrNull() ?: 10f

    // 3. Maksimum Mesafe (Metre)
    val maxMesafeMetre: Float
        get() = prefs.getString("max_mesafe", "500")?.toFloatOrNull() ?: 500f

    // 4. GPS Hassasiyet Sınırı (Metre)
    val gpsHassasiyetMetre: Float
        get() = prefs.getString("gps_hassasiyet", "50")?.toFloatOrNull() ?: 50f

    private var sonKaydedilenKonum: Location? = null

    /**
     * Konum servisine yeni bir konum geldiğinde çağrılır.
     * @return true ise haritaya/veritabanına kaydet, false ise yoksay.
     */
    fun noktaKaydedilmeliMi(yeniKonum: Location): Boolean {
        // 4. GPS Hassasiyet Sınırı Kontrolü
        if (yeniKonum.hasAccuracy() && yeniKonum.accuracy > gpsHassasiyetMetre) {
            return false // Sapma yüksek, yoksay
        }

        val sonKonum = sonKaydedilenKonum
        if (sonKonum == null) {
            sonKaydedilenKonum = yeniKonum
            return true
        }

        val katedilenMesafe = sonKonum.distanceTo(yeniKonum)

        // 2. Minimum Mesafe Kontrolü
        if (katedilenMesafe < minMesafeMetre) {
            return false
        }

        // 3. Maksimum Mesafe (Segment) Kontrolü
        if (katedilenMesafe > maxMesafeMetre) {
            // Çizgi kopması veya hızlı hareket durumu
        }

        sonKaydedilenKonum = yeniKonum
        return true
    }

    fun sifirla() {
        sonKaydedilenKonum = null
    }
}
