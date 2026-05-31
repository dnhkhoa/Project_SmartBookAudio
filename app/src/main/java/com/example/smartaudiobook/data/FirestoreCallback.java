package com.example.smartaudiobook.data;

public interface FirestoreCallback<T> {
    void onSuccess(T value);

    void onError(Exception error);
}
