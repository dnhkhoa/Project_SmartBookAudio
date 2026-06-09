package com.example.smartaudiobook;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import java.util.Locale;

public class AudioPlaybackService extends Service {
    public static final String ACTION_PLAY = "com.example.smartaudiobook.action.PLAY";
    public static final String ACTION_PAUSE = "com.example.smartaudiobook.action.PAUSE";
    public static final String ACTION_SEEK_BY = "com.example.smartaudiobook.action.SEEK_BY";
    public static final String ACTION_SEEK_TO = "com.example.smartaudiobook.action.SEEK_TO";
    public static final String ACTION_SET_SPEED = "com.example.smartaudiobook.action.SET_SPEED";
    public static final String ACTION_SYNC_STATE = "com.example.smartaudiobook.action.SYNC_STATE";
    public static final String ACTION_STATE_CHANGED = "com.example.smartaudiobook.action.STATE_CHANGED";

    public static final String EXTRA_BOOK_ID = "extra.BOOK_ID";
    public static final String EXTRA_BOOK_TITLE = "extra.BOOK_TITLE";
    public static final String EXTRA_CHAPTER_TITLE = "extra.CHAPTER_TITLE";
    public static final String EXTRA_POSITION_SEC = "extra.POSITION_SEC";
    public static final String EXTRA_DURATION_SEC = "extra.DURATION_SEC";
    public static final String EXTRA_SEEK_DELTA_SEC = "extra.SEEK_DELTA_SEC";
    public static final String EXTRA_SPEED = "extra.SPEED";
    public static final String EXTRA_IS_PLAYING = "extra.IS_PLAYING";
    public static final String EXTRA_FROM_NOTIFICATION = "extra.FROM_NOTIFICATION";

    private static final String CHANNEL_ID = "smart_audio_playback";
    private static final int NOTIFICATION_ID = 3751;
    private static final int DEFAULT_DURATION_SEC = 18 * 60 + 30;

    private MediaSession mediaSession;
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
        mediaSession = new MediaSession(this, "SmartAudioBookSession");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                isPlaying = true;
                publishState();
            }

            @Override
            public void onPause() {
                isPlaying = false;
                publishState();
            }

            @Override
            public void onSeekTo(long pos) {
                positionSec = clamp((int) (pos / 1000L));
                publishState();
            }
        });
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            updateSessionAndNotification();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        boolean fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false);
        readMetadata(intent, !fromNotification);
        if (ACTION_PLAY.equals(action)) {
            isPlaying = true;
        } else if (ACTION_PAUSE.equals(action)) {
            isPlaying = false;
        } else if (ACTION_SEEK_BY.equals(action)) {
            positionSec = clamp(positionSec + intent.getIntExtra(EXTRA_SEEK_DELTA_SEC, 0));
        } else if (ACTION_SEEK_TO.equals(action)) {
            positionSec = clamp(intent.getIntExtra(EXTRA_POSITION_SEC, positionSec));
        } else if (ACTION_SET_SPEED.equals(action)) {
            speed = Math.max(0.5f, Math.min(2.0f, intent.getFloatExtra(EXTRA_SPEED, speed)));
        } else if (ACTION_SYNC_STATE.equals(action)) {
            positionSec = clamp(intent.getIntExtra(EXTRA_POSITION_SEC, positionSec));
        }
        publishState();
        return isPlaying ? START_STICKY : START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
        }
        super.onDestroy();
    }

    private void readMetadata(Intent intent, boolean includePlaybackState) {
        bookId = intent.getStringExtra(EXTRA_BOOK_ID) == null ? bookId : intent.getStringExtra(EXTRA_BOOK_ID);
        bookTitle = fallback(intent.getStringExtra(EXTRA_BOOK_TITLE), bookTitle);
        chapterTitle = fallback(intent.getStringExtra(EXTRA_CHAPTER_TITLE), chapterTitle);
        durationSec = Math.max(1, intent.getIntExtra(EXTRA_DURATION_SEC, durationSec));
        if (includePlaybackState) {
            positionSec = clamp(intent.getIntExtra(EXTRA_POSITION_SEC, positionSec));
            speed = intent.getFloatExtra(EXTRA_SPEED, speed);
            isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying);
        }
    }

    private void publishState() {
        updateSessionAndNotification();
        broadcastState();
    }

    private void updateSessionAndNotification() {
        if (mediaSession != null) {
            int state = isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            long actions = PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_REWIND
                    | PlaybackState.ACTION_FAST_FORWARD
                    | PlaybackState.ACTION_SEEK_TO;
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setActions(actions)
                    .setState(state, positionSec * 1000L, speed)
                    .build());
            mediaSession.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, bookTitle)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, chapterTitle)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, getString(R.string.app_name))
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, durationSec * 1000L)
                    .build());
        }

        Notification notification = buildNotification();
        if (isPlaying) {
            startForeground(NOTIFICATION_ID, notification);
            return;
        }
        stopForeground(false);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
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
                .setContentText(chapterTitle + " - " + formatTime(positionSec) + " / " + formatTime(durationSec))
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
            builder.setCategory(Notification.CATEGORY_TRANSPORT);
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
        intent.putExtra(EXTRA_IS_PLAYING, isPlaying);
        intent.putExtra(EXTRA_FROM_NOTIFICATION, true);
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
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
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
