package com.pivot.banking.credit.application.port.out;

import com.pivot.banking.credit.domain.CreditCard;
import com.pivot.banking.credit.domain.CreditCardFilter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.UUID;

/** Persistence boundary for credit cards. */
public interface CreditCardRepositoryPort {

    /** Saves a card with optimistic locking. */
    Single<CreditCard> save(CreditCard card);

    /** Finds a card by identifier. */
    Maybe<CreditCard> findById(UUID cardId);

    /** Finds a stable filtered page. */
    Flowable<CreditCard> findAll(CreditCardFilter filter, int page, int size);

    /** Deletes a card using its version. */
    Completable delete(CreditCard card);
}
