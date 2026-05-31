package com.example.smartaudiobook.data.model;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class PlayerState {
    public final String bookId;
    public final String bookTitle;
    public final String chapterId;
    public final String chapterTitle;
    public final int positionSec;
    public final int durationSec;
    public final String speed;
    public final boolean isPlaying;

    public PlayerState(String bookId, String bookTitle, String chapterId, String chapterTitle,
                       int positionSec, int durationSec, String speed, boolean isPlaying) {
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.chapterId = chapterId;
        this.chapterTitle = chapterTitle;
        this.positionSec = positionSec;
        this.durationSec = durationSec;
        this.speed = speed;
        this.isPlaying = isPlaying;
    }

    public Map<String, Object> toFirestoreMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("bookId", bookId);
        data.put("bookTitle", bookTitle);
        data.put("chapterId", chapterId);
        data.put("chapterTitle", chapterTitle);
        data.put("positionSec", positionSec);
        data.put("durationSec", durationSec);
        data.put("speed", speed);
        data.put("isPlaying", isPlaying);
        data.put("updatedAt", FieldValue.serverTimestamp());
        return data;
    }

    public static PlayerState fromSnapshot(DocumentSnapshot snapshot) {
        String bookId = snapshot.getString("bookId");
        String bookTitle = snapshot.getString("bookTitle");
        String chapterId = snapshot.getString("chapterId");
        String chapterTitle = snapshot.getString("chapterTitle");
        String speed = snapshot.getString("speed");
        Long position = snapshot.getLong("positionSec");
        Long duration = snapshot.getLong("durationSec");
        Boolean playing = snapshot.getBoolean("isPlaying");
        return new PlayerState(
                bookId == null ? "" : bookId,
                bookTitle == null ? "" : bookTitle,
                chapterId == null ? "" : chapterId,
                chapterTitle == null ? "" : chapterTitle,
                position == null ? 0 : position.intValue(),
                duration == null ? 0 : duration.intValue(),
                speed == null ? "1.0x" : speed,
                Boolean.TRUE.equals(playing)
        );
    }
}
