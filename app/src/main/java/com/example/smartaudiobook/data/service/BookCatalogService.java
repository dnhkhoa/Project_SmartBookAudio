package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.example.smartaudiobook.data.model.BookSummary;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BookCatalogService {
    private static final String[] DEFAULT_AUTHOR_NAMES = {
            "Alex",
            "Adam",
            "Bryan",
            "Chris",
            "Daniel",
            "Ethan",
            "Henry",
            "James",
            "Lucas",
            "Noah",
            "Oscar",
            "Ryan"
    };

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void fetchBookTitle(String bookId, FirestoreCallback<String> callback) {
        db.collection("books").document(bookId).get()
                .addOnSuccessListener(snapshot -> {
                    callback.onSuccess(BookSummary.resolveDisplayName(snapshot));
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

    public void fetchPlayableAudioUrl(String bookId, FirestoreCallback<String> callback) {
        db.collection("books").document(bookId).get()
                .addOnSuccessListener(snapshot -> {
                    String sourceUrl = snapshot.getString("sourceUrl");
                    if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
                        sourceUrl = snapshot.getString("audioUrl");
                    }
                    if (sourceUrl != null && !sourceUrl.trim().isEmpty()) {
                        callback.onSuccess(sourceUrl);
                        return;
                    }
                    fetchFirstChapterAudioUrl(bookId, callback);
                })
                .addOnFailureListener(error -> fetchFirstChapterAudioUrl(bookId, callback));
    }

    private void fetchFirstChapterAudioUrl(String bookId, FirestoreCallback<String> callback) {
        db.collection("books")
                .document(bookId)
                .collection("chapters")
                .orderBy("order")
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onSuccess("");
                        return;
                    }
                    com.google.firebase.firestore.DocumentSnapshot chapter = snapshot.getDocuments().get(0);
                    String audioUrl = chapter.getString("audioUrl");
                    if (audioUrl == null || audioUrl.trim().isEmpty()) {
                        audioUrl = chapter.getString("sourceUrl");
                    }
                    callback.onSuccess(audioUrl == null ? "" : audioUrl);
                })
                .addOnFailureListener(callback::onError);
    }

    public void fetchBookSummaries(List<String> bookIds, FirestoreCallback<List<BookSummary>> callback) {
        if (bookIds.isEmpty()) {
            callback.onSuccess(Collections.emptyList());
            return;
        }

        List<BookSummary> summaries = new ArrayList<>();
        AtomicInteger pending = new AtomicInteger(bookIds.size());
        AtomicBoolean completed = new AtomicBoolean(false);

        for (String bookId : bookIds) {
            db.collection("books").document(bookId).get()
                    .addOnSuccessListener(snapshot -> {
                        if (completed.get()) {
                            return;
                        }
                        summaries.add(BookSummary.fromSnapshot(snapshot));
                        if (pending.decrementAndGet() == 0) {
                            callback.onSuccess(summaries);
                        }
                    })
                    .addOnFailureListener(error -> {
                        if (completed.compareAndSet(false, true)) {
                            callback.onError(error);
                        }
                    });
        }
    }

    public void createUserBook(String createdByUid, String createdByDisplayName, String title, String sourceUrl,
                               FirestoreCallback<String> callback) {
        String bookId = buildCustomBookId(title);
        String cleanedTitle = title.trim();
        String cleanedCreatorDisplayName = createdByDisplayName == null || createdByDisplayName.trim().isEmpty()
                ? "Unknown User"
                : createdByDisplayName.trim();

        Map<String, Object> book = new HashMap<>();
        book.put("displayName", cleanedTitle);
        book.put("title", cleanedTitle);
        book.put("titleLower", cleanedTitle.toLowerCase(Locale.US));
        book.put("authorDisplayName", chooseAuthorDisplayName(bookId));
        book.put("createdByUid", createdByUid);
        book.put("createdByDisplayName", cleanedCreatorDisplayName);
        book.put("sourceUrl", sourceUrl);
        book.put("createdAt", FieldValue.serverTimestamp());
        book.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> chapter = new HashMap<>();
        chapter.put("title", "Source audio");
        chapter.put("order", 1);
        chapter.put("durationSec", 0);
        chapter.put("audioUrl", sourceUrl);
        chapter.put("createdAt", FieldValue.serverTimestamp());
        chapter.put("updatedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.set(db.collection("books").document(bookId), book);
        batch.set(db.collection("books").document(bookId).collection("chapters").document("chapter-01"), chapter);
        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess(bookId))
                .addOnFailureListener(callback::onError);
    }

    public void migrateBookSchema(FirestoreCallback<Void> callback) {
        db.collection("books")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onSuccess(null);
                        return;
                    }

                    List<com.google.firebase.firestore.DocumentSnapshot> books = snapshot.getDocuments();
                    List<BookSchemaUpdate> updates = new ArrayList<>();
                    AtomicInteger pending = new AtomicInteger(books.size());
                    AtomicBoolean completed = new AtomicBoolean(false);

                    for (com.google.firebase.firestore.DocumentSnapshot document : books) {
                        document.getReference()
                                .collection("chapters")
                                .get()
                                .addOnSuccessListener(chapters -> {
                                    String directSourceUrl = fallback(document.getString("sourceUrl"), document.getString("audioUrl"));
                                    String chapterSourceUrl = findFirstChapterSourceUrl(chapters.getDocuments());
                                    synchronized (updates) {
                                        updates.add(new BookSchemaUpdate(
                                                document,
                                                buildBookSchemaUpdates(document, fallback(directSourceUrl, chapterSourceUrl))
                                        ));
                                        for (com.google.firebase.firestore.DocumentSnapshot chapter : chapters.getDocuments()) {
                                            updates.add(new BookSchemaUpdate(chapter, buildChapterSchemaUpdates(chapter)));
                                        }
                                    }
                                    finishBookSchemaMigrationIfReady(pending, completed, updates, callback);
                                })
                                .addOnFailureListener(error -> {
                                    if (completed.compareAndSet(false, true)) {
                                        callback.onError(error);
                                    }
                                });
                    }
                })
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
                    // If even fallback fails, return the original error because it is usually more useful.
                    callback.onError(originalError != null ? originalError : err);
                });
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private String fallback(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty()
                ? (fallback == null ? "" : fallback.trim())
                : preferred.trim();
    }

    private Map<String, Object> buildBookSchemaUpdates(
            com.google.firebase.firestore.DocumentSnapshot document,
            String sourceUrl
    ) {
        String displayName = BookSummary.resolveDisplayName(document);
        String authorDisplayName = chooseAuthorDisplayName(document.getId());
        String createdByUid = fallback(document.getString("createdByUid"), document.getString("authorUid"));
        String createdByDisplayName = fallback(document.getString("createdByDisplayName"), "");

        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", displayName);
        updates.put("title", displayName);
        updates.put("titleLower", displayName.toLowerCase(Locale.US));
        updates.put("authorDisplayName", authorDisplayName);
        updates.put("createdByUid", createdByUid);
        updates.put("createdByDisplayName", createdByDisplayName);
        updates.put("sourceUrl", fallback(sourceUrl, ""));
        if (document.getTimestamp("createdAt") == null) {
            updates.put("createdAt", FieldValue.serverTimestamp());
        }
        updates.put("authorUid", FieldValue.delete());
        updates.put("author", FieldValue.delete());
        updates.put("desc", FieldValue.delete());
        updates.put("coverUrl", FieldValue.delete());
        updates.put("sourceType", FieldValue.delete());
        updates.put("audioUrl", FieldValue.delete());
        updates.put("tags", FieldValue.delete());
        updates.put("language", FieldValue.delete());
        updates.put("rating", FieldValue.delete());
        updates.put("ratingCount", FieldValue.delete());
        updates.put("chapterCount", FieldValue.delete());
        updates.put("isFree", FieldValue.delete());
        updates.put("isPremium", FieldValue.delete());
        updates.put("updatedAt", FieldValue.serverTimestamp());
        return updates;
    }

    private Map<String, Object> buildChapterSchemaUpdates(com.google.firebase.firestore.DocumentSnapshot chapter) {
        Map<String, Object> updates = new HashMap<>();
        Long order = chapter.getLong("order");
        Long durationSec = chapter.getLong("durationSec");
        updates.put("title", fallback(chapter.getString("title"), "Source audio"));
        updates.put("order", order == null ? 1 : order.intValue());
        updates.put("durationSec", durationSec == null ? 0 : durationSec.intValue());
        updates.put("audioUrl", fallback(chapter.getString("audioUrl"), chapter.getString("sourceUrl")));
        if (chapter.getTimestamp("createdAt") == null) {
            updates.put("createdAt", FieldValue.serverTimestamp());
        }
        updates.put("sourceType", FieldValue.delete());
        updates.put("sourceUrl", FieldValue.delete());
        updates.put("isFreePreview", FieldValue.delete());
        updates.put("updatedAt", FieldValue.serverTimestamp());
        return updates;
    }

    private String findFirstChapterSourceUrl(List<com.google.firebase.firestore.DocumentSnapshot> chapters) {
        com.google.firebase.firestore.DocumentSnapshot firstChapter = null;
        int firstOrder = Integer.MAX_VALUE;
        for (com.google.firebase.firestore.DocumentSnapshot chapter : chapters) {
            Long order = chapter.getLong("order");
            int normalizedOrder = order == null ? Integer.MAX_VALUE : order.intValue();
            if (firstChapter == null || normalizedOrder < firstOrder) {
                firstChapter = chapter;
                firstOrder = normalizedOrder;
            }
        }
        if (firstChapter == null) {
            return "";
        }
        return fallback(firstChapter.getString("audioUrl"), firstChapter.getString("sourceUrl"));
    }

    private String chooseAuthorDisplayName(String bookId) {
        int index = Math.abs((bookId == null ? "" : bookId).hashCode()) % DEFAULT_AUTHOR_NAMES.length;
        return DEFAULT_AUTHOR_NAMES[index];
    }

    private void finishBookSchemaMigrationIfReady(
            AtomicInteger pending,
            AtomicBoolean completed,
            List<BookSchemaUpdate> updates,
            FirestoreCallback<Void> callback
    ) {
        if (pending.decrementAndGet() != 0 || completed.get()) {
            return;
        }
        List<WriteBatch> batches = new ArrayList<>();
        WriteBatch batch = db.batch();
        int writeCount = 0;
        synchronized (updates) {
            for (BookSchemaUpdate update : updates) {
                batch.set(update.document.getReference(), update.fields, SetOptions.merge());
                writeCount++;
                if (writeCount == 450) {
                    batches.add(batch);
                    batch = db.batch();
                    writeCount = 0;
                }
            }
        }
        if (writeCount > 0) {
            batches.add(batch);
        }
        commitBatches(batches, callback);
    }

    private void commitBatches(List<WriteBatch> batches, FirestoreCallback<Void> callback) {
        if (batches.isEmpty()) {
            callback.onSuccess(null);
            return;
        }
        AtomicInteger pending = new AtomicInteger(batches.size());
        AtomicBoolean completed = new AtomicBoolean(false);
        for (WriteBatch batch : batches) {
            batch.commit()
                    .addOnSuccessListener(unused -> {
                        if (pending.decrementAndGet() == 0 && completed.compareAndSet(false, true)) {
                            callback.onSuccess(null);
                        }
                    })
                    .addOnFailureListener(error -> {
                        if (completed.compareAndSet(false, true)) {
                            callback.onError(error);
                        }
                    });
        }
    }

    private static class BookSchemaUpdate {
        final com.google.firebase.firestore.DocumentSnapshot document;
        final Map<String, Object> fields;

        BookSchemaUpdate(com.google.firebase.firestore.DocumentSnapshot document, Map<String, Object> fields) {
            this.document = document;
            this.fields = fields;
        }
    }
}
