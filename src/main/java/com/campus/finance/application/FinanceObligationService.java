package com.campus.finance.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import com.campus.finance.domain.*;
import com.campus.shared.application.PageResult;
import com.campus.student.application.StudentManagementService;
import com.campus.student.domain.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FinanceObligationService {
    private final FinanceRepository repository;
    private final StudentManagementService students;
    private final FinanceAudit audit;
    private final Clock clock;
    public FinanceObligationService(FinanceRepository repository, StudentManagementService students, FinanceAudit audit, Clock clock) {
        this.repository = repository; this.students = students; this.audit = audit; this.clock = clock;
    }
    public FeeDefinition createFee(UUID actor, String code, String name, BigDecimal amount) {
        actor(actor); var now = clock.instant();
        var saved = repository.createFee(new FeeDefinition(UUID.randomUUID(), code, name, amount, "VND", FeeStatus.ACTIVE, 0, now, now));
        event(actor, saved, "CREATED"); return saved;
    }
    public FeeDefinition updateFee(UUID actor, UUID id, String code, String name, BigDecimal amount, FeeStatus status, long expectedVersion) {
        actor(actor); version(expectedVersion);
        var old = repository.lockFee(id);
        if (old.rowVersion() != expectedVersion) throw new StaleVersionException();
        var saved = repository.updateFee(new FeeDefinition(id, code, name, amount, old.currency(), status, old.rowVersion(), old.createdAt(), clock.instant()), expectedVersion);
        event(actor, saved, "UPDATED"); return saved;
    }
    public StudentCharge createCharge(UUID actor, String number, UUID studentId, UUID feeId, LocalDate dueDate) {
        actor(actor);
        if (studentId == null || feeId == null) throw new IllegalArgumentException("References required");
        var fee = repository.lockFee(feeId);
        if (fee.status() != FeeStatus.ACTIVE) throw new ReferenceUnavailableException();
        try { if (students.get(studentId).status() != StudentStatus.ACTIVE) throw new ReferenceUnavailableException(); }
        catch (StudentManagementService.StudentNotFoundException failure) { throw new ReferenceUnavailableException(); }
        var now = clock.instant();
        var saved = repository.createCharge(new StudentCharge(UUID.randomUUID(), number, studentId, feeId, fee.code(), fee.name(),
                fee.amount(), fee.currency(), dueDate, ChargeStatus.OPEN, 0, now, now));
        event(actor, saved, "CREATED"); return saved;
    }
    public StudentCharge cancelCharge(UUID actor, UUID id, long expectedVersion) {
        actor(actor); version(expectedVersion);
        var old = repository.lockCharge(id);
        if (old.rowVersion() != expectedVersion) throw new StaleVersionException();
        if (old.status() != ChargeStatus.OPEN) throw new InvalidStateException();
        var saved = repository.updateCharge(old.cancel(clock.instant()), expectedVersion);
        event(actor, saved, "CANCELLED"); return saved;
    }
    @Transactional(readOnly = true) public FeeDefinition fee(UUID id) { return repository.fee(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly = true) public StudentCharge charge(UUID id) { return repository.charge(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly = true) public PageResult<FeeDefinition> fees(FinanceSearch query) { query.fees(); return repository.fees(query); }
    @Transactional(readOnly = true) public PageResult<StudentCharge> charges(FinanceSearch query) { query.charges(); return repository.charges(query); }
    private void event(UUID actor, FeeDefinition fee, String action) { audit.record(actor, FinanceAudit.Resource.FEE, fee.id(), action, fee.rowVersion(), fee.status().name(), clock.instant()); }
    private void event(UUID actor, StudentCharge charge, String action) { audit.record(actor, FinanceAudit.Resource.CHARGE, charge.id(), action, charge.rowVersion(), charge.status().name(), clock.instant()); }
    private void actor(UUID actor) { if (actor == null) throw new IllegalArgumentException("Authenticated actor required"); }
    private void version(long version) { if (version < 0) throw new IllegalArgumentException("Invalid expectedVersion"); }
    public static final class NotFoundException extends RuntimeException { }
    public static final class StaleVersionException extends RuntimeException { }
    public static final class ReferenceUnavailableException extends RuntimeException { }
    public static final class InvalidStateException extends RuntimeException { }
}
