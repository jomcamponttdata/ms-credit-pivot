package com.pivot.banking.credit.domain.strategy;

import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CustomerSnapshot;
import com.pivot.banking.credit.domain.CustomerType;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;

import java.util.List;

/** Allows multiple credits for an active business customer. */
public class BusinessCreditEligibilityPolicy implements CreditEligibilityPolicy {

    /** Rejects inactive or non-business customers. */
    @Override
    public void validate(CustomerSnapshot customer, List<Credit> existingCredits) {
        if (customer.customerType() != CustomerType.BUSINESS || !customer.active()) {
            throw new BusinessRuleViolationException(
                    "A business credit requires an active business customer");
        }
    }
}
