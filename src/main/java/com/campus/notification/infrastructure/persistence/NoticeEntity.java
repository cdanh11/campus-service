package com.campus.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.notification.domain.Notice;
import jakarta.persistence.*;

@Entity @Table(name="notification_notices")
public class NoticeEntity {
    @Id UUID id;
    @Column(name="template_id",nullable=false,updatable=false) UUID templateId;
    @Column(nullable=false,length=160) String title;
    @Column(nullable=false,length=4000) String body;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) Notice.Status status;
    @Version @Column(name="row_version",nullable=false) long version;
    @Column(name="published_at") Instant publishedAt;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    protected NoticeEntity() { }
    NoticeEntity(Notice value) { id=value.id(); templateId=value.templateId(); createdAt=value.createdAt(); update(value); }
    void update(Notice value) { title=value.title(); body=value.body(); status=value.status(); publishedAt=value.publishedAt(); updatedAt=value.updatedAt(); }
    Notice domain() { return new Notice(id,templateId,title,body,status,version,publishedAt,createdAt,updatedAt); }
}
