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
        showHome();

    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void showSplash() {
        prepareLightWindow();
        setContentView(R.layout.activity_splash);
    }

    private void showOnboarding() {
        prepareLightWindow();
        setContentView(R.layout.activity_onboarding);
        findViewById(R.id.btnGetStarted).setOnClickListener(v -> showLogin());
        findViewById(R.id.txtGoLogin).setOnClickListener(v -> showLogin());
    }

    private void showLogin() {
        prepareLightWindow();
        setContentView(R.layout.activity_login);
        findViewById(R.id.txtForgot).setOnClickListener(v -> showForgotPassword());
        findViewById(R.id.txtGoRegister).setOnClickListener(v -> showRegister());
        findViewById(R.id.btnLogin).setOnClickListener(v -> showHome());
    }

    private void showRegister() {
        prepareLightWindow();
        setContentView(R.layout.activity_register);
        findViewById(R.id.txtGoLogin).setOnClickListener(v -> showLogin());
        findViewById(R.id.btnRegister).setOnClickListener(v -> showHome());
    }

    private void showForgotPassword() {
        prepareLightWindow();
        setContentView(R.layout.activity_forgot_password);
        findViewById(R.id.btnBack).setOnClickListener(v -> showLogin());
        findViewById(R.id.txtBackLogin).setOnClickListener(v -> showLogin());
        findViewById(R.id.btnSendInstruction).setOnClickListener(v -> showLogin());
    }

    private void showHome() {
        prepareLightWindow();
        setContentView(R.layout.activity_home);
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
        findViewById(R.id.navLibrary).setOnClickListener(v -> showLibrary());
        findViewById(R.id.navProfile).setOnClickListener(v -> showProfile());
        findViewById(R.id.cardContinueListening).setOnClickListener(v -> showFullPlayer());
        findViewById(R.id.cardGeneratedAudioOne).setOnClickListener(v -> showSearch());
        findViewById(R.id.cardGeneratedAudioTwo).setOnClickListener(v -> showEbookDetail());
        findViewById(R.id.miniPlayerDock).setOnClickListener(v -> showFullPlayer());
    }

    private void showExplore() {
        prepareLightWindow();
        setContentView(R.layout.activity_explore);
        findViewById(R.id.navHome).setOnClickListener(v -> showHome());
        findViewById(R.id.navLibrary).setOnClickListener(v -> showLibrary());
        findViewById(R.id.navProfile).setOnClickListener(v -> showProfile());
        findViewById(R.id.btnOpenSearch).setOnClickListener(v -> showSearch());
    }

    private void showSearch() {
        prepareLightWindow();
        setContentView(R.layout.activity_search);
        findViewById(R.id.navHome).setOnClickListener(v -> showHome());
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
        findViewById(R.id.navLibrary).setOnClickListener(v -> showLibrary());
        findViewById(R.id.navProfile).setOnClickListener(v -> showProfile());
        findViewById(R.id.itemAndroidServiceTts).setOnClickListener(v -> showDetail());
        findViewById(R.id.itemServiceAndroid).setOnClickListener(v -> showEbookDetail());
    }

    private void showLibrary() {
        prepareLightWindow();
        setContentView(R.layout.activity_library);
        findViewById(R.id.navHome).setOnClickListener(v -> showHome());
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
        findViewById(R.id.navProfile).setOnClickListener(v -> showProfile());
        findViewById(R.id.libraryItemAndroid).setOnClickListener(v -> showFullPlayer());
    }

    private void showProfile() {
        prepareProfileWindow();
        setContentView(R.layout.activity_profile);
        findViewById(R.id.navHome).setOnClickListener(v -> showHome());
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
        findViewById(R.id.navLibrary).setOnClickListener(v -> showLibrary());
        findViewById(R.id.btnSignOut).setOnClickListener(v -> showLogin());
    }

    private void showDetail() {
        prepareLightWindow();
        setContentView(R.layout.activity_detail);
        findViewById(R.id.btnBackDetail).setOnClickListener(v -> showSearch());
    }

    private void showEbookDetail() {
        prepareLightWindow();
        setContentView(R.layout.activity_ebook_detail);
        findViewById(R.id.btnBackEbookDetail).setOnClickListener(v -> showSearch());
    }

    private void showFullPlayer() {
        preparePlayerWindow();
        setContentView(R.layout.activity_full_player);
        findViewById(R.id.btnBackFullPlayer).setOnClickListener(v -> showHome());
        findViewById(R.id.btnOpenBackgroundPopup).setOnClickListener(v -> showBackgroundPopup());
    }

    private void showBackgroundPopup() {
        preparePlayerWindow();
        setContentView(R.layout.activity_background_popup);
        findViewById(R.id.btnReturnToPlayer).setOnClickListener(v -> showFullPlayer());
    }

    private void prepareLightWindow() {
        getWindow().setStatusBarColor(Color.parseColor("#F7F8FF"));
        getWindow().setNavigationBarColor(Color.parseColor("#F7F8FF"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setBackgroundDrawableResource(R.color.bg_screen);
    }

    private void preparePlayerWindow() {
        getWindow().setStatusBarColor(Color.parseColor("#171A3A"));
        getWindow().setNavigationBarColor(Color.parseColor("#2E2B72"));
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setBackgroundDrawableResource(R.drawable.bg_full_player);
    }

    private void prepareProfileWindow() {
        getWindow().setStatusBarColor(Color.parseColor("#5C63FF"));
        getWindow().setNavigationBarColor(Color.parseColor("#F7F8FF"));
        getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setBackgroundDrawableResource(R.drawable.bg_screen);
    }
}
