package com.pivot.banking.credit.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pivot.banking.credit.application.port.in.CardUseCases.CreateCardCommand;
import com.pivot.banking.credit.application.port.in.CardUseCases.OperationCommand;
import com.pivot.banking.credit.application.port.in.CardUseCases.UpdateCardCommand;
import com.pivot.banking.credit.application.port.out.CreditCardRepositoryPort;
import com.pivot.banking.credit.application.port.out.CustomerGateway;
import com.pivot.banking.credit.application.port.out.MovementGateway;
import com.pivot.banking.credit.domain.CardType;
import com.pivot.banking.credit.domain.CreditCard;
import com.pivot.banking.credit.domain.CreditCardFilter;
import com.pivot.banking.credit.domain.CustomerSnapshot;
import com.pivot.banking.credit.domain.CustomerType;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementReceipt;
import com.pivot.banking.credit.domain.MovementType;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import com.pivot.banking.credit.domain.exception.DownstreamServiceException;
import com.pivot.banking.credit.domain.exception.ResourceNotFoundException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardApplicationServiceTest {

  @Mock private CreditCardRepositoryPort repository;
  @Mock private CustomerGateway customers;
  @Mock private MovementGateway movements;

  private CardApplicationService service;

  @BeforeEach
  void setUp() {
    service = new CardApplicationService(repository, customers, movements, 100);
  }

  @Test
  void issuesCardWithFullAvailableCredit() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    var command =
        new CreateCardCommand(
            customerId,
            CardType.PERSONAL,
            new Money(new BigDecimal("1000.00"), "PEN"),
            "card-correlation-1");
    when(customers.findById(eq(customerId), nullable(String.class)))
        .thenReturn(Single.just(new CustomerSnapshot(customerId, CustomerType.PERSONAL, true)));
    when(repository.save(any(CreditCard.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var result = service.create(command);

    // Assert
    result
        .test()
        .assertValue(
            card -> {
              assertEquals(customerId, card.customerId());
              assertEquals(card.creditLimit(), card.availableCredit());
              assertEquals(true, card.cardNumber().startsWith("****"));
              assertEquals(null, card.version());
              return true;
            })
        .assertComplete();
    verify(customers).findById(customerId, "card-correlation-1");
  }

  @Test
  void rejectsCardIssuanceForInactiveOrMismatchedCustomer() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    var command =
        new CreateCardCommand(
            customerId, CardType.PERSONAL, new Money(new BigDecimal("1000.00"), "PEN"));
    when(customers.findById(eq(customerId), nullable(String.class)))
        .thenReturn(Single.just(new CustomerSnapshot(customerId, CustomerType.PERSONAL, false)));

    // Act
    var inactive = service.create(command);
    when(customers.findById(eq(customerId), nullable(String.class)))
        .thenReturn(Single.just(new CustomerSnapshot(customerId, CustomerType.BUSINESS, true)));
    var mismatched = service.create(command);
    var incomplete = service.create(new CreateCardCommand(null, null, null));

    // Assert
    inactive.test().assertError(BusinessRuleViolationException.class);
    mismatched.test().assertError(BusinessRuleViolationException.class);
    incomplete.test().assertError(BusinessRuleViolationException.class);
    verify(repository, never()).save(any(CreditCard.class));
  }

  @Test
  void findsCardsAndValidatesPagination() {
    // Arrange
    CreditCard card = card();
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));
    when(repository.findAll(new CreditCardFilter(null), 0, 20)).thenReturn(Flowable.just(card));
    UUID missing = UUID.randomUUID();
    when(repository.findById(missing)).thenReturn(Maybe.empty());

    // Act
    var found = service.findById(card.id());
    var page = service.findAll(new CreditCardFilter(null), 0, 20);
    var notFound = service.findById(missing);
    var invalid = service.findAll(new CreditCardFilter(null), 1, 0);

    // Assert
    found.test().assertValue(card).assertComplete();
    page.test().assertValue(card).assertComplete();
    notFound.test().assertError(ResourceNotFoundException.class);
    invalid.test().assertError(IllegalArgumentException.class);
  }

  @Test
  void returnsAvailableCredit() {
    // Arrange
    CreditCard card = card();
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));

    // Act
    var result = service.getBalance(card.id());

    // Assert
    result
        .test()
        .assertValue(
            balance -> {
              assertEquals(card.availableCredit(), balance.balance());
              return true;
            })
        .assertComplete();
  }

  @Test
  void updatesCreditLimitWithoutChangingIdentity() {
    // Arrange
    CreditCard card = card();
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));
    when(repository.save(any(CreditCard.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    var command = new UpdateCardCommand(new BigDecimal("6000.00"));

    // Act
    var result = service.update(card.id(), command);

    // Assert
    result
        .test()
        .assertValue(
            updated -> {
              assertEquals(card.id(), updated.id());
              assertEquals(new BigDecimal("6000.00"), updated.creditLimit().amount());
              assertEquals(new BigDecimal("5000.00"), updated.availableCredit().amount());
              return true;
            })
        .assertComplete();
  }

  @Test
  void rejectsInvalidCardUpdates() {
    // Arrange
    CreditCard card = card();
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));

    // Act
    var missing = service.update(card.id(), new UpdateCardCommand(null));
    var wrongCurrency = service.update(card.id(), new UpdateCardCommand(BigDecimal.ZERO));
    var tooSmall = service.update(card.id(), new UpdateCardCommand(new BigDecimal("100.00")));

    // Assert
    missing.test().assertError(BusinessRuleViolationException.class);
    wrongCurrency.test().assertError(BusinessRuleViolationException.class);
    tooSmall.test().assertError(BusinessRuleViolationException.class);
    verify(repository, never()).save(any(CreditCard.class));
  }

  @Test
  void deletesExistingCardAndReportsMissingCard() {
    // Arrange
    CreditCard card = card();
    UUID missing = UUID.randomUUID();
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));
    when(repository.findById(missing)).thenReturn(Maybe.empty());
    when(repository.delete(card)).thenReturn(Completable.complete());

    // Act
    var deleted = service.delete(card.id());
    var notFound = service.delete(missing);

    // Assert
    deleted.test().assertComplete();
    notFound.test().assertError(ResourceNotFoundException.class);
  }

  @Test
  void chargesCardAndRegistersMovement() {
    // Arrange
    CreditCard card = card();
    Money amount = new Money(new BigDecimal("200.00"), "PEN");
    var receipt = new MovementReceipt("movement-1", MovementType.CHARGE, amount, Instant.now());
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));
    when(repository.save(any(CreditCard.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    when(movements.register(any(MovementGateway.MovementCommand.class)))
        .thenReturn(Single.just(receipt));

    // Act
    var result =
        service.charge(new OperationCommand(card.id(), amount, "Purchase", "charge-correlation-1"));

    // Assert
    result.test().assertValue(receipt).assertComplete();
    var movement = org.mockito.ArgumentCaptor.forClass(MovementGateway.MovementCommand.class);
    verify(movements).register(movement.capture());
    assertEquals("charge-correlation-1", movement.getValue().correlationId());
  }

  @Test
  void paysUsedCreditAndRegistersMovement() {
    // Arrange
    CreditCard card = card();
    Money amount = new Money(new BigDecimal("200.00"), "PEN");
    var receipt = new MovementReceipt("movement-2", MovementType.PAYMENT, amount, Instant.now());
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));
    when(repository.save(any(CreditCard.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    when(movements.register(any(MovementGateway.MovementCommand.class)))
        .thenReturn(Single.just(receipt));

    // Act
    var result = service.pay(new OperationCommand(card.id(), amount, "Payment"));

    // Assert
    result.test().assertValue(receipt).assertComplete();
  }

  @Test
  void rejectsInvalidCardOperations() {
    // Arrange
    CreditCard card = card();
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));

    // Act
    var overLimit =
        service.charge(
            new OperationCommand(card.id(), new Money(new BigDecimal("5001.00"), "PEN"), null));
    var overPayment =
        service.pay(
            new OperationCommand(card.id(), new Money(new BigDecimal("1001.00"), "PEN"), null));
    var invalidAmount =
        service.charge(
            new OperationCommand(card.id(), new Money(new BigDecimal("0.00"), "PEN"), null));
    var wrongCurrency =
        service.charge(
            new OperationCommand(card.id(), new Money(new BigDecimal("1.00"), "USD"), null));

    // Assert
    overLimit.test().assertError(BusinessRuleViolationException.class);
    overPayment.test().assertError(BusinessRuleViolationException.class);
    invalidAmount.test().assertError(BusinessRuleViolationException.class);
    wrongCurrency.test().assertError(BusinessRuleViolationException.class);
    verify(repository, never()).save(any(CreditCard.class));
  }

  @Test
  void compensatesChargeOnlyAfterConfirmedFailure() {
    // Arrange
    CreditCard card = card();
    Money amount = new Money(new BigDecimal("100.00"), "PEN");
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));
    when(repository.save(any(CreditCard.class)))
        .thenReturn(Single.just(card.withVersion(1L)))
        .thenReturn(Single.just(card));
    when(movements.register(any(MovementGateway.MovementCommand.class)))
        .thenReturn(Single.error(new DownstreamServiceException("Rejected", false, null)));

    // Act
    var result = service.charge(new OperationCommand(card.id(), amount, null));

    // Assert
    result.test().assertError(DownstreamServiceException.class);
    verify(repository, times(2)).save(any(CreditCard.class));
  }

  @Test
  void compensatesPaymentOnlyAfterConfirmedFailure() {
    // Arrange
    CreditCard usedCard =
        new CreditCard(
            card().id(),
            card().customerId(),
            CardType.PERSONAL,
            "**** **** **** 1234",
            new Money(new BigDecimal("5000.00"), "PEN"),
            new Money(new BigDecimal("4000.00"), "PEN"),
            0L,
            Instant.now());
    Money amount = new Money(new BigDecimal("100.00"), "PEN");
    when(repository.findById(usedCard.id())).thenReturn(Maybe.just(usedCard));
    when(repository.save(any(CreditCard.class)))
        .thenReturn(Single.just(usedCard.withVersion(1L)))
        .thenReturn(Single.just(usedCard));
    when(movements.register(any(MovementGateway.MovementCommand.class)))
        .thenReturn(Single.error(new DownstreamServiceException("Rejected", false, null)));

    // Act
    var result = service.pay(new OperationCommand(usedCard.id(), amount, null));

    // Assert
    result.test().assertError(DownstreamServiceException.class);
    verify(repository, times(2)).save(any(CreditCard.class));
  }

  @Test
  void doesNotCompensateAmbiguousResults() {
    // Arrange
    CreditCard card = card();
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));
    when(repository.save(any(CreditCard.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    when(movements.register(any(MovementGateway.MovementCommand.class)))
        .thenReturn(Single.error(new DownstreamServiceException("Timeout", true, null)));

    // Act
    var result =
        service.charge(
            new OperationCommand(card.id(), new Money(new BigDecimal("100.00"), "PEN"), null));

    // Assert
    result
        .test()
        .assertError(
            error -> error instanceof DownstreamServiceException failure && failure.isAmbiguous());
    verify(repository).save(any(CreditCard.class));
  }

  @Test
  void reportsEmptyReceiptAsAmbiguous() {
    // Arrange
    CreditCard card = card();
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));
    when(repository.save(any(CreditCard.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    when(movements.register(any(MovementGateway.MovementCommand.class)))
        .thenReturn(Single.error(new DownstreamServiceException("No receipt", true, null)));

    // Act
    var result =
        service.charge(
            new OperationCommand(card.id(), new Money(new BigDecimal("100.00"), "PEN"), null));

    // Assert
    result
        .test()
        .assertError(
            error -> error instanceof DownstreamServiceException failure && failure.isAmbiguous());
  }

  @Test
  void acceptsMaximumPageSize() {
    // Arrange
    CreditCard card = card();
    when(repository.findAll(new CreditCardFilter(null), 0, 100)).thenReturn(Flowable.just(card));

    // Act
    var result = service.findAll(new CreditCardFilter(null), 0, 100);

    // Assert
    result.test().assertValue(card).assertComplete();
  }

  @Test
  void rejectsEachIncompleteCreateFieldAndOversizedPage() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    Money limit = new Money(new BigDecimal("1000.00"), "PEN");

    // Act
    var missingCustomer = service.create(new CreateCardCommand(null, CardType.PERSONAL, limit));
    var missingType = service.create(new CreateCardCommand(customerId, null, limit));
    var missingLimit = service.create(new CreateCardCommand(customerId, CardType.PERSONAL, null));
    var oversized = service.findAll(new CreditCardFilter(null), 0, 101);

    // Assert
    missingCustomer.test().assertError(BusinessRuleViolationException.class);
    missingType.test().assertError(BusinessRuleViolationException.class);
    missingLimit.test().assertError(BusinessRuleViolationException.class);
    oversized.test().assertError(IllegalArgumentException.class);
    verify(customers, never()).findById(any(), nullable(String.class));
  }

  @Test
  void rejectsNullOperationAmountAndMarksCompensationConflictAmbiguous() {
    // Arrange
    CreditCard card = card();
    when(repository.findById(card.id())).thenReturn(Maybe.just(card));
    when(repository.save(any(CreditCard.class)))
        .thenReturn(Single.just(card.withVersion(1L)))
        .thenReturn(Single.error(new IllegalStateException("stale")));
    when(movements.register(any(MovementGateway.MovementCommand.class)))
        .thenReturn(Single.error(new DownstreamServiceException("Rejected", false, null)));

    // Act
    var nullAmount = service.charge(new OperationCommand(card.id(), null, null));
    var compensationConflict =
        service.charge(
            new OperationCommand(card.id(), new Money(new BigDecimal("100.00"), "PEN"), null));

    // Assert
    nullAmount.test().assertError(BusinessRuleViolationException.class);
    compensationConflict
        .test()
        .assertError(
            error -> error instanceof DownstreamServiceException failure && failure.isAmbiguous());
  }

  private CreditCard card() {
    return new CreditCard(
        UUID.randomUUID(),
        UUID.randomUUID(),
        CardType.PERSONAL,
        "**** **** **** 1234",
        new Money(new BigDecimal("5000.00"), "PEN"),
        new Money(new BigDecimal("4000.00"), "PEN"),
        0L,
        Instant.now());
  }
}
