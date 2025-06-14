package upjv.asi_mobile.carnetdevoyage;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import upjv.asi_mobile.carnetdevoyage.model.Trajet;

public class TripListActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private TripAdapter adapter;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_list);

        // Initialisation des vues, Firestore et de l'adaptateur
        RecyclerView recyclerView = findViewById(R.id.tripListView);
        MaterialButton btnBack = findViewById(R.id.btnBack);
        FloatingActionButton btnViewOtherUser = findViewById(R.id.btnViewOtherUser);
        db = FirebaseFirestore.getInstance();
        dbHelper = new DatabaseHelper();
        List<Trajet> trajets = new ArrayList<>();
        adapter = new TripAdapter(trajets);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Chargement initial des trajets de l'utilisateur connecté
        loadTrips(null);

        // Gestion du bouton pour voir les trajets d'un autre utilisateur
        btnViewOtherUser.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Voir les trajets d’un autre utilisateur");
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setHint("Entrez le nom d'utilisateur");
            builder.setView(input);
            builder.setPositiveButton("OK", (dialog, which) -> {
                String username = input.getText().toString().trim();
                if (username.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setMessage("Veuillez entrer un nom d'utilisateur.")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                dbHelper.getUserIdByUsername(username, (userId, error) -> {
                    if (userId != null) {
                        loadTrips(userId);
                    } else {
                        new AlertDialog.Builder(this)
                                .setMessage(error != null ? error : "Utilisateur non trouvé.")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            });
            builder.setNegativeButton("Annuler", (dialog, which) -> dialog.cancel());
            builder.show();
        });

        // Gestion du bouton de retour
        btnBack.setOnClickListener(v -> finish());
    }

    // Charge les trajets depuis Firestore pour un utilisateur donné
    private void loadTrips(String userId) {
        db.collection("carnetdevoyage").document("data").collection("trajets")
                .whereEqualTo("userId", userId != null ? userId : dbHelper.getCurrentUserId())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Trajet> newTrajets = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String titre = document.getString("titre");
                        String trajetId = document.getString("trajet_id");
                        if (trajetId != null) {
                            newTrajets.add(new Trajet(trajetId, titre));
                        }
                    }
                    adapter.setTrajets(newTrajets);
                    if (newTrajets.isEmpty()) {
                        new AlertDialog.Builder(this)
                                .setMessage(userId == null ? "Aucun trajet trouvé pour vous." : "Aucun trajet trouvé pour cet utilisateur.")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                })
                .addOnFailureListener(e -> new AlertDialog.Builder(this)
                        .setMessage("Erreur lors du chargement des trajets : " + e.getMessage())
                        .setPositiveButton("OK", null)
                        .show());
    }

    // Adaptateur pour afficher la liste des trajets
    private class TripAdapter extends RecyclerView.Adapter<TripAdapter.ViewHolder> {
        private final List<Trajet> trajets;

        TripAdapter(List<Trajet> trajets) {
            this.trajets = trajets;
        }

        public void setTrajets(List<Trajet> newTrajets) {
            trajets.clear();
            trajets.addAll(newTrajets);
            notifyItemRangeChanged(0, trajets.size());
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Trajet trajet = trajets.get(position);
            holder.textView.setText(trajet.getTitre());
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(TripListActivity.this, MapActivity.class);
                intent.putExtra("trajetId", trajet.getId());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return trajets.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            ViewHolder(View itemView) {
                super(itemView);
                textView = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}