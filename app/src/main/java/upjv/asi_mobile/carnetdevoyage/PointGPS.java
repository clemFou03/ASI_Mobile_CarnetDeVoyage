package upjv.asi_mobile.carnetdevoyage;

import java.util.Date;
public class PointGPS {
    private double latitude;
    private double longitude;
    private Date date;

    public PointGPS(double latitude, double longitude, Date date) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.date = date;
    }

    // Getters
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Date getDate() { return date; }
}
