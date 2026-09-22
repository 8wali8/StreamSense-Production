package com.streamsense.analyticsservice.storage;

/** The object store refused or failed a read, write, or delete. */
public class LogoStorageException extends RuntimeException {

    public LogoStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
