package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/** Spring Data repository for credit rows. */
public interface SpringDataCreditRepository extends ReactiveCrudRepository<CreditEntity, UUID> {}
