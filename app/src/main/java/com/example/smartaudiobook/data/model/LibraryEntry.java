package com.example.smartaudiobook.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class LibraryEntry {
    public static final String STATUS_SAVED = "saved";
    public static final String STATUS_DOWNLOADING = "downloading";
    public static final String STATUS_FINISHED = "finished";

    public final String bookId;
    public final String status;
    public final boolean isDownloaded;
    public final Timestamp addedAt;
    public final Timestamp downloadedAt;
    public final Timestamp lastOpenedAt;
    public final String lastChapterId;
    public final int lastPositionSec;
    public final String localCacheKey;

    public LibraryEntry(String bookId, String status, boolean isDownloaded, Timestamp addedAt,
                        Timestamp downloadedAt, Timestamp lastOpenedAt, String lastChapterId,
                        int lastPositionSec, String localCacheKey) {
        this.bookId = bookId;
        this.status = status;
        this.isDownloaded = isDownloaded;
        this.addedAt = addedAt;
        this.downloadedAt = downloadedAt;
        this.lastOpenedAt = lastOpenedAt;
        this.lastChapterId = lastChapterId;
        this.lastPositionSec = lastPositionSec;
        this.localCacheKey = localCacheKey;
    }

    public static LibraryEntry fromSnapshot(DocumentSnapshot snapshot) {
        String status = snapshot.getString("status");
        Boolean downloaded = snapshot.getBoolean("isDownloaded");
        String lastChapterId = snapshot.getString("lastChapterId");
        String localCacheKey = snapshot.getString("localCacheKey");
        Long position = snapshot.getLong("lastPositionSec");
        return new LibraryEntry(
                snapshot.getId(),
                status == null ? STATUS_SAVED : status,
                Boolean.TRUE.equals(downloaded),
                snapshot.getTimestamp("addedAt"),
                snapshot.getTimestamp("downloadedAt"),
                snapshot.getTimestamp("lastOpenedAt"),
                lastChapterId == null ? "" : lastChapterId,
                position == null ? 0 : position.intValue(),
                localCacheKey == null ? "" : localCacheKey
        );
    }
}
