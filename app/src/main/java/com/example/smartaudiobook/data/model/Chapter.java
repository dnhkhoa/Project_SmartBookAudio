package com.example.smartaudiobook.data.model;

import com.google.firebase.firestore.DocumentSnapshot;

public class Chapter {
    public final String id;
    public final String title;
    public final int order;
    public final int durationSec;
    public final String audioUrl;
    public final boolean isFreePreview;

    public Chapter(String id, String title, int order, int durationSec, String audioUrl, boolean isFreePreview) {
        this.id = id;
        this.title = title;
        this.order = order;
        this.durationSec = durationSec;
        this.audioUrl = audioUrl;
        this.isFreePreview = isFreePreview;
    }

    public static Chapter fromSnapshot(DocumentSnapshot snapshot) {
        String title = snapshot.getString("title");
        String audioUrl = snapshot.getString("audioUrl");
        Long order = snapshot.getLong("order");
        Long duration = snapshot.getLong("durationSec");
        Boolean freePreview = snapshot.getBoolean("isFreePreview");
        return new Chapter(
                snapshot.getId(),
                title == null ? "Untitled chapter" : title,
                order == null ? 0 : order.intValue(),
                duration == null ? 0 : duration.intValue(),
                audioUrl == null ? "" : audioUrl,
                Boolean.TRUE.equals(freePreview)
        );
    }
}
