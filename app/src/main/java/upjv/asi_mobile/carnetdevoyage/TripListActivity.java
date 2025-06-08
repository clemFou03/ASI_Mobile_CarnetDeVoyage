package upjv.asi_mobile.carnetdevoyage;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class TripListActivity extends AppCompatActivity {
    private ListView tripListView;
    private Button btnBack;
    private FirebaseFirestore db;
    private List<Trajet> trajets;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_list);

        tripListView = findViewById(R.id.tripListView);
        btnBack = findViewById(R.id.btnBack);
        db = FirebaseFirestore.getInstance();
        trajets = new ArrayList<>();
        List<String> tripTitles = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, tripTitles);
        tripListView.setAdapter(adapter);

        // Charger les trajets depuis Firestore
        db.collection("carnetdevoyage").document("data").collection("trajets")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    tripTitles.clear();
                    trajets.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String titre = document.getString("titre");
                        String id = document.getId().replace("trajet_", "");
                        trajets.add(new Trajet(Long.parseLong(id), titre));
                        tripTitles.add(titre);
                    }
                    adapter.notifyDataSetChanged();
                });

        // Gérer la sélection d’un trajet
        tripListView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(TripListActivity.this, MapActivity.class);
            intent.putExtra("trajetId", trajets.get(position).getId());
            startActivity(intent);
        });

        // Bouton de retour
        btnBack.setOnClickListener(v -> finish());
    }
}
