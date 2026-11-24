package com.pivot.banking.credit.domain.exception;

/** Signals that an aggregate was modified concurrently. */
public class ConcurrentModificationException extends RuntimeException {

    /** Creates the exception for a resource identifier. */
    public ConcurrentModificationException(java.util.UUID resourceId) {
        super("Resource was modified concurrently: " + resourceId);
    }
}
