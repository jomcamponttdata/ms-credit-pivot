package com.pivot.banking.credit.infrastructure.adapter.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pivot.banking.credit.application.port.in.CreditUseCases.CreateCredit;
import com.pivot.banking.credit.application.port.in.CreditUseCases.CreateCreditCommand;
import com.pivot.banking.credit.application.port.in.CreditUseCases.DeleteCredit;
import com.pivot.banking.credit.application.port.in.CreditUseCases.GetCredits;
import com.pivot.banking.credit.application.port.in.CreditUseCases.OperationCommand;
import com.pivot.banking.credit.application.port.in.CreditUseCases.PayCredit;
import com.pivot.banking.credit.application.port.in.CreditUseCases.UpdateCredit;
import com.pivot.banking.credit.domain.CreditFilter;
import com.pivot.banking.credit.domain.CreditStatus;
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
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CreditControllerTest {

  private static final String URI = "/api/v1/credits";

  @Mock private CreateCredit createCredit;
  @Mock private GetCredits getCredits;
  @Mock private UpdateCredit updateCredit;
  @Mock private DeleteCredit deleteCredit;
  @Mock private PayCredit payCredit;
  @Spy private CreditRestMapper mapper = new CreditRestMapper();

  @InjectMocks
  private CreditController controller =
      new CreditController(null, null, null, null, null, new CreditRestMapper(), 0, 20);

  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    controller =
        new CreditController(
            createCredit, getCredits, updateCredit, deleteCredit, payCredit, mapper, 0, 20);
    webTestClient =
        WebTestClient.bindToController(controller)
            .controllerAdvice(new CreditExceptionHandler())
            .build();
  }

  @Test
  void createsCreditAndReturnsLocation() {
    // Arrange
    UUID customerId = UUID.randomUUID();
    var created =
        new com.pivot.banking.credit.domain.Credit(
            UUID.randomUUID(),
            customerId,
            com.pivot.banking.credit.domain.CreditType.PERSONAL,
            new Money(new BigDecimal("5000.00"), "PEN"),
            new Money(new BigDecimal("5000.00"), "PEN"),
            new BigDecimal("18.5"),
            CreditStatus.ACTIVE,
            0L,
            Instant.parse("2026-01-01T00:00:00Z"));
    when(createCredit.create(any(CreateCreditCommand.class))).thenReturn(Single.just(created));

    // Act
    var response =
        webTestClient
            .post()
            .uri(URI)
            .header("X-Correlation-ID", "credit-create-1")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"customerId":"%s","creditType":"PERSONAL","approvedAmount":5000.00,
                 "currency":"PEN","interestRate":18.5}
                """
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
        .jsonPath("$.creditType")
        .isEqualTo("PERSONAL")
        .jsonPath("$.outstandingBalance")
        .isEqualTo(5000.00);
    var captor = ArgumentCaptor.forClass(CreateCreditCommand.class);
    verify(createCredit).create(captor.capture());
    assertEquals(customerId, captor.getValue().customerId());
    assertEquals(new BigDecimal("5000.00"), captor.getValue().approvedAmount().amount());
    assertEquals("credit-create-1", captor.getValue().correlationId());
  }

  @Test
  void findsCreditByIdAndBalance() {
    // Arrange
    var existing = credit(CreditStatus.ACTIVE);
    when(getCredits.findById(existing.id())).thenReturn(Single.just(existing));
    when(getCredits.getBalance(existing.id()))
        .thenReturn(
            Single.just(
                new com.pivot.banking.credit.domain.Balance(
                    existing.id(), ProductType.CREDIT, existing.outstandingBalance())));

    // Act
    var byId = webTestClient.get().uri(URI + "/{id}", existing.id()).exchange();
    var balance = webTestClient.get().uri(URI + "/{id}/balance", existing.id()).exchange();

    // Assert
    byId.expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(existing.id().toString());
    balance
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.productId")
        .isEqualTo(existing.id().toString())
        .jsonPath("$.productType")
        .isEqualTo("CREDIT")
        .jsonPath("$.balance")
        .isEqualTo(3000.00);
  }

  @Test
  void listsCreditsWithDefaultsAndFilters() {
    // Arrange
    var existing = credit(CreditStatus.ACTIVE);
    UUID customerId = UUID.randomUUID();
    when(getCredits.findAll(new CreditFilter(null), 0, 20)).thenReturn(Flowable.just(existing));
    when(getCredits.findAll(new CreditFilter(customerId), 1, 5)).thenReturn(Flowable.empty());

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
                        .queryParam("page", 1)
                        .queryParam("size", 5)
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
  void mapsMissingCreditTo404() {
    // Arrange
    UUID id = UUID.randomUUID();
    when(getCredits.findById(id))
        .thenReturn(Single.error(new ResourceNotFoundException("Credit not found")));

    // Act
    var response = webTestClient.get().uri(URI + "/{id}", id).exchange();

    // Assert
    response
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("Resource not found")
        .jsonPath("$.status")
        .isEqualTo(404);
  }

  @Test
  void updatesCreditWithMappedFields() {
    // Arrange
    var existing = credit(CreditStatus.ACTIVE);
    when(updateCredit.update(eq(existing.id()), any())).thenReturn(Single.just(existing));

    // Act
    var response =
        webTestClient
            .put()
            .uri(URI + "/{id}", existing.id())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"approvedAmount\":4000.00,\"interestRate\":15.0}")
            .exchange();

    // Assert
    response
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(existing.id().toString());
    verify(updateCredit).update(eq(existing.id()), any());
  }

  @Test
  void paysCreditAndReturnsReceipt() {
    // Arrange
    UUID id = UUID.randomUUID();
    var receipt =
        new MovementReceipt(
            "movement-1",
            MovementType.PAYMENT,
            new Money(new BigDecimal("100.00"), "PEN"),
            Instant.now());
    when(payCredit.pay(any(OperationCommand.class))).thenReturn(Single.just(receipt));

    // Act
    var response =
        webTestClient
            .post()
            .uri(URI + "/{id}/payments", id)
            .header("X-Correlation-ID", "credit-payment-1")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"amount\":100.00,\"currency\":\"PEN\",\"description\":\"Payment\"}")
            .exchange();

    // Assert
    response
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.movementId")
        .isEqualTo("movement-1")
        .jsonPath("$.movementType")
        .isEqualTo("PAYMENT")
        .jsonPath("$.amount")
        .isEqualTo(100.00);
    var captor = ArgumentCaptor.forClass(OperationCommand.class);
    verify(payCredit).pay(captor.capture());
    assertEquals(id, captor.getValue().creditId());
    assertEquals("Payment", captor.getValue().description());
    assertEquals("credit-payment-1", captor.getValue().correlationId());
  }

  @Test
  void deletesCreditAndReturnsNoContent() {
    // Arrange
    UUID id = UUID.randomUUID();
    when(deleteCredit.delete(id)).thenReturn(Completable.complete());

    // Act
    var response = webTestClient.delete().uri(URI + "/{id}", id).exchange();

    // Assert
    response.expectStatus().isNoContent();
    verify(deleteCredit).delete(id);
  }

  @Test
  void mapsInvalidCurrencyToProblem() {
    // Arrange
    UUID id = UUID.randomUUID();

    // Act
    var response =
        webTestClient
            .post()
            .uri(URI + "/{id}/payments", id)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"amount\":100.00,\"currency\":\"pen\"}")
            .exchange();

    // Assert
    response
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("Invalid request")
        .jsonPath("$.status")
        .isEqualTo(400);
    verify(payCredit, never()).pay(any());
  }

  @Test
  void usesConfiguredDefaultsWhenPagingValuesAreAbsent() {
    // Arrange
    when(getCredits.findAll(new CreditFilter(null), 0, 20)).thenReturn(Flowable.empty());

    // Act
    var result = controller.findAllCredits(null, null, null, null);

    // Assert
    StepVerifier.create(result)
        .assertNext(response -> StepVerifier.create(response.getBody()).verifyComplete())
        .verifyComplete();
  }

  private com.pivot.banking.credit.domain.Credit credit(CreditStatus status) {
    return new com.pivot.banking.credit.domain.Credit(
        UUID.randomUUID(),
        UUID.randomUUID(),
        com.pivot.banking.credit.domain.CreditType.PERSONAL,
        new Money(new BigDecimal("5000.00"), "PEN"),
        new Money(new BigDecimal("3000.00"), "PEN"),
        new BigDecimal("18.5"),
        status,
        0L,
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}
