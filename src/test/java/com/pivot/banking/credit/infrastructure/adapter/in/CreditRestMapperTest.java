package com.pivot.banking.credit.infrastructure.adapter.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.pivot.banking.credit.adapter.in.dto.Balance;
import com.pivot.banking.credit.adapter.in.dto.CardType;
import com.pivot.banking.credit.adapter.in.dto.CreditCardCreateRequest;
import com.pivot.banking.credit.adapter.in.dto.CreditCardUpdateRequest;
import com.pivot.banking.credit.adapter.in.dto.CreditCreateRequest;
import com.pivot.banking.credit.adapter.in.dto.CreditType;
import com.pivot.banking.credit.adapter.in.dto.CreditUpdateRequest;
import com.pivot.banking.credit.adapter.in.dto.OperationRequest;
import com.pivot.banking.credit.domain.CreditStatus;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementReceipt;
import com.pivot.banking.credit.domain.MovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for REST mapping. */
class CreditRestMapperTest {

  private final CreditRestMapper mapper = new CreditRestMapper();

  @Test
  void mapsCreateAndUpdateCommands() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    var creditRequest =
        new CreditCreateRequest(customerId, CreditType.PERSONAL, new BigDecimal("5000.00"));
    creditRequest.setCurrency("USD");
    creditRequest.setInterestRate(new BigDecimal("18.5"));
    var cardRequest =
        new CreditCardCreateRequest(customerId, CardType.BUSINESS, new BigDecimal("8000.00"));
    var creditUpdate = new CreditUpdateRequest();
    creditUpdate.setApprovedAmount(new BigDecimal("6000.00"));
    creditUpdate.setInterestRate(new BigDecimal("12.0"));
    var cardUpdate = new CreditCardUpdateRequest();
    cardUpdate.setCreditLimit(new BigDecimal("9000.00"));

    // Act
    var creditCommand = mapper.toCreateCreditCommand(creditRequest);
    var cardCommand = mapper.toCreateCardCommand(cardRequest);
    var creditUpdateCommand = mapper.toUpdateCreditCommand(creditUpdate);
    var cardUpdateCommand = mapper.toUpdateCardCommand(cardUpdate);
    var emptyCreditUpdate = mapper.toUpdateCreditCommand(new CreditUpdateRequest());

    // Assert
    assertEquals("USD", creditCommand.approvedAmount().currency());
    assertEquals(new BigDecimal("18.5"), creditCommand.interestRate());
    assertEquals(customerId, cardCommand.customerId());
    assertEquals(new BigDecimal("6000.00"), creditUpdateCommand.approvedAmount());
    assertEquals(new BigDecimal("9000.00"), cardUpdateCommand.creditLimit());
    assertNull(emptyCreditUpdate.approvedAmount());
  }

  @Test
  void mapsResponsesAndOperationAmount() {
    // Arrange
    UUID id = UUID.randomUUID();
    var credit =
        new com.pivot.banking.credit.domain.Credit(
            id,
            UUID.randomUUID(),
            com.pivot.banking.credit.domain.CreditType.PERSONAL,
            new Money(new BigDecimal("5000.00"), "PEN"),
            new Money(new BigDecimal("3000.00"), "PEN"),
            new BigDecimal("18.5"),
            CreditStatus.ACTIVE,
            0L,
            Instant.parse("2026-01-01T00:00:00Z"));
    var card =
        new com.pivot.banking.credit.domain.CreditCard(
            id,
            UUID.randomUUID(),
            com.pivot.banking.credit.domain.CardType.PERSONAL,
            "**** **** **** 1234",
            new Money(new BigDecimal("5000.00"), "PEN"),
            new Money(new BigDecimal("4000.00"), "PEN"),
            0L,
            Instant.parse("2026-01-01T00:00:00Z"));
    var operation = new OperationRequest();
    operation.setAmount(new BigDecimal("100.00"));

    // Act
    var creditResponse = mapper.toCreditResponse(credit);
    var cardResponse = mapper.toCardResponse(card);
    var balance =
        mapper.toBalanceResponse(
            new com.pivot.banking.credit.domain.Balance(
                id,
                com.pivot.banking.credit.domain.ProductType.CREDIT,
                credit.outstandingBalance()));
    var receipt =
        mapper.toMovementResponse(
            new MovementReceipt(
                "movement-1",
                MovementType.PAYMENT,
                new Money(new BigDecimal("100.00"), "PEN"),
                Instant.parse("2026-01-01T00:00:00Z")));
    var amount = mapper.toAmount(operation);

    // Assert
    assertEquals("PERSONAL", creditResponse.getCreditType().name());
    assertEquals("**** **** **** 1234", cardResponse.getCardNumber());
    assertEquals(Balance.ProductTypeEnum.CREDIT, balance.getProductType());
    assertEquals("PAYMENT", receipt.getMovementType().name());
    assertEquals(new Money(new BigDecimal("100.00"), "PEN"), amount);
  }

  @Test
  void usesDefaultCurrencyWhenRequestOmitsIt() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    var creditRequest =
        new CreditCreateRequest(customerId, CreditType.PERSONAL, new BigDecimal("5000.00"));
    creditRequest.setCurrency(null);
    var cardRequest =
        new CreditCardCreateRequest(customerId, CardType.PERSONAL, new BigDecimal("5000.00"));
    cardRequest.setCurrency(null);
    var operation = new OperationRequest();
    operation.setAmount(new BigDecimal("100.00"));
    operation.setCurrency(null);

    // Act
    var creditCommand = mapper.toCreateCreditCommand(creditRequest);
    var cardCommand = mapper.toCreateCardCommand(cardRequest);
    var amount = mapper.toAmount(operation);

    // Assert
    assertEquals("PEN", creditCommand.approvedAmount().currency());
    assertEquals("PEN", cardCommand.creditLimit().currency());
    assertEquals("PEN", amount.currency());
  }
}
