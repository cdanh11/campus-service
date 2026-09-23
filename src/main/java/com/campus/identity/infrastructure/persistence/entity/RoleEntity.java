package com.campus.identity.infrastructure.persistence.entity;

import java.util.UUID;

import com.campus.identity.domain.RoleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_roles")
public class RoleEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoleCode code;

    protected RoleEntity() {
    }

    public UUID getId() {
        return id;
    }

    public RoleCode getCode() {
        return code;
    }
}
