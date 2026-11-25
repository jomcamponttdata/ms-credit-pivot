package com.pivot.banking.credit.domain.strategy;

import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CustomerSnapshot;
import com.pivot.banking.credit.domain.CustomerType;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import java.util.List;

/** Allows one credit per personal customer. */
public class PersonalCreditEligibilityPolicy implements CreditEligibilityPolicy {

  /** Rejects inactive, non-personal and already-served customers. */
  @Override
  public void validate(CustomerSnapshot customer, List<Credit> existingCredits) {
    if (customer.customerType() != CustomerType.PERSONAL || !customer.active()) {
      throw new BusinessRuleViolationException(
          "A personal credit requires an active personal customer");
    }
    if (!existingCredits.isEmpty()) {
      throw new BusinessRuleViolationException("A personal customer may have only one credit");
    }
  }
}
