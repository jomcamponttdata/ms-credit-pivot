package com.pivot.banking.credit.application.service;

import com.pivot.banking.credit.application.port.in.CardUseCases.ChargeCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.CreateCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.CreateCardCommand;
import com.pivot.banking.credit.application.port.in.CardUseCases.DeleteCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.GetCards;
import com.pivot.banking.credit.application.port.in.CardUseCases.OperationCommand;
import com.pivot.banking.credit.application.port.in.CardUseCases.PayCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.UpdateCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.UpdateCardCommand;
import com.pivot.banking.credit.application.port.out.CreditCardRepositoryPort;
import com.pivot.banking.credit.application.port.out.CustomerGateway;
import com.pivot.banking.credit.application.port.out.MovementGateway;
import com.pivot.banking.credit.domain.Balance;
import com.pivot.banking.credit.domain.CreditCard;
import com.pivot.banking.credit.domain.CreditCardFilter;
import com.pivot.banking.credit.domain.CustomerType;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementReceipt;
import com.pivot.banking.credit.domain.MovementType;
import com.pivot.banking.credit.domain.ProductType;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import com.pivot.banking.credit.domain.exception.DownstreamServiceException;
import com.pivot.banking.credit.domain.exception.ResourceNotFoundException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Coordinates credit card CRUD, charges and payments. */
@Service
public class CardApplicationService
    implements CreateCard, GetCards, UpdateCard, DeleteCard, ChargeCard, PayCard {

  private final CreditCardRepositoryPort repository;
  private final CustomerGateway customers;
  private final MovementGateway movements;
  private final int maxPageSize;

  /** Creates the service with its persistence port and gateways. */
  public CardApplicationService(
      CreditCardRepositoryPort repository,
      CustomerGateway customers,
      MovementGateway movements,
      @Value("${credit.pagination.max-size:100}") int maxPageSize) {
    this.repository = repository;
    this.customers = customers;
    this.movements = movements;
    this.maxPageSize = maxPageSize;
  }

  /** Issues a card after validating the active customer and card type. */
  @Override
  public Single<CreditCard> create(CreateCardCommand command) {
    if (command.customerId() == null
        || command.cardType() == null
        || command.creditLimit() == null) {
      return Single.error(
          new BusinessRuleViolationException("Customer, type and limit are required"));
    }
    return customers
        .findById(command.customerId(), command.correlationId())
        .flatMap(
            customer -> {
              if (!customer.active()) {
                return Single.error(new BusinessRuleViolationException("Customer must be active"));
              }
              if (customer.customerType() != CustomerType.valueOf(command.cardType().name())) {
                return Single.error(
                    new BusinessRuleViolationException("Card type does not match customer type"));
              }
              CreditCard card =
                  new CreditCard(
                      UUID.randomUUID(),
                      command.customerId(),
                      command.cardType(),
                      maskedNumber(),
                      command.creditLimit(),
                      command.creditLimit(),
                      null,
                      Instant.now());
              return repository.save(card);
            });
  }

  /** Returns one stable paginated card page. */
  @Override
  public Flowable<CreditCard> findAll(CreditCardFilter filter, int page, int size) {
    if (page < 0 || size < 1 || size > maxPageSize) {
      return Flowable.error(
          new IllegalArgumentException(
              "page must be non-negative and size must be between 1 and " + maxPageSize));
    }
    return repository.findAll(filter, page, size);
  }

  /** Retrieves a card or reports that it does not exist. */
  @Override
  public Single<CreditCard> findById(UUID cardId) {
    return repository
        .findById(cardId)
        .switchIfEmpty(
            Maybe.error(new ResourceNotFoundException("Credit card not found: " + cardId)))
        .toSingle();
  }

  /** Returns the available credit of a card. */
  @Override
  public Single<Balance> getBalance(UUID cardId) {
    return findById(cardId)
        .map(card -> new Balance(card.id(), ProductType.CREDIT_CARD, card.availableCredit()));
  }

  /** Updates the credit limit without changing the customer, type or card number. */
  @Override
  public Single<CreditCard> update(UUID cardId, UpdateCardCommand command) {
    if (command.creditLimit() == null || command.creditLimit().signum() <= 0) {
      return Single.error(new BusinessRuleViolationException("Credit limit is required"));
    }
    return findById(cardId)
        .flatMap(
            card -> {
              Money newLimit = new Money(command.creditLimit(), card.creditLimit().currency());
              Money usedCredit = card.creditLimit().subtract(card.availableCredit());
              if (!newLimit.covers(usedCredit)) {
                return Single.error(
                    new BusinessRuleViolationException("Credit limit must cover the used credit"));
              }
              Money availableCredit = newLimit.subtract(usedCredit);
              return repository.save(
                  new CreditCard(
                      card.id(),
                      card.customerId(),
                      card.cardType(),
                      card.cardNumber(),
                      newLimit,
                      availableCredit,
                      card.version(),
                      card.createdAt()));
            });
  }

  /** Deletes a card using the version read from the database. */
  @Override
  public Completable delete(UUID cardId) {
    return findById(cardId).flatMapCompletable(repository::delete);
  }

  /** Charges a consumption to the card. */
  @Override
  public Single<MovementReceipt> charge(OperationCommand command) {
    return findById(command.cardId()).flatMap(card -> applyOperation(card, command, true));
  }

  /** Pays part of the used credit of the card. */
  @Override
  public Single<MovementReceipt> pay(OperationCommand command) {
    return findById(command.cardId()).flatMap(card -> applyOperation(card, command, false));
  }

  private Single<MovementReceipt> applyOperation(
      CreditCard original, OperationCommand command, boolean charge) {
    if (command.amount() == null || command.amount().amount().signum() <= 0) {
      return Single.error(
          new BusinessRuleViolationException("Operation amount must be greater than zero"));
    }
    CreditCard updated;
    try {
      updated = charge ? original.charge(command.amount()) : original.pay(command.amount());
    } catch (RuntimeException failure) {
      return Single.error(new BusinessRuleViolationException(failure.getMessage()));
    }
    return repository
        .save(updated)
        .flatMap(
            saved ->
                registerMovement(
                    saved, original, command, charge ? MovementType.CHARGE : MovementType.PAYMENT));
  }

  private Single<MovementReceipt> registerMovement(
      CreditCard updated, CreditCard original, OperationCommand command, MovementType type) {
    var movement =
        new MovementGateway.MovementCommand(
            updated.id(),
            ProductType.CREDIT_CARD,
            type,
            command.amount(),
            command.description(),
            command.correlationId());
    return movements
        .register(movement)
        .onErrorResumeNext(
            failure -> {
              if (!(failure instanceof DownstreamServiceException downstreamFailure)) {
                return Single.error(failure);
              }
              if (downstreamFailure.isAmbiguous()) {
                return Single.error(downstreamFailure);
              }
              CreditCard compensation =
                  type == MovementType.CHARGE
                      ? original.compensateCharge(command.amount())
                      : original.compensatePayment(command.amount());
              return repository
                  .save(compensation.withVersion(updated.version()))
                  .flatMap(ignored -> Single.<MovementReceipt>error(downstreamFailure))
                  .onErrorResumeNext(
                      rollbackFailure ->
                          rollbackFailure == downstreamFailure
                              ? Single.error(rollbackFailure)
                              : Single.error(
                                  new DownstreamServiceException(
                                      "Operation failed and local compensation could not be"
                                          + " confirmed",
                                      true,
                                      rollbackFailure)));
            });
  }

  private String maskedNumber() {
    int suffix = 1000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(9000);
    return "**** **** **** " + suffix;
  }
}
