package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pivot.banking.credit.domain.CardType;
import com.pivot.banking.credit.domain.CreditCard;
import com.pivot.banking.credit.domain.CreditCardFilter;
import com.pivot.banking.credit.domain.Money;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class R2dbcCreditCardRepositoryAdapterTest {

  @Mock private SpringDataCreditCardRepository repository;
  @Mock private R2dbcEntityTemplate template;
  @InjectMocks private R2dbcCreditCardRepositoryAdapter adapter;

  @Test
  void savesCardAndMapsOptimisticFailure() {
    // Arrange
    CreditCard card = card();
    when(repository.save(any(CreditCardEntity.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)))
        .thenReturn(Mono.error(new OptimisticLockingFailureException("stale")));

    // Act
    var saved = adapter.save(card);
    var conflict = adapter.save(card);

    // Assert
    saved.test().assertResult(card);
    conflict.test().assertError(ConcurrentModificationException.class);
  }

  @Test
  void findsByIdReturnsEmptyWhenMissing() {
    // Arrange
    CreditCard card = card();
    UUID missing = UUID.randomUUID();
    when(repository.findById(card.id())).thenReturn(Mono.just(CreditCardEntity.fromDomain(card)));
    when(repository.findById(missing)).thenReturn(Mono.empty());

    // Act
    var found = adapter.findById(card.id());
    var empty = adapter.findById(missing);

    // Assert
    found.test().assertValue(card).assertComplete();
    empty.test().assertNoValues().assertComplete();
  }

  @Test
  void findsStablePageWithCustomerCriteria() {
    // Arrange
    CreditCard card = card();
    UUID customerId = UUID.randomUUID();
    when(template.select(any(Query.class), any(Class.class)))
        .thenReturn(Flux.just(CreditCardEntity.fromDomain(card)));

    // Act
    var result = adapter.findAll(new CreditCardFilter(customerId), 1, 10);

    // Assert
    result.test().assertResult(card);
    var query = ArgumentCaptor.forClass(Query.class);
    verify(template).select(query.capture(), any(Class.class));
    assertEquals(10, query.getValue().getLimit());
    assertEquals(10, query.getValue().getOffset());
  }

  @Test
  void findsUnfilteredPageWithoutCustomerCriteria() {
    // Arrange
    when(template.select(any(Query.class), any(Class.class))).thenReturn(Flux.empty());

    // Act
    var result = adapter.findAll(new CreditCardFilter(null), 0, 20);

    // Assert
    result.test().assertNoValues().assertComplete();
    verify(template).select(any(Query.class), any(Class.class));
  }

  @Test
  void deletesCardUsingItsVersion() {
    // Arrange
    CreditCard card = card();
    when(repository.delete(any(CreditCardEntity.class))).thenReturn(Mono.empty());

    // Act
    var result = adapter.delete(card);

    // Assert
    result.test().assertComplete();
    var captor = ArgumentCaptor.forClass(CreditCardEntity.class);
    verify(repository).delete(captor.capture());
    assertEquals(3L, captor.getValue().getVersion());
  }

  private CreditCard card() {
    return new CreditCard(
        UUID.randomUUID(),
        UUID.randomUUID(),
        CardType.PERSONAL,
        "**** **** **** 1234",
        new Money(new BigDecimal("5000.00"), "PEN"),
        new Money(new BigDecimal("4000.00"), "PEN"),
        3L,
        Instant.now());
  }
}
