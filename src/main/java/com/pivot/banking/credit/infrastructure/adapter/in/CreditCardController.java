package com.pivot.banking.credit.infrastructure.adapter.in;

import com.pivot.banking.credit.adapter.in.api.CreditCardsApi;
import com.pivot.banking.credit.adapter.in.dto.Balance;
import com.pivot.banking.credit.adapter.in.dto.CreditCard;
import com.pivot.banking.credit.adapter.in.dto.CreditCardCreateRequest;
import com.pivot.banking.credit.adapter.in.dto.CreditCardUpdateRequest;
import com.pivot.banking.credit.adapter.in.dto.MovementReceipt;
import com.pivot.banking.credit.adapter.in.dto.OperationRequest;
import com.pivot.banking.credit.application.port.in.CardUseCases.ChargeCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.CreateCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.DeleteCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.GetCards;
import com.pivot.banking.credit.application.port.in.CardUseCases.OperationCommand;
import com.pivot.banking.credit.application.port.in.CardUseCases.PayCard;
import com.pivot.banking.credit.application.port.in.CardUseCases.UpdateCard;
import com.pivot.banking.credit.domain.CreditCardFilter;
import reactor.adapter.rxjava.RxJava3Adapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

/** REST adapter for credit card endpoints. */
@Controller
public class CreditCardController implements CreditCardsApi {

    private final CreateCard createCard;
    private final GetCards getCards;
    private final UpdateCard updateCard;
    private final DeleteCard deleteCard;
    private final ChargeCard chargeCard;
    private final PayCard payCard;
    private final CreditRestMapper mapper;
    private final int defaultPage;
    private final int defaultSize;

    /** Creates the controller with its use cases, mapper and paging defaults. */
    public CreditCardController(CreateCard createCard, GetCards getCards, UpdateCard updateCard,
                                DeleteCard deleteCard, ChargeCard chargeCard, PayCard payCard,
                                CreditRestMapper mapper,
                                @Value("${credit.pagination.default-page:0}") int defaultPage,
                                @Value("${credit.pagination.default-size:20}") int defaultSize) {
        this.createCard = createCard;
        this.getCards = getCards;
        this.updateCard = updateCard;
        this.deleteCard = deleteCard;
        this.chargeCard = chargeCard;
        this.payCard = payCard;
        this.mapper = mapper;
        this.defaultPage = defaultPage;
        this.defaultSize = defaultSize;
    }

    /** Charges a consumption to a card. */
    @Override
    public Mono<ResponseEntity<MovementReceipt>> chargeCreditCard(UUID cardId, Mono<OperationRequest> request,
                                                                  ServerWebExchange exchange) {
        return request.flatMap(body -> RxJava3Adapter.singleToMono(chargeCard.charge(
                        new OperationCommand(cardId, mapper.toAmount(body), body.getDescription(),
                                correlationId(exchange)))))
                .map(mapper::toMovementResponse)
                .map(receipt -> ResponseEntity.created(
                        URI.create("/api/v1/credit-cards/" + cardId + "/movements/" + receipt.getMovementId()))
                        .body(receipt));
    }

    /** Issues a card. */
    @Override
    public Mono<ResponseEntity<CreditCard>> createCreditCard(Mono<CreditCardCreateRequest> request,
                                                             ServerWebExchange exchange) {
        return request.flatMap(body -> RxJava3Adapter.singleToMono(
                        createCard.create(mapper.toCreateCardCommand(body, correlationId(exchange)))))
                .map(mapper::toCardResponse)
                .map(card -> ResponseEntity.created(URI.create("/api/v1/credit-cards/" + card.getId()))
                        .body(card));
    }

    /** Deletes a card. */
    @Override
    public Mono<ResponseEntity<Void>> deleteCreditCard(UUID cardId, ServerWebExchange exchange) {
        return RxJava3Adapter.completableToMono(deleteCard.delete(cardId))
                .thenReturn(ResponseEntity.noContent().build());
    }

    /** Returns a filtered and paginated card list. */
    @Override
    public Mono<ResponseEntity<Flux<CreditCard>>> findAllCreditCards(@Nullable UUID customerId, Integer page,
                                                                     Integer size, ServerWebExchange exchange) {
        Flux<CreditCard> cards = RxJava3Adapter.flowableToFlux(getCards.findAll(new CreditCardFilter(customerId),
                        page == null ? defaultPage : page, size == null ? defaultSize : size)
                ).map(mapper::toCardResponse);
        return Mono.just(ResponseEntity.ok(cards));
    }

    /** Returns a card by identifier. */
    @Override
    public Mono<ResponseEntity<CreditCard>> findCreditCardById(UUID cardId, ServerWebExchange exchange) {
        return RxJava3Adapter.singleToMono(getCards.findById(cardId))
                .map(mapper::toCardResponse).map(ResponseEntity::ok);
    }

    /** Returns the available credit of a card. */
    @Override
    public Mono<ResponseEntity<Balance>> getCreditCardBalance(UUID cardId, ServerWebExchange exchange) {
        return RxJava3Adapter.singleToMono(getCards.getBalance(cardId))
                .map(mapper::toBalanceResponse).map(ResponseEntity::ok);
    }

    /** Pays part of the used credit of a card. */
    @Override
    public Mono<ResponseEntity<MovementReceipt>> payCreditCard(UUID cardId, Mono<OperationRequest> request,
                                                               ServerWebExchange exchange) {
        return request.flatMap(body -> RxJava3Adapter.singleToMono(payCard.pay(
                        new OperationCommand(cardId, mapper.toAmount(body), body.getDescription(),
                                correlationId(exchange)))))
                .map(mapper::toMovementResponse)
                .map(receipt -> ResponseEntity.created(
                        URI.create("/api/v1/credit-cards/" + cardId + "/movements/" + receipt.getMovementId()))
                        .body(receipt));
    }

    /** Updates editable card fields. */
    @Override
    public Mono<ResponseEntity<CreditCard>> updateCreditCard(UUID cardId, Mono<CreditCardUpdateRequest> request,
                                                             ServerWebExchange exchange) {
        return request.flatMap(body -> RxJava3Adapter.singleToMono(
                        updateCard.update(cardId, mapper.toUpdateCardCommand(body))))
                .map(mapper::toCardResponse)
                .map(ResponseEntity::ok);
    }

    private String correlationId(ServerWebExchange exchange) {
        String responseHeader = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        return responseHeader != null
                ? responseHeader
                : exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
    }
}
