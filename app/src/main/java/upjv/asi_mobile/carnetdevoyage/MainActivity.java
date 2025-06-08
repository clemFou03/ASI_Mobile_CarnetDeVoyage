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
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    // Éléments de l'interface utilisateur
    private RadioGroup radioGroupMode;
    private Button btnStart, btnPoint, btnEnd;
    private FusedLocationProviderClient fusedLocationClient; // Client pour accéder aux services de localisation
    private DatabaseHelper dbHelper;
    private long currentTrajetId = -1; // ID du trajet en cours
    private boolean isTracking = false; // Indique si un trajet est en cours
    private boolean isManualMode = true; // Indicateur pour le mode de pointage (manuel/automatique)
    private Handler handler; // Pour gérer les tâches périodiques
    private Runnable periodicTask; // Tâche pour le pointage automatique
    private ActivityResultLauncher<String> requestPermissionLauncher; // Pour demander la permission de localisation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialisation des composants UI
        radioGroupMode = findViewById(R.id.radioGroupMode);
        btnStart = findViewById(R.id.btnStart);
        btnPoint = findViewById(R.id.btnPoint);
        btnEnd = findViewById(R.id.btnEnd);
        // Initialisation des services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        dbHelper = new DatabaseHelper(this);
        handler = new Handler(Looper.getMainLooper());

        // Configuration du launcher pour les permissions
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                startTracking();
            } else {
                showAlert("Permission GPS refusée. L'application ne peut pas fonctionner.");
            }
        });

        // Gestion du clic sur le bouton "Départ"
        btnStart.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                startTracking();
            }
        });

        // Gestion du clic sur le bouton "Pointage"
        btnPoint.setOnClickListener(v -> saveCurrentLocation());

        // Gestion du clic sur le bouton "Fin"
        btnEnd.setOnClickListener(v -> endTracking());
    }

    /**
     * Démarre le suivi du trajet selon le mode sélectionné
     */
    private void startTracking() {
        // Détermine le mode de pointage sélectionné
        isManualMode = ((RadioButton) findViewById(radioGroupMode.getCheckedRadioButtonId())).getText().toString().equals("Pointage manuel");
        isTracking = true;
        radioGroupMode.setEnabled(false); // Désactive le choix du mode pendant le trajet
        btnStart.setVisibility(View.GONE);
        btnEnd.setVisibility(View.VISIBLE);
        if (isManualMode) {
            btnPoint.setVisibility(View.VISIBLE); // Mode manuel: affiche le bouton de pointage
        } else {
            // Mode automatique : active le pointage automatique
            periodicTask = new Runnable() {
                @Override
                public void run() {
                    saveCurrentLocation();
                    handler.postDelayed(this, 10 * 1000); // Toutes les 10 secondes
                }
            };
            handler.post(periodicTask);
        }

        // Crée un nouveau trajet dans la bd
        currentTrajetId = dbHelper.addTrajet("Trajet " + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));
    }

    /**
     * Enregistre la position actuelle dans la base de données
     */
    private void saveCurrentLocation() {
        // Vérifie la permission GPS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showAlert("Permission GPS non accordée. Impossible d'enregistrer la position.");
            return;
        }

        // Configure la requête de localisation
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000);

        try {
            // Demande la position actuelle
            fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null && locationResult.getLastLocation() != null) {
                        // Récupère et enregistre les coordonnées
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

        // Arrête le pointage périodique si mode automatique
        if (!isManualMode) {
            handler.removeCallbacks(periodicTask);
        }

        // Réinitialise l'interface
        btnPoint.setVisibility(View.GONE);
        btnEnd.setVisibility(View.GONE);
        btnStart.setVisibility(View.VISIBLE);
        radioGroupMode.setEnabled(true);

        // Demande un titre pour le fichier GPX
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Titre du fichier GPX");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String titre = input.getText().toString().isEmpty() ? "voyage" : input.getText().toString();
            createAndShareGpxFile(titre); // Crée et partage le fichier
        });
        builder.setNegativeButton("Annuler", null);
        builder.show();
    }

    /**
     * Crée un fichier GPX et propose de le partager
     * @param titre Le nom du fichier GPX
     */
    private void createAndShareGpxFile(String titre) {
        // Construction du contenu XML du fichier GPX
        StringBuilder gpxContent = new StringBuilder();
        gpxContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<gpx version=\"1.1\" creator=\"CarnetDeVoyage\">\n")
                .append("<trk><name>").append(titre).append("</name><trkseg>\n");

        // Ajout de tous les points GPS du trajet
        for (PointGPS point : dbHelper.getPointsForTrajet(currentTrajetId)) {
            gpxContent.append("<trkpt lat=\"").append(point.getLatitude())
                    .append("\" lon=\"").append(point.getLongitude()).append("\">\n")
                    .append("<time>").append(point.getTimestamp()).append("</time>\n")
                    .append("</trkpt>\n");
        }
        gpxContent.append("</trkseg></trk></gpx>");

        try {
            // Utilisation de MediaStore pour écrire dans le répertoire Download
            ContentValues values = new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, titre + ".gpx");
            values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/gpx+xml");
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Download/");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                // Écriture du fichier
                try (FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
                    fos.write(gpxContent.toString().getBytes());
                }

                // Intent pour partager le fichier
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("application/gpx+xml");
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Fichier GPX : " + titre);
                emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
                emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(emailIntent, "Envoyer le fichier GPX"));
            }
        } catch (Exception e) {
            showAlert("Erreur lors de la création ou du partage du fichier : " + e.getMessage());
        }
    }

    /**
     * Affiche une alerte avec un message
     * @param message Le message à afficher
     */
    private void showAlert(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}