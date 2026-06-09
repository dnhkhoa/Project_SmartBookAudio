package com.example.smartaudiobook;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;

import java.util.Locale;

public class AudioPlaybackService extends Service implements TextToSpeech.OnInitListener {
    public static final String ACTION_PLAY = "com.example.smartaudiobook.action.PLAY";
    public static final String ACTION_PAUSE = "com.example.smartaudiobook.action.PAUSE";
    public static final String ACTION_SEEK_BY = "com.example.smartaudiobook.action.SEEK_BY";
    public static final String ACTION_SEEK_TO = "com.example.smartaudiobook.action.SEEK_TO";
    public static final String ACTION_SET_SPEED = "com.example.smartaudiobook.action.SET_SPEED";
    public static final String ACTION_STATE_CHANGED = "com.example.smartaudiobook.action.STATE_CHANGED";

    public static final String EXTRA_BOOK_ID = "extra.BOOK_ID";
    public static final String EXTRA_BOOK_TITLE = "extra.BOOK_TITLE";
    public static final String EXTRA_CHAPTER_TITLE = "extra.CHAPTER_TITLE";
    public static final String EXTRA_POSITION_SEC = "extra.POSITION_SEC";
    public static final String EXTRA_DURATION_SEC = "extra.DURATION_SEC";
    public static final String EXTRA_SEEK_DELTA_SEC = "extra.SEEK_DELTA_SEC";
    public static final String EXTRA_SPEED = "extra.SPEED";
    public static final String EXTRA_IS_PLAYING = "extra.IS_PLAYING";

    private static final String CHANNEL_ID = "smart_audio_playback";
    private static final int NOTIFICATION_ID = 3751;
    private static final int DEFAULT_DURATION_SEC = 18 * 60 + 30;
    private static final String UTTERANCE_ID = "smart_audio_book_utterance";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying) {
                return;
            }
            positionSec = Math.min(durationSec, positionSec + 1);
            if (positionSec >= durationSec) {
                isPlaying = false;
                stopSpeech();
            }
            updateSessionAndNotification();
            broadcastState();
            if (isPlaying) {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    private TextToSpeech textToSpeech;
    private MediaSession mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean ttsReady;
    private boolean isPlaying;
    private int positionSec = 85;
    private int durationSec = DEFAULT_DURATION_SEC;
    private float speed = 1.0f;
    private String bookId = "";
    private String bookTitle = "Smart AudioBook";
    private String chapterTitle = "Chapter";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        textToSpeech = new TextToSpeech(this, this);
        mediaSession = new MediaSession(this, "SmartAudioBookSession");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) (pos / 1000L));
            }
        });
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            if (isPlaying) {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
            return START_STICKY;
        }

        readMetadata(intent);
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            play();
        } else if (ACTION_PAUSE.equals(action)) {
            pause();
        } else if (ACTION_SEEK_BY.equals(action)) {
            seekBy(intent.getIntExtra(EXTRA_SEEK_DELTA_SEC, 0));
        } else if (ACTION_SEEK_TO.equals(action)) {
            seekTo(intent.getIntExtra(EXTRA_POSITION_SEC, positionSec));
        } else if (ACTION_SET_SPEED.equals(action)) {
            setSpeed(intent.getFloatExtra(EXTRA_SPEED, speed));
        } else {
            updateSessionAndNotification();
            broadcastState();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onInit(int status) {
        ttsReady = status == TextToSpeech.SUCCESS;
        if (!ttsReady) {
            return;
        }
        textToSpeech.setLanguage(Locale.US);
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                if (isPlaying) {
                    handler.post(AudioPlaybackService.this::speakCurrentChapter);
                }
            }

            @Override
            public void onError(String utteranceId) {
                if (isPlaying) {
                    handler.postDelayed(AudioPlaybackService.this::speakCurrentChapter, 1200L);
                }
            }
        });
        if (isPlaying) {
            speakCurrentChapter();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (isPlaying) {
            updateSessionAndNotification();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopSpeech();
        abandonAudioFocus();
        if (mediaSession != null) {
            mediaSession.release();
        }
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private void readMetadata(Intent intent) {
        bookId = intent.getStringExtra(EXTRA_BOOK_ID) == null ? bookId : intent.getStringExtra(EXTRA_BOOK_ID);
        bookTitle = fallback(intent.getStringExtra(EXTRA_BOOK_TITLE), bookTitle);
        chapterTitle = fallback(intent.getStringExtra(EXTRA_CHAPTER_TITLE), chapterTitle);
        durationSec = Math.max(1, intent.getIntExtra(EXTRA_DURATION_SEC, durationSec));
        positionSec = clamp(intent.getIntExtra(EXTRA_POSITION_SEC, positionSec));
        speed = intent.getFloatExtra(EXTRA_SPEED, speed);
    }

    private void play() {
        isPlaying = true;
        if (!requestAudioFocus()) {
            isPlaying = false;
            broadcastState();
            return;
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        speakCurrentChapter();
        handler.removeCallbacks(progressTicker);
        handler.postDelayed(progressTicker, 1000L);
        updateSessionAndNotification();
        broadcastState();
    }

    private void pause() {
        isPlaying = false;
        handler.removeCallbacks(progressTicker);
        stopSpeech();
        abandonAudioFocus();
        updateSessionAndNotification();
        stopForeground(false);
        broadcastState();
    }

    private void seekBy(int deltaSec) {
        seekTo(positionSec + deltaSec);
    }

    private void seekTo(int targetSec) {
        positionSec = clamp(targetSec);
        if (isPlaying) {
            speakCurrentChapter();
        }
        updateSessionAndNotification();
        broadcastState();
    }

    private void setSpeed(float nextSpeed) {
        speed = Math.max(0.5f, Math.min(2.0f, nextSpeed));
        if (textToSpeech != null) {
            textToSpeech.setSpeechRate(speed);
        }
        if (isPlaying) {
            speakCurrentChapter();
        }
        updateSessionAndNotification();
        broadcastState();
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(this::handleAudioFocusChange)
                    .build();
            return audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return audioManager.requestAudioFocus(
                this::handleAudioFocusChange,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(this::handleAudioFocusChange);
        }
    }

    private void handleAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            pause();
        }
    }

    private void speakCurrentChapter() {
        if (!ttsReady || textToSpeech == null || !isPlaying) {
            return;
        }
        textToSpeech.setSpeechRate(speed);
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID);
        textToSpeech.speak(buildSpeechText(), TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID);
    }

    private String buildSpeechText() {
        String title = TextUtils.isEmpty(bookTitle) ? "Smart AudioBook" : bookTitle;
        String chapter = TextUtils.isEmpty(chapterTitle) ? "Current chapter" : chapterTitle;
        return title + ". " + chapter + ". "
                + "This audiobook is playing from a foreground media service. "
                + "You can lock the screen, leave the app, or use the notification controls and playback will continue.";
    }

    private void stopSpeech() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    private void updateSessionAndNotification() {
        if (mediaSession != null) {
            int state = isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            long actions = PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_SEEK_TO;
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setActions(actions)
                    .setState(state, positionSec * 1000L, speed)
                    .build());
        }
        Notification notification = buildNotification();
        if (isPlaying) {
            startForeground(NOTIFICATION_ID, notification);
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Action rewindAction = new Notification.Action.Builder(
                R.drawable.ic_notification_replay_15,
                "-15",
                servicePendingIntent(ACTION_SEEK_BY, -15)
        ).build();
        Notification.Action playPauseAction = new Notification.Action.Builder(
                isPlaying ? R.drawable.ic_notification_pause : R.drawable.ic_notification_play,
                isPlaying ? "Pause" : "Play",
                servicePendingIntent(isPlaying ? ACTION_PAUSE : ACTION_PLAY, 0)
        ).build();
        Notification.Action forwardAction = new Notification.Action.Builder(
                R.drawable.ic_notification_forward_15,
                "+15",
                servicePendingIntent(ACTION_SEEK_BY, 15)
        ).build();

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setContentTitle(bookTitle)
                .setContentText(chapterTitle + " • " + formatTime(positionSec) + " / " + formatTime(durationSec))
                .setSmallIcon(R.drawable.ic_notification_play)
                .setContentIntent(openPendingIntent)
                .setShowWhen(false)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .addAction(rewindAction)
                .addAction(playPauseAction)
                .addAction(forwardAction);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        return builder.build();
    }

    private PendingIntent servicePendingIntent(String action, int seekDeltaSec) {
        Intent intent = new Intent(this, AudioPlaybackService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_SEEK_DELTA_SEC, seekDeltaSec);
        intent.putExtra(EXTRA_BOOK_ID, bookId);
        intent.putExtra(EXTRA_BOOK_TITLE, bookTitle);
        intent.putExtra(EXTRA_CHAPTER_TITLE, chapterTitle);
        intent.putExtra(EXTRA_POSITION_SEC, positionSec);
        intent.putExtra(EXTRA_DURATION_SEC, durationSec);
        intent.putExtra(EXTRA_SPEED, speed);
        return PendingIntent.getService(
                this,
                action.hashCode() + seekDeltaSec,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private void broadcastState() {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_BOOK_ID, bookId);
        intent.putExtra(EXTRA_BOOK_TITLE, bookTitle);
        intent.putExtra(EXTRA_CHAPTER_TITLE, chapterTitle);
        intent.putExtra(EXTRA_POSITION_SEC, positionSec);
        intent.putExtra(EXTRA_DURATION_SEC, durationSec);
        intent.putExtra(EXTRA_SPEED, speed);
        intent.putExtra(EXTRA_IS_PLAYING, isPlaying);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.playback_channel_description));
        channel.setShowBadge(false);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private int clamp(int seconds) {
        return Math.max(0, Math.min(durationSec, seconds));
    }

    private static String fallback(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
}
