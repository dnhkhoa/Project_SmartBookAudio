package com.example.smartaudiobook;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Locale;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private enum Screen {
        SPLASH,
        ONBOARDING,
        LOGIN,
        REGISTER,
        FORGOT_PASSWORD,
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
    private static final String FIRESTORE_TEST_TAG = "FS_TEST";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::runDebouncedSearch;
    private final Navigator navigator = new Navigator();
    private static final long SEARCH_DEBOUNCE_MS = 450L;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private EditText searchInput;
    private Screen currentScreen = Screen.HOME;
    private int playerPositionSeconds = 85;
    private int playbackSpeedIndex = 1;
    private int selectedLibraryFilter = 0;
    private boolean isPlaying = true;
    private boolean cleanCodeRemoved = false;
    private boolean librarySortAscending = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!navigator.goBack()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        navigator.resetTo(Screen.HOME);

    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private final class Navigator {
        private final ArrayDeque<Screen> backStack = new ArrayDeque<>();

        private void resetTo(Screen target) {
            backStack.clear();
            routeTo(target);
        }

        private void navigateTo(Screen target) {
            if (target == currentScreen) {
                return;
            }
            backStack.push(currentScreen);
            routeTo(target);
        }

        private void switchTab(Screen target) {
            if (target == currentScreen) {
                return;
            }
            backStack.clear();
            if (target != Screen.HOME) {
                backStack.push(Screen.HOME);
            }
            routeTo(target);
        }

        private boolean goBack() {
            if (!backStack.isEmpty()) {
                routeTo(backStack.pop());
                return true;
            }
            if (isPrimaryTab(currentScreen) && currentScreen != Screen.HOME) {
                routeTo(Screen.HOME);
                return true;
            }
            return false;
        }

        private void routeTo(Screen target) {
            handler.removeCallbacks(searchRunnable);
            searchInput = null;
            switch (target) {
                case SPLASH:
                    showSplash();
                    break;
                case ONBOARDING:
                    showOnboarding();
                    break;
                case LOGIN:
                    showLogin();
                    break;
                case REGISTER:
                    showRegister();
                    break;
                case FORGOT_PASSWORD:
                    showForgotPassword();
                    break;
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
                case FULL_PLAYER:
                    showFullPlayer();
                    break;
                case BACKGROUND_POPUP:
                    showBackgroundPopup();
                    break;
                case HOME:
                default:
                    showHome();
                    break;
            }
        }

        private boolean isPrimaryTab(Screen screen) {
            return screen == Screen.HOME
                    || screen == Screen.EXPLORE
                    || screen == Screen.LIBRARY
                    || screen == Screen.PROFILE;
        }
    }

    private void showSplash() {
        currentScreen = Screen.SPLASH;
        prepareLightWindow();
        setContentView(R.layout.activity_splash);
    }

    private void showOnboarding() {
        currentScreen = Screen.ONBOARDING;
        prepareLightWindow();
        setContentView(R.layout.activity_onboarding);
        findViewById(R.id.btnGetStarted).setOnClickListener(v -> navigator.navigateTo(Screen.LOGIN));
        findViewById(R.id.txtGoLogin).setOnClickListener(v -> navigator.navigateTo(Screen.LOGIN));
    }

    private void showLogin() {
        currentScreen = Screen.LOGIN;
        prepareLightWindow();
        setContentView(R.layout.activity_login);
        EditText emailInput = findViewById(R.id.edtEmail);
        EditText passwordInput = findViewById(R.id.edtPassword);
        TextInputLayout emailLayout = findViewById(R.id.tilLoginEmail);
        TextInputLayout passwordLayout = findViewById(R.id.tilLoginPassword);
        findViewById(R.id.txtForgot).setOnClickListener(v -> navigator.navigateTo(Screen.FORGOT_PASSWORD));
        findViewById(R.id.txtGoRegister).setOnClickListener(v -> navigator.navigateTo(Screen.REGISTER));
        findViewById(R.id.btnLogin).setOnClickListener(v ->
                handleLogin(emailInput, passwordInput, emailLayout, passwordLayout));
    }

    private void showRegister() {
        currentScreen = Screen.REGISTER;
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
        findViewById(R.id.txtGoLogin).setOnClickListener(v -> navigator.goBack());
        findViewById(R.id.btnRegister).setOnClickListener(v ->
                handleRegister(fullNameInput, emailInput, passwordInput, confirmPasswordInput,
                        fullNameLayout, emailLayout, passwordLayout, confirmPasswordLayout));
    }

    private void showForgotPassword() {
        currentScreen = Screen.FORGOT_PASSWORD;
        prepareLightWindow();
        setContentView(R.layout.activity_forgot_password);
        EditText emailInput = findViewById(R.id.edtEmail);
        TextInputLayout emailLayout = findViewById(R.id.tilForgotEmail);
        findViewById(R.id.btnBack).setOnClickListener(v -> navigator.goBack());
        findViewById(R.id.txtBackLogin).setOnClickListener(v -> navigator.goBack());
        findViewById(R.id.btnSendInstruction).setOnClickListener(v -> handleForgotPassword(emailInput, emailLayout));
    }

    private void showHome() {
        currentScreen = Screen.HOME;
        prepareLightWindow();
        setContentView(R.layout.activity_home);
        findViewById(R.id.navExplore).setOnClickListener(v -> navigator.switchTab(Screen.EXPLORE));
        findViewById(R.id.navLibrary).setOnClickListener(v -> navigator.switchTab(Screen.LIBRARY));
        findViewById(R.id.navProfile).setOnClickListener(v -> navigator.switchTab(Screen.PROFILE));
        findViewById(R.id.cardContinueListening).setOnClickListener(v -> openFullPlayer());
        findViewById(R.id.cardGeneratedAudioOne).setOnClickListener(v -> navigator.navigateTo(Screen.SEARCH));
        findViewById(R.id.cardGeneratedAudioTwo).setOnClickListener(v -> navigator.navigateTo(Screen.EBOOK_DETAIL));
        findViewById(R.id.miniPlayerDock).setOnClickListener(v -> openFullPlayer());
    }

    private void showExplore() {
        currentScreen = Screen.EXPLORE;
        prepareLightWindow();
        setContentView(R.layout.activity_explore);
        findViewById(R.id.navHome).setOnClickListener(v -> navigator.switchTab(Screen.HOME));
        findViewById(R.id.navLibrary).setOnClickListener(v -> navigator.switchTab(Screen.LIBRARY));
        findViewById(R.id.navProfile).setOnClickListener(v -> navigator.switchTab(Screen.PROFILE));
        findViewById(R.id.btnOpenSearch).setOnClickListener(v -> navigator.navigateTo(Screen.SEARCH));
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
        findViewById(R.id.navHome).setOnClickListener(v -> navigator.switchTab(Screen.HOME));
        findViewById(R.id.navExplore).setOnClickListener(v -> navigator.switchTab(Screen.EXPLORE));
        findViewById(R.id.navLibrary).setOnClickListener(v -> navigator.switchTab(Screen.LIBRARY));
        findViewById(R.id.navProfile).setOnClickListener(v -> navigator.switchTab(Screen.PROFILE));
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
        findViewById(R.id.itemAndroidServiceTts).setOnClickListener(v -> navigator.navigateTo(Screen.DETAIL));
        findViewById(R.id.itemServiceAndroid).setOnClickListener(v -> navigator.navigateTo(Screen.EBOOK_DETAIL));
    }

    private void showLibrary() {
        currentScreen = Screen.LIBRARY;
        prepareLightWindow();
        setContentView(R.layout.activity_library);
        findViewById(R.id.navHome).setOnClickListener(v -> navigator.switchTab(Screen.HOME));
        findViewById(R.id.navExplore).setOnClickListener(v -> navigator.switchTab(Screen.EXPLORE));
        findViewById(R.id.navProfile).setOnClickListener(v -> navigator.switchTab(Screen.PROFILE));
        findViewById(R.id.btnLibrarySort).setOnClickListener(v -> sortLibraryItems());
        findViewById(R.id.btnLibraryAdd).setOnClickListener(v -> addLibraryItem());
        findViewById(R.id.libraryCreateCard).setOnClickListener(v -> addLibraryItem());
        findViewById(R.id.libraryItemAndroid).setOnClickListener(v -> navigator.navigateTo(Screen.DETAIL));
        findViewById(R.id.libraryItemAndroidPlay).setOnClickListener(v -> openFullPlayer());
        findViewById(R.id.libraryItemCleanCode).setOnClickListener(v -> navigator.navigateTo(Screen.DETAIL));
        findViewById(R.id.libraryItemCleanCode).setOnLongClickListener(v -> {
            removeLibraryItem();
            return true;
        });
        findViewById(R.id.libraryItemEnglish).setOnClickListener(v -> navigator.navigateTo(Screen.DETAIL));
        findViewById(R.id.libraryItemAiLecture).setOnClickListener(v -> navigator.navigateTo(Screen.DETAIL));
        bindLibraryFilters();
    }

    private void showProfile() {
        currentScreen = Screen.PROFILE;
        prepareProfileWindow();
        setContentView(R.layout.activity_profile);
        findViewById(R.id.navHome).setOnClickListener(v -> navigator.switchTab(Screen.HOME));
        findViewById(R.id.navExplore).setOnClickListener(v -> navigator.switchTab(Screen.EXPLORE));
        findViewById(R.id.navLibrary).setOnClickListener(v -> navigator.switchTab(Screen.LIBRARY));
        findViewById(R.id.menuDefaultVoice).setOnClickListener(v -> showProfileAction(getString(R.string.profile_default_voice)));
        findViewById(R.id.menuAppLanguage).setOnClickListener(v -> showProfileAction(getString(R.string.profile_app_language)));
        findViewById(R.id.menuDataStorage).setOnClickListener(v -> showProfileAction(getString(R.string.profile_data_storage)));
        findViewById(R.id.menuHelpSupport).setOnClickListener(v -> showProfileAction(getString(R.string.profile_help_support)));
        findViewById(R.id.btnSignOut).setOnClickListener(v -> navigator.resetTo(Screen.LOGIN));
    }

    private void showDetail() {
        currentScreen = Screen.DETAIL;
        prepareLightWindow();
        setContentView(R.layout.activity_detail);
        findViewById(R.id.btnBackDetail).setOnClickListener(v -> navigator.goBack());
        findViewById(R.id.detailPlayButton).setOnClickListener(v -> openFullPlayer());
    }

    private void showEbookDetail() {
        currentScreen = Screen.EBOOK_DETAIL;
        prepareLightWindow();
        setContentView(R.layout.activity_ebook_detail);
        findViewById(R.id.btnBackEbookDetail).setOnClickListener(v -> navigator.goBack());
    }

    private void openFullPlayer() {
        navigator.navigateTo(Screen.FULL_PLAYER);
    }

    private void showFullPlayer() {
        currentScreen = Screen.FULL_PLAYER;
        preparePlayerWindow();
        setContentView(R.layout.activity_full_player);
        findViewById(R.id.btnBackFullPlayer).setOnClickListener(v -> navigator.goBack());
        findViewById(R.id.btnFullPlayerRewind).setOnClickListener(v -> seekPlayerBy(-15));
        findViewById(R.id.btnFullPlayerPlayPause).setOnClickListener(v -> togglePlayback());
        findViewById(R.id.btnFullPlayerForward).setOnClickListener(v -> seekPlayerBy(15));
        findViewById(R.id.btnFullPlayerSpeed).setOnClickListener(v -> cyclePlaybackSpeed());
        findViewById(R.id.btnOpenBackgroundPopup).setOnClickListener(v -> navigator.navigateTo(Screen.BACKGROUND_POPUP));
        findViewById(R.id.btnFullPlayerChapter).setOnClickListener(v -> showToast("Chapter list selected"));
        findViewById(R.id.fullPlayerProgress).setOnTouchListener((view, event) -> handleProgressTouch(view, event));
        updateFullPlayerUi();
    }

    private void showBackgroundPopup() {
        currentScreen = Screen.BACKGROUND_POPUP;
        preparePlayerWindow();
        setContentView(R.layout.activity_background_popup);
        findViewById(R.id.btnReturnToPlayer).setOnClickListener(v -> navigator.goBack());
        findViewById(R.id.backgroundNotification).setOnClickListener(v -> navigator.goBack());
        findViewById(R.id.btnBackgroundPrevious).setOnClickListener(v -> seekPlayerBy(-15));
        findViewById(R.id.btnBackgroundPlayPause).setOnClickListener(v -> togglePlayback());
        findViewById(R.id.btnBackgroundNext).setOnClickListener(v -> seekPlayerBy(15));
        updateBackgroundPopupUi();
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
        setLibraryItemVisibility(R.id.libraryItemCleanCode, !cleanCodeRemoved && (selectedLibraryFilter == 0 || selectedLibraryFilter == 1 || selectedLibraryFilter == 3));
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

    private void handleLogin(EditText emailInput, EditText passwordInput, TextInputLayout emailLayout, TextInputLayout passwordLayout) {
        boolean valid = validateEmailField(emailInput, emailLayout) & validateRequired(passwordInput, passwordLayout, "Password is required");
        if (!valid) {
            Toast.makeText(this, "Login failed. Please check your input.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Login success", Toast.LENGTH_SHORT).show();
        runFirestoreConnectionTest(emailInput.getText().toString().trim());
        navigator.resetTo(Screen.HOME);
    }

    private void runFirestoreConnectionTest(String email) {
        String normalizedEmail = TextUtils.isEmpty(email) ? "demo" : email.toLowerCase(Locale.US);
        String docId = "test_" + normalizedEmail.replaceAll("[^a-z0-9]", "_");

        Map<String, Object> data = new HashMap<>();
        data.put("ping", "ok");
        data.put("email", normalizedEmail);
        data.put("time", Timestamp.now());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(docId).set(data)
                .addOnSuccessListener(unused -> {
                    Log.d(FIRESTORE_TEST_TAG, "WRITE_OK docId=" + docId);
                    db.collection("users").document(docId).get()
                            .addOnSuccessListener(snapshot -> {
                                Log.d(FIRESTORE_TEST_TAG, "READ_OK data=" + snapshot.getData());
                                Toast.makeText(this, "Firebase OK (write/read success)", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(error -> {
                                Log.e(FIRESTORE_TEST_TAG, "READ_FAIL", error);
                                Toast.makeText(this, "Firebase read failed", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(error -> {
                    Log.e(FIRESTORE_TEST_TAG, "WRITE_FAIL", error);
                    Toast.makeText(this, "Firebase write failed", Toast.LENGTH_SHORT).show();
                });
    }

    private void handleRegister(EditText fullNameInput, EditText emailInput, EditText passwordInput, EditText confirmPasswordInput,
                                TextInputLayout fullNameLayout, TextInputLayout emailLayout, TextInputLayout passwordLayout, TextInputLayout confirmPasswordLayout) {
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
        navigator.resetTo(Screen.HOME);
    }

    private void handleForgotPassword(EditText emailInput, TextInputLayout emailLayout) {
        if (!validateEmailField(emailInput, emailLayout)) {
            Toast.makeText(this, "Cannot send instruction. Invalid email.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Instruction sent to email", Toast.LENGTH_SHORT).show();
        navigator.resetTo(Screen.LOGIN);
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

    private void removeLibraryItem() {
        cleanCodeRemoved = true;
        updateLibraryFilters();
        showToast("Removed item");
    }

    private void addLibraryItem() {
        if (!cleanCodeRemoved) {
            showToast("Playlist created");
            return;
        }
        cleanCodeRemoved = false;
        updateLibraryFilters();
        showToast("Added item back to playlist");
    }

    private void sortLibraryItems() {
        LinearLayout content = findViewById(R.id.libraryContent);
        View androidItem = findViewById(R.id.libraryItemAndroid);
        View cleanCodeItem = findViewById(R.id.libraryItemCleanCode);
        View aiItem = findViewById(R.id.libraryItemAiLecture);
        View englishItem = findViewById(R.id.libraryItemEnglish);

        content.removeView(androidItem);
        content.removeView(cleanCodeItem);
        content.removeView(aiItem);
        content.removeView(englishItem);

        int insertIndex = 2;
        if (librarySortAscending) {
            content.addView(aiItem, insertIndex++);
            content.addView(androidItem, insertIndex++);
            content.addView(cleanCodeItem, insertIndex++);
            content.addView(englishItem, insertIndex);
            showToast("Sort: A-Z");
        } else {
            content.addView(englishItem, insertIndex++);
            content.addView(cleanCodeItem, insertIndex++);
            content.addView(androidItem, insertIndex++);
            content.addView(aiItem, insertIndex);
            showToast("Sort: Z-A");
        }
        librarySortAscending = !librarySortAscending;
        updateLibraryFilters();
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
        }
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
        navigator.navigateTo(Screen.SEARCH);
        if (searchInput != null) {
            searchInput.setText(topic);
            searchInput.setSelection(topic.length());
        }
        runSearch();
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

