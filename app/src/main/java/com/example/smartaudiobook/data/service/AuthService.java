package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AuthService {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void login(String email, String password, FirestoreCallback<UserRecord> callback) {
        String normalizedEmail = normalizeEmail(email);
        db.collection("users")
                .whereEqualTo("email", normalizedEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onError(new Exception("Email does not exist"));
                        return;
                    }
                    com.google.firebase.firestore.DocumentSnapshot document = snapshot.getDocuments().get(0);
                    String savedPassword = document.getString("password");
                    if (savedPassword == null || !savedPassword.equals(password)) {
                        callback.onError(new Exception("Wrong password"));
                        return;
                    }
                    callback.onSuccess(UserRecord.fromDocument(document));
                })
                .addOnFailureListener(callback::onError);
    }

    public void register(String documentId, String displayName, String email, String password,
                         FirestoreCallback<UserRecord> callback) {
        String normalizedEmail = normalizeEmail(email);
        db.collection("users")
                .whereEqualTo("email", normalizedEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        callback.onError(new Exception("Email already exists"));
                        return;
                    }
                    db.collection("users").document(documentId).get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    callback.onError(new Exception("Account ID already exists"));
                                    return;
                                }
                                Map<String, Object> data = new HashMap<>();
                                data.put("displayName", displayName);
                                data.put("email", normalizedEmail);
                                data.put("password", password);
                                db.collection("users").document(documentId)
                                        .set(data)
                                        .addOnSuccessListener(unused ->
                                                callback.onSuccess(new UserRecord(documentId, displayName, normalizedEmail)))
                                        .addOnFailureListener(callback::onError);
                            })
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void findPasswordByEmail(String email, FirestoreCallback<String> callback) {
        String normalizedEmail = normalizeEmail(email);
        db.collection("users")
                .whereEqualTo("email", normalizedEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onError(new Exception("Email does not exist"));
                        return;
                    }
                    String password = snapshot.getDocuments().get(0).getString("password");
                    callback.onSuccess(password == null ? "" : password);
                })
                .addOnFailureListener(callback::onError);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.US);
    }

    public static class UserRecord {
        public final String documentId;
        public final String displayName;
        public final String email;

        public UserRecord(String documentId, String displayName, String email) {
            this.documentId = documentId;
            this.displayName = displayName;
            this.email = email;
        }

        public static UserRecord fromDocument(com.google.firebase.firestore.DocumentSnapshot document) {
            String displayName = document.getString("displayName");
            String email = document.getString("email");
            return new UserRecord(
                    document.getId(),
                    displayName == null ? document.getId() : displayName,
                    email == null ? "" : email
            );
        }
    }
}
