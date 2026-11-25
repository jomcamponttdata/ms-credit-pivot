package com.pivot.banking.credit.infrastructure.adapter.in;

import com.pivot.banking.credit.adapter.in.api.CreditsApi;
import com.pivot.banking.credit.adapter.in.dto.Balance;
import com.pivot.banking.credit.adapter.in.dto.Credit;
import com.pivot.banking.credit.adapter.in.dto.CreditCreateRequest;
import com.pivot.banking.credit.adapter.in.dto.CreditUpdateRequest;
import com.pivot.banking.credit.adapter.in.dto.MovementReceipt;
import com.pivot.banking.credit.adapter.in.dto.OperationRequest;
import com.pivot.banking.credit.application.port.in.CreditUseCases.CreateCredit;
import com.pivot.banking.credit.application.port.in.CreditUseCases.DeleteCredit;
import com.pivot.banking.credit.application.port.in.CreditUseCases.GetCredits;
import com.pivot.banking.credit.application.port.in.CreditUseCases.OperationCommand;
import com.pivot.banking.credit.application.port.in.CreditUseCases.PayCredit;
import com.pivot.banking.credit.application.port.in.CreditUseCases.UpdateCredit;
import com.pivot.banking.credit.domain.CreditFilter;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ServerWebExchange;
import reactor.adapter.rxjava.RxJava3Adapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** REST adapter for credit endpoints. */
@Controller
public class CreditController implements CreditsApi {

  private final CreateCredit createCredit;
  private final GetCredits getCredits;
  private final UpdateCredit updateCredit;
  private final DeleteCredit deleteCredit;
  private final PayCredit payCredit;
  private final CreditRestMapper mapper;
  private final int defaultPage;
  private final int defaultSize;

  /** Creates the controller with its use cases, mapper and paging defaults. */
  public CreditController(
      CreateCredit createCredit,
      GetCredits getCredits,
      UpdateCredit updateCredit,
      DeleteCredit deleteCredit,
      PayCredit payCredit,
      CreditRestMapper mapper,
      @Value("${credit.pagination.default-page:0}") int defaultPage,
      @Value("${credit.pagination.default-size:20}") int defaultSize) {
    this.createCredit = createCredit;
    this.getCredits = getCredits;
    this.updateCredit = updateCredit;
    this.deleteCredit = deleteCredit;
    this.payCredit = payCredit;
    this.mapper = mapper;
    this.defaultPage = defaultPage;
    this.defaultSize = defaultSize;
  }

  /** Creates a credit and returns its resource URI. */
  @Override
  public Mono<ResponseEntity<Credit>> createCredit(
      Mono<CreditCreateRequest> request, ServerWebExchange exchange) {
    return request
        .flatMap(
            body ->
                RxJava3Adapter.singleToMono(
                    createCredit.create(
                        mapper.toCreateCreditCommand(body, correlationId(exchange)))))
        .map(mapper::toCreditResponse)
        .map(
            credit ->
                ResponseEntity.created(URI.create("/api/v1/credits/" + credit.getId()))
                    .body(credit));
  }

  /** Deletes a credit. */
  @Override
  public Mono<ResponseEntity<Void>> deleteCredit(UUID creditId, ServerWebExchange exchange) {
    return RxJava3Adapter.completableToMono(deleteCredit.delete(creditId))
        .thenReturn(ResponseEntity.noContent().build());
  }

  /** Returns a filtered and paginated credit list. */
  @Override
  public Mono<ResponseEntity<Flux<Credit>>> findAllCredits(
      @Nullable UUID customerId, Integer page, Integer size, ServerWebExchange exchange) {
    Flux<Credit> credits =
        RxJava3Adapter.flowableToFlux(
                getCredits.findAll(
                    new CreditFilter(customerId),
                    page == null ? defaultPage : page,
                    size == null ? defaultSize : size))
            .map(mapper::toCreditResponse);
    return Mono.just(ResponseEntity.ok(credits));
  }

  /** Returns a credit by identifier. */
  @Override
  public Mono<ResponseEntity<Credit>> findCreditById(UUID creditId, ServerWebExchange exchange) {
    return RxJava3Adapter.singleToMono(getCredits.findById(creditId))
        .map(mapper::toCreditResponse)
        .map(ResponseEntity::ok);
  }

  /** Returns the outstanding balance of a credit. */
  @Override
  public Mono<ResponseEntity<Balance>> getCreditBalance(UUID creditId, ServerWebExchange exchange) {
    return RxJava3Adapter.singleToMono(getCredits.getBalance(creditId))
        .map(mapper::toBalanceResponse)
        .map(ResponseEntity::ok);
  }

  /** Pays a credit and returns the movement receipt. */
  @Override
  public Mono<ResponseEntity<MovementReceipt>> payCredit(
      UUID creditId, Mono<OperationRequest> request, ServerWebExchange exchange) {
    return request
        .flatMap(
            body ->
                RxJava3Adapter.singleToMono(
                    payCredit.pay(
                        new OperationCommand(
                            creditId,
                            mapper.toAmount(body),
                            body.getDescription(),
                            correlationId(exchange)))))
        .map(mapper::toMovementResponse)
        .map(
            receipt ->
                ResponseEntity.created(
                        URI.create(
                            "/api/v1/credits/" + creditId + "/payments/" + receipt.getMovementId()))
                    .body(receipt));
  }

  /** Updates editable credit fields. */
  @Override
  public Mono<ResponseEntity<Credit>> updateCredit(
      UUID creditId, Mono<CreditUpdateRequest> request, ServerWebExchange exchange) {
    return request
        .flatMap(
            body ->
                RxJava3Adapter.singleToMono(
                    updateCredit.update(creditId, mapper.toUpdateCreditCommand(body))))
        .map(mapper::toCreditResponse)
        .map(ResponseEntity::ok);
  }

  private String correlationId(ServerWebExchange exchange) {
    String responseHeader = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
    return responseHeader != null
        ? responseHeader
        : exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
  }
}
