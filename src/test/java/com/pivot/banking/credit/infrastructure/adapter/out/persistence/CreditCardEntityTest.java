package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import com.pivot.banking.credit.domain.CardType;
import com.pivot.banking.credit.domain.CreditCard;
import com.pivot.banking.credit.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for the credit card relational mapping. */
class CreditCardEntityTest {

    @Test
    void mapsDomainToEntityAndBack() {
        // Arrange
        CreditCard card = new CreditCard(UUID.randomUUID(), UUID.randomUUID(), CardType.PERSONAL,
                "**** **** **** 1234", new Money(new BigDecimal("5000.00"), "PEN"),
                new Money(new BigDecimal("4000.00"), "PEN"), 1L, Instant.now());

        // Act
        CreditCardEntity entity = CreditCardEntity.fromDomain(card);
        CreditCard restored = entity.toDomain();

        // Assert
        assertEquals(card, restored);
        assertEquals("PERSONAL", entity.getCardType());
        assertEquals(1L, entity.getVersion());
    }
}
