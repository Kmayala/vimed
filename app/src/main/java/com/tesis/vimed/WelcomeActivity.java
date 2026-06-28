package com.tesis.vimed;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class WelcomeActivity extends AppCompatActivity {

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        sessionManager = new SessionManager(this);

        if (sessionManager.isLoggedIn() && sessionManager.hasRole()) {
            goToHome();
            return;
        }

        MaterialButton btnGoogle = findViewById(R.id.btn_google);
        MaterialButton btnCreateAccount = findViewById(R.id.btn_create_account);
        MaterialButton btnLoginLink = findViewById(R.id.btn_login_link);

        btnGoogle.setOnClickListener(v ->
            Toast.makeText(this, R.string.google_soon, Toast.LENGTH_LONG).show()
        );

        btnCreateAccount.setOnClickListener(v ->
            startActivity(new Intent(this, RegisterActivity.class))
        );

        btnLoginLink.setOnClickListener(v ->
            startActivity(new Intent(this, LoginActivity.class))
        );
    }

    private void goToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
