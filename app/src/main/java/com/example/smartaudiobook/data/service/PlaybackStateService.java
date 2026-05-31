package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.example.smartaudiobook.data.model.PlayerState;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

public class PlaybackStateService {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadCurrent(String uid, FirestoreCallback<PlayerState> callback) {
        db.collection("users")
                .document(uid)
                .collection("playback")
                .document("current")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        callback.onSuccess(null);
                        return;
                    }
                    callback.onSuccess(PlayerState.fromSnapshot(snapshot));
                })
                .addOnFailureListener(callback::onError);
    }

    public void saveCurrent(String uid, PlayerState state) {
        if (uid == null || uid.isEmpty()) {
            return;
        }
        db.collection("users")
                .document(uid)
                .collection("playback")
                .document("current")
                .set(state.toFirestoreMap(), SetOptions.merge());
    }
}
