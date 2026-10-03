CREATE TABLE finance_fee_definitions (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    amount NUMERIC NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_finance_fee_code CHECK (char_length(btrim(code, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_finance_fee_name CHECK (char_length(btrim(name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_finance_fee_amount CHECK (amount > 0 AND amount <= 9999999999999999999 AND amount = trunc(amount)),
    CONSTRAINT ck_finance_fee_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_finance_fee_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_finance_fee_version CHECK (row_version >= 0)
);
CREATE UNIQUE INDEX ux_finance_fee_code ON finance_fee_definitions(lower(code));
CREATE INDEX ix_finance_fee_status ON finance_fee_definitions(status);

CREATE TABLE finance_student_charges (
    id UUID PRIMARY KEY,
    charge_number VARCHAR(32) NOT NULL,
    student_id UUID NOT NULL REFERENCES students(id),
    fee_id UUID NOT NULL REFERENCES finance_fee_definitions(id),
    fee_code VARCHAR(32) NOT NULL,
    fee_name VARCHAR(160) NOT NULL,
    amount NUMERIC NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    due_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_finance_charge_number CHECK (char_length(btrim(charge_number, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_finance_charge_fee_code CHECK (char_length(btrim(fee_code, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_finance_charge_fee_name CHECK (char_length(btrim(fee_name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_finance_charge_amount CHECK (amount > 0 AND amount <= 9999999999999999999 AND amount = trunc(amount)),
    CONSTRAINT ck_finance_charge_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_finance_charge_status CHECK (status IN ('OPEN','CANCELLED')),
    CONSTRAINT ck_finance_charge_version CHECK (row_version >= 0)
);
CREATE UNIQUE INDEX ux_finance_charge_number ON finance_student_charges(lower(charge_number));
CREATE INDEX ix_finance_charge_student_status ON finance_student_charges(student_id,status);
CREATE INDEX ix_finance_charge_fee_status ON finance_student_charges(fee_id,status);
CREATE INDEX ix_finance_charge_due_date ON finance_student_charges(due_date);

CREATE TABLE finance_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES identity_users(id),
    resource_type VARCHAR(16) NOT NULL,
    target_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    resource_version BIGINT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_finance_audit_policy CHECK (
        (resource_type = 'FEE' AND action IN ('CREATED','UPDATED')) OR
        (resource_type = 'CHARGE' AND action IN ('CREATED','CANCELLED'))),
    CONSTRAINT ck_finance_audit_version CHECK (resource_version >= 0),
    CONSTRAINT ck_finance_audit_metadata CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX ix_finance_audit_target_time ON finance_audit_events(resource_type,target_id,occurred_at);
