package com.campus.finance.infrastructure.obligations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.campus.finance.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "finance_fee_definitions")
public class FeeDefinitionEntity {
    @Id @Column(nullable = false) private UUID id;
    @Column(nullable = false, length = 32) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false, length = 3, updatable = false) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private FeeStatus status;
    @Version @Column(name = "row_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected FeeDefinitionEntity() { }
    FeeDefinitionEntity(FeeDefinition value) { id = value.id(); currency = value.currency(); createdAt = value.createdAt(); update(value); }
    void update(FeeDefinition value) { code = value.code(); name = value.name(); amount = value.amount(); status = value.status(); updatedAt = value.updatedAt(); }
    FeeDefinition domain() { return new FeeDefinition(id, code, name, amount, currency, status, version, createdAt, updatedAt); }
}
