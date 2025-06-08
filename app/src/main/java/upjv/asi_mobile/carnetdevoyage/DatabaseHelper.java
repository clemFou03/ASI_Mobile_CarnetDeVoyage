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
    // Nom et version de la bd
    private static final String DATABASE_NAME = "CarnetDeVoyage.db";
    private static final int DATABASE_VERSION = 1;

    // Noms des tables
    private static final String TABLE_TRAJETS = "trajets";
    private static final String TABLE_POINTS = "points";

    // Constructeur
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Création de la table des trajets avec : id auto-incrémenté et un titre
        db.execSQL("CREATE TABLE " + TABLE_TRAJETS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, titre TEXT)");

        // Création de la table des points GPS avec : id auto-incrémenté, trajet_id (clé étrangère), latitude, longitude et timestamp
        db.execSQL("CREATE TABLE " + TABLE_POINTS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, trajet_id INTEGER, latitude REAL, longitude REAL, timestamp TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Suppression des tables existantes si la version change
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POINTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRAJETS);
        // Recréation des tables
        onCreate(db);
    }

    /**
     * Ajoute un nouveau trajet dans la base de données
     * @param titre Le nom/titre du trajet
     * @return L'ID du trajet créé
     */
    public long addTrajet(String titre) {
        try (SQLiteDatabase db = getWritableDatabase()) { // Utilisation de try-with-resources pour garantir la fermeture de la base
            ContentValues values = new ContentValues();
            values.put("titre", titre);
            // Insertion et retour de l'ID généré
            return db.insert(TABLE_TRAJETS, null, values);
        }
    }

    /**
     * Ajoute un point GPS à un trajet existant
     * @param trajetId L'ID du trajet auquel ajouter le point
     * @param latitude La latitude du point
     * @param longitude La longitude du point
     */
    public void addPoint(long trajetId, double latitude, double longitude) {
        try (SQLiteDatabase db = getWritableDatabase()) {
            ContentValues values = new ContentValues();
            values.put("trajet_id", trajetId);
            values.put("latitude", latitude);
            values.put("longitude", longitude);
            // Formatage de la date/heure actuelle
            values.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            db.insert(TABLE_POINTS, null, values);
        }
    }

    /**
     * Récupère tous les points GPS d'un trajet donné
     * @param trajetId L'ID du trajet
     * @return Une liste de points GPS triés par ordre d'ajout (id croissant)
     */
    public List<PointGPS> getPointsForTrajet(long trajetId) {
        List<PointGPS> points = new ArrayList<>();
        try (SQLiteDatabase db = getReadableDatabase();
             Cursor cursor = db.query(TABLE_POINTS,
                     new String[]{"id", "trajet_id", "latitude", "longitude", "timestamp"},
                     "trajet_id = ?", new String[]{String.valueOf(trajetId)},
                     null, null, "id ASC")) { // Tri par ID pour avoir les points dans l'ordre
            while (cursor.moveToNext()) {
                // Parcours des résultats
                points.add(new PointGPS(
                        cursor.getLong(0),    // ID du point
                        cursor.getLong(1),    // ID du trajet
                        cursor.getDouble(2),  // Latitude
                        cursor.getDouble(3),  // Longitude
                        cursor.getString(4)   // Timestamp
                ));
            }
        }
        return points;
    }
}