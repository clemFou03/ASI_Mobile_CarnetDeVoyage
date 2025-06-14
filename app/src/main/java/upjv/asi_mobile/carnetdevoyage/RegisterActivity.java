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

public class RegisterActivity extends AppCompatActivity {
    private TextInputEditText etEmail, etUsername, etPassword;
    private Button btnRegister;
    private TextView tvMessage, tvGoToLogin;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialisation des vues et de l'aide à la base de données
        etEmail = findViewById(R.id.etEmail);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvMessage = findViewById(R.id.tvMessage);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);
        dbHelper = new DatabaseHelper();

        // Redirection vers l'écran de connexion
        tvGoToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // Gestion de l'inscription
        btnRegister.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            // Vérification des champs vides
            if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
                tvMessage.setText("Veuillez remplir tous les champs.");
                tvMessage.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                return;
            }
            // Appel à la méthode d'inscription
            dbHelper.registerUser(email, username, password, (success, message) -> {
                if (success) {
                    tvMessage.setText(message);
                    tvMessage.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    // Redirection vers MainActivity après un délai
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
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