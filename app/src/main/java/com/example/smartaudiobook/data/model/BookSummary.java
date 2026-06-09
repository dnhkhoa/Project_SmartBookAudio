package com.example.smartaudiobook.data.model;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;

public class BookSummary {
    @NonNull
    public final String id;
    @NonNull
    public final String title;
    @NonNull
    public final String author;
    @NonNull
    public final String sourceUrl;

    public BookSummary(@NonNull String id, @NonNull String title) {
        this(id, title, "User Author", "");
    }

    public BookSummary(@NonNull String id, @NonNull String title, @NonNull String author, @NonNull String sourceUrl) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.sourceUrl = sourceUrl;
    }

    @NonNull
    public static BookSummary fromSnapshot(@NonNull DocumentSnapshot snapshot) {
        String id = snapshot.getId();
        String title = snapshot.getString("title");
        if (title == null || title.trim().isEmpty()) {
            title = id;
        }
        String author = snapshot.getString("author");
        if (author == null || author.trim().isEmpty()) {
            author = "User Author";
        }
        String sourceUrl = snapshot.getString("sourceUrl");
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
            sourceUrl = snapshot.getString("audioUrl");
        }
        return new BookSummary(id, title, author, sourceUrl == null ? "" : sourceUrl);
    }
}

