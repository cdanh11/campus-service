package com.campus.shared.infrastructure.observability;

import java.util.UUID;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.*;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.HandlerMapping;
import static org.assertj.core.api.Assertions.*;

class HttpObservationFilterTest {
    @Test void safeCorrelationStructuredEventAndBoundedMetricsExcludeSensitiveRequestData() throws Exception {
        var meters=new SimpleMeterRegistry(); var filter=new HttpObservationFilter(meters);
        var request=new MockHttpServletRequest("GET","/sensitive-private-fixture");
        String id=UUID.randomUUID().toString(); request.addHeader("X-Request-ID",id);
        request.addHeader("Authorization","private-fixture"); request.setQueryString("secret=private-fixture");
        request.setContent("private-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response=new MockHttpServletResponse();
        var logger=(ch.qos.logback.classic.Logger)LoggerFactory.getLogger(HttpObservationFilter.class);
        var capture=new ListAppender<ILoggingEvent>();capture.start();logger.addAppender(capture);
        try {
            filter.doFilter(request,response,(req,res)->{
                assertThat(MDC.get("requestId")).isEqualTo(id);
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/v1/admin/reports/{report}");
                ((jakarta.servlet.http.HttpServletResponse)res).setStatus(422);
            });
            assertThat(response.getHeader("X-Request-ID")).isEqualTo(id);
            assertThat(MDC.get("requestId")).isNull();
            String log=capture.list.getFirst().getFormattedMessage();
            assertThat(log).doesNotContain("private-fixture","Authorization","secret=");
            var event=new ObjectMapper().readTree(log);
            assertThat(event.path("requestId").asText()).isEqualTo(id);
            assertThat(event.path("status").asInt()).isEqualTo(422);
            assertThat(meters.get("campus.http.requests").tag("route","/api/v1/admin/reports/{report}").timer().count()).isEqualTo(1);
            assertThat(meters.getMeters().getFirst().getId().getTags().toString()).doesNotContain(id,"private-fixture");
        } finally { logger.detachAppender(capture); capture.stop();meters.close(); }
    }
    @Test void maliciousOrOversizedCorrelationIsReplacedAndMdcIsRestoredEvenAfterFailure() {
        var meters=new SimpleMeterRegistry();var filter=new HttpObservationFilter(meters);
        try {
            for(String bad:new String[]{"secret\r\nforged-log","a".repeat(10000),"1-1-1-1-1"}) {
                var request=new MockHttpServletRequest("UNKNOWN","/private-fixture");request.addHeader("X-Request-ID",bad);
                var response=new MockHttpServletResponse();MDC.put("requestId","previous");
                assertThatThrownBy(()->filter.doFilter(request,response,(req,res)->{throw new ServletException("private-fixture");})).isInstanceOf(ServletException.class);
                assertThat(UUID.fromString(response.getHeader("X-Request-ID")).toString()).isEqualTo(response.getHeader("X-Request-ID"));
                assertThat(MDC.get("requestId")).isEqualTo("previous");
            }
            assertThat(meters.get("campus.http.requests").tag("method","OTHER").tag("route","UNMATCHED").tag("status","500").timer().count()).isEqualTo(3);
        } finally { MDC.remove("requestId");meters.close(); }
    }
}
