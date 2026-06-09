package com.example.smartaudiobook.data.model;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Locale;

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
        this(id, title, "Unknown Author", "");
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
        String title = resolveDisplayName(snapshot);
        String author = snapshot.getString("authorDisplayName");
        if (author == null || author.trim().isEmpty()) {
            author = snapshot.getString("author");
        }
        if (author == null || author.trim().isEmpty()) {
            author = "Unknown Author";
        }
        String sourceUrl = snapshot.getString("sourceUrl");
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
            sourceUrl = snapshot.getString("audioUrl");
        }
        return new BookSummary(id, title, author, sourceUrl == null ? "" : sourceUrl);
    }

    @NonNull
    public static String resolveDisplayName(@NonNull DocumentSnapshot snapshot) {
        String displayName = snapshot.getString("displayName");
        if (displayName != null && !displayName.trim().isEmpty()) {
            String cleanedDisplayName = displayName.trim();
            return isTechnicalBookName(cleanedDisplayName, snapshot.getId())
                    ? humanizeBookId(cleanedDisplayName)
                    : cleanedDisplayName;
        }
        String title = snapshot.getString("title");
        if (title != null && !title.trim().isEmpty()) {
            String cleanedTitle = title.trim();
            return isTechnicalBookName(cleanedTitle, snapshot.getId())
                    ? humanizeBookId(cleanedTitle)
                    : cleanedTitle;
        }
        return humanizeBookId(snapshot.getId());
    }

    private static boolean isTechnicalBookName(@NonNull String value, @NonNull String bookId) {
        String normalizedValue = value.toLowerCase(Locale.US).replace('_', '-');
        String normalizedId = bookId.toLowerCase(Locale.US).replace('_', '-');
        return normalizedValue.equals(normalizedId)
                || normalizedValue.startsWith("custom-")
                || (value.contains("_") && !value.contains(" "));
    }

    @NonNull
    private static String humanizeBookId(@NonNull String bookId) {
        String normalized = bookId
                .replaceFirst("^custom[-_]+", "")
                .replaceFirst("[-_]+\\d{8,}$", "")
                .replace('-', ' ')
                .replace('_', ' ')
                .trim();
        if (normalized.isEmpty()) {
            return bookId;
        }
        String[] words = normalized.split("\\s+");
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (title.length() > 0) {
                title.append(' ');
            }
            title.append(word.substring(0, 1).toUpperCase(Locale.US));
            if (word.length() > 1) {
                title.append(word.substring(1));
            }
        }
        return title.length() == 0 ? bookId : title.toString();
    }
}

