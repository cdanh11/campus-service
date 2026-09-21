CREATE TABLE identity_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    security_version BIGINT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_identity_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_identity_users_security_version CHECK (security_version >= 0)
);

CREATE UNIQUE INDEX ux_identity_users_email_lower ON identity_users (LOWER(email));

CREATE TABLE identity_roles (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE
);

INSERT INTO identity_roles (id, code) VALUES
    ('00000000-0000-0000-0000-000000000001', 'USER'),
    ('00000000-0000-0000-0000-000000000002', 'ADMIN');

CREATE TABLE identity_user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_identity_user_roles_user FOREIGN KEY (user_id) REFERENCES identity_users (id),
    CONSTRAINT fk_identity_user_roles_role FOREIGN KEY (role_id) REFERENCES identity_roles (id)
);

CREATE INDEX ix_identity_user_roles_role_id ON identity_user_roles (role_id);
