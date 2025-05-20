package upjv.asi_mobile.carnetdevoyage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "carnet_voyage.db";
    private static final int DATABASE_VERSION = 1;

    // Table Trajet
    private static final String TABLE_TRAJET = "trajet";
    private static final String COLUMN_TRAJET_ID = "id";
    private static final String COLUMN_TRAJET_DATE_DEBUT = "date_debut";
    private static final String COLUMN_TRAJET_DATE_FIN = "date_fin";

    // Table Point
    private static final String TABLE_POINT = "point";
    private static final String COLUMN_POINT_ID = "id";
    private static final String COLUMN_POINT_TRAJET_ID = "trajet_id";
    private static final String COLUMN_POINT_LATITUDE = "latitude";
    private static final String COLUMN_POINT_LONGITUDE = "longitude";
    private static final String COLUMN_POINT_DATE = "date";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TRAJET_TABLE = "CREATE TABLE " + TABLE_TRAJET + "("
                + COLUMN_TRAJET_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TRAJET_DATE_DEBUT + " INTEGER,"
                + COLUMN_TRAJET_DATE_FIN + " INTEGER)";
        db.execSQL(CREATE_TRAJET_TABLE);

        String CREATE_POINT_TABLE = "CREATE TABLE " + TABLE_POINT + "("
                + COLUMN_POINT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_POINT_TRAJET_ID + " INTEGER,"
                + COLUMN_POINT_LATITUDE + " REAL,"
                + COLUMN_POINT_LONGITUDE + " REAL,"
                + COLUMN_POINT_DATE + " INTEGER,"
                + "FOREIGN KEY(" + COLUMN_POINT_TRAJET_ID + ") REFERENCES "
                + TABLE_TRAJET + "(" + COLUMN_TRAJET_ID + "))";
        db.execSQL(CREATE_POINT_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POINT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRAJET);
        onCreate(db);
    }

    // Méthodes pour gérer les trajets et points
    public long addTrajet(Trajet trajet) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TRAJET_DATE_DEBUT, trajet.getDateDebut().getTime());
        long id = db.insert(TABLE_TRAJET, null, values);
        db.close();
        return id;
    }

    public void updateTrajetFin(Trajet trajet) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TRAJET_DATE_FIN, trajet.getDateFin().getTime());
        db.update(TABLE_TRAJET, values, COLUMN_TRAJET_ID + " = ?",
                new String[]{String.valueOf(trajet.getId())});
        db.close();
    }

    public void addPointToTrajet(long trajetId, PointGPS point) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_POINT_TRAJET_ID, trajetId);
        values.put(COLUMN_POINT_LATITUDE, point.getLatitude());
        values.put(COLUMN_POINT_LONGITUDE, point.getLongitude());
        values.put(COLUMN_POINT_DATE, point.getDate().getTime());
        db.insert(TABLE_POINT, null, values);
        db.close();
    }

    public List<PointGPS> getPointsForTrajet(long trajetId) {
        List<PointGPS> points = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_POINT,
                new String[]{COLUMN_POINT_LATITUDE, COLUMN_POINT_LONGITUDE, COLUMN_POINT_DATE},
                COLUMN_POINT_TRAJET_ID + "=?",
                new String[]{String.valueOf(trajetId)},
                null, null, COLUMN_POINT_DATE + " ASC");

        if (cursor.moveToFirst()) {
            do {
                double lat = cursor.getDouble(0);
                double lon = cursor.getDouble(1);
                Date date = new Date(cursor.getLong(2));
                points.add(new PointGPS(lat, lon, date));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return points;
    }
}
