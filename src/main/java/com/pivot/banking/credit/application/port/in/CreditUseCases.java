package com.pivot.banking.credit.application.port.in;

import com.pivot.banking.credit.domain.Balance;
import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CreditFilter;
import com.pivot.banking.credit.domain.CreditType;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementReceipt;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.util.UUID;

/** Inbound contracts for credit management. */
public final class CreditUseCases {

  private CreditUseCases() {}

  /** Data required to request a credit. */
  public record CreateCreditCommand(
      UUID customerId,
      CreditType creditType,
      Money approvedAmount,
      BigDecimal interestRate,
      String correlationId) {
    public CreateCreditCommand(
        UUID customerId, CreditType creditType, Money approvedAmount, BigDecimal interestRate) {
      this(customerId, creditType, approvedAmount, interestRate, null);
    }
  }

  /** Editable credit fields. */
  public record UpdateCreditCommand(BigDecimal approvedAmount, BigDecimal interestRate) {}

  /** Data required by a credit payment. */
  public record OperationCommand(
      UUID creditId, Money amount, String description, String correlationId) {
    public OperationCommand(UUID creditId, Money amount, String description) {
      this(creditId, amount, description, null);
    }
  }

  /** Creates a credit. */
  public interface CreateCredit {
    Single<Credit> create(CreateCreditCommand command);
  }

  /** Retrieves credits and balances. */
  public interface GetCredits {
    Flowable<Credit> findAll(CreditFilter filter, int page, int size);

    Single<Credit> findById(UUID creditId);

    Single<Balance> getBalance(UUID creditId);
  }

  /** Updates editable credit fields. */
  public interface UpdateCredit {
    Single<Credit> update(UUID creditId, UpdateCreditCommand command);
  }

  /** Deletes a credit. */
  public interface DeleteCredit {
    Completable delete(UUID creditId);
  }

  /** Pays an outstanding credit balance. */
  public interface PayCredit {
    Single<MovementReceipt> pay(OperationCommand command);
  }
}
