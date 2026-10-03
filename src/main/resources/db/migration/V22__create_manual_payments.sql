CREATE TABLE finance_manual_payments (
    id UUID PRIMARY KEY,
    receipt_number VARCHAR(32) NOT NULL,
    charge_id UUID NOT NULL REFERENCES finance_student_charges(id),
    amount NUMERIC NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(16) NOT NULL DEFAULT 'RECORDED',
    row_version BIGINT NOT NULL DEFAULT 0,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_at TIMESTAMP WITH TIME ZONE,
    reversal_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_finance_payment_receipt CHECK (char_length(btrim(receipt_number,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_finance_payment_amount CHECK (amount > 0 AND amount <= 9999999999999999999 AND amount = trunc(amount)),
    CONSTRAINT ck_finance_payment_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_finance_payment_status CHECK (status IN ('RECORDED','REVERSED')),
    CONSTRAINT ck_finance_payment_version CHECK (row_version >= 0),
    CONSTRAINT ck_finance_payment_reversal CHECK (
        (status = 'RECORDED' AND reversed_at IS NULL AND reversal_reason IS NULL) OR
        (status = 'REVERSED' AND reversed_at IS NOT NULL AND reversed_at >= recorded_at
         AND reversal_reason IS NOT NULL AND char_length(btrim(reversal_reason,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 500))
);
CREATE UNIQUE INDEX ux_finance_payment_receipt ON finance_manual_payments(lower(receipt_number));
CREATE INDEX ix_finance_payment_charge_status ON finance_manual_payments(charge_id,status);

ALTER TABLE finance_audit_events DROP CONSTRAINT ck_finance_audit_policy;
ALTER TABLE finance_audit_events ADD CONSTRAINT ck_finance_audit_policy CHECK (
    (resource_type = 'FEE' AND action IN ('CREATED','UPDATED')) OR
    (resource_type = 'CHARGE' AND action IN ('CREATED','CANCELLED')) OR
    (resource_type = 'PAYMENT' AND action IN ('RECORDED','REVERSED')));
