package upjv.asi_mobile.carnetdevoyage;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
    private final FirebaseAuth auth;

    // Initialisation de Firestore, FirebaseAuth et collections
    public DatabaseHelper() {
        firestore = FirebaseFirestore.getInstance();
        trajetsRef = firestore.collection("carnetdevoyage").document("data").collection("trajets");
        pointsRef = firestore.collection("carnetdevoyage").document("data").collection("points");
        trajetIdCounter = new AtomicLong(System.currentTimeMillis());
        auth = FirebaseAuth.getInstance();
    }

    // Récupère l'ID de l'utilisateur connecté
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // Inscrit un nouvel utilisateur et stocke ses données
    public void registerUser(String email, String username, String password, RegisterCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        HashMap<String, Object> userData = new HashMap<>();
                        userData.put("email", email);
                        userData.put("username", username); // Stocke le username
                        firestore.collection("carnetdevoyage").document("data").collection("users").document(user.getUid()).set(userData)
                                .addOnSuccessListener(aVoid -> callback.onResult(true, "Inscription réussie."))
                                .addOnFailureListener(e -> callback.onResult(false, "Erreur Firestore : " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> callback.onResult(false, "Erreur : " + e.getMessage()));
    }

    // Connecte un utilisateur avec email et mot de passe
    public void loginUser(String identifier, String password, RegisterCallback callback) {
        // Essaye de se connecter avec email ou username
        auth.signInWithEmailAndPassword(identifier, password)
                .addOnSuccessListener(authResult -> callback.onResult(true, "Connexion réussie."))
                .addOnFailureListener(e -> {
                    // Si l'email échoue, on ne fait rien de plus ici (Firebase gère l'auth)
                    callback.onResult(false, "Erreur : " + e.getMessage());
                });
    }

    // Récupère l'ID d'un utilisateur par son nom d'utilisateur
    public void getUserIdByUsername(String username, UserCallback callback) {
        firestore.collection("carnetdevoyage").document("data").collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        String userId = querySnapshot.getDocuments().get(0).getId();
                        callback.onResult(userId, null);
                    } else {
                        callback.onResult(null, "Utilisateur non trouvé.");
                    }
                })
                .addOnFailureListener(e -> callback.onResult(null, "Erreur : " + e.getMessage()));
    }

    // Ajoute un nouveau trajet à Firestore
    public long addTrajet(String titre) {
        long trajetId = trajetIdCounter.getAndIncrement();
        String trajetDocId = "trajet_" + trajetId;
        HashMap<String, Object> trajetData = new HashMap<>();
        trajetData.put("titre", titre);
        trajetData.put("userId", getCurrentUserId());
        trajetsRef.document(trajetDocId).set(trajetData);
        return trajetId;
    }

    // Ajoute un point GPS à Firestore
    public void addPoint(long trajetId, double latitude, double longitude) {
        String pointDocId = pointsRef.document().getId();
        HashMap<String, Object> pointData = new HashMap<>();
        pointData.put("trajet_id", trajetId);
        pointData.put("latitude", latitude);
        pointData.put("longitude", longitude);
        pointData.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        pointsRef.document(pointDocId).set(pointData);
    }

    // Interface pour les callbacks d'inscription/connexion
    public interface RegisterCallback {
        void onResult(boolean success, String message);
    }

    // Interface pour les callbacks de recherche d'utilisateur
    public interface UserCallback {
        void onResult(String userId, String error);
    }
}