package upjv.asi_mobile.carnetdevoyage;

import android.os.Bundle;
import android.preference.PreferenceManager;
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

public class MapActivity extends AppCompatActivity {
    private MapView mapView;
    private MaterialButton btnBack;
    private FirebaseFirestore db;
    private String trajetId; // Changé de long à String

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        setContentView(R.layout.activity_map);

        // Initialisation des vues et de Firestore
        mapView = findViewById(R.id.mapView);
        btnBack = findViewById(R.id.btnBack);
        db = FirebaseFirestore.getInstance();

        // Récupération de l'ID du trajet depuis l'intent
        trajetId = getIntent().getStringExtra("trajetId"); // Changé de getLongExtra
        if (trajetId == null || trajetId.isEmpty()) {
            showError("Trajet ID non valide.");
            finish();
            return;
        }

        // Configuration du bouton de retour
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        } else {
            showError("Bouton de retour non trouvé.");
        }

        // Configuration de la carte
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        // Chargement des points du trajet
        loadTripPoints();
    }

    // Charge les points GPS depuis Firestore et affiche la polyligne et les marqueurs
    private void loadTripPoints() {
        db.collection("carnetdevoyage").document("data").collection("points")
                .whereEqualTo("trajet_id", trajetId)
                .orderBy("timestamp")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<GeoPoint> points = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Double latitude = document.getDouble("latitude");
                        Double longitude = document.getDouble("longitude");
                        String timestamp = document.getString("timestamp");
                        if (latitude != null && longitude != null) {
                            points.add(new GeoPoint(latitude, longitude));
                        }
                    }
                    if (!points.isEmpty()) {
                        Polyline polyline = new Polyline();
                        polyline.setPoints(points);
                        polyline.setColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                        polyline.setWidth(5f);
                        mapView.getOverlays().add(polyline);

                        Marker startMarker = new Marker(mapView);
                        startMarker.setPosition(points.get(0));
                        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        startMarker.setTitle("Début du trajet");
                        mapView.getOverlays().add(startMarker);

                        if (points.size() > 1) {
                            Marker endMarker = new Marker(mapView);
                            endMarker.setPosition(points.get(points.size() - 1));
                            endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            endMarker.setTitle("Fin du trajet");
                            mapView.getOverlays().add(endMarker);
                        }

                        mapView.getController().setCenter(points.get(0));
                        mapView.invalidate();
                    } else {
                        showError("Aucun point trouvé pour ce trajet.");
                    }
                })
                .addOnFailureListener(e -> showError("Erreur lors du chargement des points : " + e.getMessage()));
    }

    // Affiche une alerte en cas d'erreur
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