package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

/** Spring Data repository for credit rows. */
public interface SpringDataCreditRepository extends ReactiveCrudRepository<CreditEntity, UUID> {
}
