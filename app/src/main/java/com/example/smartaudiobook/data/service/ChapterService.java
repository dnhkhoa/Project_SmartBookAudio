package com.example.smartaudiobook.data.service;

import com.example.smartaudiobook.data.FirestoreCallback;
import com.example.smartaudiobook.data.model.Chapter;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ChapterService {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void fetchChapters(String bookId, FirestoreCallback<List<Chapter>> callback) {
        db.collection("books")
                .document(bookId)
                .collection("chapters")
                .orderBy("order")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Chapter> chapters = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                        chapters.add(Chapter.fromSnapshot(document));
                    }
                    callback.onSuccess(chapters);
                })
                .addOnFailureListener(callback::onError);
    }
}
