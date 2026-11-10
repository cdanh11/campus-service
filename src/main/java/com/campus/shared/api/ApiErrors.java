package com.campus.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.ErrorResponse;

public final class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger("com.campus.api.errors");
    private ApiErrors() { }
    public static String traceId(HttpServletRequest request) {
        return request.getAttribute("campus.requestId") instanceof String id ? id : null;
    }
    public static void logUnexpected(HttpServletRequest request, Throwable failure) {
        LOG.error("Unhandled error at {} {}", request.getMethod(), request.getRequestURI(), failure);
    }
    public static FrameworkError frameworkError(Throwable failure) {
        if (!(failure instanceof ErrorResponse response) || !response.getStatusCode().is4xxClientError()) return null;
        int status = response.getStatusCode().value();
        return new FrameworkError(status, switch (status) {
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "REQUEST_REJECTED";
        });
    }
    public record FrameworkError(int status, String code) { }
}
