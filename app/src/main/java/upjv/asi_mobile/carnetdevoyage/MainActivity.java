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
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private RadioGroup radioGroupMode;
    private Button btnStart, btnPoint, btnEnd;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseHelper dbHelper;
    private long currentTrajetId = -1; // Identifiant du trajet en cours
    private boolean isTracking = false; // Indique si le suivi est actif
    private boolean isManualMode = true; // Mode manuel ou automatique
    private Handler handler;
    private Runnable periodicTask;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialisation des composants de l'interface UI
        radioGroupMode = findViewById(R.id.radioGroupMode);
        btnStart = findViewById(R.id.btnStart);
        btnPoint = findViewById(R.id.btnPoint);
        btnEnd = findViewById(R.id.btnEnd);
        // Initialisation des services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        dbHelper = new DatabaseHelper();
        handler = new Handler(Looper.getMainLooper());

        // Gestion de la demande de permission GPS
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                startTracking();
            } else {
                showAlert("Permission GPS refusée. L'application ne peut pas fonctionner.");
            }
        });

        // Bouton pour démarrer le suivi
        btnStart.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                startTracking();
            }
        });

        // Bouton pour enregistrer un point manuellement
        btnPoint.setOnClickListener(v -> saveCurrentLocation());

        // Bouton pour arrêter le suivi
        btnEnd.setOnClickListener(v -> endTracking());
    }

    /**
     * Démarre le suivi du trajet selon le mode sélectionné
     */
    private void startTracking() {
        // Vérifie si le mode manuel est sélectionné
        isManualMode = ((RadioButton) findViewById(radioGroupMode.getCheckedRadioButtonId())).getText().toString().equals("Pointage manuel");
        isTracking = true;
        radioGroupMode.setEnabled(false); // Désactive le choix du mode pendant le suivi
        btnStart.setVisibility(View.GONE);
        btnEnd.setVisibility(View.VISIBLE);
        if (isManualMode) {
            btnPoint.setVisibility(View.VISIBLE); // Affiche le bouton de pointage en mode manuel
        } else {
            // Tâche périodique pour enregistrer la position toutes les 5 minutes en mode automatique
            periodicTask = new Runnable() {
                @Override
                public void run() {
                    saveCurrentLocation();
                    handler.postDelayed(this, 5 * 60 * 1000); // 5 minutes
                }
            };
            handler.post(periodicTask);
        }

        // Enregistre un nouveau trajet avec un nom basé sur la date et l'heure
        currentTrajetId = dbHelper.addTrajet("Trajet " + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));
    }

    /**
     * Enregistre la position actuelle dans la base de données
     */
    private void saveCurrentLocation() {
        // Vérifie la permission GPS avant d'accéder à la localisation
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showAlert("Permission GPS non accordée. Impossible d'enregistrer la position.");
            return;
        }

        // Configuration de la requête de localisation
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000);

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null && locationResult.getLastLocation() != null) {
                        // Enregistre les coordonnées dans la base de données
                        double latitude = locationResult.getLastLocation().getLatitude();
                        double longitude = locationResult.getLastLocation().getLongitude();
                        dbHelper.addPoint(currentTrajetId, latitude, longitude);
                    }
                }
            }, Looper.getMainLooper());
        } catch (SecurityException e) {
            showAlert("Erreur de permission GPS : " + e.getMessage());
        }
    }


    /**
     * Termine le trajet en cours et propose de créer/sauvegarder le fichier GPX
     */
    private void endTracking() {
        isTracking = false;
        if (!isManualMode) {
            handler.removeCallbacks(periodicTask); // Arrête la tâche périodique en mode automatique
        }

        // Réinitialise l'interface
        btnPoint.setVisibility(View.GONE);
        btnEnd.setVisibility(View.GONE);
        btnStart.setVisibility(View.VISIBLE);
        radioGroupMode.setEnabled(true);

        // Affiche une boîte de dialogue pour saisir le nom du fichier GPX
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Titre du fichier GPX");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String titre = input.getText().toString().isEmpty() ? "voyage" : input.getText().toString();
            createAndShareGpxFile(titre);
        });
        builder.setNegativeButton("Annuler", null);
        builder.show();
    }


    /**
     * Crée un fichier GPX et propose de le partager
     * @param titre Le nom du fichier GPX
     */
    private void createAndShareGpxFile(String titre) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Requête pour récupérer les points du trajet actuel
        Query query = db.collection("carnetdevoyage").document("data").collection("points")
                .whereEqualTo("trajet_id", currentTrajetId)
                .orderBy("timestamp");

        query.get().addOnSuccessListener(querySnapshot -> {
            // Construit le contenu du fichier GPX
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
                // Crée un fichier GPX dans le dossier Téléchargements
                ContentValues values = new ContentValues();
                values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, titre + ".gpx");
                values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/gpx+xml");
                values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Download/");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (uri != null) {
                    try (FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
                        fos.write(gpxContent.toString().getBytes());
                    }

                    // Partage le fichier GPX via mail
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
            // Gestion des erreurs spécifiques de Firebase
            if (e.getMessage().contains("FAILED_PRECONDITION")) {
                showAlert("Erreur : Une indexation est requise. Veuillez créer un index dans la console Firebase (voir les détails dans les logs) et réessayez.");
            } else {
                showAlert("Erreur lors de la récupération des points : " + e.getMessage());
            }
        });
    }

    // Affiche une alerte avec un message personnalisé
    private void showAlert(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}