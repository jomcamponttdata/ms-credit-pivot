package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import com.pivot.banking.credit.application.port.out.CreditRepositoryPort;
import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CreditFilter;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import com.pivot.banking.credit.domain.exception.ConcurrentModificationException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.dao.DataIntegrityViolationException;
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

/** R2DBC implementation of the credit persistence port. */
@Repository
public class R2dbcCreditRepositoryAdapter implements CreditRepositoryPort {

    private final SpringDataCreditRepository repository;
    private final R2dbcEntityTemplate template;

    /** Creates the adapter with the Spring Data repository and query template. */
    public R2dbcCreditRepositoryAdapter(SpringDataCreditRepository repository, R2dbcEntityTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    /** Saves a credit and translates constraint conflicts to domain errors. */
    @Override
    @Transactional
    public Single<Credit> save(Credit credit) {
        Mono<Credit> result = repository.save(CreditEntity.fromDomain(credit))
                .map(CreditEntity::toDomain)
                .onErrorMap(OptimisticLockingFailureException.class,
                        failure -> new ConcurrentModificationException(credit.id()))
                .onErrorMap(DataIntegrityViolationException.class, failure ->
                        isPersonalCreditDuplicate(failure)
                                ? new BusinessRuleViolationException(
                                        "A personal customer may have only one credit")
                                : failure);
        return RxJava3Adapter.monoToSingle(result);
    }

    /** Finds a credit by identifier. */
    @Override
    public Maybe<Credit> findById(UUID creditId) {
        Mono<Credit> result = repository.findById(creditId).map(CreditEntity::toDomain);
        return RxJava3Adapter.monoToMaybe(result);
    }

    /** Finds a stable filtered page. */
    @Override
    public Flowable<Credit> findAll(CreditFilter filter, int page, int size) {
        Criteria criteria = filter.customerId() == null
                ? Criteria.empty()
                : Criteria.where("customerId").is(filter.customerId());
        Query query = Query.query(criteria)
                .sort(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
                .limit(size)
                .offset((long) page * size);
        Flux<Credit> result = template.select(query, CreditEntity.class).map(CreditEntity::toDomain);
        return RxJava3Adapter.fluxToFlowable(result);
    }

    /** Finds all credits owned by a customer. */
    @Override
    public Flowable<Credit> findByCustomerId(UUID customerId) {
        Query query = Query.query(Criteria.where("customerId").is(customerId));
        Flux<Credit> result = template.select(query, CreditEntity.class).map(CreditEntity::toDomain);
        return RxJava3Adapter.fluxToFlowable(result);
    }

    /** Deletes a credit using its optimistic version. */
    @Override
    @Transactional
    public Completable delete(Credit credit) {
        Mono<Void> result = repository.delete(CreditEntity.fromDomain(credit))
                .onErrorMap(OptimisticLockingFailureException.class,
                        failure -> new ConcurrentModificationException(credit.id()));
        return RxJava3Adapter.monoToCompletable(result);
    }

    /** Identifies the unique-index violation for personal credit products. */
    private boolean isPersonalCreditDuplicate(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("uq_personal_credit")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
