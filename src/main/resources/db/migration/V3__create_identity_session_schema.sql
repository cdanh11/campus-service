CREATE TABLE identity_auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(64),
    CONSTRAINT fk_identity_auth_sessions_user FOREIGN KEY (user_id) REFERENCES identity_users (id),
    CONSTRAINT ck_identity_auth_sessions_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX ix_identity_auth_sessions_user_revoked ON identity_auth_sessions (user_id, revoked_at);
CREATE INDEX ix_identity_auth_sessions_expires_at ON identity_auth_sessions (expires_at);

CREATE TABLE identity_refresh_tokens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    token_hash BYTEA NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by_id UUID,
    CONSTRAINT uq_identity_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_identity_refresh_tokens_session FOREIGN KEY (session_id) REFERENCES identity_auth_sessions (id),
    CONSTRAINT fk_identity_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES identity_refresh_tokens (id),
    CONSTRAINT ck_identity_refresh_tokens_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX ix_identity_refresh_tokens_session_id ON identity_refresh_tokens (session_id);
CREATE INDEX ix_identity_refresh_tokens_expires_at ON identity_refresh_tokens (expires_at);
