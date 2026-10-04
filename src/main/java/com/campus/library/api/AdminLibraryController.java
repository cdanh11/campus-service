package com.campus.library.api;

import java.util.*;
import com.campus.library.application.LibraryService;
import com.campus.library.domain.*;
import com.campus.library.domain.LibrarySearch.Resource;
import com.campus.shared.application.PageResult;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/library")
@SecurityRequirement(name = "bearerAuth")
public class AdminLibraryController {
    private final LibraryService library;
    public AdminLibraryController(LibraryService library) { this.library = library; }
    @PostMapping("/titles") public ResponseEntity<BookTitle> createTitle(Authentication auth, @Valid @RequestBody TitleCreate body) {
        return created(library.createTitle(actor(auth), body.code(), body.title(), body.author()), BookTitle::id);
    }
    @GetMapping("/titles/{id}") public BookTitle title(@PathVariable UUID id) { return library.title(id); }
    @PutMapping("/titles/{id}") public BookTitle updateTitle(Authentication auth, @PathVariable UUID id, @Valid @RequestBody TitleUpdate body) {
        return library.updateTitle(actor(auth), id, body.code(), body.title(), body.author(), body.status(), body.expectedVersion());
    }
    @GetMapping("/titles") public TitlePage titles(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(defaultValue = "code,asc") String sort) {
        var result = library.titles(search(Resource.TITLE, page, size, q, null, null, null, status, sort));
        return new TitlePage(result.content(), page, size, result.totalElements(), pages(result, size));
    }
    @PostMapping("/copies") public ResponseEntity<BookCopy> createCopy(Authentication auth, @Valid @RequestBody CopyCreate body) {
        return created(library.createCopy(actor(auth), body.titleId(), body.code()), BookCopy::id);
    }
    @GetMapping("/copies/{id}") public BookCopy copy(@PathVariable UUID id) { return library.copy(id); }
    @PutMapping("/copies/{id}") public BookCopy updateCopy(Authentication auth, @PathVariable UUID id, @Valid @RequestBody CopyUpdate body) {
        return library.updateCopy(actor(auth), id, body.code(), body.status(), body.expectedVersion());
    }
    @GetMapping("/copies") public CopyPage copies(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) UUID titleId, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "code,asc") String sort) {
        var result = library.copies(search(Resource.COPY, page, size, q, titleId, null, null, status, sort));
        return new CopyPage(result.content(), page, size, result.totalElements(), pages(result, size));
    }
    @PostMapping("/loans") public ResponseEntity<BookLoan> borrow(Authentication auth, @Valid @RequestBody LoanCreate body) {
        return created(library.borrow(actor(auth), body.copyId(), body.studentId()), BookLoan::id);
    }
    @GetMapping("/loans/{id}") public BookLoan loan(@PathVariable UUID id) { return library.loan(id); }
    @PutMapping("/loans/{id}/return") public BookLoan returnBook(Authentication auth, @PathVariable UUID id, @Valid @RequestBody LoanReturn body) {
        return library.returnBook(actor(auth), id, body.expectedVersion());
    }
    @GetMapping("/loans") public LoanPage loans(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID copyId, @RequestParam(required = false) UUID studentId, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "borrowedAt,desc") String sort) {
        var result = library.loans(search(Resource.LOAN, page, size, null, null, copyId, studentId, status, sort));
        return new LoanPage(result.content(), page, size, result.totalElements(), pages(result, size));
    }
    private LibrarySearch search(Resource resource, int page, int size, String q, UUID titleId, UUID copyId, UUID studentId, String status, String sort) {
        try {
            var parts = sort.split(",", -1);
            if (parts.length != 2 || !Set.of("asc", "desc").contains(parts[1])) throw new IllegalArgumentException();
            return new LibrarySearch(resource, page, size, q, titleId, copyId, studentId, status, parts[0], parts[1].equals("asc"));
        } catch (IllegalArgumentException failure) { throw new InvalidQueryException(); }
    }
    private UUID actor(Authentication auth) { return (UUID) auth.getPrincipal(); }
    private int pages(PageResult<?> result, int size) { return (int) Math.ceil((double) result.totalElements() / size); }
    private <T> ResponseEntity<T> created(T value, java.util.function.Function<T, UUID> id) {
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id.apply(value)).toUri()).body(value);
    }
    public static final class InvalidQueryException extends RuntimeException { }
    @Schema(name = "LibraryTitleCreate") public record TitleCreate(@NotBlank String code, @NotBlank String title, @NotBlank String author) { }
    @Schema(name = "LibraryTitleUpdate") public record TitleUpdate(@NotBlank String code, @NotBlank String title, @NotBlank String author,
            @NotNull LibraryStatus status, @NotNull @PositiveOrZero @JsonDeserialize(using = LibraryVersionDeserializer.class) Long expectedVersion) { }
    @Schema(name = "LibraryCopyCreate") public record CopyCreate(@NotNull UUID titleId, @NotBlank String code) { }
    @Schema(name = "LibraryCopyUpdate") public record CopyUpdate(@NotBlank String code, @NotNull LibraryStatus status,
            @NotNull @PositiveOrZero @JsonDeserialize(using = LibraryVersionDeserializer.class) Long expectedVersion) { }
    @Schema(name = "LibraryLoanCreate") public record LoanCreate(@NotNull UUID copyId, @NotNull UUID studentId) { }
    @Schema(name = "LibraryLoanReturn") public record LoanReturn(@NotNull @PositiveOrZero @JsonDeserialize(using = LibraryVersionDeserializer.class) Long expectedVersion) { }
    @Schema(name = "LibraryTitlePage") public record TitlePage(List<BookTitle> content, int page, int size, long totalElements, int totalPages) { }
    @Schema(name = "LibraryCopyPage") public record CopyPage(List<BookCopy> content, int page, int size, long totalElements, int totalPages) { }
    @Schema(name = "LibraryLoanPage") public record LoanPage(List<BookLoan> content, int page, int size, long totalElements, int totalPages) { }
}
