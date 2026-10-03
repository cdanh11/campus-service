package com.campus.academic.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AcademicDeliveryTest {
    @Test
    void validatesTermDateOrderIncludingSameDayBoundary() {
        var day = LocalDate.of(2027, 1, 1);
        var term = term(" \tT01\n", day, day);
        assertThat(term.code()).isEqualTo("T01");
        assertThatThrownBy(() -> term("T01", day.plusDays(1), day)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void verifiesEveryTermTransition() {
        for (var from : AcademicTermStatus.values()) for (var to : AcademicTermStatus.values()) {
            boolean valid = from == to || java.util.Set.of("PLANNED:ACTIVE", "PLANNED:CANCELLED", "ACTIVE:CLOSED").contains(from + ":" + to);
            if (valid) assertThatCode(() -> AcademicLifecycle.term(from, to)).doesNotThrowAnyException();
            else assertThatThrownBy(() -> AcademicLifecycle.term(from, to)).isInstanceOf(AcademicLifecycle.InvalidTransitionException.class);
        }
    }
    @Test
    void verifiesEveryOfferingAndSectionTransition() {
        for (var from : AcademicDeliveryStatus.values()) for (var to : AcademicDeliveryStatus.values()) {
            boolean valid = from == to || java.util.Set.of("DRAFT:OPEN", "DRAFT:CANCELLED", "OPEN:CLOSED").contains(from + ":" + to);
            if (valid) assertThatCode(() -> AcademicLifecycle.delivery(from, to)).doesNotThrowAnyException();
            else assertThatThrownBy(() -> AcademicLifecycle.delivery(from, to)).isInstanceOf(AcademicLifecycle.InvalidTransitionException.class);
        }
    }
    @ParameterizedTest @ValueSource(ints = {1, 2147483647})
    void acceptsPositiveCapacityWithoutAnInventedUpperLimit(int capacity) {
        assertThat(section(" cs01 ", capacity, null, AcademicDeliveryStatus.DRAFT).capacity()).isEqualTo(capacity);
    }
    @ParameterizedTest @ValueSource(ints = {0, -1})
    void rejectsNonpositiveCapacity(int capacity) {
        assertThatThrownBy(() -> section("CS01", capacity, null, AcademicDeliveryStatus.DRAFT)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void allowsUnassignedDraftAndRequiresFacultyForOpen() {
        assertThat(section("cs01", 30, null, AcademicDeliveryStatus.DRAFT).code()).isEqualTo("CS01");
        assertThatThrownBy(() -> section("CS01", 30, null, AcademicDeliveryStatus.OPEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> section("CS01", 30, UUID.randomUUID(), AcademicDeliveryStatus.OPEN)).doesNotThrowAnyException();
        assertThatThrownBy(() -> section("ß".repeat(32), 30, null, AcademicDeliveryStatus.DRAFT)).isInstanceOf(IllegalArgumentException.class);
    }
    private AcademicTerm term(String code, LocalDate start, LocalDate end) {
        var now = Instant.now();
        return new AcademicTerm(UUID.randomUUID(), code, "Term", start, end, AcademicTermStatus.PLANNED, 0, now, now);
    }
    private ClassSection section(String code, int capacity, UUID faculty, AcademicDeliveryStatus status) {
        var now = Instant.now();
        return new ClassSection(UUID.randomUUID(), UUID.randomUUID(), code, capacity, faculty, status, 0, now, now);
    }
}
