package upjv.asi_mobile.carnetdevoyage;;

import java.util.ArrayList;
import java.util.List;


public class Trajet {
    private long id;
    private String titre;
    private List<PointGPS> points;

    public Trajet(long id, String titre) {
        this.id = id;
        this.titre = titre;
        this.points = new ArrayList<>();
    }

    public void addPoint(PointGPS point) {
        points.add(point);
    }


    public long getId() { return id; }
    public String getTitre() { return titre; }
    public List<PointGPS> getPoints() { return points; }
}