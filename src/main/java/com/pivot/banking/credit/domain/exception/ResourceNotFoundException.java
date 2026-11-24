package com.pivot.banking.credit.domain.exception;

/** Signals that a requested resource does not exist. */
public class ResourceNotFoundException extends RuntimeException {

    /** Creates the exception for a missing resource identifier. */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
