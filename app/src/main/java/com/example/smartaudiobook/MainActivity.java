package com.example.smartaudiobook;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.example.smartaudiobook.data.model.BookSummary;
import com.example.smartaudiobook.data.model.Chapter;
import com.example.smartaudiobook.data.model.LibraryEntry;
import com.example.smartaudiobook.data.model.PlayerState;
import com.example.smartaudiobook.data.model.UserProfile;
import com.example.smartaudiobook.data.service.AuthService;
import com.example.smartaudiobook.data.service.BookCatalogService;
import com.example.smartaudiobook.data.service.ChapterService;
import com.example.smartaudiobook.data.service.PlaybackStateService;
import com.example.smartaudiobook.data.service.ProfileService;
import com.example.smartaudiobook.data.service.UserLibraryService;
import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;

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

    private static final int DEFAULT_PLAYER_DURATION_SECONDS = 18 * 60 + 30;
    private static final String[] PLAYBACK_SPEEDS = {"0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};
    private static final String FIRESTORE_TAG = "SMARTBOOK_FIRESTORE";
    private static final String PREFS_AUTH = "smartbook_auth";
    private static final String DEFAULT_BOOK_ID = "clean-code-principles";
    private static final String DEFAULT_BOOK_TITLE = "Clean Code Principles";
    private static final String BOOK_ANDROID = "atomic-habits";
    private static final String BOOK_CLEAN_CODE = "clean-code-principles";
    private static final String BOOK_AI = "ai-for-everyone";
    private static final String BOOK_ENGLISH = "the-little-prince";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 731;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handlePlaybackServiceState(intent);
        }
    };
    private final Navigator navigator = new Navigator();
    private final AuthService authService = new AuthService();
    private final BookCatalogService bookCatalogService = new BookCatalogService();
    private final ChapterService chapterService = new ChapterService();
    private final PlaybackStateService playbackStateService = new PlaybackStateService();
    private final ProfileService profileService = new ProfileService();
    private final UserLibraryService userLibraryService = new UserLibraryService();
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private EditText searchInput;
    private LinearLayout searchResultsContainer;
    private TextView searchMatchCountView;
    private Screen currentScreen = Screen.HOME;
    private int playerPositionSeconds = 85;
    private int playbackSpeedIndex = 1;
    private int selectedLibraryFilter = 0;
    private boolean isPlaying = true;
    private boolean librarySortAscending = true;
    private String activeUid = "";
    private String activeAccountEmail = "";
    private String selectedBookId = DEFAULT_BOOK_ID;
    private String selectedBookTitle = DEFAULT_BOOK_TITLE;
    private final List<Chapter> playerQueue = new ArrayList<>();
    private int currentChapterIndex = 0;
    private final Set<String> libraryBookIds = new HashSet<>();
    private final Map<String, String> libraryStatuses = new HashMap<>();
    private boolean playbackReceiverRegistered;
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // If denied, foreground service may still run but notification can be blocked on newer Android.
            });
    private SharedPreferences authPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authPreferences = getSharedPreferences(PREFS_AUTH, MODE_PRIVATE);
        maybeRequestNotificationPermission();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!navigator.goBack()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        registerPlaybackReceiver();
        navigator.resetTo(Screen.LOGIN);

    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unregisterPlaybackReceiver();
        super.onDestroy();
    }

    private void registerPlaybackReceiver() {
        if (playbackReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(AudioPlaybackService.ACTION_STATE_CHANGED);
        ContextCompat.registerReceiver(this, playbackReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        playbackReceiverRegistered = true;
    }

    private void unregisterPlaybackReceiver() {
        if (!playbackReceiverRegistered) {
            return;
        }
        unregisterReceiver(playbackReceiver);
        playbackReceiverRegistered = false;
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
        CheckBox rememberCheckBox = findViewById(R.id.chkRemember);
        TextInputLayout emailLayout = findViewById(R.id.tilLoginEmail);
        TextInputLayout passwordLayout = findViewById(R.id.tilLoginPassword);
        boolean rememberMe = authPreferences.getBoolean("remember", false);
        rememberCheckBox.setChecked(rememberMe);
        if (rememberMe) {
            emailInput.setText(authPreferences.getString("email", ""));
            passwordInput.setText(authPreferences.getString("password", ""));
        } else {
            emailInput.setText("");
            passwordInput.setText("");
        }
        findViewById(R.id.txtForgot).setOnClickListener(v -> navigator.navigateTo(Screen.FORGOT_PASSWORD));
        findViewById(R.id.txtGoRegister).setOnClickListener(v -> navigator.navigateTo(Screen.REGISTER));
        findViewById(R.id.btnLogin).setOnClickListener(v ->
                handleLogin(emailInput, passwordInput, rememberCheckBox, emailLayout, passwordLayout));
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
        searchResultsContainer = findViewById(R.id.searchResultsContainer);
        searchMatchCountView = findViewById(R.id.txtSearchMatchCount);
        findViewById(R.id.navHome).setOnClickListener(v -> navigator.switchTab(Screen.HOME));
        findViewById(R.id.navExplore).setOnClickListener(v -> navigator.switchTab(Screen.EXPLORE));
        findViewById(R.id.navLibrary).setOnClickListener(v -> navigator.switchTab(Screen.LIBRARY));
        findViewById(R.id.navProfile).setOnClickListener(v -> navigator.switchTab(Screen.PROFILE));
        findViewById(R.id.searchIcon).setOnClickListener(v -> searchBooks());
        findViewById(R.id.btnClearSearch).setOnClickListener(v -> clearSearch());
        findViewById(R.id.recentSearchAndroid).setOnClickListener(v -> searchKeyword("android service"));
        findViewById(R.id.recentSearchMl).setOnClickListener(v -> searchKeyword("machine learning"));
        findViewById(R.id.recentSearchStudy).setOnClickListener(v -> searchKeyword("study skills"));
        findViewById(R.id.recentSearchTts).setOnClickListener(v -> searchKeyword("text to speech"));
        searchInput.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchBooks();
                return true;
            }
            return false;
        });
        renderSearchState("", new ArrayList<>());
    }

    private void showLibrary() {
        currentScreen = Screen.LIBRARY;
        prepareLightWindow();
        setContentView(R.layout.activity_library);
        findViewById(R.id.navHome).setOnClickListener(v -> navigator.switchTab(Screen.HOME));
        findViewById(R.id.navExplore).setOnClickListener(v -> navigator.switchTab(Screen.EXPLORE));
        findViewById(R.id.navProfile).setOnClickListener(v -> navigator.switchTab(Screen.PROFILE));
        findViewById(R.id.btnLibrarySort).setOnClickListener(v -> sortLibraryItems());
        findViewById(R.id.btnLibraryAdd).setOnClickListener(v -> showCreateAudioBookDialog());
        findViewById(R.id.libraryCreateCard).setOnClickListener(v -> showCreateAudioBookDialog());
        findViewById(R.id.libraryItemAndroid).setOnClickListener(v -> openBookDetail(BOOK_ANDROID));
        findViewById(R.id.libraryItemAndroidPlay).setOnClickListener(v -> openFullPlayer(BOOK_ANDROID));
        findViewById(R.id.libraryItemCleanCode).setOnClickListener(v -> openBookDetail(BOOK_CLEAN_CODE));
        findViewById(R.id.libraryItemCleanCode).setOnLongClickListener(v -> {
            removeLibraryItem(BOOK_CLEAN_CODE);
            return true;
        });
        findViewById(R.id.libraryItemEnglish).setOnClickListener(v -> openBookDetail(BOOK_ENGLISH));
        findViewById(R.id.libraryItemAiLecture).setOnClickListener(v -> openBookDetail(BOOK_AI));
        bindLibraryFilters();
        loadLibraryFromFirestore();
    }

    private void showProfile() {
        currentScreen = Screen.PROFILE;
        prepareProfileWindow();
        setContentView(R.layout.activity_profile);
        findViewById(R.id.navHome).setOnClickListener(v -> navigator.switchTab(Screen.HOME));
        findViewById(R.id.navExplore).setOnClickListener(v -> navigator.switchTab(Screen.EXPLORE));
        findViewById(R.id.navLibrary).setOnClickListener(v -> navigator.switchTab(Screen.LIBRARY));
        findViewById(R.id.btnSignOut).setOnClickListener(v -> signOut());
        bindProfileFromFirebase();
    }

    private void bindProfileFromFirebase() {
        TextView nameView = findViewById(R.id.profileName);
        TextView avatarView = findViewById(R.id.profileAvatar);
        TextView emailView = findViewById(R.id.profileEmail);

        ensureAuthenticated(() -> profileService.loadProfile(activeUid, new FirestoreCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (profile == null) {
                    nameView.setText("Guest");
                    emailView.setText("");
                    avatarView.setText("G");
                    return;
                }
                nameView.setText(profile.displayName);
                emailView.setText(profile.email);
                avatarView.setText(buildAvatarInitial(profile.displayName));
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "PROFILE_READ_FAIL uid=" + activeUid, error);
                nameView.setText("Guest");
                emailView.setText("");
                avatarView.setText("G");
            }
        }));
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
        openFullPlayer(selectedBookId);
    }

    private void openFullPlayer(String bookId) {
        selectedBookId = bookId;
        ensureAuthenticated(() -> userLibraryService.markOpened(activeUid, selectedBookId, getCurrentChapterId(), playerPositionSeconds));
        syncBackgroundPlayback();
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
        loadPlayerQueue();
        startPlaybackService(isPlaying ? AudioPlaybackService.ACTION_PLAY : AudioPlaybackService.ACTION_PAUSE);
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
        startPlaybackService(isPlaying ? AudioPlaybackService.ACTION_PLAY : AudioPlaybackService.ACTION_PAUSE);
    }

    private boolean handleProgressTouch(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
            int width = view.getWidth();
            if (width > 0) {
                float fraction = Math.max(0f, Math.min(1f, event.getX() / width));
                playerPositionSeconds = clampPlayerPosition((int) (getCurrentDurationSec() * fraction));
                updateFullPlayerUi();
                startPlaybackService(AudioPlaybackService.ACTION_SEEK_TO);
                savePlaybackState();
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
        updateVisiblePlayerUi();
        startPlaybackService(AudioPlaybackService.ACTION_SEEK_BY, deltaSeconds);
        savePlaybackState();
        showToast("Position " + formatTime(playerPositionSeconds));
    }

    private int clampPlayerPosition(int seconds) {
        return Math.max(0, Math.min(getCurrentDurationSec(), seconds));
    }

    private void togglePlayback() {
        isPlaying = !isPlaying;
        updateVisiblePlayerUi();
        startPlaybackService(isPlaying ? AudioPlaybackService.ACTION_PLAY : AudioPlaybackService.ACTION_PAUSE);
        savePlaybackState();
        syncBackgroundPlayback();
        showToast(isPlaying ? "Playing" : "Paused");
    }

    private void syncBackgroundPlayback() {
        // Keep audio running in a foreground service when "playing" so it continues outside the app UI.
        Intent intent = new Intent(this, AudioPlaybackService.class)
                .setAction(isPlaying ? AudioPlaybackService.ACTION_PLAY : AudioPlaybackService.ACTION_PAUSE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent);
        } else {
            startService(intent);
        }
    }

    private void cyclePlaybackSpeed() {
        playbackSpeedIndex = (playbackSpeedIndex + 1) % PLAYBACK_SPEEDS.length;
        updateFullPlayerUi();
        startPlaybackService(AudioPlaybackService.ACTION_SET_SPEED);
        savePlaybackState();
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

        ((TextView) findViewById(R.id.fullPlayerBookTitle)).setText(selectedBookTitle);
        elapsed.setText(formatTime(playerPositionSeconds));
        duration.setText(formatTime(getCurrentDurationSec()));
        playPause.setText(isPlaying ? getString(R.string.full_player_pause_icon) : ">");
        speed.setText(PLAYBACK_SPEEDS[playbackSpeedIndex]);
        chapter.setText(getCurrentChapterTitle());

        progress.post(() -> {
            int progressWidth = progress.getWidth();
            if (progressWidth <= 0) {
                return;
            }
            float fraction = getCurrentDurationSec() == 0 ? 0f : (float) playerPositionSeconds / getCurrentDurationSec();
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
        TextView title = findViewById(R.id.backgroundAudioTitle);
        TextView chapter = findViewById(R.id.backgroundAudioChapter);
        playPause.setText(isPlaying ? getString(R.string.full_player_pause_icon) : ">");
        title.setText(selectedBookTitle);
        chapter.setText(getCurrentChapterTitle() + " - " + formatTime(playerPositionSeconds));
    }

    private void updateVisiblePlayerUi() {
        if (currentScreen == Screen.FULL_PLAYER) {
            updateFullPlayerUi();
        } else if (currentScreen == Screen.BACKGROUND_POPUP) {
            updateBackgroundPopupUi();
        }
    }

    private void startPlaybackService(String action) {
        startPlaybackService(action, 0);
    }

    private void startPlaybackService(String action, int seekDeltaSeconds) {
        if (AudioPlaybackService.ACTION_PLAY.equals(action)) {
            requestNotificationPermissionIfNeeded();
        }
        Intent intent = new Intent(this, AudioPlaybackService.class);
        intent.setAction(action);
        intent.putExtra(AudioPlaybackService.EXTRA_BOOK_ID, selectedBookId);
        intent.putExtra(AudioPlaybackService.EXTRA_BOOK_TITLE, selectedBookTitle);
        intent.putExtra(AudioPlaybackService.EXTRA_CHAPTER_TITLE, getCurrentChapterTitle());
        intent.putExtra(AudioPlaybackService.EXTRA_POSITION_SEC, playerPositionSeconds);
        intent.putExtra(AudioPlaybackService.EXTRA_DURATION_SEC, getCurrentDurationSec());
        intent.putExtra(AudioPlaybackService.EXTRA_SPEED, getPlaybackSpeedValue());
        intent.putExtra(AudioPlaybackService.EXTRA_IS_PLAYING, isPlaying);
        intent.putExtra(AudioPlaybackService.EXTRA_SEEK_DELTA_SEC, seekDeltaSeconds);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shouldStartForeground(action)) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private boolean shouldStartForeground(String action) {
        return isPlaying
                || AudioPlaybackService.ACTION_PLAY.equals(action);
    }

    private void refreshPlaybackServiceIfActive() {
        if (currentScreen != Screen.FULL_PLAYER && currentScreen != Screen.BACKGROUND_POPUP) {
            return;
        }
        startPlaybackService(isPlaying ? AudioPlaybackService.ACTION_PLAY : AudioPlaybackService.ACTION_PAUSE);
    }

    private void handlePlaybackServiceState(Intent intent) {
        if (intent == null || !AudioPlaybackService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }
        selectedBookTitle = fallback(intent.getStringExtra(AudioPlaybackService.EXTRA_BOOK_TITLE), selectedBookTitle);
        playerPositionSeconds = clampPlayerPosition(intent.getIntExtra(
                AudioPlaybackService.EXTRA_POSITION_SEC,
                playerPositionSeconds
        ));
        isPlaying = intent.getBooleanExtra(AudioPlaybackService.EXTRA_IS_PLAYING, isPlaying);
        playbackSpeedIndex = findPlaybackSpeedIndex(intent.getFloatExtra(
                AudioPlaybackService.EXTRA_SPEED,
                getPlaybackSpeedValue()
        ));
        updateVisiblePlayerUi();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
    }

    private float getPlaybackSpeedValue() {
        String speed = PLAYBACK_SPEEDS[playbackSpeedIndex].replace("x", "");
        try {
            return Float.parseFloat(speed);
        } catch (NumberFormatException ignored) {
            return 1.0f;
        }
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
        setLibraryItemVisibility(R.id.libraryItemAndroid, shouldShowLibraryBook(BOOK_ANDROID));
        setLibraryItemVisibility(R.id.libraryItemCleanCode, shouldShowLibraryBook(BOOK_CLEAN_CODE));
        setLibraryItemVisibility(R.id.libraryItemAiLecture, shouldShowLibraryBook(BOOK_AI));
        setLibraryItemVisibility(R.id.libraryItemEnglish, shouldShowLibraryBook(BOOK_ENGLISH));
    }

    private boolean shouldShowLibraryBook(String bookId) {
        if (!libraryBookIds.contains(bookId)) {
            return false;
        }
        String status = libraryStatuses.get(bookId);
        if (status == null) {
            status = LibraryEntry.STATUS_SAVED;
        }
        switch (selectedLibraryFilter) {
            case 1:
                return LibraryEntry.STATUS_SAVED.equals(status);
            case 2:
                return LibraryEntry.STATUS_DOWNLOADING.equals(status);
            case 3:
                return LibraryEntry.STATUS_FINISHED.equals(status);
            case 0:
            default:
                return true;
        }
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

    private void openBookDetail(String bookId) {
        selectedBookId = bookId;
        ensureAuthenticated(() -> userLibraryService.markOpened(activeUid, selectedBookId, getCurrentChapterId(), playerPositionSeconds));
        navigator.navigateTo(Screen.DETAIL);
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

    private void handleLogin(EditText emailInput, EditText passwordInput, CheckBox rememberCheckBox,
                             TextInputLayout emailLayout, TextInputLayout passwordLayout) {
        boolean valid = validateEmailField(emailInput, emailLayout) & validateRequired(passwordInput, passwordLayout, "Password is required");
        if (!valid) {
            Toast.makeText(this, "Login failed. Please check your input.", Toast.LENGTH_SHORT).show();
            return;
        }
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        authService.login(email, password, new FirestoreCallback<AuthService.UserRecord>() {
            @Override
            public void onSuccess(AuthService.UserRecord user) {
                applyLoggedInUser(user);
                if (rememberCheckBox.isChecked()) {
                    authPreferences.edit()
                            .putBoolean("remember", true)
                            .putString("email", user.email)
                            .putString("password", password)
                            .apply();
                } else {
                    authPreferences.edit().clear().apply();
                }
                Toast.makeText(MainActivity.this, "Login success", Toast.LENGTH_SHORT).show();
                navigator.resetTo(Screen.HOME);
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
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
        String displayName = fullNameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String documentId = buildUserDocumentId(email);

        authService.register(documentId, displayName, email, password, new FirestoreCallback<AuthService.UserRecord>() {
            @Override
            public void onSuccess(AuthService.UserRecord user) {
                applyLoggedInUser(user);
                authPreferences.edit().clear().apply();
                Toast.makeText(MainActivity.this, "Register success", Toast.LENGTH_SHORT).show();
                navigator.resetTo(Screen.HOME);
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleForgotPassword(EditText emailInput, TextInputLayout emailLayout) {
        if (!validateEmailField(emailInput, emailLayout)) {
            Toast.makeText(this, "Cannot send instruction. Invalid email.", Toast.LENGTH_SHORT).show();
            return;
        }
        String email = emailInput.getText().toString().trim();
        authService.findPasswordByEmail(email, new FirestoreCallback<String>() {
            @Override
            public void onSuccess(String password) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Your Password")
                        .setMessage("Password: " + password)
                        .setPositiveButton("OK", null)
                        .show();
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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

    private void applyLoggedInUser(AuthService.UserRecord user) {
        boolean accountChanged = !TextUtils.equals(activeUid, user.documentId);
        activeUid = user.documentId;
        activeAccountEmail = user.email;
        if (accountChanged) {
            libraryBookIds.clear();
            libraryStatuses.clear();
        }
    }

    private void signOut() {
        activeUid = "";
        activeAccountEmail = "";
        libraryBookIds.clear();
        libraryStatuses.clear();
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
        navigator.resetTo(Screen.LOGIN);
    }

    private String buildAvatarInitial(String displayName) {
        if (TextUtils.isEmpty(displayName)) {
            return "G";
        }
        return displayName.substring(0, 1).toUpperCase(Locale.US);
    }

    private void removeLibraryItem(String bookId) {
        ensureAuthenticated(() -> userLibraryService.removeBook(activeUid, bookId, new FirestoreCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                libraryBookIds.remove(bookId);
                libraryStatuses.remove(bookId);
                updateLibraryFilters();
                showToast("Removed from library");
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "LIBRARY_REMOVE_FAIL bookId=" + bookId, error);
                showToast("Remove failed");
            }
        }));
    }

    private void addLibraryItem(String bookId) {
        ensureAuthenticated(() -> userLibraryService.addBook(activeUid, bookId, LibraryEntry.STATUS_SAVED, new FirestoreCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                libraryBookIds.add(bookId);
                libraryStatuses.put(bookId, LibraryEntry.STATUS_SAVED);
                updateLibraryFilters();
                showToast("Added to library");
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "LIBRARY_ADD_FAIL bookId=" + bookId, error);
                showToast("Add failed");
            }
        }));
    }

    private void showCreateAudioBookDialog() {
        EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setHint("Audio book title");
        titleInput.setPadding(32, 16, 32, 16);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create Audio Book")
                .setView(titleInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (TextUtils.isEmpty(title)) {
                titleInput.setError("Title is required");
                return;
            }
            dialog.dismiss();
            createCustomAudioBook(title);
        }));
        dialog.show();
    }

    private void createCustomAudioBook(String title) {
        ensureAuthenticated(() -> userLibraryService.addCustomBook(activeUid, title, new FirestoreCallback<String>() {
            @Override
            public void onSuccess(String bookId) {
                libraryBookIds.add(bookId);
                libraryStatuses.put(bookId, LibraryEntry.STATUS_SAVED);
                updateLibraryFilters();
                showToast("Created: " + title);
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "CUSTOM_AUDIOBOOK_CREATE_FAIL title=" + title, error);
                showToast("Create failed");
            }
        }));
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

    private void searchBooks() {
        if (searchInput == null) {
            return;
        }
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter keyword", Toast.LENGTH_SHORT).show();
            renderSearchState("", new ArrayList<>());
            return;
        }
        Toast.makeText(this, "Searching: " + query, Toast.LENGTH_SHORT).show();

        renderSearchState("Searching...", new ArrayList<>());
        bookCatalogService.searchBooksByTitle(query, 12, new FirestoreCallback<List<BookSummary>>() {
            @Override
            public void onSuccess(List<BookSummary> books) {
                renderSearchState(query, books);
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "BOOK_SEARCH_FAIL query=" + query, error);
                showToast("Search failed");
                renderSearchState(query, new ArrayList<>());
            }
        });
    }

    private void clearSearch() {
        if (searchInput != null) {
            searchInput.setText("");
        }
        Toast.makeText(this, "Search cleared", Toast.LENGTH_SHORT).show();
        renderSearchState("", new ArrayList<>());
    }

    private void searchKeyword(String keyword) {
        if (searchInput != null) {
            searchInput.setText(keyword);
            searchInput.setSelection(keyword.length());
        }
        searchBooks();
    }

    private void openTopic(String topic) {
        navigator.navigateTo(Screen.SEARCH);
        searchKeyword(topic);
    }

    private void renderSearchState(String queryLabel, List<BookSummary> books) {
        if (searchMatchCountView != null) {
            int count = books == null ? 0 : books.size();
            if (queryLabel == null || queryLabel.trim().isEmpty()) {
                searchMatchCountView.setText("Search results");
            } else if ("Searching...".equals(queryLabel)) {
                searchMatchCountView.setText("Searching...");
            } else {
                searchMatchCountView.setText("Matches (" + count + "): " + queryLabel);
            }
        }

        if (searchResultsContainer == null) {
            return;
        }
        searchResultsContainer.removeAllViews();

        if (books == null || books.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No results");
            empty.setTextColor(getResources().getColor(R.color.text_gray));
            empty.setTextSize(12f);
            empty.setPadding(0, 18, 0, 0);
            searchResultsContainer.addView(empty);
            return;
        }

        for (BookSummary book : books) {
            View item = getLayoutInflater().inflate(R.layout.item_search_result, searchResultsContainer, false);
            TextView title = item.findViewById(R.id.title);
            TextView subtitle = item.findViewById(R.id.subtitle);
            View play = item.findViewById(R.id.playButton);

            title.setText(book.title);
            subtitle.setText("bookId: " + book.id);

            item.setOnClickListener(v -> openBookDetail(book.id));
            play.setOnClickListener(v -> openFullPlayer(book.id));

            searchResultsContainer.addView(item);
        }
    }

    private void ensureAuthenticated(Runnable onReady) {
        if (!TextUtils.isEmpty(activeUid)) {
            if (onReady != null) {
                onReady.run();
            }
            return;
        }
        showToast("Please login first");
        navigator.resetTo(Screen.LOGIN);
    }

    private String buildUserDocumentId(String email) {
        String localPart = email;
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            localPart = email.substring(0, atIndex);
        }
        String normalized = localPart
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return TextUtils.isEmpty(normalized) ? "user" : normalized;
    }

    private void loadLibraryFromFirestore() {
        ensureAuthenticated(() -> userLibraryService.listLibrary(activeUid, new FirestoreCallback<List<LibraryEntry>>() {
            @Override
            public void onSuccess(List<LibraryEntry> entries) {
                libraryBookIds.clear();
                libraryStatuses.clear();
                for (LibraryEntry entry : entries) {
                    libraryBookIds.add(entry.bookId);
                    libraryStatuses.put(entry.bookId, entry.status);
                }
                updateLibraryFilters();
                if (entries.isEmpty()) {
                    showToast("Library is empty. Tap + to save a book.");
                }
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "LIBRARY_LOAD_FAIL uid=" + activeUid, error);
                showToast("Library load failed");
            }
        }));
    }

    private void loadPlayerQueue() {
        bookCatalogService.fetchBookTitle(selectedBookId, new FirestoreCallback<String>() {
            @Override
            public void onSuccess(String title) {
                selectedBookTitle = title;
                if (currentScreen == Screen.FULL_PLAYER) {
                    updateFullPlayerUi();
                } else if (currentScreen == Screen.BACKGROUND_POPUP) {
                    updateBackgroundPopupUi();
                }
                refreshPlaybackServiceIfActive();
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "BOOK_TITLE_LOAD_FAIL bookId=" + selectedBookId, error);
            }
        });

        chapterService.fetchChapters(selectedBookId, new FirestoreCallback<List<Chapter>>() {
            @Override
            public void onSuccess(List<Chapter> chapters) {
                playerQueue.clear();
                playerQueue.addAll(chapters);
                if (currentChapterIndex >= playerQueue.size()) {
                    currentChapterIndex = 0;
                }
                playerPositionSeconds = clampPlayerPosition(playerPositionSeconds);
                loadSavedPlaybackState();
                if (currentScreen == Screen.FULL_PLAYER) {
                    updateFullPlayerUi();
                }
                refreshPlaybackServiceIfActive();
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "CHAPTER_LOAD_FAIL bookId=" + selectedBookId, error);
                showToast("Chapter load failed");
            }
        });
    }

    private void loadSavedPlaybackState() {
        ensureAuthenticated(() -> playbackStateService.loadCurrent(activeUid, new FirestoreCallback<PlayerState>() {
            @Override
            public void onSuccess(PlayerState state) {
                if (state == null || !selectedBookId.equals(state.bookId)) {
                    savePlaybackState();
                    return;
                }
                selectedBookTitle = TextUtils.isEmpty(state.bookTitle) ? selectedBookTitle : state.bookTitle;
                currentChapterIndex = findChapterIndex(state.chapterId);
                playerPositionSeconds = clampPlayerPosition(state.positionSec);
                playbackSpeedIndex = findPlaybackSpeedIndex(state.speed);
                isPlaying = state.isPlaying;
                if (currentScreen == Screen.FULL_PLAYER) {
                    updateFullPlayerUi();
                } else if (currentScreen == Screen.BACKGROUND_POPUP) {
                    updateBackgroundPopupUi();
                }
                refreshPlaybackServiceIfActive();
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "PLAYBACK_LOAD_FAIL uid=" + activeUid, error);
            }
        }));
    }

    private void savePlaybackState() {
        ensureAuthenticated(() -> {
            PlayerState state = new PlayerState(
                    selectedBookId,
                    selectedBookTitle,
                    getCurrentChapterId(),
                    getCurrentChapterTitle(),
                    playerPositionSeconds,
                    getCurrentDurationSec(),
                    PLAYBACK_SPEEDS[playbackSpeedIndex],
                    isPlaying
            );
            playbackStateService.saveCurrent(activeUid, state);
            userLibraryService.markOpened(activeUid, selectedBookId, state.chapterId, state.positionSec);
        });
    }

    private int getCurrentDurationSec() {
        Chapter chapter = getCurrentChapter();
        return chapter == null || chapter.durationSec <= 0 ? DEFAULT_PLAYER_DURATION_SECONDS : chapter.durationSec;
    }

    private String getCurrentChapterId() {
        Chapter chapter = getCurrentChapter();
        return chapter == null ? "" : chapter.id;
    }

    private String getCurrentChapterTitle() {
        Chapter chapter = getCurrentChapter();
        if (chapter == null) {
            return getString(R.string.full_player_chapter);
        }
        return "Chapter " + chapter.order + ": " + chapter.title;
    }

    private Chapter getCurrentChapter() {
        if (playerQueue.isEmpty() || currentChapterIndex < 0 || currentChapterIndex >= playerQueue.size()) {
            return null;
        }
        return playerQueue.get(currentChapterIndex);
    }

    private int findChapterIndex(String chapterId) {
        if (TextUtils.isEmpty(chapterId)) {
            return 0;
        }
        for (int i = 0; i < playerQueue.size(); i++) {
            if (chapterId.equals(playerQueue.get(i).id)) {
                return i;
            }
        }
        return 0;
    }

    private int findPlaybackSpeedIndex(String speed) {
        for (int i = 0; i < PLAYBACK_SPEEDS.length; i++) {
            if (PLAYBACK_SPEEDS[i].equals(speed)) {
                return i;
            }
        }
        return 1;
    }

    private int findPlaybackSpeedIndex(float speed) {
        int closestIndex = 1;
        float closestDistance = Float.MAX_VALUE;
        for (int i = 0; i < PLAYBACK_SPEEDS.length; i++) {
            String value = PLAYBACK_SPEEDS[i].replace("x", "");
            try {
                float candidate = Float.parseFloat(value);
                float distance = Math.abs(candidate - speed);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestIndex = i;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return closestIndex;
    }

    private static String fallback(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private static final class NoopFirestoreCallback<T> implements FirestoreCallback<T> {
        @Override
        public void onSuccess(T value) {
        }

        @Override
        public void onError(Exception error) {
        }
    }

}
