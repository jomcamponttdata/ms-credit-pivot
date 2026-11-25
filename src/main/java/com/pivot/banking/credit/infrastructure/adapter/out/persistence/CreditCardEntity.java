package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import com.pivot.banking.credit.domain.CardType;
import com.pivot.banking.credit.domain.CreditCard;
import com.pivot.banking.credit.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/** Relational representation of a credit card. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("credit_cards")
public class CreditCardEntity {

  @Id private UUID id;
  private UUID customerId;
  private String cardType;
  private String cardNumber;
  private BigDecimal creditLimit;
  private BigDecimal availableCredit;
  private String currency;
  @Version private Long version;
  private Instant createdAt;

  /** Converts a domain card to its relational row. */
  public static CreditCardEntity fromDomain(CreditCard card) {
    return new CreditCardEntity(
        card.id(),
        card.customerId(),
        card.cardType().name(),
        card.cardNumber(),
        card.creditLimit().amount(),
        card.availableCredit().amount(),
        card.creditLimit().currency(),
        card.version(),
        card.createdAt());
  }

  /** Rebuilds the domain card from its relational row. */
  public CreditCard toDomain() {
    return new CreditCard(
        id,
        customerId,
        CardType.valueOf(cardType),
        cardNumber,
        new Money(creditLimit, currency),
        new Money(availableCredit, currency),
        version,
        createdAt);
  }
}
