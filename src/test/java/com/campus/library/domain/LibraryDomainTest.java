package com.campus.library.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LibraryDomainTest {
    private final Instant now = Instant.parse("2026-10-04T12:00:00Z");
    @Test void titlesNormalizeExactlyApprovedWhitespaceAndCountCodePoints() {
        var value = BookTitle.create(UUID.randomUUID(), " \t\n\r\u000b\fßs \t", "😀".repeat(160), " Nguyễn Văn ", now);
        assertThat(value.code()).isEqualTo("SSS"); assertThat(value.author()).isEqualTo("Nguyễn Văn");
        assertThat(value.title().codePointCount(0, value.title().length())).isEqualTo(160);
        assertThat(BookTitle.create(UUID.randomUUID(), "vv", "vv", "vv", now).title()).isEqualTo("vv");
        assertThatThrownBy(() -> BookTitle.create(UUID.randomUUID(), "aa", "😀".repeat(161), "aa", now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BookTitle.create(UUID.randomUUID(), "ß".repeat(32), "aa", "aa", now)).isInstanceOf(IllegalArgumentException.class);
        for (String invalid : new String[]{null, "", "a", " \t\n\r\u000b\f ", " a "})
            assertThatThrownBy(() -> LibraryValues.text(invalid, 160)).isInstanceOf(IllegalArgumentException.class);
        assertThat(LibraryValues.text("\u00a0\u00a0", 160)).isEqualTo("\u00a0\u00a0");
    }
    @Test void updatesPreserveIdentifiersAssociationCreationAndPersistenceVersion() {
        var title = new BookTitle(UUID.randomUUID(), "ab", "Title", "Author", LibraryStatus.ACTIVE, 7, now, now);
        assertThat(title.update("xy", "Other title", "Other author", LibraryStatus.INACTIVE, now.plusSeconds(1)))
                .extracting(BookTitle::id, BookTitle::createdAt, BookTitle::rowVersion).containsExactly(title.id(), now, 7L);
        var copy = BookCopy.create(UUID.randomUUID(), title.id(), "Copy-1", now);
        assertThat(copy.update("Copy-2", LibraryStatus.INACTIVE, now.plusSeconds(1)))
                .extracting(BookCopy::id, BookCopy::titleId, BookCopy::createdAt).containsExactly(copy.id(), title.id(), now);
        assertThatThrownBy(() -> new BookCopy(copy.id(), title.id(), "Copy", LibraryStatus.ACTIVE, -1, now, now)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void defaultDueTimeIsExactlyFourteenDaysAndOverdueReturnKeepsOriginalHistory() {
        var loan = BookLoan.borrow(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now);
        assertThat(loan.dueAt()).isEqualTo(now.plus(14, ChronoUnit.DAYS)); assertThat(loan.returnedAt()).isNull();
        var returned = loan.returnBook(now.plus(30, ChronoUnit.DAYS));
        assertThat(returned.status()).isEqualTo(BookLoan.Status.RETURNED);
        assertThat(returned).extracting(BookLoan::id, BookLoan::copyId, BookLoan::studentId, BookLoan::borrowedAt, BookLoan::dueAt, BookLoan::createdAt)
                .containsExactly(loan.id(), loan.copyId(), loan.studentId(), now, loan.dueAt(), now);
        assertThatThrownBy(() -> returned.returnBook(now.plus(31, ChronoUnit.DAYS))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> loan.returnBook(now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidLifecycleShapesDatesAndVersionsAreRejected() {
        UUID id = UUID.randomUUID(), copy = UUID.randomUUID(), student = UUID.randomUUID();
        assertThatThrownBy(() -> new BookLoan(id, copy, student, BookLoan.Status.OPEN, 0, now, now, null, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookLoan(id, copy, student, BookLoan.Status.OPEN, 0, now, now.plusSeconds(1), now, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookLoan(id, copy, student, BookLoan.Status.RETURNED, 0, now, now.plusSeconds(1), null, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BookLoan(id, copy, student, BookLoan.Status.OPEN, -1, now, now.plusSeconds(1), null, now, now)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void queryBoundsFiltersAndSortsAreResourceSpecific() {
        assertThat(new LibrarySearch(LibrarySearch.Resource.TITLE, 0, 100, " \t%_! ", null, null, null, "ACTIVE", "author", true).query()).isEqualTo("%_!");
        assertThatThrownBy(() -> new LibrarySearch(LibrarySearch.Resource.LOAN, 0, 20, null, null, null, null, "ACTIVE", "dueAt", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LibrarySearch(LibrarySearch.Resource.LOAN, 0, 20, "search", null, null, null, null, "dueAt", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LibrarySearch(LibrarySearch.Resource.COPY, Integer.MAX_VALUE, 100, null, null, null, null, null, "code", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LibrarySearch(LibrarySearch.Resource.TITLE, 0, 101, null, null, null, null, null, "code", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LibrarySearch(LibrarySearch.Resource.TITLE, 0, 20, null, null, null, null, null, "passwordHash", true)).isInstanceOf(IllegalArgumentException.class);
    }
}
