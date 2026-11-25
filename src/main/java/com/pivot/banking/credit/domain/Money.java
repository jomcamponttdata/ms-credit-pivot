package com.pivot.banking.credit.domain;

import java.math.BigDecimal;

/** Monetary value with an ISO 4217 currency. */
public record Money(BigDecimal amount, String currency) {

  /** Validates amount precision and currency format. */
  public Money {
    if (amount == null
        || amount.stripTrailingZeros().scale() > 2
        || currency == null
        || !currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("Invalid monetary amount or currency");
    }
  }

  /** Adds another value expressed in the same currency. */
  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  /** Subtracts another value expressed in the same currency. */
  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  /** Returns whether this value covers the other one. */
  public boolean covers(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount) >= 0;
  }

  /** Ensures that monetary arithmetic never mixes currencies. */
  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException("Currencies must match");
    }
  }
}
