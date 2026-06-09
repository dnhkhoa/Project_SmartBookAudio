package com.example.smartaudiobook.data.model;

import com.google.firebase.firestore.DocumentSnapshot;

public class UserProfile {
    public final String displayName;
    public final String email;

    public UserProfile(String displayName, String email) {
        this.displayName = displayName;
        this.email = email;
    }

    public static UserProfile fromSnapshot(DocumentSnapshot snapshot) {
        String displayName = snapshot.getString("displayName");
        String email = snapshot.getString("email");
        return new UserProfile(
                displayName == null ? "Guest" : displayName,
                email == null ? "" : email
        );
    }
}
