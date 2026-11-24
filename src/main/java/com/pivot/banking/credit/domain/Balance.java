package com.pivot.banking.credit.domain;

import java.util.UUID;

/** Balance response shared by credit and credit card endpoints. */
public record Balance(UUID productId, ProductType productType, Money balance) {
}
