package com.campus.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.notification.domain.NotificationTemplate;
import jakarta.persistence.*;

@Entity @Table(name="notification_templates")
public class NotificationTemplateEntity {
    @Id UUID id;
    @Column(nullable=false,length=32) String code;
    @Column(nullable=false,length=160) String name;
    @Column(nullable=false,length=160) String title;
    @Column(nullable=false,length=4000) String body;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) NotificationTemplate.Status status;
    @Version @Column(name="row_version",nullable=false) long version;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    protected NotificationTemplateEntity() { }
    NotificationTemplateEntity(NotificationTemplate value) { id=value.id(); createdAt=value.createdAt(); update(value); }
    void update(NotificationTemplate value) { code=value.code(); name=value.name(); title=value.title(); body=value.body(); status=value.status(); updatedAt=value.updatedAt(); }
    NotificationTemplate domain() { return new NotificationTemplate(id,code,name,title,body,status,version,createdAt,updatedAt); }
}
