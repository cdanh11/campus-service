package com.campus.finance.infrastructure.obligations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.campus.finance.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "finance_student_charges")
public class StudentChargeEntity {
    @Id @Column(nullable = false) private UUID id;
    @Column(name = "charge_number", nullable = false, length = 32, updatable = false) private String chargeNumber;
    @Column(name = "student_id", nullable = false, updatable = false) private UUID studentId;
    @Column(name = "fee_id", nullable = false, updatable = false) private UUID feeId;
    @Column(name = "fee_code", nullable = false, length = 32, updatable = false) private String feeCode;
    @Column(name = "fee_name", nullable = false, length = 160, updatable = false) private String feeName;
    @Column(nullable = false, updatable = false) private BigDecimal amount;
    @Column(nullable = false, length = 3, updatable = false) private String currency;
    @Column(name = "due_date", nullable = false, updatable = false) private LocalDate dueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ChargeStatus status;
    @Version @Column(name = "row_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected StudentChargeEntity() { }
    StudentChargeEntity(StudentCharge value) {
        id = value.id(); chargeNumber = value.chargeNumber(); studentId = value.studentId(); feeId = value.feeId();
        feeCode = value.feeCode(); feeName = value.feeName(); amount = value.amount(); currency = value.currency();
        dueDate = value.dueDate(); createdAt = value.createdAt(); update(value);
    }
    void update(StudentCharge value) { status = value.status(); updatedAt = value.updatedAt(); }
    StudentCharge domain() { return new StudentCharge(id, chargeNumber, studentId, feeId, feeCode, feeName, amount, currency, dueDate, status, version, createdAt, updatedAt); }
}
