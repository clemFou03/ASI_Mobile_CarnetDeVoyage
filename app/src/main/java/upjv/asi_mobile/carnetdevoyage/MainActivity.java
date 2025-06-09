package upjv.asi_mobile.carnetdevoyage;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
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

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigation;
    private MaterialButton btnStart, btnPoint, btnEnd;
    private MaterialRadioButton radioManual, radioAuto;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseHelper dbHelper;
    private long currentTrajetId = -1;
    private String currentTrajetTitre = "";
    private boolean isTracking = false;
    private boolean isManualMode = true;
    private Handler handler;
    private LocationCallback locationCallback;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private FirebaseAuth auth;

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
        handler = new Handler(Looper.getMainLooper());

        // Gestion de la navigation via BottomNavigationView
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                return true;
            } else if (itemId == R.id.nav_trips) {
                startActivity(new Intent(MainActivity.this, TripListActivity.class));
                return true;
            } else if (itemId == R.id.nav_logout) {
                // Déconnexion et retour à WelcomeActivity
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
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null && locationResult.getLastLocation() != null && isTracking) {
                    double latitude = locationResult.getLastLocation().getLatitude();
                    double longitude = locationResult.getLastLocation().getLongitude();
                    dbHelper.addPoint(currentTrajetId, latitude, longitude);
                }
            }
        };

        // Gestion de la demande de permission GPS
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                showTitleDialog();
            } else {
                showAlert("Permission GPS refusée. L'application ne peut pas fonctionner.");
            }
        });

        // Bouton pour démarrer un trajet
        btnStart.setOnClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                } else {
                    showTitleDialog();
                }
            }).start();
        });

        btnPoint.setOnClickListener(v -> saveCurrentLocation());
        btnEnd.setOnClickListener(v -> endTracking());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Nettoyage des callbacks de localisation
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    // Affiche un dialogue pour entrer le titre du trajet
    private void showTitleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Titre du trajet");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Entrez le titre du trajet");
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String titre = input.getText().toString().trim();
            if (titre.isEmpty()) {
                titre = "Trajet " + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            }
            currentTrajetTitre = titre;
            startTracking(titre);
        });
        builder.setNegativeButton("Annuler", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // Démarre le suivi du trajet (manuel ou auto)
    private void startTracking(String titre) {
        isManualMode = radioManual.isChecked();
        isTracking = true;
        radioManual.setEnabled(false);
        radioAuto.setEnabled(false);
        btnStart.setVisibility(View.GONE);
        btnEnd.setVisibility(View.VISIBLE);
        bottomNavigation.setEnabled(false);

        // Création du trajet dans Firestore
        currentTrajetId = dbHelper.addTrajet(titre);

        if (isManualMode) {
            // Mode manuel : affiche le bouton de pointage
            btnPoint.setVisibility(View.VISIBLE);
        } else {
            // Mode automatique : configure les mises à jour continues
            LocationRequest locationRequest = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .setInterval(5 * 60 * 1000) // Mise à jour toutes les 5 minutes
                    .setFastestInterval(2 * 60 * 1000); // Intervalle minimum
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            } catch (SecurityException e) {
                showAlert("Erreur de permission GPS : " + e.getMessage());
            }
        }
    }

    // Enregistre la position actuelle (pour mode manuel)
    private void saveCurrentLocation() {
        if (!isTracking || currentTrajetId == -1) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showAlert("Permission GPS non accordée. Impossible d'enregistrer la position.");
            return;
        }

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(1)
                .setInterval(10000);

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            showAlert("Erreur de permission GPS : " + e.getMessage());
        }
    }

    // Arrête le suivi et génère le fichier GPX
    private void endTracking() {
        isTracking = false;
        // Arrête les mises à jour de localisation
        fusedLocationClient.removeLocationUpdates(locationCallback);
        btnPoint.setVisibility(View.GONE);
        btnEnd.setVisibility(View.GONE);
        btnStart.setVisibility(View.VISIBLE);
        radioManual.setEnabled(true);
        radioAuto.setEnabled(true);
        bottomNavigation.setEnabled(true);
        createAndShareGpxFile(currentTrajetTitre);
        currentTrajetId = -1;
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
                    showAlert("Erreur : Impossible de créer le fichier GPX.");
                }
            } catch (Exception e) {
                showAlert("Erreur lors de la création ou du partage du fichier : " + e.getMessage());
            }
        }).addOnFailureListener(e -> {
            if (e.getMessage().contains("FAILED_PRECONDITION")) {
                showAlert("Erreur : Une indexation est requise. Veuillez créer un index dans la console Firebase avec les champs 'trajet_id' et 'timestamp'.");
            } else {
                showAlert("Erreur lors de la récupération des points : " + e.getMessage());
            }
        });
    }

    // Affiche une alerte avec un message
    private void showAlert(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}