package com.campus.academic.infrastructure.persistence.grading;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.campus.academic.application.AcademicGradeService;
import com.campus.academic.domain.AcademicGrade;
import com.campus.academic.domain.AcademicGradeRepository;
import com.campus.academic.domain.GradeSearch;
import com.campus.shared.application.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class AcademicGradePersistenceAdapter implements AcademicGradeRepository {
    private final AcademicGradeJpaRepository repository;
    private final EntityManager entityManager;

    AcademicGradePersistenceAdapter(AcademicGradeJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    public AcademicGrade create(AcademicGrade value) { return repository.saveAndFlush(new AcademicGradeEntity(value)).domain(); }

    public AcademicGrade update(AcademicGrade value, long expectedVersion) {
        var entity = repository.findByIdForUpdate(value.id()).orElseThrow(AcademicGradeService.NotFoundException::new);
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        if (entity.getRowVersion() != expectedVersion) throw new AcademicGradeService.StaleVersionException();
        entity.apply(value);
        return repository.saveAndFlush(entity).domain();
    }

    public Optional<AcademicGrade> findById(UUID id) { return repository.findById(id).map(AcademicGradeEntity::domain); }

    public Optional<AcademicGrade> findByEnrollmentId(UUID enrollmentId) {
        return repository.findByEnrollmentId(enrollmentId).map(AcademicGradeEntity::domain);
    }

    public List<AcademicGrade> findAllByEnrollmentIds(Collection<UUID> enrollmentIds) {
        if (enrollmentIds.isEmpty()) return List.of();
        return repository.findAllByEnrollmentIdIn(enrollmentIds.stream().distinct().toList()).stream()
                .map(AcademicGradeEntity::domain).toList();
    }

    public PageResult<AcademicGrade> search(GradeSearch query) {
        Specification<AcademicGradeEntity> specification = (root, criteria, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (query.enrollmentId() != null) predicates.add(builder.equal(root.get("enrollmentId"), query.enrollmentId()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        var sort = Sort.by(query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, query.sortField()).and(Sort.by("id"));
        var page = repository.findAll(specification, PageRequest.of(query.page(), query.size(), sort));
        return new PageResult<>(page.getContent().stream().map(AcademicGradeEntity::domain).toList(), page.getTotalElements());
    }
}
