package com.pivot.banking.credit.application.port.in;

import com.pivot.banking.credit.domain.Balance;
import com.pivot.banking.credit.domain.CardType;
import com.pivot.banking.credit.domain.CreditCard;
import com.pivot.banking.credit.domain.CreditCardFilter;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementReceipt;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.util.UUID;

/** Inbound contracts for credit card management. */
public final class CardUseCases {

  private CardUseCases() {}

  /** Data required to issue a card. */
  public record CreateCardCommand(
      UUID customerId, CardType cardType, Money creditLimit, String correlationId) {
    public CreateCardCommand(UUID customerId, CardType cardType, Money creditLimit) {
      this(customerId, cardType, creditLimit, null);
    }
  }

  /** Editable card fields. */
  public record UpdateCardCommand(BigDecimal creditLimit) {}

  /** Data required by a card charge or payment. */
  public record OperationCommand(
      UUID cardId, Money amount, String description, String correlationId) {
    public OperationCommand(UUID cardId, Money amount, String description) {
      this(cardId, amount, description, null);
    }
  }

  /** Issues a card. */
  public interface CreateCard {
    Single<CreditCard> create(CreateCardCommand command);
  }

  /** Retrieves cards and balances. */
  public interface GetCards {
    Flowable<CreditCard> findAll(CreditCardFilter filter, int page, int size);

    Single<CreditCard> findById(UUID cardId);

    Single<Balance> getBalance(UUID cardId);
  }

  /** Updates editable card fields. */
  public interface UpdateCard {
    Single<CreditCard> update(UUID cardId, UpdateCardCommand command);
  }

  /** Deletes a card. */
  public interface DeleteCard {
    Completable delete(UUID cardId);
  }

  /** Charges a consumption to a card. */
  public interface ChargeCard {
    Single<MovementReceipt> charge(OperationCommand command);
  }

  /** Pays the used credit of a card. */
  public interface PayCard {
    Single<MovementReceipt> pay(OperationCommand command);
  }
}
