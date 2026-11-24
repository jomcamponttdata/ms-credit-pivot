package com.pivot.banking.credit.domain.strategy;

import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CustomerSnapshot;
import java.util.List;

/** Strategy for validating credit eligibility by customer type. */
public interface CreditEligibilityPolicy {

    /** Validates the customer and the credits they already hold. */
    void validate(CustomerSnapshot customer, List<Credit> existingCredits);
}
