package com.pivot.banking.credit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for the monetary value object. */
class MoneyTest {

  @Test
  void validatesAmountAndCurrency() {
    // Arrange
    // Act
    Money money = new Money(new BigDecimal("100.50"), "PEN");

    // Assert
    assertEquals(new BigDecimal("100.50"), money.amount());
    assertEquals("PEN", money.currency());
    assertThrows(IllegalArgumentException.class, () -> new Money(null, "PEN"));
    assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("1.234"), "PEN"));
    assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.ONE, null));
    assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.ONE, "pen"));
  }

  @Test
  void performsArithmeticAndComparesValues() {
    // Arrange
    Money left = new Money(new BigDecimal("100.00"), "PEN");
    Money right = new Money(new BigDecimal("25.50"), "PEN");
    Money otherCurrency = new Money(new BigDecimal("1.00"), "USD");

    // Act
    Money sum = left.add(right);
    Money difference = left.subtract(right);

    // Assert
    assertEquals(new BigDecimal("125.50"), sum.amount());
    assertEquals(new BigDecimal("74.50"), difference.amount());
    assertTrue(left.covers(right));
    assertFalse(right.covers(left));
    assertThrows(IllegalArgumentException.class, () -> left.add(otherCurrency));
    assertThrows(IllegalArgumentException.class, () -> left.subtract(otherCurrency));
    assertThrows(IllegalArgumentException.class, () -> left.covers(otherCurrency));
  }
}
