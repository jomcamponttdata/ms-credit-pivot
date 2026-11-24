package com.pivot.banking.credit.application.port.out;

import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CreditFilter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.UUID;

/** Persistence boundary for credits. */
public interface CreditRepositoryPort {

    /** Saves a credit with optimistic locking. */
    Single<Credit> save(Credit credit);

    /** Finds a credit by identifier. */
    Maybe<Credit> findById(UUID creditId);

    /** Finds a stable filtered page. */
    Flowable<Credit> findAll(CreditFilter filter, int page, int size);

    /** Finds all credits owned by a customer. */
    Flowable<Credit> findByCustomerId(UUID customerId);

    /** Deletes a credit using its version. */
    Completable delete(Credit credit);
}
