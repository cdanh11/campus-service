package com.campus.library.infrastructure.persistence;

import java.util.*;
import java.util.function.Function;
import com.campus.library.application.LibraryService;
import com.campus.library.domain.*;
import com.campus.shared.application.PageResult;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

@Repository
class LibraryPersistenceAdapter implements LibraryRepository {
    private final EntityManager entities;
    LibraryPersistenceAdapter(EntityManager entities) { this.entities = entities; }
    private <T> T create(T entity) { entities.persist(entity); save(entity); return entity; }
    private void save(Object entity) { entities.flush(); entities.refresh(entity); }
    private <T> T lock(Class<T> type, UUID id) {
        var entity = entities.find(type, id);
        if (entity == null) throw new LibraryService.NotFoundException();
        entities.refresh(entity, LockModeType.PESSIMISTIC_WRITE); return entity;
    }
    private void version(long actual, long expected) {
        if (actual != expected) throw new LibraryService.StaleVersionException();
    }
    public BookTitle createTitle(BookTitle value) { return create(new BookTitleEntity(value)).domain(); }
    public Optional<BookTitle> title(UUID id) { return Optional.ofNullable(entities.find(BookTitleEntity.class, id)).map(BookTitleEntity::domain); }
    public BookTitle lockTitle(UUID id) { return lock(BookTitleEntity.class, id).domain(); }
    public BookTitle updateTitle(BookTitle value, long expectedVersion) {
        var entity = lock(BookTitleEntity.class, value.id()); version(entity.version, expectedVersion);
        entity.update(value); save(entity); return entity.domain();
    }
    public BookCopy createCopy(BookCopy value) { return create(new BookCopyEntity(value)).domain(); }
    public Optional<BookCopy> copy(UUID id) { return Optional.ofNullable(entities.find(BookCopyEntity.class, id)).map(BookCopyEntity::domain); }
    public BookCopy lockCopy(UUID id) { return lock(BookCopyEntity.class, id).domain(); }
    public BookCopy updateCopy(BookCopy value, long expectedVersion) {
        var entity = lock(BookCopyEntity.class, value.id()); version(entity.version, expectedVersion);
        entity.update(value); save(entity); return entity.domain();
    }
    public BookLoan createLoan(BookLoan value) { return create(new BookLoanEntity(value)).domain(); }
    public Optional<BookLoan> loan(UUID id) { return Optional.ofNullable(entities.find(BookLoanEntity.class, id)).map(BookLoanEntity::domain); }
    public BookLoan lockLoan(UUID id) { return lock(BookLoanEntity.class, id).domain(); }
    public BookLoan updateLoan(BookLoan value, long expectedVersion) {
        var entity = lock(BookLoanEntity.class, value.id()); version(entity.version, expectedVersion);
        entity.update(value); save(entity); return entity.domain();
    }
    public boolean hasOpenLoan(UUID copyId) {
        return !entities.createQuery("select l.id from BookLoanEntity l where l.copyId=:copy and l.status=:status", UUID.class)
                .setParameter("copy", copyId).setParameter("status", BookLoan.Status.OPEN).setMaxResults(1).getResultList().isEmpty();
    }
    public PageResult<BookTitle> titles(LibrarySearch search) { return page(BookTitleEntity.class, BookTitleEntity::domain, search); }
    public PageResult<BookCopy> copies(LibrarySearch search) { return page(BookCopyEntity.class, BookCopyEntity::domain, search); }
    public PageResult<BookLoan> loans(LibrarySearch search) { return page(BookLoanEntity.class, BookLoanEntity::domain, search); }
    private <E, D> PageResult<D> page(Class<E> type, Function<E, D> domain, LibrarySearch search) {
        LibrarySearch.Resource expected = type == BookTitleEntity.class ? LibrarySearch.Resource.TITLE
                : type == BookCopyEntity.class ? LibrarySearch.Resource.COPY : LibrarySearch.Resource.LOAN;
        if (search.resource() != expected) throw new IllegalArgumentException("Query resource mismatch");
        var cb = entities.getCriteriaBuilder(); var query = cb.createQuery(type); var root = query.from(type);
        query.where(filters(cb, root, search));
        query.orderBy(search.ascending() ? cb.asc(root.get(search.sortField())) : cb.desc(root.get(search.sortField())), cb.asc(root.get("id")));
        var content = entities.createQuery(query).setFirstResult(search.page() * search.size()).setMaxResults(search.size()).getResultList();
        var count = cb.createQuery(Long.class); var other = count.from(type);
        count.select(cb.count(other)).where(filters(cb, other, search));
        return new PageResult<>(content.stream().map(domain).toList(), entities.createQuery(count).getSingleResult());
    }
    private Predicate[] filters(CriteriaBuilder cb, Root<?> root, LibrarySearch search) {
        var filters = new ArrayList<Predicate>();
        if (search.status() != null) filters.add(cb.equal(root.get("status"), search.resource() == LibrarySearch.Resource.LOAN
                ? BookLoan.Status.valueOf(search.status()) : LibraryStatus.valueOf(search.status())));
        if (search.titleId() != null) filters.add(cb.equal(root.get("titleId"), search.titleId()));
        if (search.copyId() != null) filters.add(cb.equal(root.get("copyId"), search.copyId()));
        if (search.studentId() != null) filters.add(cb.equal(root.get("studentId"), search.studentId()));
        if (search.query() != null) {
            String literal = "%" + search.query().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            var matches = new ArrayList<Predicate>(); matches.add(cb.like(cb.lower(root.get("code")), literal, '!'));
            if (search.resource() == LibrarySearch.Resource.TITLE) {
                matches.add(cb.like(cb.lower(root.get("title")), literal, '!')); matches.add(cb.like(cb.lower(root.get("author")), literal, '!'));
            }
            filters.add(cb.or(matches.toArray(Predicate[]::new)));
        }
        return filters.toArray(Predicate[]::new);
    }
}
