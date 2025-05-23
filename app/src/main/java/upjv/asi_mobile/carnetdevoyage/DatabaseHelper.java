package upjv.asi_mobile.carnetdevoyage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "CarnetDeVoyage.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_TRAJETS = "trajets";
    private static final String TABLE_POINTS = "points";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTrajetsTable = "CREATE TABLE " + TABLE_TRAJETS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, titre TEXT)";
        String createPointsTable = "CREATE TABLE " + TABLE_POINTS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, trajet_id INTEGER, latitude REAL, longitude REAL, timestamp TEXT)";
        db.execSQL(createTrajetsTable);
        db.execSQL(createPointsTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POINTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRAJETS);
        onCreate(db);
    }

    public long addTrajet(String titre) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("titre", titre);
        long id = db.insert(TABLE_TRAJETS, null, values);
        db.close();
        return id;
    }

    public void addPoint(long trajetId, double latitude, double longitude) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("trajet_id", trajetId);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        db.insert(TABLE_POINTS, null, values);
        db.close();
    }

    public List<PointGPS> getPointsForTrajet(long trajetId) {
        List<PointGPS> points = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_POINTS, new String[]{"id", "trajet_id", "latitude", "longitude", "timestamp"},
                "trajet_id = ?", new String[]{String.valueOf(trajetId)}, null, null, null);
        while (cursor.moveToNext()) {
            points.add(new PointGPS(
                    cursor.getLong(0),
                    cursor.getLong(1),
                    cursor.getDouble(2),
                    cursor.getDouble(3),
                    cursor.getString(4)
            ));
        }
        cursor.close();
        db.close();
        return points;
    }
}