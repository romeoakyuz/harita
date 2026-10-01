package com.harita.gps;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private Button btnBaslat, btnDurdur, btnAyarlar, btnTemizle;
    private TextView tvDurum, tvNoktaSayisi, tvSonKonum;
    private KonumVeritabani db;

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && GpsTrackingService.ACTION_LOCATION_BROADCAST.equals(intent.getAction())) {
                double lat = intent.getDoubleExtra(GpsTrackingService.EXTRA_LAT, 0.0);
                double lng = intent.getDoubleExtra(GpsTrackingService.EXTRA_LNG, 0.0);
                float acc = intent.getFloatExtra(GpsTrackingService.EXTRA_ACCURACY, 0f);
                boolean saved = intent.getBooleanExtra(GpsTrackingService.EXTRA_SAVED, false);

                String durumText = saved ? "✅ Nokta Kaydedildi" : "⏳ Filtreye Takıldı (Atlandı)";
                tvSonKonum.setText(String.format("Son Konum: %.5f, %.5f (Sapma: %.1fm)\n%s", lat, lng, acc, durumText));
                ekraniGuncelle();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new KonumVeritabani(this);

        btnBaslat = findViewById(R.id.btnBaslat);
        btnDurdur = findViewById(R.id.btnDurdur);
        btnAyarlar = findViewById(R.id.btnAyarlar);
        btnTemizle = findViewById(R.id.btnTemizle);

        tvDurum = findViewById(R.id.tvDurum);
        tvNoktaSayisi = findViewById(R.id.tvNoktaSayisi);
        tvSonKonum = findViewById(R.id.tvSonKonum);

        izinleriKontrolEt();

        btnBaslat.setOnClickListener(v -> servisBaslat());
        btnDurdur.setOnClickListener(v -> servisDurdur());
        btnAyarlar.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        btnTemizle.setOnClickListener(v -> {
            db.tumNoktalariSil();
            Toast.makeText(this, "Tüm veriler temizlendi.", Toast.LENGTH_SHORT).show();
            ekraniGuncelle();
        });

        ekraniGuncelle();
    }

    private void servisBaslat() {
        Intent intent = new Intent(this, GpsTrackingService.class);
        intent.setAction(GpsTrackingService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        tvDurum.setText("Durum: Kayıt Yapılıyor...");
        btnBaslat.setEnabled(false);
        btnDurdur.setEnabled(true);
    }

    private void servisDurdur() {
        Intent intent = new Intent(this, GpsTrackingService.class);
        intent.setAction(GpsTrackingService.ACTION_STOP);
        startService(intent);
        tvDurum.setText("Durum: Durduruldu");
        btnBaslat.setEnabled(true);
        btnDurdur.setEnabled(false);
    }

    private void ekraniGuncelle() {
        int count = db.getNoktaSayisi();
        tvNoktaSayisi.setText("Toplam Kayıtlı Nokta: " + count);
    }

    private void izinleriKontrolEt() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(this, locationReceiver, new IntentFilter(GpsTrackingService.ACTION_LOCATION_BROADCAST), ContextCompat.RECEIVER_EXPORTED);
        ekraniGuncelle();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(locationReceiver);
        } catch (Exception ignored) {}
    }
}
