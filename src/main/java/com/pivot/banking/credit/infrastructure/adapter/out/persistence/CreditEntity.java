package com.pivot.banking.credit.infrastructure.adapter.out.persistence;

import com.pivot.banking.credit.domain.Credit;
import com.pivot.banking.credit.domain.CreditStatus;
import com.pivot.banking.credit.domain.CreditType;
import com.pivot.banking.credit.domain.Money;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Relational representation of a credit. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("credits")
public class CreditEntity {

    @Id
    private UUID id;
    private UUID customerId;
    private String creditType;
    private BigDecimal approvedAmount;
    private BigDecimal outstandingBalance;
    private String currency;
    private BigDecimal interestRate;
    private String status;
    @Version
    private Long version;
    private Instant createdAt;

    /** Converts a domain credit to its relational row. */
    public static CreditEntity fromDomain(Credit credit) {
        return new CreditEntity(credit.id(), credit.customerId(), credit.creditType().name(),
                credit.approvedAmount().amount(), credit.outstandingBalance().amount(),
                credit.approvedAmount().currency(), credit.interestRate(), credit.status().name(),
                credit.version(), credit.createdAt());
    }

    /** Rebuilds the domain credit from its relational row. */
    public Credit toDomain() {
        return new Credit(id, customerId, CreditType.valueOf(creditType),
                new Money(approvedAmount, currency), new Money(outstandingBalance, currency),
                interestRate, CreditStatus.valueOf(status), version, createdAt);
    }
}
