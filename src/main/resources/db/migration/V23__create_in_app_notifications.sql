CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notification_template_code CHECK (char_length(btrim(code,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 32),
    CONSTRAINT ck_notification_template_name CHECK (char_length(btrim(name,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_notification_template_title CHECK (char_length(btrim(title,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_notification_template_body CHECK (char_length(btrim(body,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 4000),
    CONSTRAINT ck_notification_template_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_notification_template_version CHECK (row_version >= 0)
);
CREATE UNIQUE INDEX ux_notification_template_code ON notification_templates(lower(code));

CREATE TABLE notification_notices (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES notification_templates(id),
    title VARCHAR(160) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    row_version BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notification_notice_title CHECK (char_length(btrim(title,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 160),
    CONSTRAINT ck_notification_notice_body CHECK (char_length(btrim(body,chr(32)||chr(9)||chr(10)||chr(13)||chr(11)||chr(12))) BETWEEN 2 AND 4000),
    CONSTRAINT ck_notification_notice_status CHECK (status IN ('DRAFT','PUBLISHED')),
    CONSTRAINT ck_notification_notice_version CHECK (row_version >= 0),
    CONSTRAINT ck_notification_notice_publish CHECK ((status='DRAFT' AND published_at IS NULL) OR (status='PUBLISHED' AND published_at IS NOT NULL))
);
CREATE INDEX ix_notification_notice_status_time ON notification_notices(status,created_at);

CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY,
    notice_id UUID NOT NULL REFERENCES notification_notices(id),
    recipient_id UUID NOT NULL REFERENCES identity_users(id),
    status VARCHAR(16) NOT NULL DEFAULT 'UNREAD',
    row_version BIGINT NOT NULL DEFAULT 0,
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_notification_delivery_notice_recipient UNIQUE (notice_id,recipient_id),
    CONSTRAINT ck_notification_delivery_status CHECK (status IN ('UNREAD','READ')),
    CONSTRAINT ck_notification_delivery_version CHECK (row_version >= 0),
    CONSTRAINT ck_notification_delivery_read CHECK ((status='UNREAD' AND read_at IS NULL) OR (status='READ' AND read_at IS NOT NULL AND read_at>=delivered_at))
);
CREATE INDEX ix_notification_delivery_recipient_status ON notification_deliveries(recipient_id,status,delivered_at);

CREATE TABLE notification_audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES identity_users(id),
    resource_type VARCHAR(16) NOT NULL,
    target_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL,
    resource_version BIGINT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_notification_audit_version CHECK (resource_version>=0),
    CONSTRAINT ck_notification_audit_metadata CHECK (jsonb_typeof(metadata)='object'),
    CONSTRAINT ck_notification_audit_policy CHECK (
        (resource_type='TEMPLATE' AND action IN ('CREATED','UPDATED')) OR
        (resource_type='NOTICE' AND action IN ('CREATED','UPDATED','PUBLISHED')) OR
        (resource_type='DELIVERY' AND action='READ'))
);
CREATE INDEX ix_notification_audit_target_time ON notification_audit_events(resource_type,target_id,occurred_at);
CREATE INDEX ix_notification_audit_actor_time ON notification_audit_events(actor_user_id,occurred_at);
