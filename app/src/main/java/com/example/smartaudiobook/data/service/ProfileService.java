package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.example.smartaudiobook.data.model.LibraryEntry;
import com.example.smartaudiobook.data.model.UserProfile;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ProfileService {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void findUserIdByEmail(String email, FirestoreCallback<String> callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onSuccess("");
            return;
        }
        db.collection("users")
                .whereEqualTo("email", email.trim().toLowerCase(java.util.Locale.US))
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onSuccess("");
                        return;
                    }
                    callback.onSuccess(snapshot.getDocuments().get(0).getId());
                })
                .addOnFailureListener(callback::onError);
    }

    public void ensureProfile(String uid, String email, FirestoreCallback<Void> callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onSuccess(null);
                        return;
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("displayName", "Guest Listener");
                    data.put("email", email == null ? "" : email);
                    data.put("savedBookIds", new ArrayList<String>());
                    data.put("downloadedBookIds", new ArrayList<String>());
                    data.put("libraryBookCount", 0);
                    data.put("downloadedBookCount", 0);
                    data.put("createdAt", FieldValue.serverTimestamp());
                    data.put("updatedAt", FieldValue.serverTimestamp());
                    db.collection("users").document(uid).set(data, SetOptions.merge())
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void loadProfile(String uid, FirestoreCallback<UserProfile> callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        callback.onSuccess(null);
                        return;
                    }
                    callback.onSuccess(UserProfile.fromSnapshot(snapshot));
                })
                .addOnFailureListener(callback::onError);
    }

    public void migrateAllUsersSchema(FirestoreCallback<Void> callback) {
        db.collection("users").get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onSuccess(null);
                        return;
                    }

                    List<com.google.firebase.firestore.DocumentSnapshot> users = snapshot.getDocuments();
                    List<UserSchemaUpdate> updates = new ArrayList<>();
                    AtomicInteger pending = new AtomicInteger(users.size());
                    AtomicBoolean completed = new AtomicBoolean(false);

                    for (com.google.firebase.firestore.DocumentSnapshot document : users) {
                        document.getReference()
                                .collection("library")
                                .get()
                                .addOnSuccessListener(librarySnapshot -> {
                                    synchronized (updates) {
                                        updates.add(new UserSchemaUpdate(
                                                document,
                                                buildUserSchemaUpdates(document, librarySnapshot.getDocuments())
                                        ));
                                        for (com.google.firebase.firestore.DocumentSnapshot libraryDocument : librarySnapshot.getDocuments()) {
                                            updates.add(new UserSchemaUpdate(
                                                    libraryDocument,
                                                    buildLibraryEntrySchemaUpdates(libraryDocument)
                                            ));
                                        }
                                    }
                                    finishUserSchemaMigrationIfReady(pending, completed, updates, callback);
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

    public void updatePreference(String uid, String key, String value, FirestoreCallback<Void> callback) {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put(key, value);

        Map<String, Object> updates = new HashMap<>();
        updates.put("preferences", preferences);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid).set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    private Map<String, Object> buildUserSchemaUpdates(
            com.google.firebase.firestore.DocumentSnapshot snapshot,
            List<com.google.firebase.firestore.DocumentSnapshot> libraryDocuments
    ) {
        Map<String, Object> updates = new HashMap<>();
        List<String> savedBookIds = new ArrayList<>();
        List<String> downloadedBookIds = new ArrayList<>();
        for (com.google.firebase.firestore.DocumentSnapshot document : libraryDocuments) {
            String bookId = document.getId();
            savedBookIds.add(bookId);
            if (Boolean.TRUE.equals(document.getBoolean("isDownloaded"))
                    || "downloaded".equals(document.getString("status"))) {
                downloadedBookIds.add(bookId);
            }
        }
        Collections.sort(savedBookIds);
        Collections.sort(downloadedBookIds);

        updates.put("displayName", fallback(snapshot.getString("displayName"), humanizeUserId(snapshot.getId())));
        updates.put("email", fallback(snapshot.getString("email"), ""));
        updates.put("password", fallback(snapshot.getString("password"), ""));
        updates.put("savedBookIds", savedBookIds);
        updates.put("downloadedBookIds", downloadedBookIds);
        updates.put("libraryBookCount", savedBookIds.size());
        updates.put("downloadedBookCount", downloadedBookIds.size());
        if (snapshot.get("preferences") == null) {
            updates.put("preferences", new HashMap<String, Object>());
        }
        if (snapshot.getTimestamp("createdAt") == null) {
            updates.put("createdAt", FieldValue.serverTimestamp());
        }
        updates.put("avatarUrl", FieldValue.delete());
        updates.put("isPremium", FieldValue.delete());
        updates.put("defaultVoice", FieldValue.delete());
        updates.put("appLanguage", FieldValue.delete());
        updates.put("storageMode", FieldValue.delete());
        updates.put("booksCount", FieldValue.delete());
        updates.put("totalListeningSec", FieldValue.delete());
        updates.put("updatedAt", FieldValue.serverTimestamp());
        return updates;
    }

    private Map<String, Object> buildLibraryEntrySchemaUpdates(
            com.google.firebase.firestore.DocumentSnapshot snapshot
    ) {
        Map<String, Object> updates = new HashMap<>();
        String status = fallback(snapshot.getString("status"), LibraryEntry.STATUS_SAVED);
        boolean legacyDownloaded = "downloaded".equals(status);
        boolean staleDownloading = LibraryEntry.STATUS_DOWNLOADING.equals(status);
        boolean isDownloaded = Boolean.TRUE.equals(snapshot.getBoolean("isDownloaded")) || legacyDownloaded;

        updates.put("status", legacyDownloaded || staleDownloading ? LibraryEntry.STATUS_SAVED : status);
        updates.put("isDownloaded", isDownloaded);
        updates.put("lastChapterId", fallback(snapshot.getString("lastChapterId"), ""));
        Long position = snapshot.getLong("lastPositionSec");
        updates.put("lastPositionSec", position == null ? 0 : position.intValue());
        updates.put("localCacheKey", fallback(snapshot.getString("localCacheKey"), ""));
        if (snapshot.getTimestamp("addedAt") == null) {
            updates.put("addedAt", FieldValue.serverTimestamp());
        }
        if (snapshot.getTimestamp("lastOpenedAt") == null) {
            updates.put("lastOpenedAt", FieldValue.serverTimestamp());
        }
        if (isDownloaded && snapshot.getTimestamp("downloadedAt") == null) {
            updates.put("downloadedAt", FieldValue.serverTimestamp());
        }
        if (!isDownloaded) {
            updates.put("downloadedAt", FieldValue.delete());
        }
        updates.put("title", FieldValue.delete());
        updates.put("displayName", FieldValue.delete());
        updates.put("author", FieldValue.delete());
        updates.put("authorDisplayName", FieldValue.delete());
        updates.put("createdByUid", FieldValue.delete());
        updates.put("createdByDisplayName", FieldValue.delete());
        updates.put("source", FieldValue.delete());
        updates.put("sourceUrl", FieldValue.delete());
        updates.put("audioUrl", FieldValue.delete());
        return updates;
    }

    private String fallback(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty()
                ? (fallback == null ? "" : fallback.trim())
                : preferred.trim();
    }

    private String humanizeUserId(String uid) {
        String normalized = uid == null ? "" : uid.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return "Guest Listener";
        }
        String[] words = normalized.split("\\s+");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (displayName.length() > 0) {
                displayName.append(' ');
            }
            displayName.append(word.substring(0, 1).toUpperCase(java.util.Locale.US));
            if (word.length() > 1) {
                displayName.append(word.substring(1));
            }
        }
        return displayName.length() == 0 ? "Guest Listener" : displayName.toString();
    }

    private void finishUserSchemaMigrationIfReady(
            AtomicInteger pending,
            AtomicBoolean completed,
            List<UserSchemaUpdate> updates,
            FirestoreCallback<Void> callback
    ) {
        if (pending.decrementAndGet() != 0 || completed.get()) {
            return;
        }
        List<WriteBatch> batches = new ArrayList<>();
        WriteBatch batch = db.batch();
        int writeCount = 0;
        synchronized (updates) {
            for (UserSchemaUpdate update : updates) {
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

    private static class UserSchemaUpdate {
        final com.google.firebase.firestore.DocumentSnapshot document;
        final Map<String, Object> fields;

        UserSchemaUpdate(com.google.firebase.firestore.DocumentSnapshot document, Map<String, Object> fields) {
            this.document = document;
            this.fields = fields;
        }
    }
}
