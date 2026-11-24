CREATE TABLE IF NOT EXISTS credits (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    credit_type VARCHAR(16) NOT NULL,
    approved_amount NUMERIC(19, 2) NOT NULL,
    outstanding_balance NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    interest_rate NUMERIC(7, 4),
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_credits_type CHECK (credit_type IN ('PERSONAL', 'BUSINESS')),
    CONSTRAINT ck_credits_status CHECK (status IN ('ACTIVE', 'PAID_OFF')),
    CONSTRAINT ck_credits_amounts CHECK (approved_amount > 0
        AND outstanding_balance >= 0 AND outstanding_balance <= approved_amount),
    CONSTRAINT ck_credits_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_personal_credit
    ON credits (customer_id) WHERE credit_type = 'PERSONAL';
CREATE INDEX IF NOT EXISTS ix_credits_customer ON credits (customer_id);
CREATE INDEX IF NOT EXISTS ix_credits_created_id ON credits (created_at, id);

CREATE TABLE IF NOT EXISTS credit_cards (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    card_type VARCHAR(16) NOT NULL,
    card_number VARCHAR(32) NOT NULL UNIQUE,
    credit_limit NUMERIC(19, 2) NOT NULL,
    available_credit NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_cards_type CHECK (card_type IN ('PERSONAL', 'BUSINESS')),
    CONSTRAINT ck_cards_amounts CHECK (credit_limit > 0
        AND available_credit >= 0 AND available_credit <= credit_limit),
    CONSTRAINT ck_cards_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX IF NOT EXISTS ix_cards_customer ON credit_cards (customer_id);
CREATE INDEX IF NOT EXISTS ix_cards_created_id ON credit_cards (created_at, id);
