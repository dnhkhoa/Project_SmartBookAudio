package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FieldValue;
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
                                data.put("createdAt", FieldValue.serverTimestamp());
                                data.put("updatedAt", FieldValue.serverTimestamp());
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

    public void createPasswordResetOtp(String email, String otp, long expiresAtMillis,
                                       FirestoreCallback<UserRecord> callback) {
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
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("resetOtp", otp);
                    updates.put("resetOtpExpiresAt", expiresAtMillis);
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    db.collection("users").document(document.getId())
                            .update(updates)
                            .addOnSuccessListener(unused -> callback.onSuccess(UserRecord.fromDocument(document)))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void resetPasswordWithOtp(String email, String otp, String newPassword,
                                     FirestoreCallback<UserRecord> callback) {
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
                    String savedOtp = document.getString("resetOtp");
                    Long expiresAt = document.getLong("resetOtpExpiresAt");
                    if (savedOtp == null || !savedOtp.equals(otp)) {
                        callback.onError(new Exception("Invalid OTP"));
                        return;
                    }
                    if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
                        callback.onError(new Exception("OTP expired"));
                        return;
                    }
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("password", newPassword);
                    updates.put("resetOtp", FieldValue.delete());
                    updates.put("resetOtpExpiresAt", FieldValue.delete());
                    updates.put("updatedAt", FieldValue.serverTimestamp());
                    db.collection("users").document(document.getId())
                            .update(updates)
                            .addOnSuccessListener(unused -> callback.onSuccess(UserRecord.fromDocument(document)))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public static String mapAuthErrorMessage(Exception error) {
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreError = (FirebaseFirestoreException) error;
            if (firestoreError.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Firestore rules are blocking access to users collection";
            }
        }
        return error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()
                ? "Authentication failed"
                : error.getMessage();
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
