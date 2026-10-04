package com.campus.library.application;

import java.time.Clock;
import java.util.UUID;
import com.campus.library.domain.*;
import com.campus.library.domain.LibrarySearch.Resource;
import com.campus.shared.application.PageResult;
import com.campus.student.application.StudentAccountDirectory;
import com.campus.student.domain.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LibraryService {
    private final LibraryRepository library;
    private final StudentAccountDirectory students;
    private final LibraryAudit audit;
    private final Clock clock;
    public LibraryService(LibraryRepository library, StudentAccountDirectory students, LibraryAudit audit, Clock clock) {
        this.library = library; this.students = students; this.audit = audit; this.clock = clock;
    }
    public BookTitle createTitle(UUID actor, String code, String title, String author) {
        actor(actor);
        var saved = library.createTitle(BookTitle.create(UUID.randomUUID(), code, title, author, clock.instant()));
        record(actor, Resource.TITLE, saved.id(), "CREATED", saved.rowVersion(), saved.status().name()); return saved;
    }
    public BookTitle updateTitle(UUID actor, UUID id, String code, String title, String author, LibraryStatus status, long expectedVersion) {
        actor(actor); var old = library.lockTitle(id); version(old.rowVersion(), expectedVersion);
        var saved = library.updateTitle(old.update(code, title, author, status, clock.instant()), expectedVersion);
        record(actor, Resource.TITLE, id, "UPDATED", saved.rowVersion(), saved.status().name()); return saved;
    }
    public BookCopy createCopy(UUID actor, UUID titleId, String code) {
        actor(actor); var title = library.lockTitle(titleId);
        if (title.status() != LibraryStatus.ACTIVE) throw new UnavailableReferenceException();
        var saved = library.createCopy(BookCopy.create(UUID.randomUUID(), titleId, code, clock.instant()));
        record(actor, Resource.COPY, saved.id(), "CREATED", saved.rowVersion(), saved.status().name()); return saved;
    }
    public BookCopy updateCopy(UUID actor, UUID id, String code, LibraryStatus status, long expectedVersion) {
        actor(actor); var original = copy(id); library.lockTitle(original.titleId());
        var old = library.lockCopy(id); version(old.rowVersion(), expectedVersion);
        var saved = library.updateCopy(old.update(code, status, clock.instant()), expectedVersion);
        record(actor, Resource.COPY, id, "UPDATED", saved.rowVersion(), saved.status().name()); return saved;
    }
    public BookLoan borrow(UUID actor, UUID copyId, UUID studentId) {
        actor(actor); var original = copy(copyId); var title = library.lockTitle(original.titleId());
        var copy = library.lockCopy(copyId);
        if (title.status() != LibraryStatus.ACTIVE || copy.status() != LibraryStatus.ACTIVE
                || studentId == null || students.findByStudent(studentId).filter(s -> s.status() == StudentStatus.ACTIVE).isEmpty())
            throw new UnavailableReferenceException();
        if (library.hasOpenLoan(copyId)) throw new CopyAlreadyLoanedException();
        var saved = library.createLoan(BookLoan.borrow(UUID.randomUUID(), copyId, studentId, clock.instant()));
        record(actor, Resource.LOAN, saved.id(), "BORROWED", saved.rowVersion(), saved.status().name()); return saved;
    }
    public BookLoan returnBook(UUID actor, UUID id, long expectedVersion) {
        actor(actor); var original = loan(id); var copy = copy(original.copyId());
        library.lockTitle(copy.titleId()); library.lockCopy(copy.id());
        var old = library.lockLoan(id); version(old.rowVersion(), expectedVersion);
        BookLoan next;
        try { next = old.returnBook(clock.instant()); }
        catch (IllegalStateException failure) { throw new InvalidStateException(); }
        var saved = library.updateLoan(next, expectedVersion);
        record(actor, Resource.LOAN, id, "RETURNED", saved.rowVersion(), saved.status().name()); return saved;
    }
    private void record(UUID actor, Resource resource, UUID id, String action, long version, String status) {
        audit.record(actor, resource, id, action, version, status, clock.instant());
    }
    private void actor(UUID actor) { if (actor == null) throw new IllegalArgumentException("Authenticated actor required"); }
    private void version(long actual, long expected) {
        LibraryValues.version(expected); if (actual != expected) throw new StaleVersionException();
    }
    @Transactional(readOnly = true) public BookTitle title(UUID id) { return library.title(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly = true) public BookCopy copy(UUID id) { return library.copy(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly = true) public BookLoan loan(UUID id) { return library.loan(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly = true) public PageResult<BookTitle> titles(LibrarySearch search) { return library.titles(search); }
    @Transactional(readOnly = true) public PageResult<BookCopy> copies(LibrarySearch search) { return library.copies(search); }
    @Transactional(readOnly = true) public PageResult<BookLoan> loans(LibrarySearch search) { return library.loans(search); }
    public static final class NotFoundException extends RuntimeException { }
    public static final class StaleVersionException extends RuntimeException { }
    public static final class UnavailableReferenceException extends RuntimeException { }
    public static final class CopyAlreadyLoanedException extends RuntimeException { }
    public static final class InvalidStateException extends RuntimeException { }
}
