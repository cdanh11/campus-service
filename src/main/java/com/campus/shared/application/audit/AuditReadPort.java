package com.campus.shared.application.audit;

import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

/** Exported by each owner's application contract; no foreign table/entity access. */
public interface AuditReadPort {
    AuditSource source();
    AuditPolicy policy();
    Optional<AuditView> find(UUID id);
    PageResult<AuditView> search(AuditSearch search);
}
