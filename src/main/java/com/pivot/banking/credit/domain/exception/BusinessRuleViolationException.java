package com.pivot.banking.credit.domain.exception;

/** Signals that a domain rule prevented the operation. */
public class BusinessRuleViolationException extends RuntimeException {

  /** Creates the exception with a business-readable detail. */
  public BusinessRuleViolationException(String message) {
    super(message);
  }
}
