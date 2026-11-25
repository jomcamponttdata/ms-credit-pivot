package com.pivot.banking.credit.domain.strategy;

import com.pivot.banking.credit.domain.CustomerType;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import java.util.List;

/** Selects the eligibility policy matching the customer type. */
public class CreditEligibilityPolicyFactory {

  private final List<CreditEligibilityPolicy> policies;

  /** Creates the factory with all registered policies. */
  public CreditEligibilityPolicyFactory(List<CreditEligibilityPolicy> policies) {
    this.policies = List.copyOf(policies);
  }

  /** Returns the policy for the given customer type. */
  public CreditEligibilityPolicy get(CustomerType customerType) {
    return switch (customerType) {
      case PERSONAL ->
          policies.stream()
              .filter(PersonalCreditEligibilityPolicy.class::isInstance)
              .findFirst()
              .orElseThrow(() -> new BusinessRuleViolationException("Personal policy unavailable"));
      case BUSINESS ->
          policies.stream()
              .filter(BusinessCreditEligibilityPolicy.class::isInstance)
              .findFirst()
              .orElseThrow(() -> new BusinessRuleViolationException("Business policy unavailable"));
    };
  }
}
