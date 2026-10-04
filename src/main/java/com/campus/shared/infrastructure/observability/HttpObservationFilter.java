package com.campus.shared.infrastructure.observability;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Safe completion events: no URI/query/body/account/header payload or unbounded metric tags. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpObservationFilter extends OncePerRequestFilter {
    public static final String HEADER="X-Request-ID";
    private static final Logger LOG=LoggerFactory.getLogger(HttpObservationFilter.class);
    private static final Set<String> METHODS=Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS","TRACE","CONNECT");
    private final MeterRegistry meters;
    public HttpObservationFilter(MeterRegistry meters) { this.meters=meters; }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String candidate=request.getHeader(HEADER);
        String id=candidate!=null && candidate.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                ?UUID.fromString(candidate).toString():UUID.randomUUID().toString();
        request.setAttribute("campus.requestId",id); response.setHeader(HEADER,id);
        String previous=MDC.get("requestId"); MDC.put("requestId",id);
        long start=System.nanoTime(); boolean failed=false;
        try { chain.doFilter(request,response); }
        catch(IOException|ServletException|RuntimeException failure) { failed=true; throw failure; }
        finally {
            try {
                int status=failed?500:response.getStatus();
                long nanos=System.nanoTime()-start;
                String method=METHODS.contains(request.getMethod())?request.getMethod():"OTHER";
                Object matched=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String route=matched instanceof String pattern && pattern.length()<=160 && pattern.matches("/[A-Za-z0-9_{}./*:-]*")?pattern:"UNMATCHED";
                Timer.builder("campus.http.requests").tags("method",method,"route",route,"status",Integer.toString(status))
                        .register(meters).record(nanos,TimeUnit.NANOSECONDS);
                // All string fields above have a fixed alphabet without quotes/backslashes.
                LOG.info("{\"event\":\"http_request_completed\",\"requestId\":\"{}\",\"method\":\"{}\",\"route\":\"{}\",\"status\":{},\"durationMs\":{}}",
                        id,method,route,status,TimeUnit.NANOSECONDS.toMillis(nanos));
            } finally {
                if(previous==null) MDC.remove("requestId"); else MDC.put("requestId",previous);
            }
        }
    }
}
