package com.example.smartaudiobook;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prepareLightWindow();
        showLogin();

    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void showSplash() {
        setContentView(R.layout.activity_splash);
    }

    private void showOnboarding() {
        setContentView(R.layout.activity_onboarding);
        findViewById(R.id.btnGetStarted).setOnClickListener(v -> showLogin());
        findViewById(R.id.txtGoLogin).setOnClickListener(v -> showLogin());
    }

    private void showLogin() {
        setContentView(R.layout.activity_login);
        findViewById(R.id.txtForgot).setOnClickListener(v -> showForgotPassword());
        findViewById(R.id.txtGoRegister).setOnClickListener(v -> showRegister());
        findViewById(R.id.btnLogin).setOnClickListener(v -> showHome());
    }

    private void showRegister() {
        setContentView(R.layout.activity_register);
        findViewById(R.id.txtGoLogin).setOnClickListener(v -> showLogin());
        findViewById(R.id.btnRegister).setOnClickListener(v -> showHome());
    }

    private void showForgotPassword() {
        setContentView(R.layout.activity_forgot_password);
        findViewById(R.id.btnBack).setOnClickListener(v -> showLogin());
        findViewById(R.id.txtBackLogin).setOnClickListener(v -> showLogin());
        findViewById(R.id.btnSendInstruction).setOnClickListener(v -> showLogin());
    }

    private void showHome() {
        setContentView(R.layout.activity_home);
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
    }

    private void showExplore() {
        setContentView(R.layout.activity_explore);
        findViewById(R.id.navHome).setOnClickListener(v -> showHome());
        findViewById(R.id.btnOpenSearch).setOnClickListener(v -> showSearch());
    }

    private void showSearch() {
        setContentView(R.layout.activity_search);
        findViewById(R.id.navHome).setOnClickListener(v -> showHome());
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
    }

    private void prepareLightWindow() {
        getWindow().setStatusBarColor(Color.parseColor("#F7F8FF"));
        getWindow().setNavigationBarColor(Color.parseColor("#F7F8FF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setBackgroundDrawableResource(R.color.bg_screen);
    }
}
