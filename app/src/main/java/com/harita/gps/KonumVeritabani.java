package com.harita.gps;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;

public class KonumVeritabani extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "gps_noktalari.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_NAME = "noktalar";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_LAT = "enlem";
    public static final String COLUMN_LNG = "boylam";
    public static final String COLUMN_ALT = "yukseklik";
    public static final String COLUMN_ACC = "hassasiyet";
    public static final String COLUMN_TIME = "zaman";

    public KonumVeritabani(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_LAT + " REAL, " +
                COLUMN_LNG + " REAL, " +
                COLUMN_ALT + " REAL, " +
                COLUMN_ACC + " REAL, " +
                COLUMN_TIME + " INTEGER)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public boolean noktaEkle(Location location) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_LAT, location.getLatitude());
        cv.put(COLUMN_LNG, location.getLongitude());
        cv.put(COLUMN_ALT, location.getAltitude());
        cv.put(COLUMN_ACC, location.getAccuracy());
        cv.put(COLUMN_TIME, location.getTime());

        long result = db.insert(TABLE_NAME, null, cv);
        return result != -1;
    }

    public int getNoktaSayisi() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    public void tumNoktalariSil() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_NAME);
    }
}
