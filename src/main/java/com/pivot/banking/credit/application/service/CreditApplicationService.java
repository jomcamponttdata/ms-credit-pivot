package com.pivot.banking.credit.application.service;

import com.pivot.banking.credit.application.port.in.CreditUseCases.CreateCredit;
import com.pivot.banking.credit.application.port.in.CreditUseCases.CreateCreditCommand;
import com.pivot.banking.credit.application.port.in.CreditUseCases.DeleteCredit;
import com.pivot.banking.credit.application.port.in.CreditUseCases.GetCredits;
import com.pivot.banking.credit.application.port.in.CreditUseCases.OperationCommand;
import com.pivot.banking.credit.application.port.in.CreditUseCases.PayCredit;
import com.pivot.banking.credit.application.port.in.CreditUseCases.UpdateCredit;
import com.pivot.banking.credit.application.port.in.CreditUseCases.UpdateCreditCommand;
import com.pivot.banking.credit.application.port.out.CreditRepositoryPort;
import com.pivot.banking.credit.application.port.out.CustomerGateway;
import com.pivot.banking.credit.application.port.out.MovementGateway;
import com.pivot.banking.credit.domain.Balance;
import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CreditFilter;
import com.pivot.banking.credit.domain.CreditStatus;
import com.pivot.banking.credit.domain.CustomerType;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementReceipt;
import com.pivot.banking.credit.domain.MovementType;
import com.pivot.banking.credit.domain.ProductType;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import com.pivot.banking.credit.domain.exception.DownstreamServiceException;
import com.pivot.banking.credit.domain.exception.ResourceNotFoundException;
import com.pivot.banking.credit.domain.strategy.CreditEligibilityPolicyFactory;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Coordinates credit CRUD, balances and payments. */
@Service
public class CreditApplicationService
    implements CreateCredit, GetCredits, UpdateCredit, DeleteCredit, PayCredit {

  private final CreditRepositoryPort repository;
  private final CustomerGateway customers;
  private final MovementGateway movements;
  private final CreditEligibilityPolicyFactory policies;
  private final int maxPageSize;

  /** Creates the service with its persistence port, gateways and policy factory. */
  public CreditApplicationService(
      CreditRepositoryPort repository,
      CustomerGateway customers,
      MovementGateway movements,
      CreditEligibilityPolicyFactory policies,
      @Value("${credit.pagination.max-size:100}") int maxPageSize) {
    this.repository = repository;
    this.customers = customers;
    this.movements = movements;
    this.policies = policies;
    this.maxPageSize = maxPageSize;
  }

  /** Creates a credit after validating the active customer and product rules. */
  @Override
  public Single<Credit> create(CreateCreditCommand command) {
    if (command.customerId() == null
        || command.creditType() == null
        || command.approvedAmount() == null
        || (command.interestRate() != null && command.interestRate().signum() < 0)) {
      return Single.error(
          new BusinessRuleViolationException(
              "Customer, type, amount and a non-negative interest rate are required"));
    }
    return customers
        .findById(command.customerId(), command.correlationId())
        .flatMap(
            customer -> {
              validateCustomer(customer, command.creditType());
              return repository
                  .findByCustomerId(customer.id())
                  .toList()
                  .flatMap(
                      existing -> {
                        policies.get(customer.customerType()).validate(customer, existing);
                        return repository.save(candidate(command));
                      });
            });
  }

  /** Returns one stable paginated credit page. */
  @Override
  public Flowable<Credit> findAll(CreditFilter filter, int page, int size) {
    if (page < 0 || size < 1 || size > maxPageSize) {
      return Flowable.error(
          new IllegalArgumentException(
              "page must be non-negative and size must be between 1 and " + maxPageSize));
    }
    return repository.findAll(filter, page, size);
  }

  /** Retrieves a credit or reports that it does not exist. */
  @Override
  public Single<Credit> findById(UUID creditId) {
    return repository
        .findById(creditId)
        .switchIfEmpty(Maybe.error(new ResourceNotFoundException("Credit not found: " + creditId)))
        .toSingle();
  }

  /** Returns the outstanding balance of a credit. */
  @Override
  public Single<Balance> getBalance(UUID creditId) {
    return findById(creditId)
        .map(credit -> new Balance(credit.id(), ProductType.CREDIT, credit.outstandingBalance()));
  }

  /** Updates editable fields without changing customer, type or balance. */
  @Override
  public Single<Credit> update(UUID creditId, UpdateCreditCommand command) {
    if (command.approvedAmount() == null) {
      return Single.error(new BusinessRuleViolationException("Approved amount is required"));
    }
    return findById(creditId)
        .flatMap(
            credit -> {
              Money approvedAmount =
                  new Money(command.approvedAmount(), credit.approvedAmount().currency());
              if (credit.outstandingBalance().amount().compareTo(approvedAmount.amount()) > 0) {
                return Single.error(
                    new BusinessRuleViolationException(
                        "Approved amount must cover the outstanding balance"));
              }
              if (command.interestRate() != null && command.interestRate().signum() < 0) {
                return Single.error(
                    new BusinessRuleViolationException("Interest rate cannot be negative"));
              }
              Credit updated =
                  new Credit(
                      credit.id(),
                      credit.customerId(),
                      credit.creditType(),
                      approvedAmount,
                      credit.outstandingBalance(),
                      command.interestRate(),
                      credit.status(),
                      credit.version(),
                      credit.createdAt());
              return repository.save(updated);
            });
  }

  /** Deletes a credit using the version read from the database. */
  @Override
  public Completable delete(UUID creditId) {
    return findById(creditId).flatMapCompletable(repository::delete);
  }

  /** Pays a credit and registers the corresponding movement. */
  @Override
  public Single<MovementReceipt> pay(OperationCommand command) {
    if (command.amount() == null || command.amount().amount().signum() <= 0) {
      return Single.error(
          new BusinessRuleViolationException("Payment amount must be greater than zero"));
    }
    return findById(command.creditId()).flatMap(original -> applyPayment(original, command));
  }

  private Single<MovementReceipt> applyPayment(Credit original, OperationCommand command) {
    Credit paid;
    try {
      paid = original.pay(command.amount());
    } catch (RuntimeException failure) {
      return Single.error(new BusinessRuleViolationException(failure.getMessage()));
    }
    return repository.save(paid).flatMap(saved -> registerMovement(saved, original, command));
  }

  private Single<MovementReceipt> registerMovement(
      Credit updated, Credit original, OperationCommand command) {
    var movement =
        new MovementGateway.MovementCommand(
            updated.id(),
            ProductType.CREDIT,
            MovementType.PAYMENT,
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
              return repository
                  .save(original.withVersion(updated.version()))
                  .flatMap(ignored -> Single.<MovementReceipt>error(downstreamFailure))
                  .onErrorResumeNext(
                      rollbackFailure ->
                          rollbackFailure == downstreamFailure
                              ? Single.error(rollbackFailure)
                              : Single.error(
                                  new DownstreamServiceException(
                                      "Payment failed and local compensation could not be"
                                          + " confirmed",
                                      true,
                                      rollbackFailure)));
            });
  }

  private void validateCustomer(
      com.pivot.banking.credit.domain.CustomerSnapshot customer,
      com.pivot.banking.credit.domain.CreditType type) {
    if (!customer.active()) {
      throw new BusinessRuleViolationException("Customer must be active");
    }
    CustomerType expected = CustomerType.valueOf(type.name());
    if (customer.customerType() != expected) {
      throw new BusinessRuleViolationException("Credit type does not match customer type");
    }
  }

  private Credit candidate(CreateCreditCommand command) {
    return new Credit(
        UUID.randomUUID(),
        command.customerId(),
        command.creditType(),
        command.approvedAmount(),
        command.approvedAmount(),
        command.interestRate(),
        CreditStatus.ACTIVE,
        null,
        Instant.now());
  }
}
