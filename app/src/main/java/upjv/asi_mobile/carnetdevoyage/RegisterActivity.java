package upjv.asi_mobile.carnetdevoyage;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Objects;

/**
 * Activité d'inscription utilisateur
 * Crée un compte Firebase Auth + stocke les données utilisateur dans Firestore
 */
public class RegisterActivity extends AppCompatActivity {
    // Composants d'interface pour la saisie
    private TextInputEditText etEmail, etUsername, etPassword;
    private TextView tvMessage;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialisation des vues et de l'aide à la base de données
        etEmail = findViewById(R.id.etEmail);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        Button btnRegister = findViewById(R.id.btnRegister);
        tvMessage = findViewById(R.id.tvMessage);
        TextView tvGoToLogin = findViewById(R.id.tvGoToLogin);
        dbHelper = new DatabaseHelper();

        // Redirection vers la connexion
        tvGoToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // Gestion de l'inscription
        btnRegister.setOnClickListener(v -> {
            // Récupération et nettoyage des données
            String email = Objects.requireNonNull(etEmail.getText()).toString().trim();
            String username = Objects.requireNonNull(etUsername.getText()).toString().trim();
            String password = Objects.requireNonNull(etPassword.getText()).toString().trim();
            // Vérification des champs
            if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
                tvMessage.setText(R.string.register_txt_champs_vide);
                tvMessage.setTextColor(ContextCompat.getColor(this,android.R.color.holo_red_dark));
                return;
            }
            // Appel à la méthode d'inscription
            // Crée le compte Auth + stocke les données utilisateur
            dbHelper.registerUser(email, username, password, (success, message) -> {
                if (success) {
                    tvMessage.setText(message);
                    tvMessage.setTextColor(ContextCompat.getColor(this,android.R.color.holo_green_dark));
                    // Redirection vers MainActivity après un délai
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish(); // Empêche le retour sur l'écran d'inscription
                    }, 1000);
                } else {
                    tvMessage.setText(message);
                    tvMessage.setTextColor(ContextCompat.getColor(this,android.R.color.holo_red_dark));
                }
            });
        });
    }
}