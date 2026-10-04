package com.campus.library.domain;

import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface LibraryRepository {
    BookTitle createTitle(BookTitle value);
    Optional<BookTitle> title(UUID id);
    BookTitle lockTitle(UUID id);
    BookTitle updateTitle(BookTitle value, long expectedVersion);
    PageResult<BookTitle> titles(LibrarySearch search);
    BookCopy createCopy(BookCopy value);
    Optional<BookCopy> copy(UUID id);
    BookCopy lockCopy(UUID id);
    BookCopy updateCopy(BookCopy value, long expectedVersion);
    PageResult<BookCopy> copies(LibrarySearch search);
    BookLoan createLoan(BookLoan value);
    Optional<BookLoan> loan(UUID id);
    BookLoan lockLoan(UUID id);
    BookLoan updateLoan(BookLoan value, long expectedVersion);
    boolean hasOpenLoan(UUID copyId);
    PageResult<BookLoan> loans(LibrarySearch search);
}
