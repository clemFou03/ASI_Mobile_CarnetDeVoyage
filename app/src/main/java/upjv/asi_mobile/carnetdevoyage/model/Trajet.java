package upjv.asi_mobile.carnetdevoyage.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente un trajet (ensemble de points GPS)
 */
public class Trajet {
    private final String id;
    private final String titre;
    private final List<PointGPS> points;

    public Trajet(String id, String titre) {
        this.id = id;
        this.titre = titre;
        this.points = new ArrayList<>();
    }

    // Ajoute un point GPS à la liste
    public void addPoint(PointGPS point) {
        points.add(point);
    }

    // Getters
    public String getId() { return id; }
    public String getTitre() { return titre; }
    public List<PointGPS> getPoints() { return points; }
}