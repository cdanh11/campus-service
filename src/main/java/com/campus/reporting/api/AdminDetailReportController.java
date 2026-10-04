package com.campus.reporting.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.campus.reporting.application.DetailReportService;
import com.campus.shared.application.reporting.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
@SecurityRequirement(name="bearerAuth")
public class AdminDetailReportController {
    private final DetailReportService reports;
    public AdminDetailReportController(DetailReportService reports) { this.reports=reports; }
    @GetMapping("/{report}") public DetailReportService.ReportPage list(@PathVariable ReportKind report,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) UUID studentId,@RequestParam(required=false) UUID resourceId,
            @RequestParam(required=false) String status,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant until,
            @RequestParam(defaultValue="false") boolean overdueOnly,@RequestParam(defaultValue="id,asc") String sort) {
        return reports.search(report,search(page,size,studentId,resourceId,status,from,until,overdueOnly,sort));
    }
    @GetMapping("/{report}/export") public ResponseEntity<byte[]> export(@PathVariable ReportKind report,
            @RequestParam(required=false) UUID studentId,@RequestParam(required=false) UUID resourceId,
            @RequestParam(required=false) String status,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant until,
            @RequestParam(defaultValue="false") boolean overdueOnly,@RequestParam(defaultValue="id,asc") String sort) {
        String csv=reports.export(report,search(0,100,studentId,resourceId,status,from,until,overdueOnly,sort));
        return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+report.name().toLowerCase(Locale.ROOT)+".csv\"")
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(csv.getBytes(StandardCharsets.UTF_8));
    }
    private ReportSearch search(int page,int size,UUID student,UUID resource,String status,Instant from,Instant until,boolean overdue,String sort) {
        if(!Set.of("id,asc","id,desc").contains(sort)) throw new IllegalArgumentException("Invalid report sort");
        return new ReportSearch(page,size,student,resource,status,from,until,overdue,sort.endsWith("asc"));
    }
}
