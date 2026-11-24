package com.pivot.banking.credit.domain.exception;

/** Signals a downstream failure, distinguishing ambiguous outcomes from confirmed ones. */
public class DownstreamServiceException extends RuntimeException {

    private final boolean ambiguous;

    /** Creates the exception with its ambiguity flag and optional cause. */
    public DownstreamServiceException(String message, boolean ambiguous, Throwable cause) {
        super(message, cause);
        this.ambiguous = ambiguous;
    }

    /** Returns whether the final result cannot be confirmed. */
    public boolean isAmbiguous() {
        return ambiguous;
    }
}
