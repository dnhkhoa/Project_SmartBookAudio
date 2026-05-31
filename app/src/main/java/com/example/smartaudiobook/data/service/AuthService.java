package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthService {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public void ensureSignedIn(FirestoreCallback<String> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            callback.onSuccess(currentUser.getUid());
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(result -> callback.onSuccess(result.getUser().getUid()))
                .addOnFailureListener(callback::onError);
    }

    public String currentUid() {
        FirebaseUser currentUser = auth.getCurrentUser();
        return currentUser == null ? "" : currentUser.getUid();
    }
}
