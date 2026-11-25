package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pivot.banking.credit.domain.CardType;
import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CreditCard;
import com.pivot.banking.credit.domain.CreditStatus;
import com.pivot.banking.credit.domain.CreditType;
import com.pivot.banking.credit.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import reactor.core.publisher.Mono;

/** Verifies Spring's transaction interceptor adapts RxJava persistence return types reactively. */
class R2dbcReactiveTransactionIntegrationTest {

  @Test
  void appliesReactiveTransactionsToRxJavaAdapterMethods() {
    // Arrange
    ReactiveTransactionManager transactionManager = mock(ReactiveTransactionManager.class);
    ReactiveTransaction transaction = mock(ReactiveTransaction.class);
    SpringDataCreditRepository creditRepository = mock(SpringDataCreditRepository.class);
    SpringDataCreditCardRepository cardRepository = mock(SpringDataCreditCardRepository.class);
    R2dbcEntityTemplate template = mock(R2dbcEntityTemplate.class);
    when(transactionManager.getReactiveTransaction(any())).thenReturn(Mono.just(transaction));
    when(transactionManager.commit(any(ReactiveTransaction.class))).thenReturn(Mono.empty());
    when(transactionManager.rollback(any(ReactiveTransaction.class))).thenReturn(Mono.empty());
    when(creditRepository.save(any(CreditEntity.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(creditRepository.delete(any(CreditEntity.class))).thenReturn(Mono.empty());
    when(cardRepository.save(any(CreditCardEntity.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(cardRepository.delete(any(CreditCardEntity.class))).thenReturn(Mono.empty());

    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(ReactiveTransactionManager.class, () -> transactionManager);
      context.registerBean(SpringDataCreditRepository.class, () -> creditRepository);
      context.registerBean(SpringDataCreditCardRepository.class, () -> cardRepository);
      context.registerBean(R2dbcEntityTemplate.class, () -> template);
      context.registerBean(R2dbcCreditRepositoryAdapter.class);
      context.registerBean(R2dbcCreditCardRepositoryAdapter.class);
      context.register(ReactiveTransactionConfiguration.class);
      context.refresh();

      var creditAdapter = context.getBean(R2dbcCreditRepositoryAdapter.class);
      var cardAdapter = context.getBean(R2dbcCreditCardRepositoryAdapter.class);
      Credit credit = credit();
      CreditCard card = card();

      // Act
      var savedCredit = creditAdapter.save(credit);
      var deletedCredit = creditAdapter.delete(credit);
      var savedCard = cardAdapter.save(card);
      var deletedCard = cardAdapter.delete(card);

      // Assert
      savedCredit.test().assertResult(credit);
      deletedCredit.test().assertComplete();
      savedCard.test().assertResult(card);
      deletedCard.test().assertComplete();
    }
    verify(transactionManager, times(4)).getReactiveTransaction(any());
    verify(transactionManager, times(4)).commit(any(ReactiveTransaction.class));
  }

  private Credit credit() {
    return new Credit(
        UUID.randomUUID(),
        UUID.randomUUID(),
        CreditType.PERSONAL,
        new Money(new BigDecimal("1000.00"), "PEN"),
        new Money(new BigDecimal("1000.00"), "PEN"),
        null,
        CreditStatus.ACTIVE,
        null,
        Instant.now());
  }

  private CreditCard card() {
    return new CreditCard(
        UUID.randomUUID(),
        UUID.randomUUID(),
        CardType.PERSONAL,
        "**** **** **** 1234",
        new Money(new BigDecimal("1000.00"), "PEN"),
        new Money(new BigDecimal("1000.00"), "PEN"),
        null,
        Instant.now());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement(proxyTargetClass = true)
  static class ReactiveTransactionConfiguration {}
}
