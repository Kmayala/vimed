package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.tesis.vimed.database.DatabaseHelper;
import com.tesis.vimed.database.UsuarioDAO;

public class RoleSelectionActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private String selectedRole = "adulto_mayor"; // default selection

    private MaterialCardView cardAdult;
    private MaterialCardView cardFamily;
    private ImageView icCheckAdult;
    private ImageView icCheckFamily;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        sessionManager = new SessionManager(this);

        cardAdult = findViewById(R.id.card_adult);
        cardFamily = findViewById(R.id.card_family);
        icCheckAdult = findViewById(R.id.ic_check_adult);
        icCheckFamily = findViewById(R.id.ic_check_family);

        ImageButton btnBack = findViewById(R.id.btn_back);
        MaterialButton btnContinue = findViewById(R.id.btn_continue);

        // Show adult as selected by default
        applySelection("adulto_mayor");

        cardAdult.setOnClickListener(v -> applySelection("adulto_mayor"));
        cardFamily.setOnClickListener(v -> applySelection("familiar"));

        btnBack.setOnClickListener(v -> finish());

        btnContinue.setOnClickListener(v -> confirmRole());
    }

    private void applySelection(String role) {
        selectedRole = role;

        int brandStroke = ContextCompat.getColor(this, R.color.brand_500);
        int neutralStroke = ContextCompat.getColor(this, R.color.ink_7);

        if ("adulto_mayor".equals(role)) {
            cardAdult.setStrokeColor(brandStroke);
            cardAdult.setStrokeWidth(dpToPx(2));
            cardFamily.setStrokeColor(neutralStroke);
            cardFamily.setStrokeWidth(dpToPx(2));
            icCheckAdult.setVisibility(View.VISIBLE);
            icCheckFamily.setVisibility(View.INVISIBLE);
        } else {
            cardFamily.setStrokeColor(brandStroke);
            cardFamily.setStrokeWidth(dpToPx(2));
            cardAdult.setStrokeColor(neutralStroke);
            cardAdult.setStrokeWidth(dpToPx(2));
            icCheckFamily.setVisibility(View.VISIBLE);
            icCheckAdult.setVisibility(View.INVISIBLE);
        }
    }

    private void confirmRole() {
        sessionManager.saveRol(selectedRole);

        int idUsuario = sessionManager.getIdUsuario();
        if (idUsuario != -1) {
            UsuarioDAO usuarioDAO = new UsuarioDAO(DatabaseHelper.getInstance(this));
            usuarioDAO.actualizarRol(idUsuario, selectedRole);
        }

        if ("adulto_mayor".equals(selectedRole)) {
            // Ofrecer vincular familiar antes de entrar al app
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("¿Desea vincular un familiar?")
                .setMessage("Puede invitar a un cuidador para que reciba avisos sobre sus medicamentos.")
                .setPositiveButton("Vincular ahora", (d, w) -> {
                    startActivity(new Intent(this, VincularFamiliarActivity.class));
                    goToMain();
                })
                .setNegativeButton("Omitir", (d, w) -> goToMain())
                .show();
        } else {
            goToMain();
        }
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
