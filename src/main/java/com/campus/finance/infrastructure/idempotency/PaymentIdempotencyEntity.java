package com.campus.finance.infrastructure.idempotency;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.campus.finance.domain.PaymentIdempotency;
import jakarta.persistence.*;

@Entity @Table(name="finance_idempotency_keys")
@IdClass(PaymentIdempotencyEntity.Key.class)
class PaymentIdempotencyEntity {
    @Id @Column(name="actor_user_id",nullable=false,updatable=false) private UUID actorUserId;
    @Id @Column(name="idempotency_key",nullable=false,length=100,updatable=false) private String idempotencyKey;
    @Column(name="request_hash",nullable=false,length=64,updatable=false) private String requestHash;
    @Column(name="payment_id",nullable=false,updatable=false) private UUID paymentId;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected PaymentIdempotencyEntity() { }
    PaymentIdempotencyEntity(PaymentIdempotency value) {
        actorUserId=value.actorUserId(); idempotencyKey=value.key(); requestHash=value.requestHash();
        paymentId=value.paymentId(); createdAt=value.createdAt();
    }
    PaymentIdempotency domain() { return new PaymentIdempotency(actorUserId,idempotencyKey,requestHash,paymentId,createdAt); }
    static final class Key implements Serializable {
        private UUID actorUserId;
        private String idempotencyKey;
        protected Key() { }
        Key(UUID actorUserId, String idempotencyKey) { this.actorUserId=actorUserId; this.idempotencyKey=idempotencyKey; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(actorUserId,key.actorUserId) && Objects.equals(idempotencyKey,key.idempotencyKey);
        }
        @Override public int hashCode() { return Objects.hash(actorUserId,idempotencyKey); }
    }
}
