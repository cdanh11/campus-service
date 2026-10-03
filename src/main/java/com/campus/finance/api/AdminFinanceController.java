package com.campus.finance.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.campus.finance.application.FinanceObligationService;
import com.campus.finance.domain.*;
import com.campus.shared.application.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController @SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/admin/finance")
public class AdminFinanceController {
    private final FinanceObligationService service;
    public AdminFinanceController(FinanceObligationService service) { this.service = service; }
    @PostMapping("/fees") @Operation(summary = "Create a VND fee definition (ADMIN)")
    public ResponseEntity<FeeDefinition> createFee(Authentication authentication, @Valid @RequestBody FeeCreate body) {
        var value = service.createFee(actor(authentication), body.code(), body.name(), body.amount());
        return created(value.id(), value);
    }
    @GetMapping("/fees/{id}") public FeeDefinition fee(@PathVariable UUID id) { return service.fee(id); }
    @PutMapping("/fees/{id}") public FeeDefinition updateFee(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody FeeUpdate body) {
        return service.updateFee(actor(authentication), id, body.code(), body.name(), body.amount(), body.status(), body.expectedVersion());
    }
    @PostMapping("/charges") @Operation(summary = "Create a Student obligation from an active fee snapshot (ADMIN)")
    public ResponseEntity<StudentCharge> createCharge(Authentication authentication, @Valid @RequestBody ChargeCreate body) {
        var value = service.createCharge(actor(authentication), body.chargeNumber(), body.studentId(), body.feeId(), body.dueDate());
        return created(value.id(), value);
    }
    @GetMapping("/charges/{id}") public StudentCharge charge(@PathVariable UUID id) { return service.charge(id); }
    @PutMapping("/charges/{id}") @Operation(summary = "Cancel an obligation (ADMIN)", description = "CANCELLED is terminal; financial snapshot is immutable.")
    public StudentCharge cancel(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ChargeCancel body) {
        if (body.status() != ChargeStatus.CANCELLED) throw new FinanceObligationService.InvalidStateException();
        return service.cancelCharge(actor(authentication), id, body.expectedVersion());
    }
    @GetMapping("/fees") public FeePage fees(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(defaultValue = "code,asc") String sort) {
        var query = query(page, size, q, status, null, null, sort, false); var values = service.fees(query);
        return new FeePage(values.content(), page, size, values.totalElements(), pages(values, size));
    }
    @GetMapping("/charges") public ChargePage charges(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID studentId, @RequestParam(required = false) UUID feeId,
            @RequestParam(defaultValue = "dueDate,asc") String sort) {
        var query = query(page, size, q, status, studentId, feeId, sort, true); var values = service.charges(query);
        return new ChargePage(values.content(), page, size, values.totalElements(), pages(values, size));
    }
    private FinanceSearch query(int page, int size, String q, String status, UUID student, UUID fee, String sort, boolean charge) {
        try {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !Set.of("asc", "desc").contains(parts[1])) throw new IllegalArgumentException();
            var query = new FinanceSearch(page, size, q, status, student, fee, parts[0], parts[1].equals("asc"));
            if (charge) query.charges(); else query.fees(); return query;
        } catch (IllegalArgumentException failure) { throw new InvalidQueryException(); }
    }
    private UUID actor(Authentication authentication) { return (UUID) authentication.getPrincipal(); }
    private <T> ResponseEntity<T> created(UUID id, T value) {
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri()).body(value);
    }
    private int pages(PageResult<?> values, int size) { return (int) Math.ceil((double) values.totalElements() / size); }
    public static final class InvalidQueryException extends RuntimeException { }
    @Schema(name = "FinanceFeeCreate") public record FeeCreate(@NotNull String code, @NotNull String name, @NotNull BigDecimal amount) { }
    @Schema(name = "FinanceFeeUpdate") public record FeeUpdate(@NotNull String code, @NotNull String name, @NotNull BigDecimal amount,
            @NotNull FeeStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
    @Schema(name = "FinanceChargeCreate") public record ChargeCreate(@NotNull String chargeNumber, @NotNull UUID studentId,
            @NotNull UUID feeId, @NotNull LocalDate dueDate) { }
    @Schema(name = "FinanceChargeCancel") public record ChargeCancel(@NotNull ChargeStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
    @Schema(name = "FinanceFeePage") public record FeePage(List<FeeDefinition> content, int page, int size, long totalElements, int totalPages) { }
    @Schema(name = "FinanceChargePage") public record ChargePage(List<StudentCharge> content, int page, int size, long totalElements, int totalPages) { }
}
