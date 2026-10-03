package com.campus.academic.domain;

import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface AcademicProgramRepository {
    AcademicProgram save(AcademicProgram value);
    AcademicProgram saveMutation(AcademicProgram value, long expectedVersion);
    Optional<AcademicProgram> findById(UUID id);
    PageResult<AcademicProgram> search(AcademicCatalogSearch search);
    boolean existsByCode(String code);
}
