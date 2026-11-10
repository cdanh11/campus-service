package com.campus.finance.infrastructure.idempotency;

import java.util.Optional;
import java.util.UUID;
import com.campus.finance.domain.PaymentIdempotency;
import com.campus.finance.domain.PaymentIdempotencyRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
class PaymentIdempotencyPersistenceAdapter implements PaymentIdempotencyRepository {
    private final EntityManager entities;
    PaymentIdempotencyPersistenceAdapter(EntityManager entities) { this.entities=entities; }
    public void lock(UUID actorUserId, String key) {
        entities.createNativeQuery("select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) locked",Integer.class)
                .setParameter("key",actorUserId+":"+key).getSingleResult();
    }
    public Optional<PaymentIdempotency> find(UUID actorUserId, String key) {
        return Optional.ofNullable(entities.find(PaymentIdempotencyEntity.class,new PaymentIdempotencyEntity.Key(actorUserId,key)))
                .map(PaymentIdempotencyEntity::domain);
    }
    public PaymentIdempotency create(PaymentIdempotency value) {
        var entity=new PaymentIdempotencyEntity(value); entities.persist(entity); entities.flush(); return entity.domain();
    }
}
