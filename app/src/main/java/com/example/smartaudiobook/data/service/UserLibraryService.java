package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.example.smartaudiobook.data.model.LibraryEntry;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserLibraryService {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void listLibrary(String uid, FirestoreCallback<List<LibraryEntry>> callback) {
        db.collection("users")
                .document(uid)
                .collection("library")
                .orderBy("lastOpenedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<LibraryEntry> entries = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                        entries.add(LibraryEntry.fromSnapshot(document));
                    }
                    callback.onSuccess(entries);
                })
                .addOnFailureListener(callback::onError);
    }

    public void addBook(String uid, String bookId, String status, FirestoreCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference entryRef = db.collection("users")
                .document(uid)
                .collection("library")
                .document(bookId);
        entryRef.get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", status);
                    data.put("lastOpenedAt", FieldValue.serverTimestamp());
                    if (!snapshot.exists()) {
                        data.put("addedAt", FieldValue.serverTimestamp());
                        data.put("lastChapterId", "");
                        data.put("lastPositionSec", 0);
                        data.put("isDownloaded", false);
                    } else if (snapshot.getBoolean("isDownloaded") == null) {
                        data.put("isDownloaded", false);
                    }
                    entryRef.set(data, SetOptions.merge())
                            .addOnSuccessListener(unused -> syncLibrarySummary(uid, callback))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void addCustomBook(String uid, String title, FirestoreCallback<String> callback) {
        String bookId = buildCustomBookId(title);
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("status", LibraryEntry.STATUS_SAVED);
        data.put("addedAt", FieldValue.serverTimestamp());
        data.put("lastOpenedAt", FieldValue.serverTimestamp());
        data.put("lastChapterId", "");
        data.put("lastPositionSec", 0);
        data.put("isDownloaded", false);
        data.put("source", "manual");
        db.collection("users")
                .document(uid)
                .collection("library")
                .document(bookId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> syncLibrarySummary(uid, new FirestoreCallback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        callback.onSuccess(bookId);
                    }

                    @Override
                    public void onError(Exception error) {
                        callback.onError(error);
                    }
                }))
                .addOnFailureListener(callback::onError);
    }

    public void removeBook(String uid, String bookId, FirestoreCallback<Void> callback) {
        db.collection("users")
                .document(uid)
                .collection("library")
                .document(bookId)
                .delete()
                .addOnSuccessListener(unused -> syncLibrarySummary(uid, callback))
                .addOnFailureListener(callback::onError);
    }

    public void updateStatus(String uid, String bookId, String status, FirestoreCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference entryRef = db.collection("users")
                .document(uid)
                .collection("library")
                .document(bookId);
        entryRef.get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", normalizePersistentStatus(status));
                    data.put("lastOpenedAt", FieldValue.serverTimestamp());
                    if (!snapshot.exists()) {
                        addNewLibraryEntryDefaults(data);
                        data.put("isDownloaded", false);
                    } else if (snapshot.getBoolean("isDownloaded") == null) {
                        data.put("isDownloaded", false);
                    }
                    entryRef.set(data, SetOptions.merge())
                            .addOnSuccessListener(unused -> syncLibrarySummary(uid, callback))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void markDownloaded(String uid, String bookId, String localCacheKey, String status, FirestoreCallback<Void> callback) {
        com.google.firebase.firestore.DocumentReference entryRef = db.collection("users")
                .document(uid)
                .collection("library")
                .document(bookId);
        entryRef.get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", normalizePersistentStatus(status));
                    if (!snapshot.exists()) {
                        addNewLibraryEntryDefaults(data);
                    }
                    data.put("isDownloaded", true);
                    data.put("downloadedAt", FieldValue.serverTimestamp());
                    data.put("lastOpenedAt", FieldValue.serverTimestamp());
                    data.put("localCacheKey", localCacheKey == null ? "" : localCacheKey);
                    entryRef.set(data, SetOptions.merge())
                            .addOnSuccessListener(unused -> syncLibrarySummary(uid, callback))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void markOpened(String uid, String bookId, String chapterId, int positionSec) {
        if (uid == null || uid.isEmpty()) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("lastOpenedAt", FieldValue.serverTimestamp());
        data.put("lastChapterId", chapterId == null ? "" : chapterId);
        data.put("lastPositionSec", positionSec);
        db.collection("users")
                .document(uid)
                .collection("library")
                .document(bookId)
                .set(data, SetOptions.merge());
    }

    public void syncLibrarySummary(String uid, FirestoreCallback<Void> callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onSuccess(null);
            return;
        }
        db.collection("users")
                .document(uid)
                .collection("library")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<String> savedBookIds = new ArrayList<>();
                    List<String> downloadedBookIds = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                        String bookId = document.getId();
                        savedBookIds.add(bookId);
                        if (Boolean.TRUE.equals(document.getBoolean("isDownloaded"))) {
                            downloadedBookIds.add(bookId);
                        }
                    }
                    Collections.sort(savedBookIds);
                    Collections.sort(downloadedBookIds);

                    Map<String, Object> userUpdates = new HashMap<>();
                    userUpdates.put("savedBookIds", savedBookIds);
                    userUpdates.put("downloadedBookIds", downloadedBookIds);
                    userUpdates.put("libraryBookCount", savedBookIds.size());
                    userUpdates.put("downloadedBookCount", downloadedBookIds.size());
                    userUpdates.put("updatedAt", FieldValue.serverTimestamp());

                    db.collection("users")
                            .document(uid)
                            .set(userUpdates, SetOptions.merge())
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onError);
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

    private void addNewLibraryEntryDefaults(Map<String, Object> data) {
        data.put("addedAt", FieldValue.serverTimestamp());
        data.put("lastChapterId", "");
        data.put("lastPositionSec", 0);
    }

    private String normalizePersistentStatus(String status) {
        if (status == null || status.trim().isEmpty() || LibraryEntry.STATUS_DOWNLOADING.equals(status)) {
            return LibraryEntry.STATUS_SAVED;
        }
        return status;
    }
}
