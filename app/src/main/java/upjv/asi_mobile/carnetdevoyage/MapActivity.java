package upjv.asi_mobile.carnetdevoyage;

import android.content.Context;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Activité d'affichage sur carte d'un trajet avec OpenStreetMap
 */
public class MapActivity extends AppCompatActivity {
    private MapView mapView; // Composant carte OSM
    private FirebaseFirestore db;
    private String trajetId; // ID du trajet à afficher

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configuration nécessaire pour OSMDroid (cache, user-agent, etc.)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE));

        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.mapView);
        MaterialButton btnBack = findViewById(R.id.btnBack);
        db = FirebaseFirestore.getInstance();

        // Récupération de l'ID du trajet par l'Intent
        trajetId = getIntent().getStringExtra("trajetId"); // Changé de getLongExtra
        if (trajetId == null || trajetId.isEmpty()) {
            showError("Trajet ID non valide.");
            finish(); // Ferme l'activité
            return;
        }

        // Configuration du bouton de retour
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        } else {
            showError("Bouton de retour non trouvé.");
        }

        // Configuration de la carte
        mapView.setTileSource(TileSourceFactory.MAPNIK); // Source des tuiles OSM
        mapView.setMultiTouchControls(true); // Active zoom/pan tactile
        mapView.getController().setZoom(15.0); // Niveau de zoom initial

        // Chargement et affichage des données GPS
        loadTripPoints();
    }

    /**
     * Charge les points GPS depuis Firestore et les affiche sur la carte
     * Crée une polyligne connectant tous les points + marqueurs début/fin
     */
    private void loadTripPoints() {
        db.collection("carnetdevoyage").document("data").collection("points")
                .whereEqualTo("trajet_id", trajetId) // Filtre par trajet
                .orderBy("timestamp") // Tri chronologique pour tracer le chemin
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<GeoPoint> points = new ArrayList<>();

                    // Conversion des données Firestore
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Double latitude = document.getDouble("latitude");
                        Double longitude = document.getDouble("longitude");
                        //String timestamp = document.getString("timestamp");
                        if (latitude != null && longitude != null) { // Vérification de la validité des coordonnées
                            points.add(new GeoPoint(latitude, longitude));
                        }
                    }

                    // Affichage sur la carte
                    if (!points.isEmpty()) {
                        // Ligne connectant tous les points GPS du trajet
                        Polyline polyline = new Polyline();
                        polyline.setPoints(points);
                        polyline.setColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                        polyline.setWidth(5f); // Épaisseur de la ligne
                        mapView.getOverlays().add(polyline);

                        // Marquer de début
                        Marker startMarker = new Marker(mapView);
                        startMarker.setPosition(points.get(0)); // Premier point
                        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        startMarker.setTitle("Début du trajet");
                        mapView.getOverlays().add(startMarker);

                        // Marquer de fin
                        if (points.size() > 1) { // Seulement s'il y a plusieurs points
                            Marker endMarker = new Marker(mapView);
                            endMarker.setPosition(points.get(points.size() - 1)); // Dernier point
                            endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            endMarker.setTitle("Fin du trajet");
                            mapView.getOverlays().add(endMarker);
                        }

                        // Centrage de la carte
                        mapView.getController().setCenter(points.get(0)); // Centre sur le début
                        mapView.invalidate(); // Force le redessin de la carte
                    } else {
                        showError("Aucun point trouvé pour ce trajet.");
                    }
                })
                .addOnFailureListener(e -> showError("Erreur lors du chargement des points : " + e.getMessage()));
    }

    /**
     * Affiche une boîte de dialogue d'erreur
     * @param message Message d'erreur à afficher
     */
    private void showError(String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
}