package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.example.smartaudiobook.data.model.UserProfile;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileService {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

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
}
