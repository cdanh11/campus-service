package com.campus.identity.domain;

public interface AdminAuditEventRepository {

    AdminAuditEvent save(AdminAuditEvent event);
}
