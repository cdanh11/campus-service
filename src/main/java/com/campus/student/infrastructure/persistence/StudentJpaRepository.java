package com.campus.student.infrastructure.persistence;
import java.util.*; import com.campus.student.infrastructure.persistence.entity.StudentEntity; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*;
interface StudentJpaRepository extends JpaRepository<StudentEntity,UUID>, JpaSpecificationExecutor<StudentEntity>{ boolean existsByStudentNumber(String studentNumber); @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select student from StudentEntity student where student.id=:id") Optional<StudentEntity> findByIdForUpdate(UUID id); }
