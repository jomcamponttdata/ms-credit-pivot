package com.pivot.banking.credit.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Credit aggregate with its outstanding balance and optimistic version. */
public record Credit(
    UUID id,
    UUID customerId,
    CreditType creditType,
    Money approvedAmount,
    Money outstandingBalance,
    BigDecimal interestRate,
    CreditStatus status,
    Long version,
    Instant createdAt) {

  /** Creates a credit after enforcing its currency and amount invariants. */
  public Credit {
    if (id == null
        || customerId == null
        || creditType == null
        || approvedAmount == null
        || approvedAmount.amount().signum() <= 0
        || outstandingBalance == null
        || !approvedAmount.currency().equals(outstandingBalance.currency())
        || outstandingBalance.amount().signum() < 0
        || approvedAmount.covers(outstandingBalance) == false
        || (interestRate != null && interestRate.signum() < 0)
        || status == null
        || createdAt == null) {
      throw new IllegalArgumentException("Credit fields do not satisfy the contract");
    }
  }

  /** Applies a payment and marks the credit as paid off when appropriate. */
  public Credit pay(Money payment) {
    if (!outstandingBalance.currency().equals(payment.currency())) {
      throw new IllegalArgumentException("Payment currency must match credit currency");
    }
    if (payment.amount().signum() <= 0 || !outstandingBalance.covers(payment)) {
      throw new IllegalStateException("Payment cannot exceed the outstanding balance");
    }
    Money remaining = outstandingBalance.subtract(payment);
    return new Credit(
        id,
        customerId,
        creditType,
        approvedAmount,
        remaining,
        interestRate,
        remaining.amount().signum() == 0 ? CreditStatus.PAID_OFF : CreditStatus.ACTIVE,
        version,
        createdAt);
  }

  /** Restores the balance after a confirmed movement failure. */
  public Credit compensatePayment(Money payment) {
    return new Credit(
        id,
        customerId,
        creditType,
        approvedAmount,
        outstandingBalance.add(payment),
        interestRate,
        CreditStatus.ACTIVE,
        version,
        createdAt);
  }

  /** Returns a copy carrying the persistence version. */
  public Credit withVersion(Long updatedVersion) {
    return new Credit(
        id,
        customerId,
        creditType,
        approvedAmount,
        outstandingBalance,
        interestRate,
        status,
        updatedVersion,
        createdAt);
  }
}
