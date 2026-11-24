package com.pivot.banking.credit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests for the credit card aggregate. */
class CreditCardTest {

    @Test
    void chargeReducesAvailableCreditAndPaymentRestoresIt() {
        // Arrange
        CreditCard card = card();
        Money value = new Money(new BigDecimal("200.00"), "PEN");

        // Act
        CreditCard charged = card.charge(value);
        CreditCard paid = charged.pay(value);

        // Assert
        assertEquals(new BigDecimal("700.00"), charged.availableCredit().amount());
        assertEquals(new BigDecimal("900.00"), paid.availableCredit().amount());
    }

    @Test
    void rejectsInvalidOperationsAndSupportsCompensation() {
        // Arrange
        CreditCard card = card();
        Money value = new Money(new BigDecimal("100.00"), "PEN");

        // Act
        CreditCard chargeCompensated = card.compensateCharge(value);
        CreditCard paymentCompensated = card.compensatePayment(value);
        CreditCard versioned = card.withVersion(3L);

        // Assert
        assertThrows(IllegalStateException.class, () -> card.charge(new Money(new BigDecimal("1001.00"), "PEN")));
        assertThrows(IllegalStateException.class, () -> card.pay(new Money(new BigDecimal("101.00"), "PEN")));
        assertThrows(IllegalStateException.class, () -> card.charge(new Money(BigDecimal.ZERO, "PEN")));
        assertThrows(IllegalStateException.class, () -> card.pay(new Money(BigDecimal.ZERO, "PEN")));
        assertThrows(IllegalArgumentException.class, () -> card.charge(new Money(value.amount(), "USD")));
        assertEquals(new BigDecimal("1000.00"), chargeCompensated.availableCredit().amount());
        assertEquals(new BigDecimal("800.00"), paymentCompensated.availableCredit().amount());
        assertEquals(3L, versioned.version());
    }

    @Test
    void verifiesCardTypeMatchesCustomerType() {
        // Arrange
        CreditCard card = card();

        // Act
        // Assert
        card.assertMatches(CustomerType.PERSONAL);
        assertThrows(IllegalStateException.class, () -> card.assertMatches(CustomerType.BUSINESS));
    }

    @Test
    void rejectsEveryInvalidCardConstruction() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Money limit = new Money(new BigDecimal("1000.00"), "PEN");
        Money available = new Money(new BigDecimal("900.00"), "PEN");
        var invalidCards = java.util.List.<Runnable>of(
                () -> new CreditCard(null, customerId, CardType.PERSONAL, "1234", limit, available, 0L, Instant.now()),
                () -> new CreditCard(id, null, CardType.PERSONAL, "1234", limit, available, 0L, Instant.now()),
                () -> new CreditCard(id, customerId, null, "1234", limit, available, 0L, Instant.now()),
                () -> new CreditCard(id, customerId, CardType.PERSONAL, null, limit, available, 0L, Instant.now()),
                () -> new CreditCard(id, customerId, CardType.PERSONAL, " ", limit, available, 0L, Instant.now()),
                () -> new CreditCard(id, customerId, CardType.PERSONAL, "1234", null, available, 0L, Instant.now()),
                () -> new CreditCard(id, customerId, CardType.PERSONAL, "1234",
                        new Money(BigDecimal.ZERO, "PEN"), available, 0L, Instant.now()),
                () -> new CreditCard(id, customerId, CardType.PERSONAL, "1234", limit, null, 0L, Instant.now()),
                () -> new CreditCard(id, customerId, CardType.PERSONAL, "1234", limit,
                        new Money(new BigDecimal("900.00"), "USD"), 0L, Instant.now()),
                () -> new CreditCard(id, customerId, CardType.PERSONAL, "1234", limit,
                        new Money(new BigDecimal("-1.00"), "PEN"), 0L, Instant.now()),
                () -> new CreditCard(id, customerId, CardType.PERSONAL, "1234", limit,
                        new Money(new BigDecimal("1001.00"), "PEN"), 0L, Instant.now()),
                () -> new CreditCard(id, customerId, CardType.PERSONAL, "1234", limit, available, 0L, null));

        // Act
        var exceptions = invalidCards.stream()
                .map(candidate -> org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class, candidate::run))
                .toList();

        // Assert
        assertEquals(12, exceptions.size());
    }

    private CreditCard card() {
        return new CreditCard(UUID.randomUUID(), UUID.randomUUID(), CardType.PERSONAL,
                "**** **** **** 1234", new Money(new BigDecimal("1000.00"), "PEN"),
                new Money(new BigDecimal("900.00"), "PEN"), 0L, Instant.now());
    }
}
