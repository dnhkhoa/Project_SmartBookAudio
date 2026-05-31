package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.google.firebase.firestore.FirebaseFirestore;

public class BookCatalogService {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void fetchBookTitle(String bookId, FirestoreCallback<String> callback) {
        db.collection("books").document(bookId).get()
                .addOnSuccessListener(snapshot -> {
                    String title = snapshot.getString("title");
                    callback.onSuccess(title == null || title.trim().isEmpty() ? bookId : title);
                })
                .addOnFailureListener(callback::onError);
    }
}
