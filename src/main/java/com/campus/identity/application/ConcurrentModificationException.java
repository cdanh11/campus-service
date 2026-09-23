package com.campus.identity.application;

public final class ConcurrentModificationException extends RuntimeException {

    public ConcurrentModificationException() {
        super("user was modified concurrently");
    }
}
