package upjv.asi_mobile.carnetdevoyage;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity {
    private MapView mapView;
    private FirebaseFirestore db;
    private static final double INITIAL_MAX_ZOOM = 19.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.mapView);
        Button btnBack = findViewById(R.id.btnBack);
        db = FirebaseFirestore.getInstance();

        // Configurer la carte
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        // Récupérer l'ID du trajet
        long trajetId = getIntent().getLongExtra("trajetId", -1);

        // Charger les points du trajet
        db.collection("carnetdevoyage").document("data").collection("points")
                .whereEqualTo("trajet_id", trajetId)
                .orderBy("timestamp")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Polyline line = new Polyline();
                    List<GeoPoint> points = new ArrayList<>();
                    for (var doc : querySnapshot.getDocuments()) {
                        Double latitude = doc.getDouble("latitude");
                        Double longitude = doc.getDouble("longitude");
                        if (latitude != null && longitude != null) {
                            GeoPoint point = new GeoPoint(latitude, longitude);
                            line.addPoint(point);
                            points.add(point);
                        }
                    }
                    mapView.getOverlays().add(line);

                    // Ajouter marqueurs début/fin
                    if (!points.isEmpty()) {
                        Marker startMarker = new Marker(mapView);
                        startMarker.setPosition(points.get(0));
                        startMarker.setTitle("Début");
                        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        mapView.getOverlays().add(startMarker);

                        if (points.size() > 1) {
                            Marker endMarker = new Marker(mapView);
                            endMarker.setPosition(points.get(points.size() - 1));
                            endMarker.setTitle("Fin");
                            endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            mapView.getOverlays().add(endMarker);
                        }

                        // Zoom initial pour ajuster aux points
                        if (points.size() == 1) {
                            mapView.getController().setCenter(points.get(0));
                            mapView.getController().setZoom(15.0);
                        } else {
                            double minLat = points.get(0).getLatitude(), maxLat = minLat;
                            double minLon = points.get(0).getLongitude(), maxLon = minLon;

                            for (GeoPoint point : points) {
                                minLat = Math.min(minLat, point.getLatitude());
                                maxLat = Math.max(maxLat, point.getLatitude());
                                minLon = Math.min(minLon, point.getLongitude());
                                maxLon = Math.max(maxLon, point.getLongitude());
                            }

                            double latPadding = (maxLat - minLat) * 0.1;
                            double lonPadding = (maxLon - minLon) * 0.1;
                            if (latPadding == 0) latPadding = 0.001;
                            if (lonPadding == 0) lonPadding = 0.001;

                            BoundingBox box = new BoundingBox(
                                    maxLat + latPadding,
                                    maxLon + lonPadding,
                                    minLat - latPadding,
                                    minLon - lonPadding
                            );
                            mapView.zoomToBoundingBox(box, true, 50, INITIAL_MAX_ZOOM, 0L);
                        }
                    } else {
                        // Cas trajet vide
                        mapView.getController().setCenter(new GeoPoint(0.0, 0.0));
                        mapView.getController().setZoom(3.0);
                    }
                    mapView.invalidate();
                });

        // Bouton retour
        btnBack.setOnClickListener(v -> finish());
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