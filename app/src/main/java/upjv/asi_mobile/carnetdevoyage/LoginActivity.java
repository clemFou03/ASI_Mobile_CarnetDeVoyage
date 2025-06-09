package upjv.asi_mobile.carnetdevoyage;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etIdentifier, etPassword;
    private Button btnLogin;
    private TextView tvMessage, tvGoToRegister;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialisation des vues et de l'aide à la base de données
        etIdentifier = findViewById(R.id.etIdentifier);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvMessage = findViewById(R.id.tvMessage);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);
        dbHelper = new DatabaseHelper();

        // Redirection vers l'écran d'inscription
        tvGoToRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // Gestion de la connexion
        btnLogin.setOnClickListener(v -> {
            String identifier = etIdentifier.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            // Vérification des champs vides
            if (identifier.isEmpty() || password.isEmpty()) {
                tvMessage.setText("Veuillez remplir tous les champs.");
                tvMessage.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                return;
            }
            // Appel à la méthode de connexion
            dbHelper.loginUser(identifier, password, (success, message) -> {
                if (success) {
                    tvMessage.setText(message);
                    tvMessage.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    // Redirection vers MainActivity après un délai
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    }, 1000);
                } else {
                    tvMessage.setText(message);
                    tvMessage.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                }
            });
        });
    }
}