package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.example.smartaudiobook.data.model.BookSummary;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /**
     * Firestore doesn't support true full-text "contains" search natively.
     * This method tries a prefix query on `titleLower` (if your documents have it),
     * and falls back to fetching a small batch and filtering on the client.
     */
    public void searchBooksByTitle(String query, int limit, FirestoreCallback<List<BookSummary>> callback) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        db.collection("books")
                .orderBy("titleLower")
                .startAt(normalized)
                .endAt(normalized + "\uf8ff")
                .limit(limit)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<BookSummary> results = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                        results.add(BookSummary.fromSnapshot(document));
                    }
                    callback.onSuccess(results);
                })
                .addOnFailureListener(error -> fallbackClientFilter(normalized, limit, callback, error));
    }

    private void fallbackClientFilter(
            String normalized,
            int limit,
            FirestoreCallback<List<BookSummary>> callback,
            Exception originalError
    ) {
        db.collection("books")
                .limit(Math.max(20, limit * 4L))
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<BookSummary> results = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                        BookSummary book = BookSummary.fromSnapshot(document);
                        if (normalize(book.title).contains(normalized) || normalize(book.id).contains(normalized)) {
                            results.add(book);
                            if (results.size() >= limit) {
                                break;
                            }
                        }
                    }
                    callback.onSuccess(results);
                })
                .addOnFailureListener(err -> {
                    // If even fallback fails, return the original error (usually more useful).
                    callback.onError(originalError != null ? originalError : err);
                });
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
