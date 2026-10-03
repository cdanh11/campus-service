package com.campus.academic.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EnrollmentTest {
    @Test
    void withdrawalPreservesMembershipIdentityAndCreationHistory() {
        var before = new Enrollment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                EnrollmentStatus.ENROLLED, 7, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        var now = Instant.parse("2026-02-01T00:00:00Z");
        var after = before.withStatus(EnrollmentStatus.WITHDRAWN, now);
        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.studentId()).isEqualTo(before.studentId());
        assertThat(after.sectionId()).isEqualTo(before.sectionId());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.rowVersion()).isEqualTo(7);
        assertThat(after.status()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(after.updatedAt()).isEqualTo(now);
        assertThat(before.status()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void rejectsNegativeVersionBeforePersistence() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Enrollment(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), EnrollmentStatus.ENROLLED, -1, Instant.EPOCH, Instant.EPOCH));
    }
}
