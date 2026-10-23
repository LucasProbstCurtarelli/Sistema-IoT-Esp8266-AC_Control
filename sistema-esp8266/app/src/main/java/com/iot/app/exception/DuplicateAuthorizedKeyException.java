package com.iot.app.exception;

public class DuplicateAuthorizedKeyException extends RuntimeException {

    public DuplicateAuthorizedKeyException(String uid) {
        super("UID already registered");
        this.uid = uid;
    }

    private final String uid;

    public String getUid() {
        return uid;
    }
}
