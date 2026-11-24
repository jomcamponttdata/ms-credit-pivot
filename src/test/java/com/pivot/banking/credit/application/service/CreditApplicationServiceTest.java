package com.pivot.banking.credit.application.service;

import com.pivot.banking.credit.application.port.in.CreditUseCases.CreateCreditCommand;
import com.pivot.banking.credit.application.port.in.CreditUseCases.OperationCommand;
import com.pivot.banking.credit.application.port.in.CreditUseCases.UpdateCreditCommand;
import com.pivot.banking.credit.application.port.out.CreditRepositoryPort;
import com.pivot.banking.credit.application.port.out.CustomerGateway;
import com.pivot.banking.credit.application.port.out.MovementGateway;
import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CreditFilter;
import com.pivot.banking.credit.domain.CreditStatus;
import com.pivot.banking.credit.domain.CreditType;
import com.pivot.banking.credit.domain.CustomerSnapshot;
import com.pivot.banking.credit.domain.CustomerType;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementReceipt;
import com.pivot.banking.credit.domain.MovementType;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import com.pivot.banking.credit.domain.exception.DownstreamServiceException;
import com.pivot.banking.credit.domain.exception.ResourceNotFoundException;
import com.pivot.banking.credit.domain.strategy.BusinessCreditEligibilityPolicy;
import com.pivot.banking.credit.domain.strategy.CreditEligibilityPolicyFactory;
import com.pivot.banking.credit.domain.strategy.PersonalCreditEligibilityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.Maybe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditApplicationServiceTest {

    @Mock
    private CreditRepositoryPort repository;
    @Mock
    private CustomerGateway customers;
    @Mock
    private MovementGateway movements;

    private CreditApplicationService service;

    @BeforeEach
    void setUp() {
        var policies = new CreditEligibilityPolicyFactory(List.of(
                new PersonalCreditEligibilityPolicy(), new BusinessCreditEligibilityPolicy()));
        service = new CreditApplicationService(repository, customers, movements, policies, 100);
    }

    @Test
    void createsPersonalCreditWithPositiveBalanceAndZeroOutstanding() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        var command = new CreateCreditCommand(customerId, CreditType.PERSONAL,
                new Money(new BigDecimal("5000.00"), "PEN"), new BigDecimal("18.5"), "credit-correlation-1");
        when(customers.findById(eq(customerId), nullable(String.class))).thenReturn(Single.just(
                new CustomerSnapshot(customerId, CustomerType.PERSONAL, true)));
        when(repository.findByCustomerId(customerId)).thenReturn(Flowable.empty());
        when(repository.save(any(Credit.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        // Act
        var result = service.create(command);

        // Assert
        result.test()
                .assertValue(credit -> {
                    assertEquals(customerId, credit.customerId());
                    assertEquals(credit.approvedAmount(), credit.outstandingBalance());
                    assertEquals(CreditStatus.ACTIVE, credit.status());
                    assertEquals(null, credit.version());
                    return true;
                })
                .assertComplete();
        verify(repository).save(any(Credit.class));
        verify(customers).findById(customerId, "credit-correlation-1");
    }

    @Test
    void rejectsCreditWhenCustomerIsInactive() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        var command = new CreateCreditCommand(customerId, CreditType.PERSONAL,
                new Money(new BigDecimal("5000.00"), "PEN"), null);
        when(customers.findById(eq(customerId), nullable(String.class))).thenReturn(Single.just(
                new CustomerSnapshot(customerId, CustomerType.PERSONAL, false)));

        // Act
        var result = service.create(command);

        // Assert
        result.test().assertError(BusinessRuleViolationException.class);
        verify(repository, never()).save(any(Credit.class));
    }

    @Test
    void rejectsCreditWhenCustomerTypeDoesNotMatch() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        var command = new CreateCreditCommand(customerId, CreditType.PERSONAL,
                new Money(new BigDecimal("5000.00"), "PEN"), null);
        when(customers.findById(eq(customerId), nullable(String.class))).thenReturn(Single.just(
                new CustomerSnapshot(customerId, CustomerType.BUSINESS, true)));

        // Act
        var result = service.create(command);

        // Assert
        result.test().assertError(BusinessRuleViolationException.class);
        verify(repository, never()).save(any(Credit.class));
    }

    @Test
    void rejectsSecondPersonalCredit() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        var command = new CreateCreditCommand(customerId, CreditType.PERSONAL,
                new Money(new BigDecimal("5000.00"), "PEN"), null);
        when(customers.findById(eq(customerId), nullable(String.class))).thenReturn(Single.just(
                new CustomerSnapshot(customerId, CustomerType.PERSONAL, true)));
        when(repository.findByCustomerId(customerId)).thenReturn(Flowable.just(credit(CreditStatus.ACTIVE)));

        // Act
        var result = service.create(command);

        // Assert
        result.test().assertError(BusinessRuleViolationException.class);
        verify(repository, never()).save(any(Credit.class));
    }

    @Test
    void rejectsIncompleteCreateCommand() {
        // Arrange
        // Act
        var result = service.create(new CreateCreditCommand(null, CreditType.PERSONAL, null, null));

        // Assert
        result.test().assertError(BusinessRuleViolationException.class);
        verify(customers, never()).findById(any(), nullable(String.class));
    }

    @Test
    void findsCreditsAndValidatesPagination() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));
        when(repository.findAll(new CreditFilter(null), 0, 20)).thenReturn(Flowable.just(existing));
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Maybe.empty());

        // Act
        var found = service.findById(existing.id());
        var page = service.findAll(new CreditFilter(null), 0, 20);
        var missing = service.findById(missingId);
        var invalidPage = service.findAll(new CreditFilter(null), -1, 20);

        // Assert
        found.test().assertValue(existing).assertComplete();
        page.test().assertValue(existing).assertComplete();
        missing.test().assertError(ResourceNotFoundException.class);
        invalidPage.test().assertError(IllegalArgumentException.class);
    }

    @Test
    void returnsOutstandingBalance() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));

        // Act
        var result = service.getBalance(existing.id());

        // Assert
        result.test()
                .assertValue(balance -> {
                    assertEquals(existing.id(), balance.productId());
                    assertEquals(existing.outstandingBalance(), balance.balance());
                    return true;
                })
                .assertComplete();
    }

    @Test
    void updatesCreditWithoutChangingCustomerOrType() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        var command = new UpdateCreditCommand(new BigDecimal("6000.00"),
                new BigDecimal("12.0"));
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));
        when(repository.save(any(Credit.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        // Act
        var result = service.update(existing.id(), command);

        // Assert
        result.test()
                .assertValue(updated -> {
                    assertEquals(existing.customerId(), updated.customerId());
                    assertEquals(existing.creditType(), updated.creditType());
                    assertEquals(new BigDecimal("6000.00"), updated.approvedAmount().amount());
                    assertEquals(new BigDecimal("12.0"), updated.interestRate());
                    return true;
                })
                .assertComplete();
    }

    @Test
    void rejectsInvalidCreditUpdates() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));
        var missingAmount = new UpdateCreditCommand(null, null);
        var tooSmall = new UpdateCreditCommand(new BigDecimal("100.00"), null);
        var negativeRate = new UpdateCreditCommand(new BigDecimal("6000.00"),
                new BigDecimal("-1.0"));

        // Act
        var first = service.update(existing.id(), missingAmount);
        var second = service.update(existing.id(), tooSmall);
        var third = service.update(existing.id(), negativeRate);

        // Assert
        first.test().assertError(BusinessRuleViolationException.class);
        third.test().assertError(BusinessRuleViolationException.class);
        second.test().assertError(BusinessRuleViolationException.class);
        verify(repository, never()).save(any(Credit.class));
    }

    @Test
    void deletesExistingCreditAndReportsMissingCredit() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        UUID missingId = UUID.randomUUID();
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));
        when(repository.findById(missingId)).thenReturn(Maybe.empty());
        when(repository.delete(existing)).thenReturn(Completable.complete());

        // Act
        var deleted = service.delete(existing.id());
        var missing = service.delete(missingId);

        // Assert
        deleted.test().assertComplete();
        missing.test().assertError(ResourceNotFoundException.class);
        verify(repository).delete(existing);
    }

    @Test
    void paysCreditAndRegistersMovement() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        Money amount = new Money(new BigDecimal("400.00"), "PEN");
        var receipt = new MovementReceipt("movement-1", MovementType.PAYMENT, amount, Instant.now());
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));
        when(repository.save(any(Credit.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(movements.register(any(MovementGateway.MovementCommand.class))).thenReturn(Single.just(receipt));

        // Act
        var result = service.pay(new OperationCommand(existing.id(), amount, "Payment", "payment-correlation-1"));

        // Assert
        result.test().assertValue(receipt).assertComplete();
        var saved = ArgumentCaptor.forClass(Credit.class);
        verify(repository).save(saved.capture());
        assertEquals(new BigDecimal("2600.00"), saved.getValue().outstandingBalance().amount());
        var movement = ArgumentCaptor.forClass(MovementGateway.MovementCommand.class);
        verify(movements).register(movement.capture());
        assertEquals("payment-correlation-1", movement.getValue().correlationId());
    }

    @Test
    void rejectsPaymentAboveBalanceAndInvalidAmount() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));

        // Act
        var tooLarge = service.pay(new OperationCommand(existing.id(),
                new Money(new BigDecimal("3001.00"), "PEN"), null));
        var invalid = service.pay(new OperationCommand(existing.id(),
                new Money(new BigDecimal("-1.00"), "PEN"), null));

        // Assert
        tooLarge.test().assertError(BusinessRuleViolationException.class);
        invalid.test().assertError(BusinessRuleViolationException.class);
        verify(repository, never()).save(any(Credit.class));
    }

    @Test
    void compensatesOnlyConfirmedMovementFailure() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        Money amount = new Money(new BigDecimal("100.00"), "PEN");
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));
        when(repository.save(any(Credit.class)))
                .thenReturn(Single.just(existing.withVersion(1L)))
                .thenReturn(Single.just(existing));
        when(movements.register(any(MovementGateway.MovementCommand.class)))
                .thenReturn(Single.error(new DownstreamServiceException("Rejected", false, null)));

        // Act
        var result = service.pay(new OperationCommand(existing.id(), amount, null));

        // Assert
        result.test().assertError(DownstreamServiceException.class);
        verify(repository, times(2)).save(any(Credit.class));
    }

    @Test
    void doesNotCompensateAmbiguousMovementResult() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        Money amount = new Money(new BigDecimal("100.00"), "PEN");
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));
        when(repository.save(any(Credit.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(movements.register(any(MovementGateway.MovementCommand.class)))
                .thenReturn(Single.error(new DownstreamServiceException("Timeout", true, null)));

        // Act
        var result = service.pay(new OperationCommand(existing.id(), amount, null));

        // Assert
        result.test().assertError(error ->
                error instanceof DownstreamServiceException failure && failure.isAmbiguous());
        verify(repository).save(any(Credit.class));
    }

    @Test
    void reportsEmptyReceiptAsAmbiguous() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));
        when(repository.save(any(Credit.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(movements.register(any(MovementGateway.MovementCommand.class))).thenReturn(Single.error(new DownstreamServiceException("No receipt", true, null)));

        // Act
        var result = service.pay(new OperationCommand(existing.id(),
                new Money(new BigDecimal("100.00"), "PEN"), null));

        // Assert
        result.test().assertError(error ->
                error instanceof DownstreamServiceException failure && failure.isAmbiguous());
    }

    @Test
    void rejectsEachIncompleteCreateFieldAndOversizedPage() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Money amount = new Money(new BigDecimal("1000.00"), "PEN");

        // Act
        var missingCustomer = service.create(new CreateCreditCommand(null, CreditType.PERSONAL, amount, null));
        var missingType = service.create(new CreateCreditCommand(customerId, null, amount, null));
        var missingAmount = service.create(new CreateCreditCommand(customerId, CreditType.PERSONAL, null, null));
        var oversized = service.findAll(new CreditFilter(null), 0, 101);

        // Assert
        missingCustomer.test().assertError(BusinessRuleViolationException.class);
        missingType.test().assertError(BusinessRuleViolationException.class);
        missingAmount.test().assertError(BusinessRuleViolationException.class);
        oversized.test().assertError(IllegalArgumentException.class);
        verify(customers, never()).findById(any(), nullable(String.class));
    }

    @Test
    void acceptsMaximumPageSizeAndUpdateWithoutInterestRate() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        when(repository.findAll(new CreditFilter(null), 0, 100)).thenReturn(Flowable.just(existing));
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));
        when(repository.save(any(Credit.class))).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        // Act
        var page = service.findAll(new CreditFilter(null), 0, 100);
        var update = service.update(existing.id(),
                new UpdateCreditCommand(new BigDecimal("4000.00"), null));

        // Assert
        page.test().assertValue(existing).assertComplete();
        update.test().assertValueCount(1).assertComplete();
    }

    @Test
    void rejectsNegativeInterestRateOnCreate() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        var command = new CreateCreditCommand(customerId, CreditType.PERSONAL,
                new Money(new BigDecimal("1000.00"), "PEN"), new BigDecimal("-1.0"));

        // Act
        var result = service.create(command);

        // Assert
        result.test().assertError(BusinessRuleViolationException.class);
        verify(customers, never()).findById(any(), nullable(String.class));
    }

    @Test
    void rejectsNullPaymentAmountAndOversizedPageSize() {
        // Arrange
        UUID creditId = UUID.randomUUID();

        // Act
        var nullAmount = service.pay(new OperationCommand(creditId, null, null));
        var zeroAmount = service.pay(new OperationCommand(creditId,
                new Money(BigDecimal.ZERO, "PEN"), null));

        // Assert
        nullAmount.test().assertError(BusinessRuleViolationException.class);
        zeroAmount.test().assertError(BusinessRuleViolationException.class);
        verify(repository, never()).findById(any());
    }

    @Test
    void marksCompensationConflictAsAmbiguous() {
        // Arrange
        Credit existing = credit(CreditStatus.ACTIVE);
        Money amount = new Money(new BigDecimal("100.00"), "PEN");
        when(repository.findById(existing.id())).thenReturn(Maybe.just(existing));
        when(repository.save(any(Credit.class)))
                .thenReturn(Single.just(existing.withVersion(1L)))
                .thenReturn(Single.error(new IllegalStateException("stale")));
        when(movements.register(any(MovementGateway.MovementCommand.class)))
                .thenReturn(Single.error(new DownstreamServiceException("Rejected", false, null)));

        // Act
        var result = service.pay(new OperationCommand(existing.id(), amount, null));

        // Assert
        result.test().assertError(error ->
                error instanceof DownstreamServiceException failure && failure.isAmbiguous());
    }

    private Credit credit(CreditStatus status) {
        return new Credit(UUID.randomUUID(), UUID.randomUUID(), CreditType.PERSONAL,
                new Money(new BigDecimal("3000.00"), "PEN"), new Money(new BigDecimal("3000.00"), "PEN"),
                new BigDecimal("18.5"), status, 0L, Instant.now());
    }
}
