package com.campus.finance.infrastructure.payments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.campus.finance.domain.*;
import jakarta.persistence.*;

@Entity @Table(name="finance_manual_payments")
public class ManualPaymentEntity {
    @Id @Column(nullable=false) private UUID id;
    @Column(name="receipt_number",nullable=false,length=32,updatable=false) private String receiptNumber;
    @Column(name="charge_id",nullable=false,updatable=false) private UUID chargeId;
    @Column(nullable=false,updatable=false) private BigDecimal amount;
    @Column(nullable=false,length=3,updatable=false) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private PaymentStatus status;
    @Version @Column(name="row_version",nullable=false) private long version;
    @Column(name="recorded_at",nullable=false,updatable=false) private Instant recordedAt;
    @Column(name="reversed_at") private Instant reversedAt;
    @Column(name="reversal_reason",length=500) private String reversalReason;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected ManualPaymentEntity() { }
    ManualPaymentEntity(ManualPayment value) {
        id=value.id(); receiptNumber=value.receiptNumber(); chargeId=value.chargeId(); amount=value.amount(); currency=value.currency();
        recordedAt=value.recordedAt(); createdAt=value.createdAt(); update(value);
    }
    void update(ManualPayment value) { status=value.status(); reversedAt=value.reversedAt(); reversalReason=value.reversalReason(); updatedAt=value.updatedAt(); }
    ManualPayment domain() { return new ManualPayment(id,receiptNumber,chargeId,amount,currency,status,version,recordedAt,reversedAt,reversalReason,createdAt,updatedAt); }
}
