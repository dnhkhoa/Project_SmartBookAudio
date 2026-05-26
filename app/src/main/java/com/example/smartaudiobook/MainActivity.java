package com.example.smartaudiobook;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private enum Screen {
        HOME,
        EXPLORE,
        SEARCH,
        LIBRARY,
        PROFILE,
        DETAIL,
        EBOOK_DETAIL,
        FULL_PLAYER,
        BACKGROUND_POPUP
    }

    private static final int PLAYER_DURATION_SECONDS = 18 * 60 + 30;
    private static final String[] PLAYBACK_SPEEDS = {"0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::runSearch;
    private static final long SEARCH_DEBOUNCE_MS = 450L;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private EditText searchInput;
    private Screen currentScreen = Screen.HOME;
    private Screen playerReturnScreen = Screen.HOME;
    private int playerPositionSeconds = 85;
    private int playbackSpeedIndex = 1;
    private int selectedLibraryFilter = 0;
    private boolean isPlaying = true;

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
        currentScreen = Screen.HOME;
        prepareLightWindow();
        setContentView(R.layout.activity_splash);
    }

    private void showOnboarding() {
        currentScreen = Screen.HOME;
        prepareLightWindow();
        setContentView(R.layout.activity_onboarding);
        findViewById(R.id.btnGetStarted).setOnClickListener(v -> showLogin());
        findViewById(R.id.txtGoLogin).setOnClickListener(v -> showLogin());
    }

    private void showLogin() {
        currentScreen = Screen.HOME;
        prepareLightWindow();
        setContentView(R.layout.activity_login);
        EditText emailInput = findViewById(R.id.edtEmail);
        EditText passwordInput = findViewById(R.id.edtPassword);
        findViewById(R.id.txtForgot).setOnClickListener(v -> showForgotPassword());
        findViewById(R.id.txtGoRegister).setOnClickListener(v -> showRegister());
        findViewById(R.id.btnLogin).setOnClickListener(v -> handleLogin(emailInput, passwordInput));
    }

    private void showRegister() {
        currentScreen = Screen.HOME;
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
        currentScreen = Screen.HOME;
        prepareLightWindow();
        setContentView(R.layout.activity_forgot_password);
        EditText emailInput = findViewById(R.id.edtEmail);
        findViewById(R.id.btnBack).setOnClickListener(v -> showLogin());
        findViewById(R.id.txtBackLogin).setOnClickListener(v -> showLogin());
        findViewById(R.id.btnSendInstruction).setOnClickListener(v -> handleForgotPassword(emailInput));
    }

    private void showHome() {
        currentScreen = Screen.HOME;
        prepareLightWindow();
        setContentView(R.layout.activity_home);
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
        findViewById(R.id.navLibrary).setOnClickListener(v -> showLibrary());
        findViewById(R.id.navProfile).setOnClickListener(v -> showProfile());
        findViewById(R.id.cardContinueListening).setOnClickListener(v -> openFullPlayer());
        findViewById(R.id.cardGeneratedAudioOne).setOnClickListener(v -> showSearch());
        findViewById(R.id.cardGeneratedAudioTwo).setOnClickListener(v -> showEbookDetail());
        findViewById(R.id.miniPlayerDock).setOnClickListener(v -> openFullPlayer());
    }

    private void showExplore() {
        currentScreen = Screen.EXPLORE;
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
        currentScreen = Screen.SEARCH;
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
        currentScreen = Screen.LIBRARY;
        prepareLightWindow();
        setContentView(R.layout.activity_library);
        findViewById(R.id.navHome).setOnClickListener(v -> showHome());
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
        findViewById(R.id.navProfile).setOnClickListener(v -> showProfile());
        findViewById(R.id.btnLibrarySort).setOnClickListener(v ->
                Toast.makeText(this, "Sort: Updated recently", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btnLibraryAdd).setOnClickListener(v -> showSearch());
        findViewById(R.id.libraryCreateCard).setOnClickListener(v -> showSearch());
        findViewById(R.id.libraryItemAndroid).setOnClickListener(v -> openFullPlayer());
        findViewById(R.id.libraryItemAndroidPlay).setOnClickListener(v -> openFullPlayer());
        findViewById(R.id.libraryItemCleanCode).setOnClickListener(v -> openFullPlayer());
        findViewById(R.id.libraryItemCleanCode).setOnLongClickListener(v -> {
            Toast.makeText(this, "Removed item from playlist", Toast.LENGTH_SHORT).show();
            return true;
        });
        findViewById(R.id.libraryItemEnglish).setOnClickListener(v -> openFullPlayer());
        findViewById(R.id.libraryItemAiLecture).setOnClickListener(v -> showToast("Audio is still processing"));
        bindLibraryFilters();
    }

    private void showProfile() {
        currentScreen = Screen.PROFILE;
        prepareProfileWindow();
        setContentView(R.layout.activity_profile);
        findViewById(R.id.navHome).setOnClickListener(v -> showHome());
        findViewById(R.id.navExplore).setOnClickListener(v -> showExplore());
        findViewById(R.id.navLibrary).setOnClickListener(v -> showLibrary());
        findViewById(R.id.menuDefaultVoice).setOnClickListener(v -> showProfileAction(getString(R.string.profile_default_voice)));
        findViewById(R.id.menuAppLanguage).setOnClickListener(v -> showProfileAction(getString(R.string.profile_app_language)));
        findViewById(R.id.menuDataStorage).setOnClickListener(v -> showProfileAction(getString(R.string.profile_data_storage)));
        findViewById(R.id.menuHelpSupport).setOnClickListener(v -> showProfileAction(getString(R.string.profile_help_support)));
        findViewById(R.id.btnSignOut).setOnClickListener(v -> showLogin());
    }

    private void showDetail() {
        currentScreen = Screen.DETAIL;
        prepareLightWindow();
        setContentView(R.layout.activity_detail);
        findViewById(R.id.btnBackDetail).setOnClickListener(v -> showSearch());
    }

    private void showEbookDetail() {
        currentScreen = Screen.EBOOK_DETAIL;
        prepareLightWindow();
        setContentView(R.layout.activity_ebook_detail);
        findViewById(R.id.btnBackEbookDetail).setOnClickListener(v -> showSearch());
    }

    private void openFullPlayer() {
        playerReturnScreen = getPlayerReturnScreen();
        showFullPlayer();
    }

    private void showFullPlayer() {
        currentScreen = Screen.FULL_PLAYER;
        preparePlayerWindow();
        setContentView(R.layout.activity_full_player);
        findViewById(R.id.btnBackFullPlayer).setOnClickListener(v -> showPlayerReturnScreen());
        findViewById(R.id.btnFullPlayerRewind).setOnClickListener(v -> seekPlayerBy(-15));
        findViewById(R.id.btnFullPlayerPlayPause).setOnClickListener(v -> togglePlayback());
        findViewById(R.id.btnFullPlayerForward).setOnClickListener(v -> seekPlayerBy(15));
        findViewById(R.id.btnFullPlayerSpeed).setOnClickListener(v -> cyclePlaybackSpeed());
        findViewById(R.id.btnOpenBackgroundPopup).setOnClickListener(v -> showBackgroundPopup());
        findViewById(R.id.btnFullPlayerChapter).setOnClickListener(v -> showToast("Chapter list selected"));
        findViewById(R.id.fullPlayerProgress).setOnTouchListener((view, event) -> handleProgressTouch(view, event));
        updateFullPlayerUi();
    }

    private void showBackgroundPopup() {
        currentScreen = Screen.BACKGROUND_POPUP;
        preparePlayerWindow();
        setContentView(R.layout.activity_background_popup);
        findViewById(R.id.btnReturnToPlayer).setOnClickListener(v -> showFullPlayer());
        findViewById(R.id.backgroundNotification).setOnClickListener(v -> showFullPlayer());
        findViewById(R.id.btnBackgroundPrevious).setOnClickListener(v -> seekPlayerBy(-15));
        findViewById(R.id.btnBackgroundPlayPause).setOnClickListener(v -> togglePlayback());
        findViewById(R.id.btnBackgroundNext).setOnClickListener(v -> seekPlayerBy(15));
        updateBackgroundPopupUi();
    }

    private Screen getPlayerReturnScreen() {
        if (currentScreen == Screen.FULL_PLAYER || currentScreen == Screen.BACKGROUND_POPUP) {
            return Screen.HOME;
        }
        return currentScreen;
    }

    private void showPlayerReturnScreen() {
        switch (playerReturnScreen) {
            case EXPLORE:
                showExplore();
                break;
            case SEARCH:
                showSearch();
                break;
            case LIBRARY:
                showLibrary();
                break;
            case PROFILE:
                showProfile();
                break;
            case DETAIL:
                showDetail();
                break;
            case EBOOK_DETAIL:
                showEbookDetail();
                break;
            case HOME:
            case FULL_PLAYER:
            case BACKGROUND_POPUP:
            default:
                showHome();
                break;
        }
    }

    private boolean handleProgressTouch(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
            int width = view.getWidth();
            if (width > 0) {
                float fraction = Math.max(0f, Math.min(1f, event.getX() / width));
                playerPositionSeconds = clampPlayerPosition((int) (PLAYER_DURATION_SECONDS * fraction));
                updateFullPlayerUi();
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                view.performClick();
            }
            return true;
        }
        return true;
    }

    private void seekPlayerBy(int deltaSeconds) {
        playerPositionSeconds = clampPlayerPosition(playerPositionSeconds + deltaSeconds);
        if (currentScreen == Screen.FULL_PLAYER) {
            updateFullPlayerUi();
        } else if (currentScreen == Screen.BACKGROUND_POPUP) {
            updateBackgroundPopupUi();
        }
        showToast("Position " + formatTime(playerPositionSeconds));
    }

    private int clampPlayerPosition(int seconds) {
        return Math.max(0, Math.min(PLAYER_DURATION_SECONDS, seconds));
    }

    private void togglePlayback() {
        isPlaying = !isPlaying;
        if (currentScreen == Screen.FULL_PLAYER) {
            updateFullPlayerUi();
        } else if (currentScreen == Screen.BACKGROUND_POPUP) {
            updateBackgroundPopupUi();
        }
        showToast(isPlaying ? "Playing" : "Paused");
    }

    private void cyclePlaybackSpeed() {
        playbackSpeedIndex = (playbackSpeedIndex + 1) % PLAYBACK_SPEEDS.length;
        updateFullPlayerUi();
        showToast("Speed " + PLAYBACK_SPEEDS[playbackSpeedIndex]);
    }

    private void updateFullPlayerUi() {
        TextView elapsed = findViewById(R.id.fullPlayerElapsed);
        TextView duration = findViewById(R.id.fullPlayerDuration);
        TextView playPause = findViewById(R.id.btnFullPlayerPlayPause);
        TextView speed = findViewById(R.id.btnFullPlayerSpeed);
        TextView chapter = findViewById(R.id.fullPlayerChapter);
        FrameLayout progress = findViewById(R.id.fullPlayerProgress);
        View fill = findViewById(R.id.fullPlayerProgressFill);
        View thumb = findViewById(R.id.fullPlayerProgressThumb);

        elapsed.setText(formatTime(playerPositionSeconds));
        duration.setText(formatTime(PLAYER_DURATION_SECONDS));
        playPause.setText(isPlaying ? getString(R.string.full_player_pause_icon) : ">");
        speed.setText(PLAYBACK_SPEEDS[playbackSpeedIndex]);
        chapter.setText(isPlaying ? "Chapter 2: Background Audio" : "Chapter 2: Paused");

        progress.post(() -> {
            int progressWidth = progress.getWidth();
            if (progressWidth <= 0) {
                return;
            }
            float fraction = (float) playerPositionSeconds / PLAYER_DURATION_SECONDS;
            int fillWidth = Math.round(progressWidth * fraction);

            ViewGroup.LayoutParams fillParams = fill.getLayoutParams();
            fillParams.width = fillWidth;
            fill.setLayoutParams(fillParams);

            FrameLayout.LayoutParams thumbParams = (FrameLayout.LayoutParams) thumb.getLayoutParams();
            int thumbHalf = thumb.getWidth() / 2;
            thumbParams.leftMargin = Math.max(0, Math.min(progressWidth - thumb.getWidth(), fillWidth - thumbHalf));
            thumb.setLayoutParams(thumbParams);
        });
    }

    private void updateBackgroundPopupUi() {
        TextView playPause = findViewById(R.id.btnBackgroundPlayPause);
        TextView chapter = findViewById(R.id.backgroundAudioChapter);
        playPause.setText(isPlaying ? getString(R.string.full_player_pause_icon) : ">");
        chapter.setText(isPlaying ? "Chapter 2 - Playing" : "Chapter 2 - Paused");
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void bindLibraryFilters() {
        bindLibraryFilter(R.id.filterAll, 0, getString(R.string.library_filter_all));
        bindLibraryFilter(R.id.filterListening, 1, getString(R.string.library_filter_listening));
        bindLibraryFilter(R.id.filterDownloaded, 2, getString(R.string.library_filter_downloaded));
        bindLibraryFilter(R.id.filterCompleted, 3, getString(R.string.library_filter_completed));
        updateLibraryFilters();
    }

    private void bindLibraryFilter(int viewId, int filterIndex, String label) {
        findViewById(viewId).setOnClickListener(v -> {
            selectedLibraryFilter = filterIndex;
            updateLibraryFilters();
            showToast("Filter: " + label);
        });
    }

    private void updateLibraryFilters() {
        setLibraryFilterState(R.id.filterAll, selectedLibraryFilter == 0);
        setLibraryFilterState(R.id.filterListening, selectedLibraryFilter == 1);
        setLibraryFilterState(R.id.filterDownloaded, selectedLibraryFilter == 2);
        setLibraryFilterState(R.id.filterCompleted, selectedLibraryFilter == 3);
        setLibraryItemVisibility(R.id.libraryItemAndroid, selectedLibraryFilter == 0 || selectedLibraryFilter == 1);
        setLibraryItemVisibility(R.id.libraryItemCleanCode, selectedLibraryFilter == 0 || selectedLibraryFilter == 1 || selectedLibraryFilter == 3);
        setLibraryItemVisibility(R.id.libraryItemAiLecture, selectedLibraryFilter == 0);
        setLibraryItemVisibility(R.id.libraryItemEnglish, selectedLibraryFilter == 0 || selectedLibraryFilter == 2);
    }

    private void setLibraryFilterState(int viewId, boolean selected) {
        TextView filter = findViewById(viewId);
        filter.setBackgroundResource(selected ? R.drawable.bg_button_gradient : R.drawable.bg_chip_soft);
        filter.setTextColor(getColor(selected ? android.R.color.white : R.color.primary_blue));
    }

    private void setLibraryItemVisibility(int viewId, boolean visible) {
        findViewById(viewId).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showProfileAction(String title) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("This setting screen is ready for the next implementation step.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
