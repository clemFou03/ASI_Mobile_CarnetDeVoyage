package upjv.asi_mobile.carnetdevoyage;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.util.Date;


public class LocationService extends Service {
    private static final String ID_CANAL_NOTIFICATION = "location_service_channel";
    private final IBinder binder = new LocalBinder();
    private LocationCallback locationCallback;
    private DatabaseHelper dbHelper;
    private long currentTrajetId;


    public class LocalBinder extends Binder {
        LocationService getService() {
            return LocationService.this;
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new DatabaseHelper(this);
        createNotificationChannel();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification());
        currentTrajetId = intent.getLongExtra("trajet_id", -1);
        startLocationUpdates();
        return START_STICKY;
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ID_CANAL_NOTIFICATION,
                    "Canal de Service de Localisation",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }


    private Notification createNotification() {
        return new NotificationCompat.Builder(this, ID_CANAL_NOTIFICATION)
                .setContentTitle("Carnet de Voyage")
                .setContentText("Enregistrement du trajet en cours...")
                .setSmallIcon(R.drawable.ic_notification)
                .build();
    }


    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return;
        }


        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 300000) // 5 minutes
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(60000)
                .setMaxUpdateDelayMillis(300000)
                .build();


        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    saveLocation(location);
                }
            }
        };


        LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }


    private void saveLocation(Location location) {
        if (currentTrajetId != -1) {
            PointGPS point = new PointGPS(
                    location.getLatitude(),
                    location.getLongitude(),
                    new Date());
            dbHelper.addPointToTrajet(currentTrajetId, point);
        }
    }


    public void stopLocationUpdates() {
        if (locationCallback != null) {
            LocationServices.getFusedLocationProviderClient(this)
                    .removeLocationUpdates(locationCallback);
        }
        stopForeground(true);
        stopSelf();
    }
}