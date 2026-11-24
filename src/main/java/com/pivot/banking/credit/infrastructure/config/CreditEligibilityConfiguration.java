package com.pivot.banking.credit.infrastructure.config;

import com.pivot.banking.credit.domain.strategy.BusinessCreditEligibilityPolicy;
import com.pivot.banking.credit.domain.strategy.CreditEligibilityPolicyFactory;
import com.pivot.banking.credit.domain.strategy.PersonalCreditEligibilityPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Wires pure credit eligibility policies into the application. */
@Configuration
public class CreditEligibilityConfiguration {

    /** Creates the policy factory with all supported customer types. */
    @Bean
    public CreditEligibilityPolicyFactory creditEligibilityPolicyFactory() {
        return new CreditEligibilityPolicyFactory(List.of(
                new PersonalCreditEligibilityPolicy(), new BusinessCreditEligibilityPolicy()));
    }
}
