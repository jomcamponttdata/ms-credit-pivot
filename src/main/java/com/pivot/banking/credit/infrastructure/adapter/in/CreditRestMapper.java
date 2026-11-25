package com.pivot.banking.credit.infrastructure.adapter.in;

import com.pivot.banking.credit.adapter.in.dto.Balance;
import com.pivot.banking.credit.adapter.in.dto.CreditCardCreateRequest;
import com.pivot.banking.credit.adapter.in.dto.CreditCardUpdateRequest;
import com.pivot.banking.credit.adapter.in.dto.CreditCreateRequest;
import com.pivot.banking.credit.adapter.in.dto.CreditUpdateRequest;
import com.pivot.banking.credit.adapter.in.dto.OperationRequest;
import com.pivot.banking.credit.application.port.in.CardUseCases.CreateCardCommand;
import com.pivot.banking.credit.application.port.in.CardUseCases.UpdateCardCommand;
import com.pivot.banking.credit.application.port.in.CreditUseCases.CreateCreditCommand;
import com.pivot.banking.credit.application.port.in.CreditUseCases.UpdateCreditCommand;
import com.pivot.banking.credit.domain.CardType;
import com.pivot.banking.credit.domain.CreditType;
import com.pivot.banking.credit.domain.Money;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Converts generated REST DTOs to application commands and domain objects to responses. */
@Component
public class CreditRestMapper {

  /** Converts a credit creation request. */
  public CreateCreditCommand toCreateCreditCommand(CreditCreateRequest request) {
    return toCreateCreditCommand(request, null);
  }

  /** Converts a credit creation request and its HTTP correlation identifier. */
  public CreateCreditCommand toCreateCreditCommand(
      CreditCreateRequest request, String correlationId) {
    String currency = request.getCurrency() == null ? "PEN" : request.getCurrency();
    return new CreateCreditCommand(
        request.getCustomerId(),
        CreditType.valueOf(request.getCreditType().name()),
        new Money(request.getApprovedAmount(), currency),
        request.getInterestRate(),
        correlationId);
  }

  /** Converts a credit update request. */
  public UpdateCreditCommand toUpdateCreditCommand(CreditUpdateRequest request) {
    return new UpdateCreditCommand(request.getApprovedAmount(), request.getInterestRate());
  }

  /** Converts a card creation request. */
  public CreateCardCommand toCreateCardCommand(CreditCardCreateRequest request) {
    return toCreateCardCommand(request, null);
  }

  /** Converts a card creation request and its HTTP correlation identifier. */
  public CreateCardCommand toCreateCardCommand(
      CreditCardCreateRequest request, String correlationId) {
    String currency = request.getCurrency() == null ? "PEN" : request.getCurrency();
    return new CreateCardCommand(
        request.getCustomerId(),
        CardType.valueOf(request.getCardType().name()),
        new Money(request.getCreditLimit(), currency),
        correlationId);
  }

  /** Converts a card update request. */
  public UpdateCardCommand toUpdateCardCommand(CreditCardUpdateRequest request) {
    return new UpdateCardCommand(request.getCreditLimit());
  }

  /** Converts an operation request using PEN as the default currency. */
  public Money toAmount(OperationRequest request) {
    String currency = request.getCurrency() == null ? "PEN" : request.getCurrency();
    return new Money(request.getAmount(), currency);
  }

  /** Converts a credit aggregate to its REST representation. */
  public com.pivot.banking.credit.adapter.in.dto.Credit toCreditResponse(
      com.pivot.banking.credit.domain.Credit credit) {
    var response =
        new com.pivot.banking.credit.adapter.in.dto.Credit(
            credit.id(),
            credit.customerId(),
            com.pivot.banking.credit.adapter.in.dto.CreditType.valueOf(credit.creditType().name()),
            credit.approvedAmount().amount(),
            credit.outstandingBalance().amount(),
            credit.approvedAmount().currency(),
            com.pivot.banking.credit.adapter.in.dto.CreditStatus.valueOf(credit.status().name()),
            OffsetDateTime.ofInstant(credit.createdAt(), ZoneOffset.UTC));
    response.setInterestRate(credit.interestRate());
    return response;
  }

  /** Converts a card aggregate to its REST representation. */
  public com.pivot.banking.credit.adapter.in.dto.CreditCard toCardResponse(
      com.pivot.banking.credit.domain.CreditCard card) {
    return new com.pivot.banking.credit.adapter.in.dto.CreditCard(
        card.id(),
        card.customerId(),
        com.pivot.banking.credit.adapter.in.dto.CardType.valueOf(card.cardType().name()),
        card.cardNumber(),
        card.creditLimit().amount(),
        card.availableCredit().amount(),
        card.creditLimit().currency(),
        OffsetDateTime.ofInstant(card.createdAt(), ZoneOffset.UTC));
  }

  /** Converts a domain balance to its REST representation. */
  public Balance toBalanceResponse(com.pivot.banking.credit.domain.Balance balance) {
    return new Balance(
        balance.productId(),
        Balance.ProductTypeEnum.valueOf(balance.productType().name()),
        balance.balance().amount(),
        balance.balance().currency());
  }

  /** Converts a movement receipt to its REST representation. */
  public com.pivot.banking.credit.adapter.in.dto.MovementReceipt toMovementResponse(
      com.pivot.banking.credit.domain.MovementReceipt receipt) {
    return new com.pivot.banking.credit.adapter.in.dto.MovementReceipt(
        receipt.movementId(),
        com.pivot.banking.credit.adapter.in.dto.MovementReceipt.MovementTypeEnum.valueOf(
            receipt.movementType().name()),
        receipt.amount().amount(),
        receipt.amount().currency(),
        OffsetDateTime.ofInstant(receipt.createdAt(), ZoneOffset.UTC));
  }
}
