package com.campus.organization.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationUnitTest {
    @Test
    void normalizesOnlyTheApprovedBoundaryWhitespaceCharacters() {
        OrganizationUnit unit = OrganizationUnit.create(UUID.randomUUID(), "\t ENG\u000b", "\nEngineering\f", OrganizationUnitType.FACULTY, OrganizationUnitStatus.ACTIVE, Instant.now());
        assertThat(unit.code()).isEqualTo("ENG");
        assertThat(unit.name()).isEqualTo("Engineering");
    }

    @Test
    void rejectsBlankOrSingleCharacterValues() {
        assertThatThrownBy(() -> OrganizationUnit.create(UUID.randomUUID(), "\r\n", "Engineering", OrganizationUnitType.FACULTY, OrganizationUnitStatus.ACTIVE, Instant.now()))
                .isInstanceOf(OrganizationUnit.InvalidOrganizationUnitException.class);
        assertThatThrownBy(() -> OrganizationUnit.create(UUID.randomUUID(), "EN", " A ", OrganizationUnitType.FACULTY, OrganizationUnitStatus.ACTIVE, Instant.now()))
                .isInstanceOf(OrganizationUnit.InvalidOrganizationUnitException.class);
    }

    @Test
    void canonicalizesCodes() {
        assertThat(OrganizationUnit.create(UUID.randomUUID(), " eng ", "Engineering", OrganizationUnitType.FACULTY, OrganizationUnitStatus.ACTIVE, Instant.now()).code()).isEqualTo("ENG");
    }
}
