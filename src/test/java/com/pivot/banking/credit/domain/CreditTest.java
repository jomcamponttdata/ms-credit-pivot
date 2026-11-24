package com.pivot.banking.credit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests for the credit aggregate. */
class CreditTest {

    @Test
    void paymentReducesBalanceAndMarksCreditAsPaidOff() {
        // Arrange
        Credit credit = credit(new BigDecimal("100.00"), new BigDecimal("100.00"));
        Money payment = new Money(new BigDecimal("40.00"), "PEN");

        // Act
        Credit partiallyPaid = credit.pay(payment);
        Credit fullyPaid = credit.pay(new Money(new BigDecimal("100.00"), "PEN"));

        // Assert
        assertEquals(new BigDecimal("60.00"), partiallyPaid.outstandingBalance().amount());
        assertEquals(CreditStatus.ACTIVE, partiallyPaid.status());
        assertEquals(BigDecimal.ZERO, fullyPaid.outstandingBalance().amount().stripTrailingZeros());
        assertEquals(CreditStatus.PAID_OFF, fullyPaid.status());
    }

    @Test
    void rejectsInvalidPaymentsAndRestoresBalanceAfterFailure() {
        // Arrange
        Credit credit = credit(new BigDecimal("200.00"), new BigDecimal("60.00"));
        Money payment = new Money(new BigDecimal("60.00"), "PEN");

        // Act
        Credit restored = credit.compensatePayment(payment);
        Credit versioned = credit.withVersion(5L);

        // Assert
        assertThrows(IllegalStateException.class, () -> credit.pay(new Money(new BigDecimal("61.00"), "PEN")));
        assertThrows(IllegalStateException.class, () -> credit.pay(new Money(BigDecimal.ZERO, "PEN")));
        assertThrows(IllegalArgumentException.class, () -> credit.pay(new Money(new BigDecimal("1.00"), "USD")));
        assertEquals(new BigDecimal("120.00"), restored.outstandingBalance().amount());
        assertEquals(5L, versioned.version());
        assertThrows(IllegalArgumentException.class,
                () -> new Credit(UUID.randomUUID(), UUID.randomUUID(), CreditType.PERSONAL,
                        new Money(BigDecimal.ZERO, "PEN"), new Money(BigDecimal.ZERO, "PEN"),
                        null, CreditStatus.ACTIVE, 0L, Instant.now()));
    }

    @Test
    void rejectsEveryInvalidCreditConstruction() {
        // Arrange
        Money approved = new Money(new BigDecimal("100.00"), "PEN");
        Money outstanding = new Money(new BigDecimal("50.00"), "PEN");
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        var invalidCredits = java.util.List.<Runnable>of(
                () -> new Credit(null, customerId, CreditType.PERSONAL, approved, outstanding,
                        null, CreditStatus.ACTIVE, 0L, Instant.now()),
                () -> new Credit(id, null, CreditType.PERSONAL, approved, outstanding,
                        null, CreditStatus.ACTIVE, 0L, Instant.now()),
                () -> new Credit(id, customerId, null, approved, outstanding,
                        null, CreditStatus.ACTIVE, 0L, Instant.now()),
                () -> new Credit(id, customerId, CreditType.PERSONAL, null, outstanding,
                        null, CreditStatus.ACTIVE, 0L, Instant.now()),
                () -> new Credit(id, customerId, CreditType.PERSONAL,
                        new Money(BigDecimal.ZERO, "PEN"), outstanding, null, CreditStatus.ACTIVE, 0L, Instant.now()),
                () -> new Credit(id, customerId, CreditType.PERSONAL, approved, null,
                        null, CreditStatus.ACTIVE, 0L, Instant.now()),
                () -> new Credit(id, customerId, CreditType.PERSONAL, approved,
                        new Money(new BigDecimal("50.00"), "USD"), null, CreditStatus.ACTIVE, 0L, Instant.now()),
                () -> new Credit(id, customerId, CreditType.PERSONAL, approved,
                        new Money(new BigDecimal("-1.00"), "PEN"), null, CreditStatus.ACTIVE, 0L, Instant.now()),
                () -> new Credit(id, customerId, CreditType.PERSONAL, approved,
                        new Money(new BigDecimal("101.00"), "PEN"), null, CreditStatus.ACTIVE, 0L, Instant.now()),
                () -> new Credit(id, customerId, CreditType.PERSONAL, approved, outstanding,
                        new BigDecimal("-1.0"), CreditStatus.ACTIVE, 0L, Instant.now()));

        // Act
        var exceptions = invalidCredits.stream()
                .map(candidate -> org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class, candidate::run))
                .toList();

        // Assert
        assertEquals(10, exceptions.size());
    }

    private Credit credit(BigDecimal approved, BigDecimal outstanding) {
        return new Credit(UUID.randomUUID(), UUID.randomUUID(), CreditType.PERSONAL,
                new Money(approved, "PEN"), new Money(outstanding, "PEN"), new BigDecimal("18.5"),
                CreditStatus.ACTIVE, 0L, Instant.now());
    }
}
