package com.pivot.banking.credit.infrastructure.adapter.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pivot.banking.credit.application.port.in.CardUseCases.ChargeCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.CreateCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.CreateCardCommand;
import com.pivot.banking.credit.application.port.in.CardUseCases.DeleteCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.GetCards;
import com.pivot.banking.credit.application.port.in.CardUseCases.OperationCommand;
import com.pivot.banking.credit.application.port.in.CardUseCases.PayCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.UpdateCard;
import com.pivot.banking.credit.domain.CardType;
import com.pivot.banking.credit.domain.CreditCardFilter;
import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementReceipt;
import com.pivot.banking.credit.domain.MovementType;
import com.pivot.banking.credit.domain.ProductType;
import com.pivot.banking.credit.domain.exception.ResourceNotFoundException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@ExtendWith(MockitoExtension.class)
class CreditCardControllerTest {

  private static final String URI = "/api/v1/credit-cards";

  @Mock private CreateCard createCard;
  @Mock private GetCards getCards;
  @Mock private UpdateCard updateCard;
  @Mock private DeleteCard deleteCard;
  @Mock private ChargeCard chargeCard;
  @Mock private PayCard payCard;
  @Spy private CreditRestMapper mapper = new CreditRestMapper();

  @InjectMocks
  private CreditCardController controller =
      new CreditCardController(null, null, null, null, null, null, new CreditRestMapper(), 0, 20);

  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    controller =
        new CreditCardController(
            createCard, getCards, updateCard, deleteCard, chargeCard, payCard, mapper, 0, 20);
    webTestClient =
        WebTestClient.bindToController(controller)
            .controllerAdvice(new CreditExceptionHandler())
            .build();
  }

  @Test
  void createsCardAndReturnsLocation() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    var created = card();
    when(createCard.create(any(CreateCardCommand.class))).thenReturn(Single.just(created));

    // Act
    var response =
        webTestClient
            .post()
            .uri(URI)
            .header("X-Correlation-ID", "card-create-1")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                "{\"customerId\":\"%s\",\"cardType\":\"PERSONAL\",\"creditLimit\":5000.00,\"currency\":\"PEN\"}"
                    .formatted(customerId))
            .exchange();

    // Assert
    response
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueEquals("Location", URI + "/" + created.id())
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(created.id().toString())
        .jsonPath("$.availableCredit")
        .isEqualTo(5000.00);
    var captor = ArgumentCaptor.forClass(CreateCardCommand.class);
    verify(createCard).create(captor.capture());
    assertEquals(customerId, captor.getValue().customerId());
    assertEquals(CardType.PERSONAL, captor.getValue().cardType());
    assertEquals("card-create-1", captor.getValue().correlationId());
  }

  @Test
  void findsCardByIdAndBalance() {
    // Arrange
    var existing = card();
    when(getCards.findById(existing.id())).thenReturn(Single.just(existing));
    when(getCards.getBalance(existing.id()))
        .thenReturn(
            Single.just(
                new com.pivot.banking.credit.domain.Balance(
                    existing.id(), ProductType.CREDIT_CARD, existing.availableCredit())));

    // Act
    var byId = webTestClient.get().uri(URI + "/{id}", existing.id()).exchange();
    var balance = webTestClient.get().uri(URI + "/{id}/balance", existing.id()).exchange();

    // Assert
    byId.expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.cardNumber")
        .isEqualTo("**** **** **** 1234");
    balance
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.productType")
        .isEqualTo("CREDIT_CARD")
        .jsonPath("$.balance")
        .isEqualTo(5000.00);
  }

  @Test
  void listsCardsWithDefaultsAndFilters() {
    // Arrange
    var existing = card();
    UUID customerId = UUID.randomUUID();
    when(getCards.findAll(new CreditCardFilter(null), 0, 20)).thenReturn(Flowable.just(existing));
    when(getCards.findAll(new CreditCardFilter(customerId), 2, 10)).thenReturn(Flowable.empty());

    // Act
    var defaults = webTestClient.get().uri(URI).exchange();
    var filtered =
        webTestClient
            .get()
            .uri(
                builder ->
                    builder
                        .path(URI)
                        .queryParam("customerId", customerId)
                        .queryParam("page", 2)
                        .queryParam("size", 10)
                        .build())
            .exchange();

    // Assert
    defaults
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].id")
        .isEqualTo(existing.id().toString());
    filtered.expectStatus().isOk().expectBody().json("[]");
  }

  @Test
  void mapsMissingCardTo404() {
    // Arrange
    UUID id = UUID.randomUUID();
    when(getCards.findById(id))
        .thenReturn(Single.error(new ResourceNotFoundException("Card not found")));

    // Act
    var response = webTestClient.get().uri(URI + "/{id}", id).exchange();

    // Assert
    response.expectStatus().isNotFound().expectBody().jsonPath("$.status").isEqualTo(404);
  }

  @Test
  void chargesAndPaysCard() {
    // Arrange
    UUID id = UUID.randomUUID();
    var chargeReceipt =
        new MovementReceipt(
            "movement-1",
            MovementType.CHARGE,
            new Money(new BigDecimal("100.00"), "PEN"),
            Instant.now());
    var paymentReceipt =
        new MovementReceipt(
            "movement-2",
            MovementType.PAYMENT,
            new Money(new BigDecimal("50.00"), "PEN"),
            Instant.now());
    when(chargeCard.charge(any(OperationCommand.class))).thenReturn(Single.just(chargeReceipt));
    when(payCard.pay(any(OperationCommand.class))).thenReturn(Single.just(paymentReceipt));

    // Act
    var charge =
        webTestClient
            .post()
            .uri(URI + "/{id}/charges", id)
            .header("X-Correlation-ID", "card-charge-1")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"amount\":100.00,\"currency\":\"PEN\",\"description\":\"Purchase\"}")
            .exchange();
    var payment =
        webTestClient
            .post()
            .uri(URI + "/{id}/payments", id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"amount\":50.00,\"currency\":\"PEN\"}")
            .exchange();

    // Assert
    charge.expectStatus().isCreated().expectBody().jsonPath("$.movementType").isEqualTo("CHARGE");
    payment.expectStatus().isCreated().expectBody().jsonPath("$.movementType").isEqualTo("PAYMENT");
    var captor = ArgumentCaptor.forClass(OperationCommand.class);
    verify(chargeCard).charge(captor.capture());
    assertEquals("card-charge-1", captor.getValue().correlationId());
    verify(payCard).pay(any(OperationCommand.class));
  }

  @Test
  void updatesAndDeletesCard() {
    // Arrange
    var existing = card();
    UUID id = UUID.randomUUID();
    when(updateCard.update(eq(id), any())).thenReturn(Single.just(existing));
    when(deleteCard.delete(id)).thenReturn(Completable.complete());

    // Act
    var update =
        webTestClient
            .put()
            .uri(URI + "/{id}", id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"creditLimit\":6000.00}")
            .exchange();
    var delete = webTestClient.delete().uri(URI + "/{id}", id).exchange();

    // Assert
    update.expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(existing.id().toString());
    delete.expectStatus().isNoContent();
  }

  @Test
  void usesConfiguredDefaultsWhenPagingValuesAreAbsent() {
    // Arrange
    when(getCards.findAll(new CreditCardFilter(null), 0, 20)).thenReturn(Flowable.empty());

    // Act
    var result = controller.findAllCreditCards(null, null, null, null);

    // Assert
    reactor.test.StepVerifier.create(result)
        .assertNext(
            response -> reactor.test.StepVerifier.create(response.getBody()).verifyComplete())
        .verifyComplete();
  }

  private com.pivot.banking.credit.domain.CreditCard card() {
    return new com.pivot.banking.credit.domain.CreditCard(
        UUID.randomUUID(),
        UUID.randomUUID(),
        CardType.PERSONAL,
        "**** **** **** 1234",
        new Money(new BigDecimal("5000.00"), "PEN"),
        new Money(new BigDecimal("5000.00"), "PEN"),
        0L,
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}
