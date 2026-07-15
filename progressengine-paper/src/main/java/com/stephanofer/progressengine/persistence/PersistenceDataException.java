package com.stephanofer.progressengine.persistence;

public final class PersistenceDataException extends RuntimeException {
    public PersistenceDataException(String message) {
        super(message);
    }

    public PersistenceDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
