package com.campus.shared.application.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

    @Test
    void usesVietnamDateAcrossUtcMidnight() {
        Clock utc = Clock.fixed(Instant.parse("2026-01-01T17:30:00Z"), ZoneOffset.UTC);

        assertThat(new BusinessCalendar(utc).today()).isEqualTo(LocalDate.of(2026, 1, 2));
    }

    @Test
    void keepsTheSameDateBeforeVietnamMidnight() {
        Clock utc = Clock.fixed(Instant.parse("2026-01-01T16:30:00Z"), ZoneOffset.UTC);

        assertThat(new BusinessCalendar(utc).today()).isEqualTo(LocalDate.of(2026, 1, 1));
    }
}
