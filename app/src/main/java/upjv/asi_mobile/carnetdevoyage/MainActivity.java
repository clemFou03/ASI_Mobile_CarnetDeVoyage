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
import android.widget.TextView;
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

import upjv.asi_mobile.carnetdevoyage.model.PointGPS;
import upjv.asi_mobile.carnetdevoyage.model.Trajet;

/**
 * Activité principale de l'application Carnet de Voyage
 * Permet d'enregistrer un trajets GPS (automatique ou manuel)
 */
public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigation;
    private MaterialButton btnStart, btnPoint, btnEnd;
    private MaterialRadioButton radioManual, radioAuto;
    private FusedLocationProviderClient fusedLocationClient; // pour la localisation GPS
    private DatabaseHelper dbHelper; // pour intéragir avec Firebase

    // Variables d'état pour gérer le suivi du trajet
    private String currentTrajetId = null;
    private String currentTrajetTitre = "";
    private boolean isTracking = false;
    private LocationCallback locationCallback; // pour recevoir les mises à jour de position GPS
    private ActivityResultLauncher<String> requestPermissionLauncher; // pour demander la permission d'accès à la localisation
    private FirebaseAuth auth;
    private BroadcastReceiver gpsSwitchReceiver; // pour écouter les événements système (changement état GPS)


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
            // Efface la pile des activités pour éviter le retour en arrière
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish(); // Ferme cette activité
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
                return true; // Reste sur la page actuelle
            } else if (itemId == R.id.nav_trips) {
                // Navigation vers la liste des trajets
                startActivity(new Intent(MainActivity.this, TripListActivity.class));
                return true;
            } else if (itemId == R.id.nav_logout) {
                // Déconnexion et retour à l'écran d'accueil
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
                if (!isTracking) return; // Vérification que le suivi est toujours actif

                // Vérifie si la permission GPS a été retirée
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    // Arrête immédiatement le tracking
                    Toast.makeText(MainActivity.this, R.string.trajet_stopped_no_permission, Toast.LENGTH_LONG).show();
                    endTracking();
                    return;
                }

                // Si la localisation est toujours disponible, traitement de la nouvelle position GPS
                if (locationResult.getLastLocation() != null) {
                    // Extraction des coordonnées
                    double latitude = locationResult.getLastLocation().getLatitude();
                    double longitude = locationResult.getLastLocation().getLongitude();
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(new Date());
                    PointGPS point = new PointGPS(String.valueOf(System.currentTimeMillis()), currentTrajetId, latitude, longitude, timestamp);
                    // Sauvegarde du point dans Firebase
                    dbHelper.addPoint(currentTrajetId, point, (success) -> {
                        if (success) {
                            Toast.makeText(MainActivity.this, R.string.point_ajout_reussi, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, R.string.point_ajout_echoue, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        };

        // Gestion de la demande de permission de localisation
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                showTitleDialog();
            } else {
                showAlert(getString(R.string.erreur_permission_gps_refus));
            }
        });

        // Bouton pour démarrer un trajet
        btnStart.setOnClickListener(v -> v.animate()
                .scaleX(0.95f).scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> {
            v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();

            // Vérification de la permission GPS
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
                // Vérification que c'est bien un changement d'état des fournisseurs de localisation
                if (intent.getAction() != null && intent.getAction().matches(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                    LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                    boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

                    // Si le GPS a été désactivé pendant un trajet, on arrête le trajet
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
        // Enregistrement du récepteur pour surveiller les changements d'état du GPS
        registerReceiver(gpsSwitchReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Désenregistrement du récepteur pour éviter les fuites mémoire
        unregisterReceiver(gpsSwitchReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Nettoyage : arrêt des mises à jour de localisation
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    // Affiche un dialogue pour saisir le titre du trajet
    private void showTitleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_trajet_title);

        // Création du champ de saisie
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.dialog_trajet_hint);
        input.setTextColor(ContextCompat.getColor(this,R.color.black));
        input.setHintTextColor(ContextCompat.getColor(this,R.color.shadow));
        builder.setView(input);
        // Btn "OK", validation du titre et démarrage du suivi
        builder.setPositiveButton(R.string.dialog_ok_button, (dialog, which) -> {
            String titre = input.getText().toString().trim();
            // Si aucun titre n'est saisi, on génère un titre
            if (titre.isEmpty()) {
                titre = getString(R.string.default_trajet_title, new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));
            }
            currentTrajetTitre = titre;
            startTracking(titre);
        });
        // Btn "Annuler"
        builder.setNegativeButton(R.string.dialog_cancel_button, (dialog, which) -> dialog.cancel());

        AlertDialog alertDialog = builder.create();

        // Définir la couleur du titre en noir
        alertDialog.setOnShowListener(dialogInterface -> {
            TextView titleView = alertDialog.findViewById(androidx.appcompat.R.id.alertTitle);
            if (titleView != null) {
                titleView.setTextColor(ContextCompat.getColor(this,R.color.black));
            }
        });
        alertDialog.show();
    }

    /**
     * Démarre le suivi du trajet en mode manuel ou automatique
     * @param titre Le titre du trajet à créer
     */
    private void startTracking(String titre) {
        boolean isManualMode = radioManual.isChecked();
        isTracking = true;
        radioManual.setEnabled(false);
        radioAuto.setEnabled(false);
        btnStart.setVisibility(View.GONE);
        btnEnd.setVisibility(View.VISIBLE);
        bottomNavigation.setEnabled(false);

        // Création du trajet avec un ID unique
        String trajetId = String.valueOf(System.currentTimeMillis());
        Trajet trajet = new Trajet(trajetId, titre);
        currentTrajetId = dbHelper.addTrajet(trajet);

        if (isManualMode) {
            btnPoint.setVisibility(View.VISIBLE); // affichage du btn de pointage manuel
        } else {
            // Mode automatique : configuration des mises à jour de localisation
            LocationRequest locationRequest = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, // Précision maximale
                    30 * 1000)                        // Intervalle principal : 30 secondes
                    .setMinUpdateIntervalMillis(15 * 1000)  // Intervalle minimal : 10 secondes
                    .build();
            try {
                // Démarrage des mises à jour automatiques de localisation
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            } catch (SecurityException e) {
                showAlert(R.string.erreur_permission_gps + e.getMessage());
            }
        }
    }

    /**
     * Enregistre la position actuelle (mode manuel)
     */
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

    /**
     * Arrête le suivi du trajet
     * Capture un dernier point GPS, puis génère et partage le fichier GPX
     */
    private void endTracking() {
        // Vérifie qu'un trajet est en cours et si un ID de trajet existe
        if (!isTracking || currentTrajetId == null) {
            completeTrackingCleanup();
            return;
        }

        // Configuration pour obtenir une dernière position GPS
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10000)
                .setMaxUpdates(1)
                .build();
        try {
            // Demande du point final avec un callback spécifique
            fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    if (locationResult.getLastLocation() != null) {
                        // Enregistrement du point final
                        double latitude = locationResult.getLastLocation().getLatitude();
                        double longitude = locationResult.getLastLocation().getLongitude();
                        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(new Date());
                        PointGPS point = new PointGPS(String.valueOf(System.currentTimeMillis()), currentTrajetId, latitude, longitude, timestamp);

                        // Sauvegarde
                        dbHelper.addPoint(currentTrajetId, point, (success) -> {
                            if (success) {
                                Toast.makeText(MainActivity.this, R.string.point_ajout_reussi, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, R.string.point_ajout_echoue, Toast.LENGTH_SHORT).show();
                            }
                            completeTrackingCleanup(); // Finalisation du trajet après sauvegarde du point final
                        });
                    } else {
                        // Pas de position obtenue, on finalise quand même
                        completeTrackingCleanup();
                    }
                }
            }, Looper.getMainLooper());
        } catch (SecurityException e) {
            showAlert(getString(R.string.erreur_permission_gps, e.getMessage()));
            completeTrackingCleanup();
        }
    }

    /**
     * Finalise l'arrêt du trajet en remettant l'interface dans son état initial
     * et en déclenchant la création du fichier GPX
     */
    private void completeTrackingCleanup() {
        isTracking = false;
        fusedLocationClient.removeLocationUpdates(locationCallback); // Arrêt des mises à jour de localisation
        // Restauration de l'interface
        btnPoint.setVisibility(View.GONE);
        btnEnd.setVisibility(View.GONE);
        btnStart.setVisibility(View.VISIBLE);
        radioManual.setEnabled(true);
        radioAuto.setEnabled(true);
        bottomNavigation.setEnabled(true);
        createAndShareGpxFile(currentTrajetTitre); // Génération et partage du .gpx
        currentTrajetId = null;
    }

    /**
     * Crée un fichier GPX avec les points du trajet et propose le partage par email
     * @param titre Le titre du trajet pour nommer le fichier
     */
    private void createAndShareGpxFile(String titre) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Récupére les points du trajet, triés par timestamp
        Query query = db.collection("carnetdevoyage").document("data").collection("points")
                .whereEqualTo("trajet_id", currentTrajetId)
                .orderBy("timestamp");

        // Construction du .gpx
        query.get().addOnSuccessListener(querySnapshot -> {
            StringBuilder gpxContent = new StringBuilder();
            // En-tête du fichier GPX
            gpxContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    .append("<gpx version=\"1.1\" creator=\"CarnetDeVoyage\">\n")
                    .append("<trk><name>").append(titre).append("</name><trkseg>\n");

            // Ajout de chaque point GPS dans le fichier
            for (var doc : querySnapshot.getDocuments()) {
                Double latitude = doc.getDouble("latitude");
                Double longitude = doc.getDouble("longitude");
                String timestamp = doc.getString("timestamp");

                // Vérification que les données sont complètes
                if (latitude != null && longitude != null && timestamp != null) {
                    // Ajout du point au format GPX
                    gpxContent.append("<trkpt lat=\"").append(latitude)
                            .append("\" lon=\"").append(longitude).append("\">\n")
                            .append("<time>").append(timestamp).append("</time>\n")
                            .append("</trkpt>\n");
                }
            }
            // Fermeture des balises GPX
            gpxContent.append("</trkseg></trk></gpx>");

            // Sauvegarde du fichier
            try {
                // MediaStore pour sauvegarder dans le dossier Téléchargements
                ContentValues values = new ContentValues();
                values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, titre + ".gpx");
                values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/gpx+xml");
                values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Download/");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                // Création du fichier dans le système
                if (uri != null) {
                    // Écriture du contenu GPX dans le fichier
                    try (FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
                        fos.write(gpxContent.toString().getBytes());
                    }

                    // Création d'un intent pour partager le fichier
                    Intent emailIntent = new Intent(Intent.ACTION_SEND);
                    emailIntent.setType("application/gpx+xml");
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Fichier GPX : " + titre);
                    emailIntent.putExtra(Intent.EXTRA_STREAM, uri);  // Pièce jointe
                    emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    // Affichage du sélecteur d'applications pour le partage
                    startActivity(Intent.createChooser(emailIntent, "Envoyer le fichier GPX"));
                } else {
                    showAlert(getString(R.string.gpx_file_creation_error));
                }
            } catch (Exception e) {
                showAlert(R.string.gpx_file_share_error + e.getMessage());
            }
        }).addOnFailureListener(e -> {
            // Erreur spécifique : index manquant sur Firestore
            if (Objects.requireNonNull(e.getMessage()).contains("FAILED_PRECONDITION")) {
                showAlert(getString(R.string.firestore_index_error));
            } else {
                // Autres erreurs Firestore
                showAlert(R.string.firestore_points_error + e.getMessage());
            }
        });
    }

    /**
     * Affiche une boîte de dialogue d'alerte avec un message
     * @param message Le message à afficher
     */
    private void showAlert(String message) {
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok_button, null)
                .create();

        // Change la couleur du message en noir
        alertDialog.setOnShowListener(dialog -> {
            TextView messageView = alertDialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setTextColor(ContextCompat.getColor(this,R.color.black));
            }
        });

        alertDialog.show();
    }
}