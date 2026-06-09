package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.example.smartaudiobook.data.model.UserProfile;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

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
                    data.put("avatarUrl", "");
                    data.put("isPremium", false);
                    data.put("defaultVoice", "Standard voice");
                    data.put("appLanguage", "English");
                    data.put("storageMode", "Cloud sync");
                    data.put("booksCount", 0);
                    data.put("totalListeningSec", 0);
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

    public void updateLibraryStats(String uid, int bookCount) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("libraryBookCount", bookCount);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("users").document(uid).set(updates, SetOptions.merge());
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
}
