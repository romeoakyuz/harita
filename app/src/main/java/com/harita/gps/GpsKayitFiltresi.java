package com.harita.gps;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import androidx.preference.PreferenceManager;

public class GpsKayitFiltresi {

    private final Context context;
    private Location sonKaydedilenKonum;

    public GpsKayitFiltresi(Context context) {
        this.context = context;
    }

    public void sifirla() {
        sonKaydedilenKonum = null;
    }

    public int getKayitSikligiSn() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(sp.getString("kayit_sikligi", "5"));
    }

    public float getMinMesafeM() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return Float.parseFloat(sp.getString("min_mesafe", "10"));
    }

    public float getMaxSegmentMesafeM() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return Float.parseFloat(sp.getString("max_segment", "500"));
    }

    public float getMaxHassasiyetM() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return Float.parseFloat(sp.getString("max_hassasiyet", "20"));
    }

    public boolean noktaKaydedilmeliMi(Location yeniKonum) {
        if (yeniKonum == null) return false;

        if (yeniKonum.getAccuracy() > getMaxHassasiyetM()) {
            return false;
        }

        if (sonKaydedilenKonum == null) {
            sonKaydedilenKonum = yeniKonum;
            return true;
        }

        float mesafe = sonKaydedilenKonum.distanceTo(yeniKonum);

        if (mesafe > getMaxSegmentMesafeM()) {
            return false;
        }

        if (mesafe >= getMinMesafeM()) {
            sonKaydedilenKonum = yeniKonum;
            return true;
        }

        return false;
    }
}
