package com.campus.dormitory.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AccommodationAssignmentTest {
    @Test void releaseRetainsMembershipAndHistoryAndIsTerminal() {
        var now = Instant.now();
        var old = new AccommodationAssignment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AssignmentStatus.ASSIGNED, 0, now, null, now, now);
        var released = old.release(now.plusSeconds(1));
        assertThat(released.id()).isEqualTo(old.id()); assertThat(released.studentId()).isEqualTo(old.studentId());
        assertThat(released.bedId()).isEqualTo(old.bedId()); assertThat(released.assignedAt()).isEqualTo(now);
        assertThat(released.createdAt()).isEqualTo(now); assertThat(released.releasedAt()).isEqualTo(now.plusSeconds(1));
        assertThatThrownBy(() -> released.release(now.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
    }
    @Test void validatesReleaseShapeTimeAndVersion() {
        var now = Instant.now(); UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new AccommodationAssignment(id, id, id, AssignmentStatus.ASSIGNED, 0, now, now, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccommodationAssignment(id, id, id, AssignmentStatus.RELEASED, 0, now, null, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccommodationAssignment(id, id, id, AssignmentStatus.RELEASED, 0, now, now.minusSeconds(1), now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccommodationAssignment(id, id, id, AssignmentStatus.ASSIGNED, -1, now, null, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssignmentSearch(Integer.MAX_VALUE, 2, null, null, null, "assignedAt", true)).isInstanceOf(IllegalArgumentException.class);
    }
}
