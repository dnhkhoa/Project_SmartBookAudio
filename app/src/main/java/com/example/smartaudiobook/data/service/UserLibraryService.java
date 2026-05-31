package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.example.smartaudiobook.data.model.LibraryEntry;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
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
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        data.put("addedAt", FieldValue.serverTimestamp());
        data.put("lastOpenedAt", FieldValue.serverTimestamp());
        data.put("lastChapterId", "");
        data.put("lastPositionSec", 0);
        db.collection("users")
                .document(uid)
                .collection("library")
                .document(bookId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess(null))
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
        data.put("source", "manual");
        db.collection("users")
                .document(uid)
                .collection("library")
                .document(bookId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess(bookId))
                .addOnFailureListener(callback::onError);
    }

    public void removeBook(String uid, String bookId, FirestoreCallback<Void> callback) {
        db.collection("users")
                .document(uid)
                .collection("library")
                .document(bookId)
                .delete()
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void updateStatus(String uid, String bookId, String status, FirestoreCallback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        data.put("lastOpenedAt", FieldValue.serverTimestamp());
        db.collection("users")
                .document(uid)
                .collection("library")
                .document(bookId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess(null))
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
