package com.harita.gps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class GpsTrackingService extends Service implements LocationListener {

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";
    public static final String ACTION_LOCATION_BROADCAST = "com.harita.gps.LOCATION_UPDATE";
    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LNG = "extra_lng";
    public static final String EXTRA_ACCURACY = "extra_accuracy";
    public static final String EXTRA_SAVED = "extra_saved";

    private LocationManager locationManager;
    private GpsKayitFiltresi filtre;
    private KonumVeritabani db;
    private boolean isTracking = false;

    @Override
    public void onCreate() {
        super.onCreate();
        filtre = new GpsKayitFiltresi(this);
        db = new KonumVeritabani(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (action.equals(ACTION_START)) {
                baslat();
            } else if (action.equals(ACTION_STOP)) {
                durdur();
            }
        }
        return START_STICKY;
    }

    private void baslat() {
        if (isTracking) return;

        bildirimOlustur();
        filtre.sifirla();

        long minTimeMs = filtre.getKayitSikligiSn() * 1000L;
        float minDistanceM = 0f;

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMs, minDistanceM, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMs, minDistanceM, this);
            }
            isTracking = true;
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void durdur() {
        if (!isTracking) return;
        try {
            locationManager.removeUpdates(this);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        isTracking = false;
        stopForeground(true);
        stopSelf();
    }

    private void bildirimOlustur() {
        String channelId = "gps_kayit_kanal";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "GPS Kayıt Servisi",
                    NotificationManager.IMPORTANCE_LOW
            );
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("GPS Noktasal Kayıt")
                .setContentText("Konum takibi ve filtreleme aktif...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1001, notification);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;

        boolean kaydedildi = filtre.noktaKaydedilmeliMi(location);
        if (kaydedildi) {
            db.noktaEkle(location);
        }

        Intent broadcastIntent = new Intent(ACTION_LOCATION_BROADCAST);
        broadcastIntent.putExtra(EXTRA_LAT, location.getLatitude());
        broadcastIntent.putExtra(EXTRA_LNG, location.getLongitude());
        broadcastIntent.putExtra(EXTRA_ACCURACY, location.getAccuracy());
        broadcastIntent.putExtra(EXTRA_SAVED, kaydedildi);
        sendBroadcast(broadcastIntent);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        durdur();
        super.onDestroy();
    }
}
