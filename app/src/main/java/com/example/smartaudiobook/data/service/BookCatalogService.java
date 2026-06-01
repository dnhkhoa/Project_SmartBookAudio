package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BookCatalogService {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void fetchBookTitle(String bookId, FirestoreCallback<String> callback) {
        db.collection("books").document(bookId).get()
                .addOnSuccessListener(snapshot -> {
                    String title = snapshot.getString("title");
                    callback.onSuccess(title == null || title.trim().isEmpty() ? bookId : title);
                })
                .addOnFailureListener(callback::onError);
    }

    public void fetchBookSourceUrl(String bookId, FirestoreCallback<String> callback) {
        db.collection("books").document(bookId).get()
                .addOnSuccessListener(snapshot -> {
                    String sourceUrl = snapshot.getString("sourceUrl");
                    if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
                        sourceUrl = snapshot.getString("audioUrl");
                    }
                    callback.onSuccess(sourceUrl == null ? "" : sourceUrl);
                })
                .addOnFailureListener(callback::onError);
    }

    public void createUserBook(String authorUid, String title, String sourceUrl, FirestoreCallback<String> callback) {
        String bookId = buildCustomBookId(title);

        Map<String, Object> book = new HashMap<>();
        book.put("title", title);
        book.put("titleLower", title.toLowerCase(Locale.US));
        book.put("author", "User Author");
        book.put("authorUid", authorUid);
        book.put("desc", "User-created audiobook source.");
        book.put("coverUrl", "");
        book.put("sourceType", "url");
        book.put("sourceUrl", sourceUrl);
        book.put("audioUrl", sourceUrl);
        book.put("tags", Arrays.asList("user-created", "library"));
        book.put("language", "en");
        book.put("rating", 0);
        book.put("ratingCount", 0);
        book.put("chapterCount", 1);
        book.put("isFree", true);
        book.put("isPremium", false);
        book.put("createdAt", FieldValue.serverTimestamp());
        book.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> chapter = new HashMap<>();
        chapter.put("title", "Source audio");
        chapter.put("order", 1);
        chapter.put("durationSec", 0);
        chapter.put("sourceType", "url");
        chapter.put("sourceUrl", sourceUrl);
        chapter.put("audioUrl", sourceUrl);
        chapter.put("isFreePreview", true);
        chapter.put("createdAt", FieldValue.serverTimestamp());
        chapter.put("updatedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.set(db.collection("books").document(bookId), book, SetOptions.merge());
        batch.set(db.collection("books").document(bookId).collection("chapters").document("chapter-01"), chapter, SetOptions.merge());
        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(bookId))
                .addOnFailureListener(callback::onError);
    }

    private String buildCustomBookId(String title) {
        String normalized = title
                .trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) {
            normalized = "untitled";
        }
        return "custom-" + normalized + "-" + System.currentTimeMillis();
    }
}
