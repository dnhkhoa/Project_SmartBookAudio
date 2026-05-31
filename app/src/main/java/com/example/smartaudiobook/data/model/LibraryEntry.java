package com.example.smartaudiobook.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class LibraryEntry {
    public static final String STATUS_SAVED = "saved";
    public static final String STATUS_DOWNLOADING = "downloading";
    public static final String STATUS_FINISHED = "finished";

    public final String bookId;
    public final String status;
    public final Timestamp addedAt;
    public final Timestamp lastOpenedAt;
    public final String lastChapterId;
    public final int lastPositionSec;

    public LibraryEntry(String bookId, String status, Timestamp addedAt, Timestamp lastOpenedAt,
                        String lastChapterId, int lastPositionSec) {
        this.bookId = bookId;
        this.status = status;
        this.addedAt = addedAt;
        this.lastOpenedAt = lastOpenedAt;
        this.lastChapterId = lastChapterId;
        this.lastPositionSec = lastPositionSec;
    }

    public static LibraryEntry fromSnapshot(DocumentSnapshot snapshot) {
        String status = snapshot.getString("status");
        String lastChapterId = snapshot.getString("lastChapterId");
        Long position = snapshot.getLong("lastPositionSec");
        return new LibraryEntry(
                snapshot.getId(),
                status == null ? STATUS_SAVED : status,
                snapshot.getTimestamp("addedAt"),
                snapshot.getTimestamp("lastOpenedAt"),
                lastChapterId == null ? "" : lastChapterId,
                position == null ? 0 : position.intValue()
        );
    }
}
