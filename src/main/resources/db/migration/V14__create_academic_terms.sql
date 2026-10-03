CREATE TABLE academic_terms (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_academic_terms_code CHECK (char_length(btrim(code, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_academic_terms_name CHECK (char_length(btrim(name, chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) >= 2),
    CONSTRAINT ck_academic_terms_dates CHECK (start_date <= end_date),
    CONSTRAINT ck_academic_terms_status CHECK (status IN ('PLANNED', 'ACTIVE', 'CLOSED', 'CANCELLED'))
);
CREATE UNIQUE INDEX ux_academic_terms_code_lower ON academic_terms (lower(code));
CREATE INDEX ix_academic_terms_status_start ON academic_terms (status, start_date);
