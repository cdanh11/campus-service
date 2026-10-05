package com.campus.finance.api;

import java.math.BigDecimal;
import java.util.*;
import com.campus.finance.application.*;
import com.campus.finance.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController @SecurityRequirement(name="bearerAuth")
@RequestMapping("/api/v1/admin/finance")
public class AdminManualPaymentController {
    private final ManualPaymentService service;
    public AdminManualPaymentController(ManualPaymentService service) { this.service=service; }
    @PostMapping("/payments") @Operation(summary="Record a manual VND payment (administrative permission)")
    public ResponseEntity<ManualPayment> record(Authentication authentication,@Valid @RequestBody PaymentCreate body) {
        var value=service.record((UUID)authentication.getPrincipal(),body.receiptNumber(),body.chargeId(),body.amount(),body.expectedChargeVersion());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(value.id()).toUri()).body(value);
    }
    @GetMapping("/payments/{id}") public ManualPayment get(@PathVariable UUID id) { return service.get(id); }
    @PutMapping("/payments/{id}") @Operation(summary="Reverse an entire receipt (administrative permission)",description="REVERSED is terminal; original amount/references remain immutable.")
    public ManualPayment reverse(Authentication authentication,@PathVariable UUID id,@Valid @RequestBody PaymentReverse body) {
        if(body.status()!=PaymentStatus.REVERSED) throw new FinanceObligationService.InvalidStateException();
        return service.reverse((UUID)authentication.getPrincipal(),id,body.expectedVersion(),body.expectedChargeVersion(),body.reason());
    }
    @GetMapping("/charges/{id}/balance") public ChargeBalance balance(@PathVariable UUID id) { return service.balance(id); }
    @GetMapping("/payments") public PaymentPage list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String q,@RequestParam(required=false) UUID chargeId,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="recordedAt,desc") String sort) {
        PaymentSearch query;
        try {
            String[] parts=sort.split(",",-1);
            if(parts.length!=2 || !Set.of("asc","desc").contains(parts[1])) throw new IllegalArgumentException();
            query=new PaymentSearch(page,size,q,chargeId,status==null?null:PaymentStatus.valueOf(status),parts[0],parts[1].equals("asc"));
        } catch(IllegalArgumentException failure) { throw new InvalidQueryException(); }
        var value=service.search(query);
        return new PaymentPage(value.content(),page,size,value.totalElements(),(int)Math.ceil((double)value.totalElements()/size));
    }
    public static final class InvalidQueryException extends RuntimeException { }
    @Schema(name="FinancePaymentCreate") public record PaymentCreate(@NotNull String receiptNumber,@NotNull UUID chargeId,
            @NotNull BigDecimal amount,@NotNull @PositiveOrZero Long expectedChargeVersion) { }
    @Schema(name="FinancePaymentReverse") public record PaymentReverse(@NotNull PaymentStatus status,@NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @PositiveOrZero Long expectedChargeVersion,@NotNull String reason) { }
    @Schema(name="FinancePaymentPage") public record PaymentPage(List<ManualPayment> content,int page,int size,long totalElements,int totalPages) { }
}
