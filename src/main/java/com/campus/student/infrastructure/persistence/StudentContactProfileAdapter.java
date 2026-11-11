package com.campus.student.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import com.campus.student.application.StudentProfileService.ConcurrentProfileModificationException;
import com.campus.student.domain.StudentContactProfile;
import com.campus.student.domain.StudentContactProfileRepository;
import com.campus.student.infrastructure.persistence.entity.StudentContactProfileEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class StudentContactProfileAdapter implements StudentContactProfileRepository {
    private final StudentContactProfileJpaRepository repo;

    StudentContactProfileAdapter(StudentContactProfileJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentContactProfile> findByStudentId(UUID studentId) {
        return repo.findById(studentId).map(this::map);
    }

    @Override
    @Transactional
    public StudentContactProfile save(StudentContactProfile profile, long expectedVersion) {
        var entity = locked(profile.studentId(), expectedVersion);
        entity.updateContact(profile.phone(), profile.address());
        return map(repo.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public StudentContactProfile saveAcademicProfile(StudentContactProfile profile, long expectedVersion) {
        var entity = locked(profile.studentId(), expectedVersion);
        entity.updateAcademicProfile(profile.cohort(), profile.programId());
        return map(repo.saveAndFlush(entity));
    }

    private StudentContactProfileEntity locked(UUID studentId, long expectedVersion) {
        StudentContactProfileEntity entity = repo.findForUpdate(studentId).orElse(null);
        if (entity == null) {
            if (expectedVersion != 0) throw new ConcurrentProfileModificationException();
            return new StudentContactProfileEntity(studentId);
        }
        if (entity.getRowVersion() != expectedVersion) throw new ConcurrentProfileModificationException();
        return entity;
    }

    private StudentContactProfile map(StudentContactProfileEntity entity) {
        return new StudentContactProfile(entity.getStudentId(), entity.getPhone(), entity.getAddress(),
                entity.getDateOfBirth(), entity.getCohort(), entity.getProgramId(), entity.getRowVersion(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
