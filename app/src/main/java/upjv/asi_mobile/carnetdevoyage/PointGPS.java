package upjv.asi_mobile.carnetdevoyage;

public class PointGPS {
    private final long id;
    private final long trajetId;
    private final double latitude;
    private final double longitude;
    private final String timestamp;

    public PointGPS(long id, long trajetId, double latitude, double longitude, String timestamp) {
        this.id = id;
        this.trajetId = trajetId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public long getTrajetId() { return trajetId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getTimestamp() { return timestamp; }
}