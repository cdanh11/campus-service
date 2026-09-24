package com.campus.organization.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.campus.organization.domain.OrganizationUnit;
import com.campus.organization.domain.OrganizationUnitRepository;
import com.campus.organization.domain.OrganizationUnitStatus;
import com.campus.organization.domain.OrganizationUnitType;
import com.campus.organization.domain.OrganizationUnitSearch;
import com.campus.shared.application.PageResult;
import com.campus.shared.application.PeopleRegistryAudit;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationUnitManagementService {
    private final OrganizationUnitRepository units;
    private final Clock clock;
    private final PeopleRegistryAudit audits;

    public OrganizationUnitManagementService(OrganizationUnitRepository units, Clock clock, PeopleRegistryAudit audits) { this.units = units; this.clock = clock; this.audits = audits; }

    @Transactional
    public OrganizationUnit create(String code, String name, OrganizationUnitType type, OrganizationUnitStatus status) {
        return create(null, code, name, type, status);
    }
    @Transactional
    public OrganizationUnit create(UUID actorId, String code, String name, OrganizationUnitType type, OrganizationUnitStatus status) {
        OrganizationUnit unit;
        try { unit = OrganizationUnit.create(UUID.randomUUID(), code, name, type, status == null ? OrganizationUnitStatus.ACTIVE : status, clock.instant()); }
        catch (OrganizationUnit.InvalidOrganizationUnitException exception) { throw new RequestValidationException(); }
        if (units.existsByCode(unit.code())) throw new OrganizationUnitCodeAlreadyExistsException();
        OrganizationUnit saved;
        try { saved = units.save(unit); }
        catch (DataIntegrityViolationException exception) { throw new OrganizationUnitCodeAlreadyExistsException(); }
        if (actorId != null) audits.record(actorId, "ORGANIZATION_UNIT", saved.id(), "CREATED", clock.instant());
        return saved;
    }

    @Transactional(readOnly = true)
    public OrganizationUnit get(UUID id) { return units.findById(id).orElseThrow(OrganizationUnitNotFoundException::new); }

    @Transactional(readOnly = true)
    public List<OrganizationUnit> list() { return units.findAll(); }
    @Transactional(readOnly = true)
    public PageResult<OrganizationUnit> search(OrganizationUnitSearch search) { return units.search(search); }

    @Transactional
    public OrganizationUnit update(UUID id, String code, String name, OrganizationUnitType type, OrganizationUnitStatus status, long expectedVersion) {
        return update(null, id, code, name, type, status, expectedVersion);
    }
    @Transactional
    public OrganizationUnit update(UUID actorId, UUID id, String code, String name, OrganizationUnitType type, OrganizationUnitStatus status, long expectedVersion) {
        OrganizationUnit existing = get(id);
        OrganizationUnit candidate;
        try { candidate = new OrganizationUnit(existing.id(), code, name, type, status, existing.rowVersion(), existing.createdAt(), clock.instant()); }
        catch (OrganizationUnit.InvalidOrganizationUnitException exception) { throw new RequestValidationException(); }
        OrganizationUnit saved;
        try { saved = units.saveMutation(candidate, expectedVersion); }
        catch (java.util.NoSuchElementException exception) { throw new OrganizationUnitNotFoundException(); }
        catch (ConcurrentOrganizationUnitModificationException exception) { throw exception; }
        catch (DataIntegrityViolationException exception) { throw new OrganizationUnitCodeAlreadyExistsException(); }
        if (actorId != null) audits.record(actorId, "ORGANIZATION_UNIT", saved.id(), "UPDATED", clock.instant());
        return saved;
    }

    public static final class OrganizationUnitNotFoundException extends RuntimeException { }
    public static final class OrganizationUnitCodeAlreadyExistsException extends RuntimeException { }
    public static final class ConcurrentOrganizationUnitModificationException extends RuntimeException { }
    public static final class RequestValidationException extends RuntimeException { }
}
