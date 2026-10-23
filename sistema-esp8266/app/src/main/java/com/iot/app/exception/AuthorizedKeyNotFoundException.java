package com.iot.app.exception;

public class AuthorizedKeyNotFoundException extends RuntimeException {

    public AuthorizedKeyNotFoundException(String uid) {
        super("UID is not on the allowlist");
        this.uid = uid;
    }

    private final String uid;

    public String getUid() {
        return uid;
    }
}
