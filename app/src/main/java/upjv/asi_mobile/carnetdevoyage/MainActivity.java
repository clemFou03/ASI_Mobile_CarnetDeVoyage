package upjv.asi_mobile.carnetdevoyage;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigation;
    private MaterialButton btnStart, btnPoint, btnEnd;
    private MaterialRadioButton radioManual, radioAuto;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseHelper dbHelper;
    private String currentTrajetId = null;
    private String currentTrajetTitre = "";
    private boolean isTracking = false;
    private LocationCallback locationCallback;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private FirebaseAuth auth;
    private BroadcastReceiver gpsSwitchReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialisation de Firebase et vérification de l'utilisateur connecté
        dbHelper = new DatabaseHelper();
        auth = FirebaseAuth.getInstance();

        // Redirige vers WelcomeActivity si aucun utilisateur n'est connecté
        if (dbHelper.getCurrentUserId() == null) {
            Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // Initialisation des vues et services de localisation
        radioManual = findViewById(R.id.radioManual);
        radioAuto = findViewById(R.id.radioAuto);
        btnStart = findViewById(R.id.btnStart);
        btnPoint = findViewById(R.id.btnPoint);
        btnEnd = findViewById(R.id.btnEnd);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Gestion de la navigation via BottomNavigationView
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                return true;
            } else if (itemId == R.id.nav_trips) {
                startActivity(new Intent(MainActivity.this, TripListActivity.class));
                return true;
            } else if (itemId == R.id.nav_logout) {
                auth.signOut();
                Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return true;
            }
            return false;
        });

        // Callback pour enregistrer les positions GPS
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (!isTracking) return;

                // Vérifie si la permission GPS a été retirée
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    // Arrête immédiatement le tracking
                    Toast.makeText(MainActivity.this, R.string.trajet_stopped_no_permission, Toast.LENGTH_LONG).show();
                    endTracking();
                    return;
                }

                // Si la localisation est toujours disponible, on continue normalement
                if (locationResult.getLastLocation() != null) {
                    double latitude = locationResult.getLastLocation().getLatitude();
                    double longitude = locationResult.getLastLocation().getLongitude();
                    dbHelper.addPoint(currentTrajetId, latitude, longitude, (success) -> {
                        if (success) {
                            Toast.makeText(MainActivity.this, R.string.point_ajout_reussi, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, R.string.point_ajout_echoue, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        };

        // Gestion de la demande de permission GPS
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                showTitleDialog();
            } else {
                showAlert(getString(R.string.erreur_permission_gps_refus));
            }
        });

        // Bouton pour démarrer un trajet
        btnStart.setOnClickListener(v -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction(() -> {
            v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                showTitleDialog();
            }
        }).start());

        btnPoint.setOnClickListener(v -> saveCurrentLocation());
        btnEnd.setOnClickListener(v -> endTracking());

        // Détecte les changements dans l'état du GPS
        gpsSwitchReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && intent.getAction().matches(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                    LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                    boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

                    if (!isGpsEnabled && isTracking) {
                        Toast.makeText(MainActivity.this, R.string.trajet_stopped_no_permission, Toast.LENGTH_LONG).show();
                        completeTrackingCleanup();
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(gpsSwitchReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gpsSwitchReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    // Affiche un dialogue pour entrer le titre du trajet
    private void showTitleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_trajet_title);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.dialog_trajet_hint);
        builder.setView(input);
        builder.setPositiveButton(R.string.dialog_ok_button, (dialog, which) -> {
            String titre = input.getText().toString().trim();
            if (titre.isEmpty()) {
                titre = getString(R.string.default_trajet_title, new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));
            }
            currentTrajetTitre = titre;
            startTracking(titre);
        });
        builder.setNegativeButton(R.string.dialog_cancel_button, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // Démarre le suivi du trajet (manuel ou auto)
    private void startTracking(String titre) {
        boolean isManualMode = radioManual.isChecked();
        isTracking = true;
        radioManual.setEnabled(false);
        radioAuto.setEnabled(false);
        btnStart.setVisibility(View.GONE);
        btnEnd.setVisibility(View.VISIBLE);
        bottomNavigation.setEnabled(false);

        // Création du trajet dans Firestore
        currentTrajetId = dbHelper.addTrajet(titre);

        if (isManualMode) {
            btnPoint.setVisibility(View.VISIBLE);
        } else {
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60 * 1000) // 1 minute
                    .setMinUpdateIntervalMillis(30 * 1000) // 30 secondes
                    .build();
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            } catch (SecurityException e) {
                showAlert(R.string.erreur_permission_gps + e.getMessage());
            }
        }
    }

    // Enregistre la position actuelle (pour mode manuel)
    private void saveCurrentLocation() {
        if (!isTracking || currentTrajetId == null) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMaxUpdates(1)
                .build();
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            showAlert(R.string.erreur_permission_gps + e.getMessage());
        }
    }

    // Arrête le suivi du trajet, capture un dernier point GPS, et génère le fichier GPX
    private void endTracking() {
        // Vérifie si un trajet est en cours et si un ID de trajet existe
        if (!isTracking || currentTrajetId == null) {
            completeTrackingCleanup();
            return;
        }

        // Demande du point final du trajet
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMaxUpdates(1)
                .build();
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    if (locationResult.getLastLocation() != null) {
                        double latitude = locationResult.getLastLocation().getLatitude();
                        double longitude = locationResult.getLastLocation().getLongitude();
                        dbHelper.addPoint(currentTrajetId, latitude, longitude, (success) -> {
                            if (success) {
                                Toast.makeText(MainActivity.this, R.string.point_ajout_reussi, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, R.string.point_ajout_echoue, Toast.LENGTH_SHORT).show();
                            }
                            completeTrackingCleanup();
                        });
                    } else {
                        completeTrackingCleanup();
                    }
                }
            }, Looper.getMainLooper());
        } catch (SecurityException e) {
            showAlert(getString(R.string.erreur_permission_gps, e.getMessage()));
            completeTrackingCleanup();
        }
    }

    // Finalise l'arrêt du trajet en nettoyant l'état de l'application
    private void completeTrackingCleanup() {
        isTracking = false;
        fusedLocationClient.removeLocationUpdates(locationCallback);
        btnPoint.setVisibility(View.GONE);
        btnEnd.setVisibility(View.GONE);
        btnStart.setVisibility(View.VISIBLE);
        radioManual.setEnabled(true);
        radioAuto.setEnabled(true);
        bottomNavigation.setEnabled(true);
        createAndShareGpxFile(currentTrajetTitre);
        currentTrajetId = null;
    }

    // Crée et partage un fichier GPX avec les points du trajet
    private void createAndShareGpxFile(String titre) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Query query = db.collection("carnetdevoyage").document("data").collection("points")
                .whereEqualTo("trajet_id", currentTrajetId)
                .orderBy("timestamp");

        query.get().addOnSuccessListener(querySnapshot -> {
            StringBuilder gpxContent = new StringBuilder();
            gpxContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    .append("<gpx version=\"1.1\" creator=\"CarnetDeVoyage\">\n")
                    .append("<trk><name>").append(titre).append("</name><trkseg>\n");

            for (var doc : querySnapshot.getDocuments()) {
                Double latitude = doc.getDouble("latitude");
                Double longitude = doc.getDouble("longitude");
                String timestamp = doc.getString("timestamp");
                if (latitude != null && longitude != null && timestamp != null) {
                    gpxContent.append("<trkpt lat=\"").append(latitude)
                            .append("\" lon=\"").append(longitude).append("\">\n")
                            .append("<time>").append(timestamp).append("</time>\n")
                            .append("</trkpt>\n");
                }
            }

            gpxContent.append("</trkseg></trk></gpx>");

            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, titre + ".gpx");
                values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/gpx+xml");
                values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Download/");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (uri != null) {
                    try (FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
                        fos.write(gpxContent.toString().getBytes());
                    }
                    Intent emailIntent = new Intent(Intent.ACTION_SEND);
                    emailIntent.setType("application/gpx+xml");
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Fichier GPX : " + titre);
                    emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(emailIntent, "Envoyer le fichier GPX"));
                } else {
                    showAlert(getString(R.string.gpx_file_creation_error));
                }
            } catch (Exception e) {
                showAlert(R.string.gpx_file_share_error + e.getMessage());
            }
        }).addOnFailureListener(e -> {
            if (Objects.requireNonNull(e.getMessage()).contains("FAILED_PRECONDITION")) {
                showAlert(getString(R.string.firestore_index_error));
            } else {
                showAlert(R.string.firestore_points_error + e.getMessage());
            }
        });
    }

    // Affiche une alerte avec un message
    private void showAlert(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok_button, null)
                .show();
    }
}