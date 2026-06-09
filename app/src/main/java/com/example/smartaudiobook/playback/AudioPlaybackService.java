package com.example.smartaudiobook.playback;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.smartaudiobook.MainActivity;
import com.example.smartaudiobook.R;

/**
 * Minimal foreground audio service so playback continues when app goes to background.
 *
 * This project currently doesn't have a real audio source wired to the player UI.
 * To make the "background playback" behavior testable, this service plays a quiet sine tone.
 * Replace {@link #runToneLoop()} with real audio (MediaPlayer/ExoPlayer/TTS) when available.
 */
public class AudioPlaybackService extends Service {
    public static final String ACTION_PLAY = "com.example.smartaudiobook.playback.PLAY";
    public static final String ACTION_PAUSE = "com.example.smartaudiobook.playback.PAUSE";
    public static final String ACTION_STOP = "com.example.smartaudiobook.playback.STOP";

    private static final String CHANNEL_ID = "media_playback";
    private static final int NOTIFICATION_ID = 1001;

    private final Object lock = new Object();
    private volatile boolean isRunning = false;
    private volatile boolean isPlaying = false;
    private Thread audioThread;
    private AudioTrack audioTrack;

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());

        if (ACTION_PAUSE.equals(action)) {
            pausePlayback();
            return START_STICKY;
        }

        // Default: PLAY
        startPlayback();
        return START_STICKY;
    }

    private void startPlayback() {
        synchronized (lock) {
            isPlaying = true;
            if (isRunning) {
                if (audioTrack != null) {
                    audioTrack.play();
                }
                updateNotification();
                return;
            }
            isRunning = true;
            audioTrack = createAudioTrack();
            audioTrack.play();
            audioThread = new Thread(this::runToneLoop, "AudioPlaybackService-Tone");
            audioThread.start();
            updateNotification();
        }
    }

    private void pausePlayback() {
        synchronized (lock) {
            isPlaying = false;
            if (audioTrack != null) {
                try {
                    audioTrack.pause();
                    audioTrack.flush();
                } catch (Throwable ignored) {
                }
            }
            updateNotification();
        }
    }

    private void stopPlayback() {
        synchronized (lock) {
            isPlaying = false;
            isRunning = false;
            if (audioThread != null) {
                audioThread.interrupt();
                audioThread = null;
            }
            if (audioTrack != null) {
                try {
                    audioTrack.pause();
                    audioTrack.flush();
                    audioTrack.release();
                } catch (Throwable ignored) {
                }
                audioTrack = null;
            }
        }
    }

    private void runToneLoop() {
        // 440 Hz sine wave @ low volume so it's audible but not too loud.
        final int sampleRate = 44100;
        final double freq = 440.0;
        final double twoPi = Math.PI * 2.0;
        final double amplitude = 0.08; // 0..1
        short[] buffer = new short[1024];
        int t = 0;
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            if (!isPlaying) {
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }
            for (int i = 0; i < buffer.length; i++) {
                double sample = Math.sin(twoPi * freq * (t++ / (double) sampleRate));
                buffer[i] = (short) (sample * amplitude * Short.MAX_VALUE);
            }
            AudioTrack track = audioTrack;
            if (track == null) {
                break;
            }
            track.write(buffer, 0, buffer.length);
        }
    }

    private AudioTrack createAudioTrack() {
        int sampleRate = 44100;
        int channelMask = AudioFormat.CHANNEL_OUT_MONO;
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        int minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding);
        int bufferSize = Math.max(minBuffer, sampleRate / 10);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build();
        AudioTrack track = new AudioTrack(
                attrs,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );
        track.setVolume(0.6f);
        return track;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
        if (existing != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Audio playback controls");
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        PendingIntent openApp = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag()
        );
        PendingIntent toggle = PendingIntent.getService(
                this,
                1,
                new Intent(this, AudioPlaybackService.class).setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY),
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag()
        );
        PendingIntent stop = PendingIntent.getService(
                this,
                2,
                new Intent(this, AudioPlaybackService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | pendingIntentImmutableFlag()
        );

        String title = "SmartBookAudio";
        String text = isPlaying ? "Playing" : "Paused";
        int icon = isPlaying ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openApp)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .addAction(icon, isPlaying ? "Pause" : "Play", toggle)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private int pendingIntentImmutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    @Override
    public void onDestroy() {
        stopPlayback();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

