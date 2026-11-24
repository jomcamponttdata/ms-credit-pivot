package com.pivot.banking.credit.application.port.out;

import com.pivot.banking.credit.domain.Money;
import com.pivot.banking.credit.domain.MovementReceipt;
import com.pivot.banking.credit.domain.MovementType;
import com.pivot.banking.credit.domain.ProductType;
import io.reactivex.rxjava3.core.Single;

import java.util.UUID;

/** Boundary for movement registration in ms-transaction. */
public interface MovementGateway {

    /** Registers one movement and returns its confirmation. */
    Single<MovementReceipt> register(MovementCommand command);

    /** Data used to register a movement. */
    record MovementCommand(UUID productId, ProductType productType, MovementType movementType,
                           Money amount, String description, String correlationId) {
        public MovementCommand(UUID productId, ProductType productType, MovementType movementType,
                               Money amount, String description) {
            this(productId, productType, movementType, amount, description, null);
        }
    }
}
