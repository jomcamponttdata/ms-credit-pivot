package com.pivot.banking.credit.domain;

import java.time.Instant;

/** Confirmation returned by transaction-service. */
public record MovementReceipt(String movementId, MovementType movementType, Money amount,
                              Instant createdAt) {
}
