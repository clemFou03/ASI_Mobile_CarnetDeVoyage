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
    private RadioGroup radioGroupMode;
    private Button btnStart, btnPoint, btnEnd;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseHelper dbHelper;
    private long currentTrajetId = -1;
    private boolean isTracking = false;
    private boolean isManualMode = true;
    private Handler handler;
    private Runnable periodicTask;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        radioGroupMode = findViewById(R.id.radioGroupMode);
        btnStart = findViewById(R.id.btnStart);
        btnPoint = findViewById(R.id.btnPoint);
        btnEnd = findViewById(R.id.btnEnd);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        dbHelper = new DatabaseHelper(this);
        handler = new Handler(Looper.getMainLooper());

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                startTracking();
            } else {
                showAlert("Permission GPS refusée. L'application ne peut pas fonctionner.");
            }
        });

        btnStart.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                startTracking();
            }
        });

        btnPoint.setOnClickListener(v -> saveCurrentLocation());

        btnEnd.setOnClickListener(v -> endTracking());
    }

    private void startTracking() {
        isManualMode = ((RadioButton) findViewById(radioGroupMode.getCheckedRadioButtonId())).getText().toString().equals("Pointage manuel");
        isTracking = true;
        radioGroupMode.setEnabled(false);
        btnStart.setVisibility(View.GONE);
        btnEnd.setVisibility(View.VISIBLE);
        if (isManualMode) {
            btnPoint.setVisibility(View.VISIBLE);
        } else {
            periodicTask = new Runnable() {
                @Override
                public void run() {
                    saveCurrentLocation();
                    handler.postDelayed(this, 5 * 60 * 1000); // 5 minutes
                }
            };
            handler.post(periodicTask);
        }

        currentTrajetId = dbHelper.addTrajet("Trajet " + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()));
    }

    private void saveCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showAlert("Permission GPS non accordée. Impossible d'enregistrer la position.");
            return;
        }

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000);

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null && locationResult.getLastLocation() != null) {
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

    private void endTracking() {
        isTracking = false;
        if (!isManualMode) {
            handler.removeCallbacks(periodicTask);
        }
        btnPoint.setVisibility(View.GONE);
        btnEnd.setVisibility(View.GONE);
        btnStart.setVisibility(View.VISIBLE);
        radioGroupMode.setEnabled(true);

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

    private void createAndShareGpxFile(String titre) {
        StringBuilder gpxContent = new StringBuilder();
        gpxContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<gpx version=\"1.1\" creator=\"CarnetDeVoyage\">\n")
                .append("<trk><name>").append(titre).append("</name><trkseg>\n");

        for (PointGPS point : dbHelper.getPointsForTrajet(currentTrajetId)) {
            gpxContent.append("<trkpt lat=\"").append(point.getLatitude())
                    .append("\" lon=\"").append(point.getLongitude()).append("\">\n")
                    .append("<time>").append(point.getTimestamp()).append("</time>\n")
                    .append("</trkpt>\n");
        }
        gpxContent.append("</trkseg></trk></gpx>");

        try {
            // Utiliser MediaStore pour écrire dans le répertoire Download
            ContentValues values = new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, titre + ".gpx");
            values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/gpx+xml");
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Download/");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                try (FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
                    fos.write(gpxContent.toString().getBytes());
                }

                // Partager directement via MediaStore URI
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

    private void showAlert(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}