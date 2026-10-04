package com.campus.student.infrastructure.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.campus.student.application.StudentAccountDirectory;
import com.campus.student.domain.StudentStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class StudentAccountDirectoryAdapter implements StudentAccountDirectory {
    private final EntityManager entities;
    StudentAccountDirectoryAdapter(EntityManager entities) { this.entities = entities; }
    @Override @Transactional(readOnly = true)
    public Optional<LinkedStudent> findByAccount(UUID accountId) {
        Objects.requireNonNull(accountId, "Authenticated account required");
        // Scalar projection deliberately bypasses possibly stale managed Student entities.
        return entities.createQuery("select s.id, s.status from StudentEntity s where s.identityUserId=:account", Object[].class)
                .setParameter("account", accountId).getResultStream()
                .map(row -> new LinkedStudent((UUID) row[0], (StudentStatus) row[1])).findFirst();
    }
    @Override @Transactional(readOnly = true)
    public Optional<LinkedStudent> findByStudent(UUID studentId) {
        Objects.requireNonNull(studentId,"Student required");
        return entities.createQuery("select s.id, s.status from StudentEntity s where s.id=:student",Object[].class)
                .setParameter("student",studentId).getResultStream()
                .map(row -> new LinkedStudent((UUID)row[0],(StudentStatus)row[1])).findFirst();
    }
}
