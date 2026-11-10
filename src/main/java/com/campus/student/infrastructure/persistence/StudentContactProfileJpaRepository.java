package com.campus.student.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import com.campus.student.infrastructure.persistence.entity.StudentContactProfileEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface StudentContactProfileJpaRepository extends JpaRepository<StudentContactProfileEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from StudentContactProfileEntity profile where profile.studentId=:studentId")
    Optional<StudentContactProfileEntity> findForUpdate(UUID studentId);
}
