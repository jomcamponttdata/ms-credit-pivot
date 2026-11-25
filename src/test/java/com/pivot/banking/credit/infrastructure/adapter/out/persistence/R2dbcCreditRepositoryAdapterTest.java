package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CreditFilter;
import com.pivot.banking.credit.domain.CreditStatus;
import com.pivot.banking.credit.domain.CreditType;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.exception.BusinessRuleViolationException;
import com.pivot.banking.credit.domain.exception.ConcurrentModificationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class R2dbcCreditRepositoryAdapterTest {

  @Mock private SpringDataCreditRepository repository;
  @Mock private R2dbcEntityTemplate template;
  @InjectMocks private R2dbcCreditRepositoryAdapter adapter;

  @Test
  void savesCreditAndMapsItsRelations() {
    // Arrange
    Credit credit = credit();
    when(repository.save(any(CreditEntity.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    // Act
    var result = adapter.save(credit);

    // Assert
    result.test().assertResult(credit);
    var captor = ArgumentCaptor.forClass(CreditEntity.class);
    verify(repository).save(captor.capture());
    assertEquals(credit.id(), captor.getValue().getId());
    assertEquals(credit.outstandingBalance().amount(), captor.getValue().getOutstandingBalance());
  }

  @Test
  void translatesOptimisticLockingAndDuplicateFailures() {
    // Arrange
    Credit credit = credit();
    when(repository.save(any(CreditEntity.class)))
        .thenReturn(Mono.error(new OptimisticLockingFailureException("stale")))
        .thenReturn(Mono.error(new DataIntegrityViolationException("uq_personal_credit")));

    // Act
    var conflict = adapter.save(credit);
    var duplicate = adapter.save(credit);

    // Assert
    conflict.test().assertError(ConcurrentModificationException.class);
    duplicate.test().assertError(BusinessRuleViolationException.class);
  }

  @Test
  void preservesUnrelatedIntegrityViolations() {
    // Arrange
    Credit credit = credit();
    var failure = new DataIntegrityViolationException("violates another constraint");
    when(repository.save(any(CreditEntity.class))).thenReturn(Mono.error(failure));

    // Act
    var result = adapter.save(credit);

    // Assert
    result.test().assertError(error -> error == failure);
  }

  @Test
  void findsByIdAndReturnsEmptyWhenMissing() {
    // Arrange
    Credit credit = credit();
    UUID missing = UUID.randomUUID();
    when(repository.findById(credit.id())).thenReturn(Mono.just(CreditEntity.fromDomain(credit)));
    when(repository.findById(missing)).thenReturn(Mono.empty());

    // Act
    var found = adapter.findById(credit.id());
    var empty = adapter.findById(missing);

    // Assert
    found.test().assertValue(credit).assertComplete();
    empty.test().assertNoValues().assertComplete();
  }

  @Test
  void findsStableFilteredPageAndAppliesCustomerCriteria() {
    // Arrange
    Credit credit = credit();
    UUID customerId = UUID.randomUUID();
    when(template.select(any(Query.class), any(Class.class)))
        .thenReturn(Flux.just(CreditEntity.fromDomain(credit)));

    // Act
    var all = adapter.findAll(new CreditFilter(null), 2, 5);
    var byCustomer = adapter.findByCustomerId(customerId);

    // Assert
    all.test().assertResult(credit);
    byCustomer.test().assertResult(credit);
    var query = ArgumentCaptor.forClass(Query.class);
    verify(template, org.mockito.Mockito.times(2)).select(query.capture(), any(Class.class));
    assertEquals(5, query.getAllValues().get(0).getLimit());
    assertEquals(10, query.getAllValues().get(0).getOffset());
    assertEquals(
        Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")),
        query.getAllValues().get(0).getSort());
  }

  @Test
  void deletesCreditUsingItsVersion() {
    // Arrange
    Credit credit = credit();
    when(repository.delete(any(CreditEntity.class))).thenReturn(Mono.empty());

    // Act
    var result = adapter.delete(credit);

    // Assert
    result.test().assertComplete();
    var captor = ArgumentCaptor.forClass(CreditEntity.class);
    verify(repository).delete(captor.capture());
    assertEquals(4L, captor.getValue().getVersion());
  }

  private Credit credit() {
    return new Credit(
        UUID.randomUUID(),
        UUID.randomUUID(),
        CreditType.PERSONAL,
        new Money(new BigDecimal("5000.00"), "PEN"),
        new Money(new BigDecimal("3000.00"), "PEN"),
        new BigDecimal("18.5"),
        CreditStatus.ACTIVE,
        4L,
        Instant.now());
  }
}
