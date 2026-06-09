package com.example.smartaudiobook;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        FULL_PLAYER
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
    private static final String DYNAMIC_LIBRARY_ITEM_TAG = "dynamic_library_item";
    private static final String DEFAULT_PLAYABLE_AUDIO_URL = "https://www.gutenberg.org/files/23937/mp3/23937-01.mp3";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 731;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService audioDownloadExecutor = Executors.newSingleThreadExecutor();
    private final Runnable playerProgressRunnable = new Runnable() {
        @Override
        public void run() {
            syncPositionFromMediaPlayer();
            if (isPlaying) {
                handler.postDelayed(this, 1000);
            }
        }
    };
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
    private boolean isPlaying = false;
    private boolean isPlayerPreparing = false;
    private boolean isSourceUrlLoading = false;
    private boolean isAudioDownloading = false;
    private boolean playbackNotificationActive = false;
    private String activeUid = "";
    private String activeAccountEmail = "";
    private String selectedBookId = "";
    private String selectedBookTitle = "";
    private String selectedSourceUrl = "";
    private String preparedSourceUrl = "";
    private String downloadingSourceUrl = "";
    private int mediaDurationSeconds = 0;
    private MediaPlayer mediaPlayer;
    private final List<Chapter> playerQueue = new ArrayList<>();
    private int currentChapterIndex = 0;
    private final Set<String> libraryBookIds = new HashSet<>();
    private final Map<String, String> libraryStatuses = new HashMap<>();
    private final Map<String, View> dynamicLibraryViews = new HashMap<>();
    private boolean playbackReceiverRegistered;
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // If denied, foreground service may still run but notification can be blocked on newer Android.
            });
    private SharedPreferences authPreferences;
    private final List<BookSummary> loadedLibraryBooks = new ArrayList<>();

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
        releaseMediaPlayer();
        audioDownloadExecutor.shutdownNow();
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
        findViewById(R.id.libraryCreateCard).setOnClickListener(v -> showCreateAudioBookDialog());
        hideStaticLibraryItems();
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
        findViewById(R.id.menuDefaultVoice).setOnClickListener(v ->
                updateProfilePreference("defaultVoice", "Natural voice", "Default voice updated"));
        findViewById(R.id.menuAppLanguage).setOnClickListener(v ->
                updateProfilePreference("appLanguage", "Vietnamese", "Language preference updated"));
        findViewById(R.id.menuDataStorage).setOnClickListener(v ->
                updateProfilePreference("storageMode", "Offline first", "Storage preference updated"));
        findViewById(R.id.menuHelpSupport).setOnClickListener(v -> showProfileAction(getString(R.string.profile_help_support)));
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
        if (TextUtils.isEmpty(selectedBookId)) {
            openSavedPlaybackOrFirstLibraryBook();
            return;
        }
        openFullPlayer(selectedBookId);
    }

    private void openFullPlayer(String bookId) {
        openFullPlayer(bookId, "");
    }

    private void openFullPlayer(String bookId, String sourceUrl) {
        if (TextUtils.isEmpty(bookId)) {
            openSavedPlaybackOrFirstLibraryBook();
            return;
        }
        if (!selectedBookId.equals(bookId)) {
            releaseMediaPlayer();
            mediaDurationSeconds = 0;
            playerPositionSeconds = 0;
        }
        selectedBookId = bookId;
        selectedSourceUrl = sourceUrl == null ? "" : sourceUrl;
        ensureAuthenticated(() -> userLibraryService.markOpened(activeUid, selectedBookId, getCurrentChapterId(), playerPositionSeconds));
        syncBackgroundPlayback();
        navigator.navigateTo(Screen.FULL_PLAYER);
    }

    private void openSavedPlaybackOrFirstLibraryBook() {
        ensureAuthenticated(() -> playbackStateService.loadCurrent(activeUid, new FirestoreCallback<PlayerState>() {
            @Override
            public void onSuccess(PlayerState state) {
                if (state != null && !TextUtils.isEmpty(state.bookId)) {
                    selectedBookTitle = state.bookTitle;
                    playerPositionSeconds = state.positionSec;
                    playbackSpeedIndex = findPlaybackSpeedIndex(state.speed);
                    openFullPlayer(state.bookId);
                    return;
                }
                openFirstLibraryBook();
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "PLAYBACK_OPEN_FAIL uid=" + activeUid, error);
                openFirstLibraryBook();
            }
        }));
    }

    private void openFirstLibraryBook() {
        userLibraryService.listLibrary(activeUid, new FirestoreCallback<List<LibraryEntry>>() {
            @Override
            public void onSuccess(List<LibraryEntry> entries) {
                if (entries.isEmpty()) {
                    showToast("Library is empty. Create or save a book first.");
                    return;
                }
                openFullPlayer(entries.get(0).bookId);
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "LIBRARY_OPEN_FIRST_FAIL uid=" + activeUid, error);
                showToast("Library load failed");
            }
        });
    }

    private void showFullPlayer() {
        currentScreen = Screen.FULL_PLAYER;
        preparePlayerWindow();
        setContentView(R.layout.activity_full_player);
        findViewById(R.id.btnBackFullPlayer).setOnClickListener(v -> navigator.goBack());
        findViewById(R.id.btnFullPlayerRewind).setOnClickListener(v -> seekPlayerBy(-15));
        findViewById(R.id.btnFullPlayerPlayPause).setOnClickListener(v -> handlePlayPause());
        findViewById(R.id.btnFullPlayerForward).setOnClickListener(v -> seekPlayerBy(15));
        findViewById(R.id.btnFullPlayerSpeed).setOnClickListener(v -> cyclePlaybackSpeed());
        findViewById(R.id.btnFullPlayerChapter).setOnClickListener(v -> showToast("Chapter list selected"));
        findViewById(R.id.fullPlayerProgress).setOnTouchListener((view, event) -> handleProgressTouch(view, event));
        updateFullPlayerUi();
        loadPlayerQueue();
        refreshPlaybackServiceIfActive();
    }

    private boolean handleProgressTouch(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
            int width = view.getWidth();
            if (width > 0) {
                float fraction = Math.max(0f, Math.min(1f, event.getX() / width));
                playerPositionSeconds = clampPlayerPosition((int) (getCurrentDurationSec() * fraction));
                seekMediaPlayerTo(playerPositionSeconds);
                updateFullPlayerUi();
                if (shouldSyncPlaybackService()) {
                    startPlaybackService(AudioPlaybackService.ACTION_SEEK_TO);
                }
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
        seekMediaPlayerTo(playerPositionSeconds);
        updateVisiblePlayerUi();
        if (shouldSyncPlaybackService()) {
            startPlaybackService(AudioPlaybackService.ACTION_SEEK_TO);
        }
        savePlaybackState();
        showToast("Position " + formatTime(playerPositionSeconds));
    }

    private int clampPlayerPosition(int seconds) {
        return Math.max(0, Math.min(getCurrentDurationSec(), seconds));
    }

    private void handlePlayPause() {
        String playableUrl = getLoadedPlayableSourceUrl();
        if (TextUtils.isEmpty(playableUrl)) {
            resolveSourceUrlAndPlay();
            return;
        }
        if (isPlayerPreparing) {
            showToast("Audio is loading");
            return;
        }
        if (isAudioDownloading) {
            showToast("Audio is downloading");
            return;
        }
        selectedSourceUrl = playableUrl;
        if (mediaPlayer == null || !playableUrl.equals(preparedSourceUrl)) {
            prepareAndPlayAudio(playableUrl);
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            handler.removeCallbacks(playerProgressRunnable);
            syncPlayerUi();
            startPlaybackService(AudioPlaybackService.ACTION_PAUSE);
            savePlaybackState();
            showToast("Paused");
            return;
        }
        startPreparedAudio();
    }

    private String getLoadedPlayableSourceUrl() {
        if (isPlayableAudioUrl(selectedSourceUrl)) {
            return selectedSourceUrl;
        }
        Chapter chapter = getCurrentChapter();
        if (chapter != null && isPlayableAudioUrl(chapter.audioUrl)) {
            return chapter.audioUrl;
        }
        return "";
    }

    private void resolveSourceUrlAndPlay() {
        if (isSourceUrlLoading) {
            showToast("Audio is loading");
            return;
        }
        isSourceUrlLoading = true;
        syncPlayerUi();
        showToast("Loading audio");
        String resolvingBookId = selectedBookId;
        bookCatalogService.fetchPlayableAudioUrl(resolvingBookId, new FirestoreCallback<String>() {
            @Override
            public void onSuccess(String sourceUrl) {
                if (!resolvingBookId.equals(selectedBookId) || !isPlayerScreen()) {
                    isSourceUrlLoading = false;
                    syncPlayerUi();
                    return;
                }
                isSourceUrlLoading = false;
                selectedSourceUrl = isPlayableAudioUrl(sourceUrl) ? sourceUrl : DEFAULT_PLAYABLE_AUDIO_URL;
                syncPlayerUi();
                prepareAndPlayAudio(selectedSourceUrl);
            }

            @Override
            public void onError(Exception error) {
                if (!resolvingBookId.equals(selectedBookId) || !isPlayerScreen()) {
                    isSourceUrlLoading = false;
                    syncPlayerUi();
                    return;
                }
                Log.e(FIRESTORE_TAG, "AUDIO_SOURCE_RESOLVE_FAIL bookId=" + resolvingBookId, error);
                isSourceUrlLoading = false;
                selectedSourceUrl = DEFAULT_PLAYABLE_AUDIO_URL;
                syncPlayerUi();
                prepareAndPlayAudio(selectedSourceUrl);
            }
        });
    }

    private void prepareAndPlayAudio(String sourceUrl) {
        if (!isPlayableAudioUrl(sourceUrl)) {
            sourceUrl = DEFAULT_PLAYABLE_AUDIO_URL;
            selectedSourceUrl = sourceUrl;
        }
        File cachedAudio = getAudioCacheFile(selectedBookId, sourceUrl);
        if (isCachedAudioReady(cachedAudio)) {
            prepareAndPlayCachedAudio(sourceUrl, cachedAudio);
            return;
        }
        downloadAudioThenPlay(sourceUrl, cachedAudio);
    }

    private void downloadAudioThenPlay(String sourceUrl, File cacheFile) {
        if (isAudioDownloading && sourceUrl.equals(downloadingSourceUrl)) {
            showToast("Audio is downloading");
            return;
        }
        final String downloadBookId = selectedBookId;
        final String downloadSourceUrl = sourceUrl;
        isAudioDownloading = true;
        downloadingSourceUrl = downloadSourceUrl;
        syncPlayerUi();
        showToast("Downloading audio");

        audioDownloadExecutor.execute(() -> {
            Exception failure = null;
            try {
                downloadAudioToCacheWithRetry(downloadSourceUrl, cacheFile);
            } catch (Exception error) {
                failure = error;
            }

            Exception finalFailure = failure;
            handler.post(() -> {
                if (!downloadBookId.equals(selectedBookId) || !downloadSourceUrl.equals(downloadingSourceUrl) || !isPlayerScreen()) {
                    isAudioDownloading = false;
                    downloadingSourceUrl = "";
                    syncPlayerUi();
                    return;
                }
                isAudioDownloading = false;
                downloadingSourceUrl = "";
                syncPlayerUi();
                if (finalFailure != null) {
                    Log.e(FIRESTORE_TAG, "AUDIO_DOWNLOAD_FAIL url=" + downloadSourceUrl, finalFailure);
                    showToast("Audio download failed");
                    return;
                }
                prepareAndPlayCachedAudio(downloadSourceUrl, cacheFile);
            });
        });
    }

    private void prepareAndPlayCachedAudio(String sourceUrl, File audioFile) {
        final String playbackSourceUrl = sourceUrl;
        releaseMediaPlayer();
        isPlayerPreparing = true;
        syncPlayerUi();
        showToast("Loading local audio");
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.setOnPreparedListener(player -> {
                isPlayerPreparing = false;
                preparedSourceUrl = playbackSourceUrl;
                mediaDurationSeconds = Math.max(0, player.getDuration() / 1000);
                seekMediaPlayerTo(playerPositionSeconds);
                startPreparedAudio();
            });
            mediaPlayer.setOnCompletionListener(player -> {
                isPlaying = false;
                handler.removeCallbacks(playerProgressRunnable);
                playerPositionSeconds = clampPlayerPosition(getCurrentDurationSec());
                syncPlayerUi();
                startPlaybackService(AudioPlaybackService.ACTION_PAUSE);
                savePlaybackState();
                showToast("Finished");
            });
            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                Log.e(FIRESTORE_TAG, "AUDIO_PLAYBACK_FAIL what=" + what + " extra=" + extra + " url=" + playbackSourceUrl);
                releaseMediaPlayer();
                syncPlayerUi();
                showToast("Audio playback failed");
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception error) {
            Log.e(FIRESTORE_TAG, "AUDIO_PREPARE_FAIL url=" + sourceUrl + " file=" + audioFile.getAbsolutePath(), error);
            releaseMediaPlayer();
            syncPlayerUi();
            showToast("Cannot load audio");
        }
    }

    private File getAudioCacheFile(String bookId, String sourceUrl) {
        File audioDir = new File(getFilesDir(), "audio-cache");
        String normalizedBookId = bookId == null ? "unknown" : bookId
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (TextUtils.isEmpty(normalizedBookId)) {
            normalizedBookId = "unknown";
        }
        String sourceHash = Integer.toHexString(sourceUrl.hashCode());
        return new File(audioDir, normalizedBookId + "-" + sourceHash + getAudioFileExtension(sourceUrl));
    }

    private boolean isCachedAudioReady(File audioFile) {
        return audioFile.exists() && audioFile.isFile() && audioFile.length() > 0;
    }

    private String getAudioFileExtension(String sourceUrl) {
        String normalized = sourceUrl.toLowerCase(Locale.US);
        int queryStart = normalized.indexOf('?');
        if (queryStart >= 0) {
            normalized = normalized.substring(0, queryStart);
        }
        String[] extensions = {".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".m3u8"};
        for (String extension : extensions) {
            if (normalized.endsWith(extension)) {
                return extension;
            }
        }
        return ".audio";
    }

    private void downloadAudioToCacheWithRetry(String sourceUrl, File cacheFile) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                downloadAudioToCache(sourceUrl, cacheFile);
                return;
            } catch (Exception error) {
                lastFailure = error;
                Log.e(FIRESTORE_TAG, "AUDIO_DOWNLOAD_ATTEMPT_FAIL attempt=" + attempt + " url=" + sourceUrl, error);
                File partialFile = new File(cacheFile.getAbsolutePath() + ".download");
                if (partialFile.exists()) {
                    partialFile.delete();
                }
            }
        }
        throw lastFailure == null ? new IllegalStateException("Audio download failed") : lastFailure;
    }

    private void downloadAudioToCache(String sourceUrl, File cacheFile) throws Exception {
        File parent = cacheFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create audio cache directory");
        }

        File tempFile = new File(cacheFile.getAbsolutePath() + ".download");
        if (tempFile.exists() && !tempFile.delete()) {
            throw new IllegalStateException("Cannot reset partial audio download");
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("User-Agent", "SmartAudioBook/1.0 Android");
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("Audio download HTTP " + responseCode);
            }

            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(tempFile, false)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Audio download cancelled");
                    }
                    output.write(buffer, 0, read);
                }
            }

            if (tempFile.length() == 0) {
                throw new IllegalStateException("Downloaded audio file is empty");
            }
            if (cacheFile.exists() && !cacheFile.delete()) {
                throw new IllegalStateException("Cannot replace cached audio");
            }
            if (!tempFile.renameTo(cacheFile)) {
                throw new IllegalStateException("Cannot finalize cached audio");
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (tempFile.exists() && !tempFile.equals(cacheFile)) {
                tempFile.delete();
            }
        }
    }

    private void startPreparedAudio() {
        if (mediaPlayer == null) {
            return;
        }
        try {
            mediaPlayer.start();
            applyPlaybackSpeed();
            isPlaying = true;
            syncPositionFromMediaPlayer();
            handler.removeCallbacks(playerProgressRunnable);
            handler.postDelayed(playerProgressRunnable, 1000);
            syncPlayerUi();
            startPlaybackService(AudioPlaybackService.ACTION_PLAY);
            savePlaybackState();
            showToast("Playing");
        } catch (Exception error) {
            Log.e(FIRESTORE_TAG, "AUDIO_START_FAIL url=" + selectedSourceUrl, error);
            releaseMediaPlayer();
            syncPlayerUi();
            showToast("Cannot play audio");
        }
    }

    private void seekMediaPlayerTo(int seconds) {
        if (mediaPlayer == null || isPlayerPreparing) {
            return;
        }
        try {
            mediaPlayer.seekTo(clampPlayerPosition(seconds) * 1000);
        } catch (Exception error) {
            Log.e(FIRESTORE_TAG, "AUDIO_SEEK_FAIL", error);
        }
    }

    private void syncPositionFromMediaPlayer() {
        if (mediaPlayer == null || isPlayerPreparing) {
            return;
        }
        try {
            playerPositionSeconds = clampPlayerPosition(mediaPlayer.getCurrentPosition() / 1000);
            int duration = mediaPlayer.getDuration() / 1000;
            if (duration > 0) {
                mediaDurationSeconds = duration;
            }
            syncPlayerUi();
            syncPlaybackNotificationState();
        } catch (Exception error) {
            Log.e(FIRESTORE_TAG, "AUDIO_POSITION_SYNC_FAIL", error);
        }
    }

    private void syncPlayerUi() {
        if (currentScreen == Screen.FULL_PLAYER) {
            updateFullPlayerUi();
        }
    }

    private boolean isPlayerScreen() {
        return currentScreen == Screen.FULL_PLAYER;
    }

    private void applyPlaybackSpeed() {
        if (mediaPlayer == null || isPlayerPreparing) {
            return;
        }
        try {
            PlaybackParams params = mediaPlayer.getPlaybackParams();
            params.setSpeed(getPlaybackSpeedValue());
            mediaPlayer.setPlaybackParams(params);
        } catch (Exception error) {
            Log.e(FIRESTORE_TAG, "AUDIO_SPEED_FAIL speed=" + PLAYBACK_SPEEDS[playbackSpeedIndex], error);
        }
    }

    private float getPlaybackSpeedValue() {
        String speed = PLAYBACK_SPEEDS[playbackSpeedIndex].replace("x", "");
        try {
            return Float.parseFloat(speed);
        } catch (NumberFormatException error) {
            return 1.0f;
        }
    }

    private void releaseMediaPlayer() {
        boolean wasPlaying = isPlaying;
        handler.removeCallbacks(playerProgressRunnable);
        isPlaying = false;
        isPlayerPreparing = false;
        isSourceUrlLoading = false;
        isAudioDownloading = false;
        preparedSourceUrl = "";
        downloadingSourceUrl = "";
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception error) {
                Log.e(FIRESTORE_TAG, "AUDIO_RELEASE_FAIL", error);
            }
            mediaPlayer = null;
        }
        if (wasPlaying) {
            startPlaybackService(AudioPlaybackService.ACTION_PAUSE);
        }
    }

    private void syncBackgroundPlayback() {
        if (!isPlaying) {
            return;
        }
        // Keep audio running in a foreground service when "playing" so it continues outside the app UI.
        Intent intent = new Intent(this, AudioPlaybackService.class)
                .setAction(AudioPlaybackService.ACTION_PLAY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent);
        } else {
            startService(intent);
        }
    }

    private void cyclePlaybackSpeed() {
        playbackSpeedIndex = (playbackSpeedIndex + 1) % PLAYBACK_SPEEDS.length;
        if (isPlaying && mediaPlayer != null && mediaPlayer.isPlaying()) {
            applyPlaybackSpeed();
        }
        updateFullPlayerUi();
        if (shouldSyncPlaybackService()) {
            startPlaybackService(AudioPlaybackService.ACTION_SET_SPEED);
        }
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
        playPause.setText(isPlayerPreparing || isSourceUrlLoading || isAudioDownloading ? "..." : (isPlaying ? getString(R.string.full_player_pause_icon) : ">"));
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

    private void updateVisiblePlayerUi() {
        if (currentScreen == Screen.FULL_PLAYER) {
            updateFullPlayerUi();
        }
    }

    private void startPlaybackService(String action) {
        startPlaybackService(action, 0);
    }

    private void syncPlaybackNotificationState() {
        if (shouldSyncPlaybackService()) {
            startPlaybackService(AudioPlaybackService.ACTION_SYNC_STATE);
        }
    }

    private boolean shouldSyncPlaybackService() {
        return isPlaying || playbackNotificationActive;
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
        if (AudioPlaybackService.ACTION_PLAY.equals(action)
                || AudioPlaybackService.ACTION_PAUSE.equals(action)
                || AudioPlaybackService.ACTION_SEEK_TO.equals(action)
                || AudioPlaybackService.ACTION_SEEK_BY.equals(action)
                || AudioPlaybackService.ACTION_SYNC_STATE.equals(action)
                || AudioPlaybackService.ACTION_SET_SPEED.equals(action)) {
            playbackNotificationActive = true;
        }
    }

    private boolean shouldStartForeground(String action) {
        return isPlaying
                || AudioPlaybackService.ACTION_PLAY.equals(action);
    }

    private void refreshPlaybackServiceIfActive() {
        if (currentScreen != Screen.FULL_PLAYER) {
            return;
        }
        if (isPlaying) {
            startPlaybackService(AudioPlaybackService.ACTION_PLAY);
        }
    }

    private void handlePlaybackServiceState(Intent intent) {
        if (intent == null || !AudioPlaybackService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }
        selectedBookTitle = fallback(intent.getStringExtra(AudioPlaybackService.EXTRA_BOOK_TITLE), selectedBookTitle);
        int servicePositionSeconds = clampPlayerPosition(intent.getIntExtra(
                AudioPlaybackService.EXTRA_POSITION_SEC,
                playerPositionSeconds
        ));
        boolean shouldSeekLocalPlayer = Math.abs(servicePositionSeconds - playerPositionSeconds) > 1;
        playerPositionSeconds = servicePositionSeconds;
        playbackSpeedIndex = findPlaybackSpeedIndex(intent.getFloatExtra(
                AudioPlaybackService.EXTRA_SPEED,
                getPlaybackSpeedValue()
        ));
        if (shouldSeekLocalPlayer) {
            seekMediaPlayerTo(playerPositionSeconds);
        }

        boolean shouldPlay = intent.getBooleanExtra(AudioPlaybackService.EXTRA_IS_PLAYING, isPlaying);
        if (shouldPlay) {
            resumePreparedAudioFromNotification();
        } else {
            pausePreparedAudioFromNotification();
        }
        updateVisiblePlayerUi();
    }

    private void resumePreparedAudioFromNotification() {
        if (mediaPlayer == null || isPlayerPreparing || isAudioDownloading || isSourceUrlLoading) {
            isPlaying = false;
            return;
        }
        try {
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
            applyPlaybackSpeed();
            isPlaying = true;
            handler.removeCallbacks(playerProgressRunnable);
            handler.postDelayed(playerProgressRunnable, 1000);
        } catch (Exception error) {
            Log.e(FIRESTORE_TAG, "AUDIO_NOTIFICATION_RESUME_FAIL", error);
            releaseMediaPlayer();
        }
    }

    private void pausePreparedAudioFromNotification() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            } catch (Exception error) {
                Log.e(FIRESTORE_TAG, "AUDIO_NOTIFICATION_PAUSE_FAIL", error);
            }
        }
        isPlaying = false;
        handler.removeCallbacks(playerProgressRunnable);
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
        for (Map.Entry<String, View> entry : dynamicLibraryViews.entrySet()) {
            entry.getValue().setVisibility(shouldShowLibraryBook(entry.getKey()) ? View.VISIBLE : View.GONE);
        }
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

    private void hideStaticLibraryItems() {
        int[] staticLibraryItemIds = {
                R.id.libraryItemAndroid,
                R.id.libraryItemCleanCode,
                R.id.libraryItemAiLecture,
                R.id.libraryItemEnglish
        };
        for (int viewId : staticLibraryItemIds) {
            View item = findViewById(viewId);
            if (item != null) {
                item.setVisibility(View.GONE);
            }
        }
    }

    private void renderDynamicLibraryItems(List<BookSummary> books) {
        clearDynamicLibraryItems();
        LinearLayout content = findViewById(R.id.libraryContent);
        View createCard = findViewById(R.id.libraryCreateCard);
        int insertIndex = content.indexOfChild(createCard);
        if (insertIndex < 0) {
            insertIndex = content.getChildCount();
        }

        for (BookSummary book : books) {
            View item = createDynamicLibraryItem(book);
            dynamicLibraryViews.put(book.id, item);
            content.addView(item, insertIndex++);
        }
        updateLibraryFilters();
    }

    private void clearDynamicLibraryItems() {
        LinearLayout content = findViewById(R.id.libraryContent);
        for (View item : dynamicLibraryViews.values()) {
            content.removeView(item);
        }
        dynamicLibraryViews.clear();
    }

    private View createDynamicLibraryItem(BookSummary book) {
        LinearLayout item = new LinearLayout(this);
        item.setTag(DYNAMIC_LIBRARY_ITEM_TAG);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        item.setBackgroundResource(R.drawable.bg_library_card);
        item.setElevation(dp(1));
        item.setPadding(dp(14), 0, dp(10), 0);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(84)
        );
        itemParams.topMargin = dp(16);
        item.setLayoutParams(itemParams);

        View cover = new View(this);
        cover.setBackgroundResource(pickLibraryCover(book.id));
        item.addView(cover, new LinearLayout.LayoutParams(dp(58), dp(60)));

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        columnParams.leftMargin = dp(14);
        item.addView(textColumn, columnParams);

        TextView title = new TextView(this);
        title.setText(book.title);
        title.setTextColor(getColor(R.color.text_dark));
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView meta = new TextView(this);
        meta.setText(book.author + " - URL source");
        meta.setTextColor(getColor(R.color.text_gray));
        meta.setTextSize(13);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaParams.topMargin = dp(5);
        textColumn.addView(meta, metaParams);

        TextView chip = new TextView(this);
        chip.setText(TextUtils.isEmpty(book.sourceUrl) ? "NO URL" : getString(R.string.play_label));
        chip.setTextColor(getColor(R.color.primary_blue));
        chip.setTextSize(9);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setBackgroundResource(R.drawable.bg_chip_soft);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(dp(70), dp(22));
        chipParams.topMargin = dp(5);
        textColumn.addView(chip, chipParams);

        TextView playButton = new TextView(this);
        playButton.setText(">");
        playButton.setTextColor(Color.WHITE);
        playButton.setTextSize(18);
        playButton.setTypeface(playButton.getTypeface(), android.graphics.Typeface.BOLD);
        playButton.setGravity(android.view.Gravity.CENTER);
        playButton.setBackgroundResource(R.drawable.bg_circle_teal);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        item.addView(playButton, playParams);

        View.OnClickListener openPlayer = v -> openFullPlayer(book.id, book.sourceUrl);
        item.setOnClickListener(openPlayer);
        playButton.setOnClickListener(openPlayer);
        item.setOnLongClickListener(v -> {
            removeLibraryItem(book.id);
            return true;
        });

        return item;
    }

    private int pickLibraryCover(String bookId) {
        int index = Math.abs(bookId.hashCode()) % 4;
        switch (index) {
            case 0:
                return R.drawable.bg_library_cover_blue;
            case 1:
                return R.drawable.bg_library_cover_teal;
            case 2:
                return R.drawable.bg_library_cover_purple;
            case 3:
            default:
                return R.drawable.bg_library_cover_orange;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showProfileAction(String title) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("This setting screen is ready for the next implementation step.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void updateProfilePreference(String key, String value, String successMessage) {
        ensureAuthenticated(() -> profileService.updatePreference(activeUid, key, value, new FirestoreCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                showToast(successMessage);
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "PROFILE_PREFERENCE_UPDATE_FAIL key=" + key + " uid=" + activeUid, error);
                showToast("Preference update failed");
            }
        }));
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
                View dynamicView = dynamicLibraryViews.remove(bookId);
                if (dynamicView != null && currentScreen == Screen.LIBRARY) {
                    ((LinearLayout) findViewById(R.id.libraryContent)).removeView(dynamicView);
                }
                profileService.updateLibraryStats(activeUid, libraryBookIds.size());
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
        int horizontalPadding = Math.round(24 * getResources().getDisplayMetrics().density);
        int verticalPadding = Math.round(8 * getResources().getDisplayMetrics().density);
        LinearLayout inputContainer = new LinearLayout(this);
        inputContainer.setOrientation(LinearLayout.VERTICAL);
        inputContainer.setPadding(horizontalPadding, verticalPadding, horizontalPadding, 0);

        EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setHint("Audio book title");
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        titleInput.setSelectAllOnFocus(false);
        titleInput.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        inputContainer.addView(titleInput);

        EditText sourceInput = new EditText(this);
        sourceInput.setSingleLine(true);
        sourceInput.setHint("Direct audio URL (.mp3, .m4a, .m3u8)");
        sourceInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        sourceInput.setSelectAllOnFocus(false);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int sourceTopMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        sourceParams.topMargin = sourceTopMargin;
        sourceInput.setLayoutParams(sourceParams);
        inputContainer.addView(sourceInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create Audio Book")
                .setView(inputContainer)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (TextUtils.isEmpty(title)) {
                titleInput.setError("Title is required");
                return;
            }
            String sourceUrl = sourceInput.getText().toString().trim();
            if (TextUtils.isEmpty(sourceUrl)) {
                sourceInput.setError("Audio URL is required");
                return;
            }
            if (!isValidSourceUrl(sourceUrl)) {
                sourceInput.setError("Use a direct audio URL, not YouTube");
                return;
            }
            dialog.dismiss();
            createCustomAudioBook(title, sourceUrl);
        }));
        dialog.show();
    }

    private void createCustomAudioBook(String title, String sourceUrl) {
        ensureAuthenticated(() -> bookCatalogService.createUserBook(activeUid, title, sourceUrl, new FirestoreCallback<String>() {
            @Override
            public void onSuccess(String bookId) {
                saveCreatedBookToLibrary(bookId, title);
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "CUSTOM_AUDIOBOOK_CREATE_FAIL title=" + title, error);
                showToast("Create failed");
            }
        }));
    }

    private void saveCreatedBookToLibrary(String bookId, String title) {
        userLibraryService.addBook(activeUid, bookId, LibraryEntry.STATUS_SAVED, new FirestoreCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                libraryBookIds.add(bookId);
                libraryStatuses.put(bookId, LibraryEntry.STATUS_SAVED);
                profileService.updateLibraryStats(activeUid, libraryBookIds.size());
                loadLibraryFromFirestore();
                showToast("Created: " + title);
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "CUSTOM_AUDIOBOOK_LIBRARY_ADD_FAIL bookId=" + bookId, error);
                showToast("Book created, but library add failed");
            }
        });
    }

    private boolean isValidSourceUrl(String sourceUrl) {
        return isPlayableAudioUrl(sourceUrl);
    }

    private boolean isPlayableAudioUrl(String sourceUrl) {
        if (TextUtils.isEmpty(sourceUrl)) {
            return false;
        }
        String normalized = sourceUrl.toLowerCase(Locale.US);
        boolean webUrl = normalized.startsWith("http://") || normalized.startsWith("https://");
        boolean blockedPageUrl = normalized.contains("youtube.com")
                || normalized.contains("youtu.be")
                || normalized.contains("music.youtube.com");
        boolean directAudioUrl = normalized.contains(".mp3")
                || normalized.contains(".m4a")
                || normalized.contains(".aac")
                || normalized.contains(".wav")
                || normalized.contains(".ogg")
                || normalized.contains(".opus")
                || normalized.contains(".m3u8");
        return webUrl && !blockedPageUrl && directAudioUrl;
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

    private void clearActiveAccount() {
        activeUid = "";
        activeAccountEmail = "";
        libraryBookIds.clear();
        libraryStatuses.clear();
        dynamicLibraryViews.clear();
        loadedLibraryBooks.clear();
        playerQueue.clear();
        selectedBookId = "";
        selectedBookTitle = "";
        selectedSourceUrl = "";
        currentChapterIndex = 0;
        playerPositionSeconds = 85;
        playbackSpeedIndex = 1;
        mediaDurationSeconds = 0;
        releaseMediaPlayer();
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
                profileService.updateLibraryStats(activeUid, entries.size());
                loadLibraryBookSummaries(entries);
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

    private void loadLibraryBookSummaries(List<LibraryEntry> entries) {
        List<String> bookIds = new ArrayList<>();
        for (LibraryEntry entry : entries) {
            bookIds.add(entry.bookId);
        }
        bookCatalogService.fetchBookSummaries(bookIds, new FirestoreCallback<List<BookSummary>>() {
            @Override
            public void onSuccess(List<BookSummary> books) {
                Map<String, BookSummary> booksById = new HashMap<>();
                for (BookSummary book : books) {
                    booksById.put(book.id, book);
                }
                loadedLibraryBooks.clear();
                for (String bookId : bookIds) {
                    BookSummary book = booksById.get(bookId);
                    if (book != null) {
                        loadedLibraryBooks.add(book);
                    }
                }
                if (currentScreen == Screen.LIBRARY) {
                    renderDynamicLibraryItems(loadedLibraryBooks);
                }
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "LIBRARY_BOOKS_LOAD_FAIL uid=" + activeUid, error);
                if (currentScreen == Screen.LIBRARY) {
                    loadedLibraryBooks.clear();
                    clearDynamicLibraryItems();
                    updateLibraryFilters();
                }
            }
        });
    }

    private void loadPlayerQueue() {
        String loadingBookId = selectedBookId;
        if (TextUtils.isEmpty(loadingBookId)) {
            showToast("Select a book first");
            return;
        }
        bookCatalogService.fetchBookTitle(loadingBookId, new FirestoreCallback<String>() {
            @Override
            public void onSuccess(String title) {
                if (!loadingBookId.equals(selectedBookId)) {
                    return;
                }
                selectedBookTitle = title;
                if (currentScreen == Screen.FULL_PLAYER) {
                    updateFullPlayerUi();
                }
                refreshPlaybackServiceIfActive();
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "BOOK_TITLE_LOAD_FAIL bookId=" + loadingBookId, error);
            }
        });

        bookCatalogService.fetchBookSourceUrl(loadingBookId, new FirestoreCallback<String>() {
            @Override
            public void onSuccess(String sourceUrl) {
                if (!loadingBookId.equals(selectedBookId)) {
                    return;
                }
                if (!TextUtils.isEmpty(sourceUrl)) {
                    selectedSourceUrl = sourceUrl;
                }
            }

            @Override
            public void onError(Exception error) {
                Log.e(FIRESTORE_TAG, "BOOK_SOURCE_LOAD_FAIL bookId=" + loadingBookId, error);
            }
        });

        chapterService.fetchChapters(loadingBookId, new FirestoreCallback<List<Chapter>>() {
            @Override
            public void onSuccess(List<Chapter> chapters) {
                if (!loadingBookId.equals(selectedBookId)) {
                    return;
                }
                playerQueue.clear();
                playerQueue.addAll(chapters);
                if (currentChapterIndex >= playerQueue.size()) {
                    currentChapterIndex = 0;
                }
                Chapter chapter = getCurrentChapter();
                if (TextUtils.isEmpty(selectedSourceUrl) && chapter != null && !TextUtils.isEmpty(chapter.audioUrl)) {
                    selectedSourceUrl = chapter.audioUrl;
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
                Log.e(FIRESTORE_TAG, "CHAPTER_LOAD_FAIL bookId=" + loadingBookId, error);
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
                isPlaying = mediaPlayer != null && mediaPlayer.isPlaying();
                if (currentScreen == Screen.FULL_PLAYER) {
                    updateFullPlayerUi();
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
        if (TextUtils.isEmpty(selectedBookId)) {
            return;
        }
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
        if (mediaDurationSeconds > 0) {
            return mediaDurationSeconds;
        }
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
