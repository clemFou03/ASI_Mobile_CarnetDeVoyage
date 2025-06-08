package upjv.asi_mobile.carnetdevoyage;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;


public class DatabaseHelper {

    private final FirebaseFirestore firestore;
    private final CollectionReference trajetsRef;
    private final CollectionReference pointsRef;
    private final AtomicLong trajetIdCounter;

    /**
     * Constructeur pour initialiser les références Firestore et le compteur d'identifiants.
     */
    public DatabaseHelper() {
        // Initialisation de l'instance Firestore
        firestore = FirebaseFirestore.getInstance();
        // Définition des références aux collections sous carnetdevoyage/data
        trajetsRef = firestore.collection("carnetdevoyage").document("data").collection("trajets");
        pointsRef = firestore.collection("carnetdevoyage").document("data").collection("points");
        // Initialisation du compteur pour générer des identifiants uniques de trajets
        trajetIdCounter = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Ajoute un nouveau trajet à la collection Firestore 'trajets'.
     * @param titre Le titre du trajet.
     * @return L'identifiant du trajet créé.
     */
    public long addTrajet(String titre) {
        // Génère un identifiant unique pour le trajet
        long trajetId = trajetIdCounter.getAndIncrement();
        String trajetDocId = "trajet_" + trajetId;
        // Prépare les données du trajet
        HashMap<String, Object> trajetData = new HashMap<>();
        trajetData.put("titre", titre);
        // Enregistre le trajet dans Firestore
        trajetsRef.document(trajetDocId).set(trajetData);
        return trajetId;
    }

    /**
     * Ajoute un point GPS à la collection Firestore 'points' pour un trajet existant.
     * @param trajetId L'identifiant du trajet associé au point.
     * @param latitude La latitude du point GPS.
     * @param longitude La longitude du point GPS.
     */
    public void addPoint(long trajetId, double latitude, double longitude) {
        // Génère un identifiant unique pour le point
        String pointDocId = pointsRef.document().getId();
        // Prépare les données du point GPS
        HashMap<String, Object> pointData = new HashMap<>();
        pointData.put("trajet_id", trajetId);
        pointData.put("latitude", latitude);
        pointData.put("longitude", longitude);
        // Ajoute un horodatage au format ISO pour le point
        pointData.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        // Enregistre le point dans Firestore
        pointsRef.document(pointDocId).set(pointData);
    }
}