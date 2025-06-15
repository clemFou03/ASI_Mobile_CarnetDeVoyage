package upjv.asi_mobile.carnetdevoyage.model;

/**
 * Représente un point GPS enregistré lors d’un trajet
 */
public class PointGPS {
    private final String id;
    private final String trajetId;
    private final double latitude;
    private final double longitude;
    private final String timestamp;

    public PointGPS(String id, String trajetId, double latitude, double longitude, String timestamp) {
        this.id = id;
        this.trajetId = trajetId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    // Getters
    public String getId() { return id; }
    public String getTrajetId() { return trajetId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getTimestamp() { return timestamp; }
}