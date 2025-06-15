package upjv.asi_mobile.carnetdevoyage;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;

import upjv.asi_mobile.carnetdevoyage.model.PointGPS;
import upjv.asi_mobile.carnetdevoyage.model.Trajet;

/**
 * Classe utilitaire pour gérer toutes les opérations avec Firebase
 * Centralise l'accès à Firestore et Firebase Auth
 */
public class DatabaseHelper {
    private final FirebaseFirestore firestore;
    private final CollectionReference trajetsRef;
    private final CollectionReference pointsRef;
    private final FirebaseAuth auth;

    // Initialise la connexion à Firestore et Auth
    public DatabaseHelper() {
        firestore = FirebaseFirestore.getInstance(); // Récupération de l'instance Firestore
        trajetsRef = firestore.collection("carnetdevoyage").document("data").collection("trajets");
        pointsRef = firestore.collection("carnetdevoyage").document("data").collection("points");
        auth = FirebaseAuth.getInstance(); // Instance d'authentification Firebase
    }

    // Retourne l'UID de l'utilisateur actuellement connecté
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * Inscription d'un nouvel utilisateur
     * @param email Email de l'utilisateur
     * @param username Nom d'utilisateur
     * @param password Mot de passe
     * @param callback Interface de callback pour le résultat
     */
    public void registerUser(String email, String username, String password, RegisterCallback callback) {
        // Création du compte dans Firebase Auth
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        // Stockage des données utilisateur dans Firestore
                        HashMap<String, Object> userData = new HashMap<>();
                        userData.put("email", email);
                        userData.put("username", username);

                        // Sauvegarde dans la collection users avec l'UID comme clé
                        firestore.collection("carnetdevoyage").document("data").collection("users").document(user.getUid()).set(userData)
                                .addOnSuccessListener(aVoid -> callback.onResult(true, "Inscription réussie."))
                                .addOnFailureListener(e -> callback.onResult(false, "Erreur Firestore : " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> callback.onResult(false, "Erreur : " + e.getMessage()));
    }

    /**
     * Connexion d'un utilisateur existant
     * @param identifier Email de connexion
     * @param password Mot de passe
     * @param callback Interface de callback pour le résultat
     */
    public void loginUser(String identifier, String password, RegisterCallback callback) {
        // Authentification avec Firebase Auth
        auth.signInWithEmailAndPassword(identifier, password)
                .addOnSuccessListener(authResult -> callback.onResult(true, "Connexion réussie."))
                .addOnFailureListener(e -> callback.onResult(false, "Erreur : " + e.getMessage()));
    }

    /**
     * Recherche d'un utilisateur par son nom d'utilisateur
     * @param username Nom d'utilisateur à rechercher
     * @param callback Interface de callback pour le résultat
     */
    public void getUserIdByUsername(String username, UserCallback callback) {
        firestore.collection("carnetdevoyage").document("data").collection("users")
                .whereEqualTo("username", username) // Requête de filtrage
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Récupération de l'ID du premier (et unique) résultat
                        String userId = querySnapshot.getDocuments().get(0).getId();
                        callback.onResult(userId, null);
                    } else {
                        callback.onResult(null, "Utilisateur non trouvé.");
                    }
                })
                .addOnFailureListener(e -> callback.onResult(null, "Erreur : " + e.getMessage()));
    }

    /**
     * Création d'un nouveau trajet dans Firestore
     * @param trajet Objet Trajet contenant les
     * informations du trajet
     * @return ID généré automatiquement par Firestore
     */
    public String addTrajet(Trajet trajet) {
        // Vérification que l'utilisateur est connecté
        String userId = getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("Utilisateur non connecté");
        }
        String trajetId = trajet.getId();

        // Préparation des données du trajet
        HashMap<String, Object> trajetData = new HashMap<>();
        trajetData.put("titre", trajet.getTitre());
        trajetData.put("userId", userId); // Liaison avec l'utilisateur
        trajetData.put("trajet_id", trajetId); // Stocke l'ID pour lien avec points

        // Sauvegarde synchrone dans Firestore
        trajetsRef.document(trajetId).set(trajetData);
        return trajetId;
    }

    /**
     * Ajout d'un point GPS à un trajet existant
     * @param trajetId ID du trajet parent
     * @param point Objet PointGPS contenant les
     * informations du point
     * @param callback Interface de callback pour le résultat
     */
    public void addPoint(String trajetId, PointGPS point, PointCallback callback) {
        String pointDocId = String.valueOf(point.getId());

        // Préparation des données du point GPS
        HashMap<String, Object> pointData = new HashMap<>();
        pointData.put("trajet_id", trajetId); // Liaison avec le trajet parent
        pointData.put("latitude", point.getLatitude());
        pointData.put("longitude", point.getLongitude());
        pointData.put("timestamp", point.getTimestamp());

        // Sauvegarde asynchrone
        pointsRef.document(pointDocId).set(pointData)
                .addOnSuccessListener(aVoid -> callback.onResult(true))
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // Interface pour les callbacks d'inscription/connexion
    public interface RegisterCallback {
        void onResult(boolean success, String message);
    }

    // Interface pour les callbacks de recherche d'utilisateur
    public interface UserCallback {
        void onResult(String userId, String error);
    }

    // Interface pour les callbacks d'ajout de point GPS
    public interface PointCallback {
        void onResult(boolean success);
    }
}