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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::runDebouncedSearch;
    private static final long SEARCH_DEBOUNCE_MS = 450L;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private EditText searchInput;
    private String currentLibraryFilter = "All";
    private boolean sortAscending = true;
    private boolean cleanCodeRemoved = false;

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
        TextInputLayout emailLayout = findViewById(R.id.tilLoginEmail);
        TextInputLayout passwordLayout = findViewById(R.id.tilLoginPassword);
        findViewById(R.id.txtForgot).setOnClickListener(v -> showForgotPassword());
        findViewById(R.id.txtGoRegister).setOnClickListener(v -> showRegister());
        findViewById(R.id.btnLogin).setOnClickListener(v ->
                handleLogin(emailInput, passwordInput, emailLayout, passwordLayout));
    }

    private void showRegister() {
        prepareLightWindow();
        setContentView(R.layout.activity_register);
        EditText fullNameInput = findViewById(R.id.edtFullName);
        EditText emailInput = findViewById(R.id.edtEmail);
        EditText passwordInput = findViewById(R.id.edtPassword);
        EditText confirmPasswordInput = findViewById(R.id.edtConfirmPassword);
        TextInputLayout fullNameLayout = findViewById(R.id.tilRegisterFullName);
        TextInputLayout emailLayout = findViewById(R.id.tilRegisterEmail);
        TextInputLayout passwordLayout = findViewById(R.id.tilRegisterPassword);
        TextInputLayout confirmPasswordLayout = findViewById(R.id.tilRegisterConfirmPassword);
        findViewById(R.id.txtGoLogin).setOnClickListener(v -> showLogin());
        findViewById(R.id.btnRegister).setOnClickListener(v ->
                handleRegister(
                        fullNameInput,
                        emailInput,
                        passwordInput,
                        confirmPasswordInput,
                        fullNameLayout,
                        emailLayout,
                        passwordLayout,
                        confirmPasswordLayout
                ));
    }

    private void showForgotPassword() {
        prepareLightWindow();
        setContentView(R.layout.activity_forgot_password);
        EditText emailInput = findViewById(R.id.edtEmail);
        TextInputLayout emailLayout = findViewById(R.id.tilForgotEmail);
        findViewById(R.id.btnBack).setOnClickListener(v -> showLogin());
        findViewById(R.id.txtBackLogin).setOnClickListener(v -> showLogin());
        findViewById(R.id.btnSendInstruction).setOnClickListener(v -> handleForgotPassword(emailInput, emailLayout));
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
        findViewById(R.id.libraryItemCleanCode).setOnClickListener(v -> showDetail());
        findViewById(R.id.libraryItemAi).setOnClickListener(v -> showDetail());
        findViewById(R.id.libraryItemEnglish).setOnClickListener(v -> showDetail());
        findViewById(R.id.libraryItemAndroidPlay).setOnClickListener(v -> showFullPlayer());
        findViewById(R.id.libraryItemCleanCode).setOnLongClickListener(v -> {
            removeLibraryItem();
            return true;
        });
        findViewById(R.id.btnLibraryAdd).setOnClickListener(v -> addLibraryItem());
        findViewById(R.id.libraryCreateCard).setOnClickListener(v -> addLibraryItem());
        findViewById(R.id.btnLibrarySort).setOnClickListener(v -> sortLibraryItems());
        findViewById(R.id.filterAll).setOnClickListener(v -> onLibraryFilterSelected("All"));
        findViewById(R.id.filterListening).setOnClickListener(v -> onLibraryFilterSelected("Listening"));
        findViewById(R.id.filterDownloaded).setOnClickListener(v -> onLibraryFilterSelected("Downloaded"));
        findViewById(R.id.filterCompleted).setOnClickListener(v -> onLibraryFilterSelected("Completed"));
        applyLibraryFilter();
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

    private void handleLogin(EditText emailInput, EditText passwordInput, TextInputLayout emailLayout, TextInputLayout passwordLayout) {
        boolean valid = validateEmailField(emailInput, emailLayout) & validateRequired(passwordInput, passwordLayout, "Password is required");
        if (!valid) {
            Toast.makeText(this, "Login failed. Please check your input.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Login success", Toast.LENGTH_SHORT).show();
        showHome();
    }

    private void handleRegister(EditText fullNameInput, EditText emailInput, EditText passwordInput, EditText confirmPasswordInput,
                                TextInputLayout fullNameLayout, TextInputLayout emailLayout, TextInputLayout passwordLayout,
                                TextInputLayout confirmPasswordLayout) {
        boolean valid = validateRequired(fullNameInput, fullNameLayout, "Full name is required")
                & validateEmailField(emailInput, emailLayout)
                & validateRequired(passwordInput, passwordLayout, "Password is required")
                & validateRequired(confirmPasswordInput, confirmPasswordLayout, "Confirm password is required");
        if (valid && !TextUtils.equals(passwordInput.getText(), confirmPasswordInput.getText())) {
            setInputError(confirmPasswordLayout, "Passwords do not match");
            valid = false;
        }
        if (!valid) {
            Toast.makeText(this, "Register failed. Please check your input.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Register success", Toast.LENGTH_SHORT).show();
        showHome();
    }

    private void handleForgotPassword(EditText emailInput, TextInputLayout emailLayout) {
        if (!validateEmailField(emailInput, emailLayout)) {
            Toast.makeText(this, "Cannot send instruction. Invalid email.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Instruction sent to email", Toast.LENGTH_SHORT).show();
        showLogin();
    }

    private boolean validateEmailField(EditText emailInput, TextInputLayout inputLayout) {
        String email = emailInput.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            setInputError(inputLayout, "Email is required");
            return false;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            setInputError(inputLayout, "Invalid email format");
            return false;
        }
        clearInputError(inputLayout);
        return true;
    }

    private boolean validateRequired(EditText input, TextInputLayout inputLayout, String message) {
        if (TextUtils.isEmpty(input.getText().toString().trim())) {
            setInputError(inputLayout, message);
            return false;
        }
        clearInputError(inputLayout);
        return true;
    }

    private void setInputError(TextInputLayout inputLayout, String message) {
        inputLayout.setErrorEnabled(true);
        inputLayout.setError(message);
    }

    private void clearInputError(TextInputLayout inputLayout) {
        inputLayout.setError(null);
        inputLayout.setErrorEnabled(false);
    }

    private void runSearch() {
        performSearch(true);
    }

    private void runDebouncedSearch() {
        performSearch(false);
    }

    private void performSearch(boolean isSubmit) {
        if (searchInput == null) {
            return;
        }
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            if (isSubmit) {
                Toast.makeText(this, "Please enter keyword", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (isSubmit) {
            Toast.makeText(this, "Searching: " + query, Toast.LENGTH_SHORT).show();
            return;
        }
        // Debounced input-change event: simulate silent API call trigger.
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
        showSearch();
        if (searchInput != null) {
            searchInput.setText(topic);
            searchInput.setSelection(topic.length());
        }
        runSearch();
    }

    private void onLibraryFilterSelected(String filter) {
        currentLibraryFilter = filter;
        applyLibraryFilter();
        Toast.makeText(this, "Filter: " + filter, Toast.LENGTH_SHORT).show();
    }

    private void applyLibraryFilter() {
        View androidItem = findViewById(R.id.libraryItemAndroid);
        View cleanCodeItem = findViewById(R.id.libraryItemCleanCode);
        View aiItem = findViewById(R.id.libraryItemAi);
        View englishItem = findViewById(R.id.libraryItemEnglish);

        boolean showAndroid = "All".equals(currentLibraryFilter) || "Listening".equals(currentLibraryFilter);
        boolean showCleanCode = "All".equals(currentLibraryFilter) || "Listening".equals(currentLibraryFilter);
        boolean showAi = "All".equals(currentLibraryFilter) || "Completed".equals(currentLibraryFilter);
        boolean showEnglish = "All".equals(currentLibraryFilter) || "Downloaded".equals(currentLibraryFilter);

        androidItem.setVisibility(showAndroid ? View.VISIBLE : View.GONE);
        cleanCodeItem.setVisibility(showCleanCode && !cleanCodeRemoved ? View.VISIBLE : View.GONE);
        aiItem.setVisibility(showAi ? View.VISIBLE : View.GONE);
        englishItem.setVisibility(showEnglish ? View.VISIBLE : View.GONE);
    }

    private void removeLibraryItem() {
        cleanCodeRemoved = true;
        applyLibraryFilter();
        Toast.makeText(this, "Removed item", Toast.LENGTH_SHORT).show();
    }

    private void addLibraryItem() {
        if (!cleanCodeRemoved) {
            Toast.makeText(this, "Playlist created", Toast.LENGTH_SHORT).show();
            return;
        }
        cleanCodeRemoved = false;
        applyLibraryFilter();
        Toast.makeText(this, "Added item back to playlist", Toast.LENGTH_SHORT).show();
    }

    private void sortLibraryItems() {
        LinearLayout content = findViewById(R.id.libraryContent);
        View androidItem = findViewById(R.id.libraryItemAndroid);
        View cleanCodeItem = findViewById(R.id.libraryItemCleanCode);
        View aiItem = findViewById(R.id.libraryItemAi);
        View englishItem = findViewById(R.id.libraryItemEnglish);

        content.removeView(androidItem);
        content.removeView(cleanCodeItem);
        content.removeView(aiItem);
        content.removeView(englishItem);

        int insertIndex = 2;
        if (sortAscending) {
            content.addView(aiItem, insertIndex++);
            content.addView(androidItem, insertIndex++);
            content.addView(cleanCodeItem, insertIndex++);
            content.addView(englishItem, insertIndex);
            Toast.makeText(this, "Sort: A-Z", Toast.LENGTH_SHORT).show();
        } else {
            content.addView(englishItem, insertIndex++);
            content.addView(cleanCodeItem, insertIndex++);
            content.addView(androidItem, insertIndex++);
            content.addView(aiItem, insertIndex);
            Toast.makeText(this, "Sort: Z-A", Toast.LENGTH_SHORT).show();
        }
        sortAscending = !sortAscending;
        applyLibraryFilter();
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
