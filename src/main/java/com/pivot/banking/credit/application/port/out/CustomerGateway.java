package com.pivot.banking.credit.application.port.out;

import com.pivot.banking.credit.domain.CustomerSnapshot;
import io.reactivex.rxjava3.core.Single;

import java.util.UUID;

/** Boundary for customer validation in ms-customer. */
public interface CustomerGateway {

    /** Retrieves the customer type and lifecycle state. */
    Single<CustomerSnapshot> findById(UUID customerId, String correlationId);

    /** Retrieves a customer when no request correlation identifier is available. */
    default Single<CustomerSnapshot> findById(UUID customerId) {
        return findById(customerId, null);
    }
}
