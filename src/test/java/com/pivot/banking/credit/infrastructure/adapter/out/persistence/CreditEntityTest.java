package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CreditStatus;
import com.pivot.banking.credit.domain.CreditType;
import com.pivot.banking.credit.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for the credit relational mapping. */
class CreditEntityTest {

    @Test
    void mapsDomainToEntityAndBack() {
        // Arrange
        Credit credit = new Credit(UUID.randomUUID(), UUID.randomUUID(), CreditType.BUSINESS,
                new Money(new BigDecimal("5000.00"), "PEN"), new Money(new BigDecimal("1000.00"), "PEN"),
                new BigDecimal("12.5"), CreditStatus.ACTIVE, 2L, Instant.now());

        // Act
        CreditEntity entity = CreditEntity.fromDomain(credit);
        Credit restored = entity.toDomain();

        // Assert
        assertEquals(credit, restored);
        assertEquals("BUSINESS", entity.getCreditType());
        assertEquals(2L, entity.getVersion());
    }
}
