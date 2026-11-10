CREATE TABLE finance_idempotency_keys (
    actor_user_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    payment_id UUID NOT NULL REFERENCES finance_manual_payments(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_finance_idempotency_keys PRIMARY KEY (actor_user_id, idempotency_key),
    CONSTRAINT ck_finance_idempotency_key CHECK (char_length(btrim(idempotency_key, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 8 AND 100),
    CONSTRAINT ck_finance_idempotency_hash CHECK (request_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_finance_idempotency_payment ON finance_idempotency_keys(payment_id);
