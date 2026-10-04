package com.campus.reporting.api;

import java.time.Instant;
import com.campus.shared.application.reporting.ReportQueryPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses=AdminDetailReportController.class)
public class ReportingErrorHandler {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> invalid(HttpServletRequest request) { return error(400,"INVALID_QUERY_PARAMETER",request); }
    @ExceptionHandler(ReportQueryPort.ExportLimitException.class)
    ResponseEntity<Error> tooLarge(HttpServletRequest request) { return error(422,"REPORT_EXPORT_LIMIT_EXCEEDED",request); }
    private ResponseEntity<Error> error(int status,String code,HttpServletRequest request) {
        String trace=request.getAttribute("campus.requestId") instanceof String id?id:null;
        return ResponseEntity.status(status).body(new Error(Instant.now(),status,code,HttpStatus.valueOf(status).getReasonPhrase(),request.getRequestURI(),trace));
    }
    public record Error(Instant timestamp,int status,String code,String message,String path,String traceId) { }
}
