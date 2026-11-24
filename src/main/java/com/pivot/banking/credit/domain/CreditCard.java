package com.pivot.banking.credit.domain;

import java.time.Instant;
import java.util.UUID;

/** Credit card aggregate with its available credit and optimistic version. */
public record CreditCard(UUID id, UUID customerId, CardType cardType, String cardNumber,
                         Money creditLimit, Money availableCredit, Long version, Instant createdAt) {

    /** Creates a card after enforcing its currency and limit invariants. */
    public CreditCard {
        if (id == null || customerId == null || cardType == null || cardNumber == null
                || cardNumber.isBlank() || creditLimit == null || creditLimit.amount().signum() <= 0
                || availableCredit == null || !creditLimit.currency().equals(availableCredit.currency())
                || availableCredit.amount().signum() < 0 || creditLimit.covers(availableCredit) == false
                || createdAt == null) {
            throw new IllegalArgumentException("Credit card fields do not satisfy the contract");
        }
    }

    /** Charges a consumption against the available credit. */
    public CreditCard charge(Money charge) {
        requireCurrency(charge);
        if (charge.amount().signum() <= 0 || !availableCredit.covers(charge)) {
            throw new IllegalStateException("Charge cannot exceed the available credit");
        }
        return new CreditCard(id, customerId, cardType, cardNumber, creditLimit,
                availableCredit.subtract(charge), version, createdAt);
    }

    /** Restores available credit after a confirmed charge failure. */
    public CreditCard compensateCharge(Money charge) {
        return new CreditCard(id, customerId, cardType, cardNumber, creditLimit,
                availableCredit.add(charge), version, createdAt);
    }

    /** Applies a card payment, returning credit that was previously used. */
    public CreditCard pay(Money payment) {
        requireCurrency(payment);
        Money usedCredit = creditLimit.subtract(availableCredit);
        if (payment.amount().signum() <= 0 || !usedCredit.covers(payment)) {
            throw new IllegalStateException("Payment cannot exceed the used credit");
        }
        return new CreditCard(id, customerId, cardType, cardNumber, creditLimit,
                availableCredit.add(payment), version, createdAt);
    }

    /** Restores used credit after a confirmed payment failure. */
    public CreditCard compensatePayment(Money payment) {
        return new CreditCard(id, customerId, cardType, cardNumber, creditLimit,
                availableCredit.subtract(payment), version, createdAt);
    }

    /** Returns a copy carrying the persistence version. */
    public CreditCard withVersion(Long updatedVersion) {
        return new CreditCard(id, customerId, cardType, cardNumber, creditLimit, availableCredit,
                updatedVersion, createdAt);
    }

    /** Ensures that the customer type matches the card type. */
    public void assertMatches(CustomerType customerType) {
        if (customerType != CustomerType.valueOf(cardType.name())) {
            throw new IllegalStateException("Card type does not match customer type");
        }
    }

    private void requireCurrency(Money value) {
        if (!availableCredit.currency().equals(value.currency())) {
            throw new IllegalArgumentException("Operation currency must match card currency");
        }
    }
}
