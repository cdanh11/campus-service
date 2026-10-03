package com.campus.dormitory.application;

import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import com.campus.dormitory.domain.*;
import com.campus.student.application.StudentManagementService;
import com.campus.student.domain.StudentStatus;
import com.campus.shared.application.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class AccommodationAssignmentService {
    private final AssignmentRepository assignments;
    private final InventoryRepository inventory;
    private final StudentManagementService students;
    private final DormitoryAudit audit;
    private final Clock clock;

    public AccommodationAssignmentService(AssignmentRepository assignments, InventoryRepository inventory,
                                          StudentManagementService students, DormitoryAudit audit, Clock clock) {
        this.assignments = assignments; this.inventory = inventory; this.students = students; this.audit = audit; this.clock = clock;
    }
    public AccommodationAssignment create(UUID actor, UUID studentId, UUID bedId) {
        actor(actor);
        if (studentId == null || bedId == null) throw new IllegalArgumentException("References required");
        lockBed(bedId, true);
        try { if (students.get(studentId).status() != StudentStatus.ACTIVE) throw new StudentUnavailableException(); }
        catch (StudentManagementService.StudentNotFoundException failure) { throw new StudentUnavailableException(); }
        if (assignments.hasCurrentBed(bedId) || assignments.hasCurrentStudent(studentId)) throw new AlreadyAssignedException();
        var now = clock.instant();
        AccommodationAssignment saved;
        try {
            saved = assignments.create(new AccommodationAssignment(UUID.randomUUID(), studentId, bedId, AssignmentStatus.ASSIGNED, 0, now, null, now, now));
        } catch (DataIntegrityViolationException failure) {
            if (failure.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) throw new AlreadyAssignedException();
            throw failure;
        }
        audit.record(actor, saved, "ASSIGNED", now);
        return saved;
    }
    public AccommodationAssignment release(UUID actor, UUID id, long expectedVersion) {
        actor(actor);
        if (expectedVersion < 0) throw new IllegalArgumentException("Invalid expectedVersion");
        var known = get(id);
        lockBed(known.bedId(), false);
        var old = assignments.lock(id);
        if (old.rowVersion() != expectedVersion) throw new DormitoryInventoryService.StaleVersionException();
        if (old.status() != AssignmentStatus.ASSIGNED) throw new InvalidAssignmentStateException();
        var saved = assignments.update(old.release(clock.instant()), expectedVersion);
        audit.record(actor, saved, "RELEASED", clock.instant());
        return saved;
    }
    @Transactional(readOnly = true)
    public AccommodationAssignment get(UUID id) { return assignments.find(id).orElseThrow(DormitoryInventoryService.NotFoundException::new); }
    @Transactional(readOnly = true)
    public PageResult<AccommodationAssignment> search(AssignmentSearch query) { return assignments.search(query); }

    private void lockBed(UUID bedId, boolean requireActive) {
        var bed = inventory.find(InventoryKind.BED, bedId).orElseThrow(DormitoryInventoryService.NotFoundException::new);
        var room = inventory.find(InventoryKind.ROOM, bed.parentId()).orElseThrow(DormitoryInventoryService.NotFoundException::new);
        var building = inventory.lock(InventoryKind.BUILDING, room.parentId());
        room = inventory.lock(InventoryKind.ROOM, room.id());
        bed = inventory.lock(InventoryKind.BED, bedId);
        if (requireActive && (building.status() != InventoryStatus.ACTIVE || room.status() != InventoryStatus.ACTIVE || bed.status() != InventoryStatus.ACTIVE))
            throw new DormitoryInventoryService.ReferenceUnavailableException();
    }
    private void actor(UUID actor) { if (actor == null) throw new IllegalArgumentException("Authenticated actor required"); }
    public static final class AlreadyAssignedException extends RuntimeException { }
    public static final class StudentUnavailableException extends RuntimeException { }
    public static final class InvalidAssignmentStateException extends RuntimeException { }
}
