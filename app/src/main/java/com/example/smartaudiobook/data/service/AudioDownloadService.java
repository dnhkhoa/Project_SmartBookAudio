package com.example.smartaudiobook.data.service;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class AudioDownloadService {
    private static final String AUDIO_CACHE_DIR = "audio-cache";

    private final Context appContext;

    public AudioDownloadService(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public DownloadResult downloadToCache(String bookId, String sourceUrl) throws Exception {
        File cacheFile = getAudioCacheFile(bookId, sourceUrl);
        if (!isCachedAudioReady(cacheFile)) {
            downloadAudioToCacheWithRetry(sourceUrl, cacheFile);
        }
        return new DownloadResult(cacheFile, getLocalCacheKey(bookId, sourceUrl));
    }

    public File getAudioCacheFile(String bookId, String sourceUrl) {
        return new File(getAudioCacheDir(), getLocalCacheKey(bookId, sourceUrl));
    }

    public File getAudioCacheFileByKey(String localCacheKey) {
        if (localCacheKey == null || localCacheKey.trim().isEmpty()) {
            return null;
        }
        String cleanedKey = localCacheKey.trim().replace('\\', '/');
        int lastSeparator = cleanedKey.lastIndexOf('/');
        if (lastSeparator >= 0) {
            cleanedKey = cleanedKey.substring(lastSeparator + 1);
        }
        cleanedKey = cleanedKey.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleanedKey.isEmpty()) {
            return null;
        }
        if (".".equals(cleanedKey) || "..".equals(cleanedKey)) {
            return null;
        }
        return new File(getAudioCacheDir(), cleanedKey);
    }

    public boolean deleteCachedAudio(String localCacheKey) {
        File cachedAudio = getAudioCacheFileByKey(localCacheKey);
        return cachedAudio == null || !cachedAudio.exists() || cachedAudio.delete();
    }

    public String getLocalCacheKey(String bookId, String sourceUrl) {
        String normalizedBookId = bookId == null ? "unknown" : bookId
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalizedBookId.isEmpty()) {
            normalizedBookId = "unknown";
        }
        String sourceHash = Integer.toHexString(sourceUrl == null ? 0 : sourceUrl.hashCode());
        return normalizedBookId + "-" + sourceHash + getAudioFileExtension(sourceUrl == null ? "" : sourceUrl);
    }

    public boolean isCachedAudioReady(File audioFile) {
        return audioFile != null && audioFile.exists() && audioFile.isFile() && audioFile.length() > 0;
    }

    private File getAudioCacheDir() {
        return new File(appContext.getFilesDir(), AUDIO_CACHE_DIR);
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

    public static class DownloadResult {
        public final File audioFile;
        public final String localCacheKey;

        public DownloadResult(File audioFile, String localCacheKey) {
            this.audioFile = audioFile;
            this.localCacheKey = localCacheKey;
        }
    }
}
