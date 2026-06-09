package com.example.smartaudiobook.data.model;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;

public class BookSummary {
    @NonNull
    public final String id;
    @NonNull
    public final String title;

    public BookSummary(@NonNull String id, @NonNull String title) {
        this.id = id;
        this.title = title;
    }

    @NonNull
    public static BookSummary fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        String id = snapshot.getId();
        String title = snapshot.getString("title");
        if (title == null || title.trim().isEmpty()) {
            title = id;
        }
        return new BookSummary(id, title);
    }
}

