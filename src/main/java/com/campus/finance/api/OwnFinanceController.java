package com.campus.finance.api;

import java.util.UUID;
import com.campus.finance.application.StudentFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = "bearerAuth")
public class OwnFinanceController {
    private final StudentFinanceService service;
    public OwnFinanceController(StudentFinanceService service) { this.service = service; }

    @GetMapping("/finance") @Operation(summary = "Tuition charges and payments of the signed-in student")
    public StudentFinanceService.FinanceSummary summary(Authentication authentication) {
        return service.summary((UUID) authentication.getPrincipal());
    }
}
