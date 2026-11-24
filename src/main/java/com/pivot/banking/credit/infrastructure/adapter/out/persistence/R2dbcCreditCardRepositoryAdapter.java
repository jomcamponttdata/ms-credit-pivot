package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import com.pivot.banking.credit.application.port.out.CreditCardRepositoryPort;
import com.pivot.banking.credit.domain.CreditCard;
import com.pivot.banking.credit.domain.CreditCardFilter;
import com.pivot.banking.credit.domain.exception.ConcurrentModificationException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** R2DBC implementation of the credit card persistence port. */
@Repository
public class R2dbcCreditCardRepositoryAdapter implements CreditCardRepositoryPort {

    private final SpringDataCreditCardRepository repository;
    private final R2dbcEntityTemplate template;

    /** Creates the adapter with the Spring Data repository and query template. */
    public R2dbcCreditCardRepositoryAdapter(SpringDataCreditCardRepository repository, R2dbcEntityTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    /** Saves a card with optimistic locking. */
    @Override
    @Transactional
    public Single<CreditCard> save(CreditCard card) {
        Mono<CreditCard> result = repository.save(CreditCardEntity.fromDomain(card))
                .map(CreditCardEntity::toDomain)
                .onErrorMap(OptimisticLockingFailureException.class,
                        failure -> new ConcurrentModificationException(card.id()));
        return RxJava3Adapter.monoToSingle(result);
    }

    /** Finds a card by identifier. */
    @Override
    public Maybe<CreditCard> findById(UUID cardId) {
        Mono<CreditCard> result = repository.findById(cardId).map(CreditCardEntity::toDomain);
        return RxJava3Adapter.monoToMaybe(result);
    }

    /** Finds a stable filtered page. */
    @Override
    public Flowable<CreditCard> findAll(CreditCardFilter filter, int page, int size) {
        Criteria criteria = filter.customerId() == null
                ? Criteria.empty()
                : Criteria.where("customerId").is(filter.customerId());
        Query query = Query.query(criteria)
                .sort(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
                .limit(size)
                .offset((long) page * size);
        Flux<CreditCard> result = template.select(query, CreditCardEntity.class).map(CreditCardEntity::toDomain);
        return RxJava3Adapter.fluxToFlowable(result);
    }

    /** Deletes a card using its optimistic version. */
    @Override
    @Transactional
    public Completable delete(CreditCard card) {
        Mono<Void> result = repository.delete(CreditCardEntity.fromDomain(card))
                .onErrorMap(OptimisticLockingFailureException.class,
                        failure -> new ConcurrentModificationException(card.id()));
        return RxJava3Adapter.monoToCompletable(result);
    }
}
