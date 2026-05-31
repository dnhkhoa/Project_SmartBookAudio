package com.example.smartaudiobook.data.model;

import com.google.firebase.firestore.DocumentSnapshot;

public class UserProfile {
    public final String displayName;
    public final String email;
    public final boolean isPremium;
    public final String defaultVoice;
    public final String appLanguage;
    public final String storageMode;
    public final int booksCount;
    public final int totalListeningSec;

    public UserProfile(String displayName, String email, boolean isPremium, String defaultVoice,
                       String appLanguage, String storageMode, int booksCount, int totalListeningSec) {
        this.displayName = displayName;
        this.email = email;
        this.isPremium = isPremium;
        this.defaultVoice = defaultVoice;
        this.appLanguage = appLanguage;
        this.storageMode = storageMode;
        this.booksCount = booksCount;
        this.totalListeningSec = totalListeningSec;
    }

    public static UserProfile fromSnapshot(DocumentSnapshot snapshot) {
        String displayName = snapshot.getString("displayName");
        String email = snapshot.getString("email");
        Boolean premium = snapshot.getBoolean("isPremium");
        String defaultVoice = snapshot.getString("defaultVoice");
        String appLanguage = snapshot.getString("appLanguage");
        String storageMode = snapshot.getString("storageMode");
        Long booksCount = snapshot.getLong("booksCount");
        Long totalListeningSec = snapshot.getLong("totalListeningSec");
        return new UserProfile(
                displayName == null ? "Guest" : displayName,
                email == null ? "" : email,
                Boolean.TRUE.equals(premium),
                defaultVoice == null ? "Standard voice" : defaultVoice,
                appLanguage == null ? "English" : appLanguage,
                storageMode == null ? "Cloud sync" : storageMode,
                booksCount == null ? 0 : booksCount.intValue(),
                totalListeningSec == null ? 0 : totalListeningSec.intValue()
        );
    }
}
