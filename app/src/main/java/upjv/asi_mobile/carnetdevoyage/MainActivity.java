package upjv.asi_mobile.carnetdevoyage;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSIONS_REQUEST_LOCATION = 1;
    private static final int PERMISSIONS_REQUEST_NOTIFICATIONS = 2;

    private RadioGroup radioGroupMode;
    private Button btnStart, btnPoint, btnStop;
    private boolean isManualMode;
    private Trajet currentTrajet;
    private DatabaseHelper dbHelper;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationService locationService;
    private boolean bound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        radioGroupMode = findViewById(R.id.radioGroupMode);
        btnStart = findViewById(R.id.btnStart);
        btnPoint = findViewById(R.id.btnPoint);
        btnStop = findViewById(R.id.btnStop);

        dbHelper = new DatabaseHelper(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        radioGroupMode.setOnCheckedChangeListener((group, checkedId) -> {
            isManualMode = checkedId == R.id.radioManual;
        });

        btnStart.setOnClickListener(v -> startTrajet());
        btnPoint.setOnClickListener(v -> recordManualPoint());
        btnStop.setOnClickListener(v -> stopTrajet());
    }

    private void startTrajet() {
        if (checkPermissions()) {
            currentTrajet = new Trajet();
            currentTrajet.setDateDebut(new Date());
            long trajetId = dbHelper.addTrajet(currentTrajet);
            currentTrajet.setId(trajetId);

            btnStart.setVisibility(View.GONE);
            btnStop.setVisibility(View.VISIBLE);

            if (isManualMode) {
                btnPoint.setVisibility(View.VISIBLE);
            } else {
                startLocationService();
            }
        }
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_LOCATION);
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    PERMISSIONS_REQUEST_NOTIFICATIONS);
            return false;
        }

        return true;
    }

    private void startLocationService() {
        Intent intent = new Intent(this, LocationService.class);
        intent.putExtra("trajet_id", currentTrajet.getId());
        ContextCompat.startForegroundService(this, intent);
    }

    private void recordManualPoint() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                PointGPS point = new PointGPS(
                                        location.getLatitude(),
                                        location.getLongitude(),
                                        new Date());
                                dbHelper.addPointToTrajet(currentTrajet.getId(), point);
                                Toast.makeText(MainActivity.this,
                                        "Point enregistré", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    private void stopTrajet() {
        currentTrajet.setDateFin(new Date());
        dbHelper.updateTrajetFin(currentTrajet);

        if (!isManualMode && locationService != null) {
            locationService.stopLocationUpdates();
        }

        btnPoint.setVisibility(View.GONE);
        btnStop.setVisibility(View.GONE);
        btnStart.setVisibility(View.VISIBLE);

        generateAndShareGPX();
    }

    private void generateAndShareGPX() {
        List<PointGPS> points = dbHelper.getPointsForTrajet(currentTrajet.getId());
        if (points.isEmpty()) {
            Toast.makeText(this, "Aucun point GPS à exporter", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String fileName = "trajet_" + dateFormat.format(new Date()) + ".gpx";

        String gpxContent = generateGPXContent(points);

        try {
            File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(gpxContent.getBytes());
            fos.close();

            showShareDialog(file);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Erreur lors de la création du fichier", Toast.LENGTH_SHORT).show();
        }
    }

    private String generateGPXContent(List<PointGPS> points) {
        StringBuilder gpx = new StringBuilder();
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"Carnet de Voyage\">\n");
        gpx.append("<trk>\n<trkseg>\n");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());

        for (PointGPS point : points) {
            gpx.append(String.format(Locale.US,
                    "<trkpt lat=\"%.6f\" lon=\"%.6f\">\n<time>%s</time>\n</trkpt>\n",
                    point.getLatitude(),
                    point.getLongitude(),
                    dateFormat.format(point.getDate())));
        }

        gpx.append("</trkseg>\n</trk>\n</gpx>");
        return gpx.toString();
    }

    private void showShareDialog(File file) {
        new AlertDialog.Builder(this)
                .setTitle("Trajet terminé")
                .setMessage("Voulez-vous partager le fichier GPX?")
                .setPositiveButton("Oui", (dialog, which) -> shareFile(file))
                .setNegativeButton("Non", null)
                .show();
    }

    private void shareFile(File file) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/gpx+xml");
        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this,
                "upjv.asi_mobile.carnetdevoyage.fileprovider", file));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Partage de trajet");
        intent.putExtra(Intent.EXTRA_TEXT, "Voici mon trajet enregistré avec Carnet de Voyage");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Partager via"));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission de localisation accordée", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission de localisation refusée", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
