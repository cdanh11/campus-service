package com.campus.academic.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AcademicCatalogTest {
    @Test
    void normalizesCodesAndSixBoundaryWhitespaceCharacters() {
        var program = program(" \t\n\r\u000b\fcs01 ", " Computer Science ");
        assertThat(program.code()).isEqualTo("CS01");
        assertThat(program.name()).isEqualTo("Computer Science");
        assertThat(course(" cs101 ", " Intro Computing ", 3).code()).isEqualTo("CS101");
    }

    @ParameterizedTest @ValueSource(ints = {1, 30})
    void acceptsCreditBoundaries(int credits) {
        assertThat(course("CS01", "Valid", credits).credits()).isEqualTo(credits);
    }

    @ParameterizedTest @ValueSource(ints = {0, 31})
    void rejectsCreditsIndependently(int credits) {
        assertThatThrownBy(() -> course("CS01", "Valid", credits)).isInstanceOf(AcademicCourse.InvalidAcademicCourseException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"x", "", " \t\n\r\u000b\f"})
    void rejectsShortOrBlankCodeAndText(String text) {
        assertThatThrownBy(() -> program(text, "Valid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> program("CS01", text)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> course(text, "Valid", 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> course("CS01", text, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checksUnicodeCharacterLimitsAfterUppercaseExpansion() {
        assertThat(program("C".repeat(32), "😀".repeat(160)).name().codePointCount(0, 320)).isEqualTo(160);
        assertThatThrownBy(() -> program("C".repeat(33), "Valid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> program("CS01", "😀".repeat(161))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> course("ß".repeat(32), "Valid", 3)).isInstanceOf(IllegalArgumentException.class);
    }

    private AcademicProgram program(String code, String name) {
        return AcademicProgram.create(UUID.randomUUID(), code, name, UUID.randomUUID(), AcademicCatalogStatus.ACTIVE, Instant.now());
    }
    private AcademicCourse course(String code, String title, int credits) {
        return AcademicCourse.create(UUID.randomUUID(), code, title, credits, UUID.randomUUID(), AcademicCatalogStatus.ACTIVE, Instant.now());
    }
}
