package com.campus.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.notification.domain.NotificationDelivery;
import jakarta.persistence.*;

@Entity @Table(name="notification_deliveries")
public class NotificationDeliveryEntity {
    @Id UUID id;
    @Column(name="notice_id",nullable=false,updatable=false) UUID noticeId;
    @Column(name="recipient_id",nullable=false,updatable=false) UUID recipientId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) NotificationDelivery.Status status;
    @Version @Column(name="row_version",nullable=false) long version;
    @Column(name="delivered_at",nullable=false,updatable=false) Instant deliveredAt;
    @Column(name="read_at") Instant readAt;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    protected NotificationDeliveryEntity() { }
    NotificationDeliveryEntity(NotificationDelivery value) { id=value.id(); noticeId=value.noticeId(); recipientId=value.recipientId(); deliveredAt=value.deliveredAt(); createdAt=value.createdAt(); update(value); }
    void update(NotificationDelivery value) { status=value.status(); readAt=value.readAt(); updatedAt=value.updatedAt(); }
    NotificationDelivery domain() { return new NotificationDelivery(id,noticeId,recipientId,status,version,deliveredAt,readAt,createdAt,updatedAt); }
}
