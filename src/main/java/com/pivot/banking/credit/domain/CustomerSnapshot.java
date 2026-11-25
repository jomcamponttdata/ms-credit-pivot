package com.pivot.banking.credit.domain;

import java.util.UUID;

/** Customer data required for credit eligibility checks. */
public record CustomerSnapshot(UUID id, CustomerType customerType, boolean active) {}
