package com.example.smartaudiobook;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::runSearch;
    private static final long SEARCH_DEBOUNCE_MS = 450L;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private EditText searchInput;

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
        EditText emailInput = findViewById(R.id.edtEmail);
        EditText passwordInput = findViewById(R.id.edtPassword);
        findViewById(R.id.txtForgot).setOnClickListener(v -> showForgotPassword());
        findViewById(R.id.txtGoRegister).setOnClickListener(v -> showRegister());
        findViewById(R.id.btnLogin).setOnClickListener(v -> handleLogin(emailInput, passwordInput));
    }

    private void showRegister() {
        prepareLightWindow();
        setContentView(R.layout.activity_register);
        EditText fullNameInput = findViewById(R.id.edtFullName);
        EditText emailInput = findViewById(R.id.edtEmail);
        EditText passwordInput = findViewById(R.id.edtPassword);
        EditText confirmPasswordInput = findViewById(R.id.edtConfirmPassword);
        findViewById(R.id.txtGoLogin).setOnClickListener(v -> showLogin());
        findViewById(R.id.btnRegister).setOnClickListener(v ->
                handleRegister(fullNameInput, emailInput, passwordInput, confirmPasswordInput));
    }

    private void showForgotPassword() {
        prepareLightWindow();
        setContentView(R.layout.activity_forgot_password);
        EditText emailInput = findViewById(R.id.edtEmail);
        findViewById(R.id.btnBack).setOnClickListener(v -> showLogin());
        findViewById(R.id.txtBackLogin).setOnClickListener(v -> showLogin());
        findViewById(R.id.btnSendInstruction).setOnClickListener(v -> handleForgotPassword(emailInput));
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
        findViewById(R.id.topicAi).setOnClickListener(v -> openTopic("AI"));
        findViewById(R.id.topicData).setOnClickListener(v -> openTopic("Data"));
        findViewById(R.id.topicSkills).setOnClickListener(v -> openTopic("Skills"));
        findViewById(R.id.topicLanguages).setOnClickListener(v -> openTopic("Languages"));
    }

    private void showSearch() {
        prepareLightWindow();
        setContentView(R.layout.activity_search);
        searchInput = findViewById(R.id.edtSearchQuery);
        findViewById(R.id.navHome).setOnClickListener(v -> showHome());
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
        findViewById(R.id.navLibrary).setOnClickListener(v -> showLibrary());
        findViewById(R.id.navProfile).setOnClickListener(v -> showProfile());
        findViewById(R.id.searchIcon).setOnClickListener(v -> runSearch());
        findViewById(R.id.btnClearSearch).setOnClickListener(v -> clearSearch());
        findViewById(R.id.recentSearchAndroid).setOnClickListener(v -> applyRecentSearch("android service"));
        findViewById(R.id.recentSearchMl).setOnClickListener(v -> applyRecentSearch("machine learning"));
        findViewById(R.id.recentSearchStudy).setOnClickListener(v -> applyRecentSearch("study skills"));
        findViewById(R.id.recentSearchTts).setOnClickListener(v -> applyRecentSearch("text to speech"));
        searchInput.addTextChangedListener(new DebounceSearchWatcher());
        searchInput.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        findViewById(R.id.itemAndroidServiceTts).setOnClickListener(v -> showDetail());
        findViewById(R.id.itemServiceAndroid).setOnClickListener(v -> showEbookDetail());
    }

    private void showLibrary() {
        prepareLightWindow();
        setContentView(R.layout.activity_library);
        findViewById(R.id.navHome).setOnClickListener(v -> showHome());
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
        findViewById(R.id.navProfile).setOnClickListener(v -> showProfile());
        findViewById(R.id.libraryItemAndroid).setOnClickListener(v -> showDetail());
        findViewById(R.id.libraryItemAndroidPlay).setOnClickListener(v -> showFullPlayer());
        findViewById(R.id.libraryItemCleanCode).setOnLongClickListener(v -> {
            Toast.makeText(this, "Removed item from playlist", Toast.LENGTH_SHORT).show();
            return true;
        });
        findViewById(R.id.btnLibraryAdd).setOnClickListener(v ->
                Toast.makeText(this, "Playlist created", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnLibrarySort).setOnClickListener(v ->
                Toast.makeText(this, "Sort: Updated recently", Toast.LENGTH_SHORT).show());
        findViewById(R.id.filterAll).setOnClickListener(v -> onLibraryFilterSelected("All"));
        findViewById(R.id.filterListening).setOnClickListener(v -> onLibraryFilterSelected("Listening"));
        findViewById(R.id.filterDownloaded).setOnClickListener(v -> onLibraryFilterSelected("Downloaded"));
        findViewById(R.id.filterCompleted).setOnClickListener(v -> onLibraryFilterSelected("Completed"));
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

    private void handleLogin(EditText emailInput, EditText passwordInput) {
        boolean valid = validateEmailField(emailInput) & validateRequired(passwordInput, "Password is required");
        if (!valid) {
            Toast.makeText(this, "Login failed. Please check your input.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Login success", Toast.LENGTH_SHORT).show();
        showHome();
    }

    private void handleRegister(EditText fullNameInput, EditText emailInput, EditText passwordInput, EditText confirmPasswordInput) {
        boolean valid = validateRequired(fullNameInput, "Full name is required")
                & validateEmailField(emailInput)
                & validateRequired(passwordInput, "Password is required")
                & validateRequired(confirmPasswordInput, "Confirm password is required");
        if (valid && !TextUtils.equals(passwordInput.getText(), confirmPasswordInput.getText())) {
            confirmPasswordInput.setError("Passwords do not match");
            valid = false;
        }
        if (!valid) {
            Toast.makeText(this, "Register failed. Please check your input.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Register success", Toast.LENGTH_SHORT).show();
        showHome();
    }

    private void handleForgotPassword(EditText emailInput) {
        if (!validateEmailField(emailInput)) {
            Toast.makeText(this, "Cannot send instruction. Invalid email.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Instruction sent to email", Toast.LENGTH_SHORT).show();
        showLogin();
    }

    private boolean validateEmailField(EditText emailInput) {
        String email = emailInput.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            return false;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            emailInput.setError("Invalid email format");
            return false;
        }
        emailInput.setError(null);
        return true;
    }

    private boolean validateRequired(EditText input, String message) {
        if (TextUtils.isEmpty(input.getText().toString().trim())) {
            input.setError(message);
            return false;
        }
        input.setError(null);
        return true;
    }

    private void runSearch() {
        if (searchInput == null) {
            return;
        }
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter keyword", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Searching: " + query, Toast.LENGTH_SHORT).show();
    }

    private void clearSearch() {
        if (searchInput != null) {
            searchInput.setText("");
        }
        Toast.makeText(this, "Search cleared", Toast.LENGTH_SHORT).show();
    }

    private void applyRecentSearch(String keyword) {
        if (searchInput != null) {
            searchInput.setText(keyword);
            searchInput.setSelection(keyword.length());
        }
        runSearch();
    }

    private void openTopic(String topic) {
        Toast.makeText(this, "Open topic: " + topic, Toast.LENGTH_SHORT).show();
        showSearch();
    }

    private void onLibraryFilterSelected(String filter) {
        Toast.makeText(this, "Filter: " + filter, Toast.LENGTH_SHORT).show();
    }

    private final class DebounceSearchWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            handler.removeCallbacks(searchRunnable);
            handler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
