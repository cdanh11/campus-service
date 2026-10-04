package com.campus.reporting.api;

import com.campus.reporting.application.DashboardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {
    private final DashboardService reports;
    public AdminDashboardController(DashboardService reports) { this.reports = reports; }
    @GetMapping("/dashboard") public DashboardService.Dashboard dashboard() { return reports.dashboard(); }
}
