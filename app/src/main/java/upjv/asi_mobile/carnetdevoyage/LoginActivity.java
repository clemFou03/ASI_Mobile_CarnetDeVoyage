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

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etIdentifier, etPassword;
    private TextView tvMessage;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialisation des vues et de l'aide à la base de données
        etIdentifier = findViewById(R.id.etIdentifier);
        etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        tvMessage = findViewById(R.id.tvMessage);
        TextView tvGoToRegister = findViewById(R.id.tvGoToRegister);
        dbHelper = new DatabaseHelper();

        // Redirection vers l'écran d'inscription
        tvGoToRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // Gestion de la connexion
        btnLogin.setOnClickListener(v -> {
            String identifier = Objects.requireNonNull(etIdentifier.getText()).toString().trim();
            String password = Objects.requireNonNull(etPassword.getText()).toString().trim();
            // Vérification des champs vides
            if (identifier.isEmpty() || password.isEmpty()) {
                tvMessage.setText(R.string.login_txt_champs_vide);
                tvMessage.setTextColor(ContextCompat.getColor(this,android.R.color.holo_red_dark));
                return;
            }
            // Appel à la méthode de connexion
            dbHelper.loginUser(identifier, password, (success, message) -> {
                if (success) {
                    tvMessage.setText(message);
                    tvMessage.setTextColor(ContextCompat.getColor(this,android.R.color.holo_green_dark));
                    // Redirection vers MainActivity après un délai
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    }, 1000);
                } else {
                    tvMessage.setText(message);
                    tvMessage.setTextColor(ContextCompat.getColor(this,android.R.color.holo_red_dark));
                }
            });
        });
    }
}