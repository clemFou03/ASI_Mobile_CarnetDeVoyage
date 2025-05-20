package upjv.asi_mobile.carnetdevoyage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
public class Trajet {
    private long id;
    private Date dateDebut;
    private Date dateFin;
    private List<PointGPS> points;

    public Trajet() {
        this.points = new ArrayList<>();
    }

    // Getters et setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }

    public List<PointGPS> getPoints() { return points; }
    public void addPoint(PointGPS point) { points.add(point); }

}
