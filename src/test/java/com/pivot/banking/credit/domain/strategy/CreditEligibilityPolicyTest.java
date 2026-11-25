package com.pivot.banking.credit.domain.strategy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CreditType;
import com.pivot.banking.credit.domain.CustomerSnapshot;
import com.pivot.banking.credit.domain.CustomerType;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for credit eligibility policies and their factory. */
class CreditEligibilityPolicyTest {

  @Test
  void personalPolicyAllowsOneCreditForActivePersonalCustomer() {
    // Arrange
    var policy = new PersonalCreditEligibilityPolicy();
    var customer = new CustomerSnapshot(UUID.randomUUID(), CustomerType.PERSONAL, true);

    // Act
    // Assert
    assertDoesNotThrow(() -> policy.validate(customer, List.of()));
    assertThrows(
        BusinessRuleViolationException.class,
        () -> policy.validate(customer, List.of(existingCredit(CreditType.PERSONAL))));
    assertThrows(
        BusinessRuleViolationException.class,
        () ->
            policy.validate(
                new CustomerSnapshot(UUID.randomUUID(), CustomerType.BUSINESS, true), List.of()));
    assertThrows(
        BusinessRuleViolationException.class,
        () ->
            policy.validate(
                new CustomerSnapshot(UUID.randomUUID(), CustomerType.PERSONAL, false), List.of()));
  }

  @Test
  void businessPolicyAllowsMultipleCreditsForActiveBusinessCustomer() {
    // Arrange
    var policy = new BusinessCreditEligibilityPolicy();
    var customer = new CustomerSnapshot(UUID.randomUUID(), CustomerType.BUSINESS, true);

    // Act
    // Assert
    assertDoesNotThrow(
        () -> policy.validate(customer, List.of(existingCredit(CreditType.BUSINESS))));
    assertThrows(
        BusinessRuleViolationException.class,
        () ->
            policy.validate(
                new CustomerSnapshot(UUID.randomUUID(), CustomerType.PERSONAL, true), List.of()));
    assertThrows(
        BusinessRuleViolationException.class,
        () ->
            policy.validate(
                new CustomerSnapshot(UUID.randomUUID(), CustomerType.BUSINESS, false), List.of()));
  }

  @Test
  void factorySelectsPoliciesAndReportsMissingOnes() {
    // Arrange
    var factory =
        new CreditEligibilityPolicyFactory(
            List.of(new PersonalCreditEligibilityPolicy(), new BusinessCreditEligibilityPolicy()));
    var emptyFactory = new CreditEligibilityPolicyFactory(List.of());

    // Act
    var personal = factory.get(CustomerType.PERSONAL);
    var business = factory.get(CustomerType.BUSINESS);

    // Assert
    assertInstanceOf(PersonalCreditEligibilityPolicy.class, personal);
    assertInstanceOf(BusinessCreditEligibilityPolicy.class, business);
    assertThrows(
        BusinessRuleViolationException.class, () -> emptyFactory.get(CustomerType.PERSONAL));
    assertThrows(
        BusinessRuleViolationException.class, () -> emptyFactory.get(CustomerType.BUSINESS));
  }

  private Credit existingCredit(CreditType type) {
    return new Credit(
        UUID.randomUUID(),
        UUID.randomUUID(),
        type,
        new Money(new BigDecimal("1000.00"), "PEN"),
        new Money(new BigDecimal("1000.00"), "PEN"),
        null,
        com.pivot.banking.credit.domain.CreditStatus.ACTIVE,
        0L,
        Instant.now());
  }
}
