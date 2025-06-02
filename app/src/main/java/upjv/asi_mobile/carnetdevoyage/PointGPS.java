package upjv.asi_mobile.carnetdevoyage;

public class PointGPS {
    private long id;
    private long trajetId;
    private double latitude;
    private double longitude;
    private String timestamp;

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