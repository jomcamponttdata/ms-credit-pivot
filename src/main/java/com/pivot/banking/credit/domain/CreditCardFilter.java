package com.pivot.banking.credit.domain;

import java.util.UUID;

/** Optional credit card list filter. */
public record CreditCardFilter(UUID customerId) {
}
