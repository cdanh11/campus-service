package com.campus.finance.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import com.campus.finance.domain.*;
import com.campus.shared.application.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class ManualPaymentService {
    private final FinanceRepository charges;
    private final PaymentRepository payments;
    private final PaymentIdempotencyRepository idempotency;
    private final FinanceAudit audit;
    private final Clock clock;
    public ManualPaymentService(FinanceRepository charges, PaymentRepository payments, PaymentIdempotencyRepository idempotency,
                                FinanceAudit audit, Clock clock) {
        this.charges = charges; this.payments = payments; this.idempotency = idempotency; this.audit = audit; this.clock = clock;
    }
    public ManualPayment record(UUID actor, String receipt, UUID chargeId, BigDecimal amount, long expectedChargeVersion) {
        return record(actor, null, receipt, chargeId, amount, expectedChargeVersion).payment();
    }
    public Receipt record(UUID actor, String idempotencyKey, String receipt, UUID chargeId, BigDecimal amount, long expectedChargeVersion) {
        actor(actor); version(expectedChargeVersion);
        if (chargeId == null) throw new IllegalArgumentException("Charge required");
        amount = FinanceValues.amount(amount); receipt = FinanceValues.code(receipt);
        if (idempotencyKey == null) return new Receipt(save(actor, receipt, chargeId, amount, expectedChargeVersion), false);
        String key = FinanceValues.idempotencyKey(idempotencyKey);
        String hash = requestHash(receipt, chargeId, amount, expectedChargeVersion);
        idempotency.lock(actor, key);
        var existing = idempotency.find(actor, key);
        if (existing.isPresent()) {
            if (!existing.get().requestHash().equals(hash)) throw new IdempotencyKeyReusedException();
            return new Receipt(payments.find(existing.get().paymentId()).orElseThrow(FinanceObligationService.NotFoundException::new), true);
        }
        var saved = save(actor, receipt, chargeId, amount, expectedChargeVersion);
        idempotency.create(new PaymentIdempotency(actor, key, hash, saved.id(), clock.instant()));
        return new Receipt(saved, false);
    }
    private ManualPayment save(UUID actor, String receipt, UUID chargeId, BigDecimal amount, long expectedChargeVersion) {
        var charge = charges.lockCharge(chargeId);
        if (charge.rowVersion() != expectedChargeVersion) throw new FinanceObligationService.StaleVersionException();
        if (charge.status() != ChargeStatus.OPEN) throw new FinanceObligationService.InvalidStateException();
        if (amount.compareTo(charge.amount().subtract(payments.totalRecorded(chargeId))) > 0) throw new ExceedsBalanceException();
        var now = clock.instant();
        var saved = payments.create(new ManualPayment(UUID.randomUUID(),receipt,chargeId,amount,charge.currency(),PaymentStatus.RECORDED,0,now,null,null,now,now));
        charges.advanceChargeVersion(chargeId, expectedChargeVersion, now);
        event(actor, saved, "RECORDED"); return saved;
    }
    private static String requestHash(String receipt, UUID chargeId, BigDecimal amount, long expectedChargeVersion) {
        String canonical = chargeId + "\n" + receipt + "\n" + amount.stripTrailingZeros().toPlainString() + "\n" + expectedChargeVersion;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    public ManualPayment reverse(UUID actor, UUID id, long expectedVersion, long expectedChargeVersion, String reason) {
        actor(actor); version(expectedVersion); version(expectedChargeVersion);
        reason = FinanceValues.text(reason, 500);
        var known = get(id);
        var charge = charges.lockCharge(known.chargeId());
        if (charge.rowVersion() != expectedChargeVersion) throw new FinanceObligationService.StaleVersionException();
        if (charge.status() != ChargeStatus.OPEN) throw new FinanceObligationService.InvalidStateException();
        var old = payments.lock(id);
        if (old.rowVersion() != expectedVersion) throw new FinanceObligationService.StaleVersionException();
        if (old.status() != PaymentStatus.RECORDED) throw new FinanceObligationService.InvalidStateException();
        var saved = payments.update(old.reverse(reason,clock.instant()),expectedVersion);
        charges.advanceChargeVersion(charge.id(),expectedChargeVersion,clock.instant());
        event(actor,saved,"REVERSED"); return saved;
    }
    @Transactional(readOnly = true) public ManualPayment get(UUID id) { return payments.find(id).orElseThrow(FinanceObligationService.NotFoundException::new); }
    @Transactional(readOnly = true) public ChargeBalance balance(UUID id) { return payments.balance(id).orElseThrow(FinanceObligationService.NotFoundException::new); }
    @Transactional(readOnly = true) public PageResult<ManualPayment> search(PaymentSearch query) { return payments.search(query); }
    private void event(UUID actor, ManualPayment value, String action) { audit.record(actor,FinanceAudit.Resource.PAYMENT,value.id(),action,value.rowVersion(),value.status().name(),clock.instant()); }
    private void actor(UUID actor) { if(actor == null) throw new IllegalArgumentException("Authenticated actor required"); }
    private void version(long version) { if(version < 0) throw new IllegalArgumentException("Invalid expectedVersion"); }
    public static final class ExceedsBalanceException extends RuntimeException { }
    public static final class IdempotencyKeyReusedException extends RuntimeException { }
    public record Receipt(ManualPayment payment, boolean replayed) { }
}
