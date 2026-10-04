CREATE TABLE library_titles (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    author VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_library_title_code CHECK (char_length(btrim(code,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_library_title_name CHECK (char_length(btrim(title,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_library_title_author CHECK (char_length(btrim(author,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_library_title_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_library_title_version CHECK (row_version >= 0)
);
CREATE UNIQUE INDEX ux_library_title_code ON library_titles(lower(code));
CREATE INDEX ix_library_title_status_code ON library_titles(status,code);

CREATE TABLE library_copies (
    id UUID PRIMARY KEY,
    title_id UUID NOT NULL REFERENCES library_titles(id),
    code VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_library_copy_code CHECK (char_length(btrim(code,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_library_copy_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_library_copy_version CHECK (row_version >= 0)
);
CREATE UNIQUE INDEX ux_library_copy_code ON library_copies(lower(code));
CREATE INDEX ix_library_copy_title_status ON library_copies(title_id,status);

CREATE TABLE library_loans (
    id UUID PRIMARY KEY,
    copy_id UUID NOT NULL REFERENCES library_copies(id),
    student_id UUID NOT NULL REFERENCES students(id),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    row_version BIGINT NOT NULL DEFAULT 0,
    borrowed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '14 days'),
    returned_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_library_loan_status CHECK (status IN ('OPEN','RETURNED')),
    CONSTRAINT ck_library_loan_version CHECK (row_version >= 0),
    CONSTRAINT ck_library_loan_due CHECK (due_at > borrowed_at),
    CONSTRAINT ck_library_loan_history CHECK (
        (status='OPEN' AND returned_at IS NULL) OR
        (status='RETURNED' AND returned_at IS NOT NULL AND returned_at >= borrowed_at))
);
CREATE UNIQUE INDEX ux_library_open_copy ON library_loans(copy_id) WHERE status='OPEN';
CREATE INDEX ix_library_loan_student_status ON library_loans(student_id,status);
CREATE INDEX ix_library_loan_copy_time ON library_loans(copy_id,borrowed_at);
CREATE INDEX ix_library_loan_status_due ON library_loans(status,due_at);

CREATE TABLE library_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES identity_users(id),
    resource_type VARCHAR(16) NOT NULL,
    target_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    resource_version BIGINT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_library_audit_version CHECK (resource_version >= 0),
    CONSTRAINT ck_library_audit_metadata CHECK (jsonb_typeof(metadata)='object'),
    CONSTRAINT ck_library_audit_policy CHECK (
        (resource_type IN ('TITLE','COPY') AND action IN ('CREATED','UPDATED')) OR
        (resource_type='LOAN' AND action IN ('BORROWED','RETURNED')))
);
CREATE INDEX ix_library_audit_target_time ON library_audit_events(resource_type,target_id,occurred_at);
CREATE INDEX ix_library_audit_actor_time ON library_audit_events(actor_user_id,occurred_at);
