package com.campus.event.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EventRegistrationTest {
    final Instant now=Instant.parse("2026-10-04T00:00:00Z");
    private EventRegistration registered() { return EventRegistration.register(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),now); }
    @Test void cancellationRetainsMembershipIdentityAndRegistrationHistoryAndFreesSeat() {
        var old=registered(); var cancelled=old.cancel(now.plusSeconds(1));
        assertThat(old.consumesSeat()).isTrue(); assertThat(cancelled.consumesSeat()).isFalse();
        assertThat(cancelled.id()).isEqualTo(old.id()); assertThat(cancelled.eventId()).isEqualTo(old.eventId());
        assertThat(cancelled.studentId()).isEqualTo(old.studentId()); assertThat(cancelled.createdAt()).isEqualTo(old.createdAt());
        assertThat(cancelled.registeredAt()).isEqualTo(old.registeredAt()); assertThat(cancelled.rowVersion()).isZero();
        assertThat(cancelled.cancelledAt()).isEqualTo(now.plusSeconds(1)); assertThat(cancelled.attendedAt()).isNull();
        assertThatThrownBy(() -> cancelled.cancel(now.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cancelled.attend(CampusEvent.Status.OPEN,now.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
    }
    @Test void attendanceRequiresOpenOrClosedEventRetainsSeatAndIsTerminal() {
        var old=registered();
        for(var status:new CampusEvent.Status[]{CampusEvent.Status.OPEN,CampusEvent.Status.CLOSED}) {
            var attended=old.attend(status,now.plusSeconds(1));
            assertThat(attended.consumesSeat()).isTrue(); assertThat(attended.id()).isEqualTo(old.id());
            assertThat(attended.registeredAt()).isEqualTo(now); assertThat(attended.createdAt()).isEqualTo(now);
            assertThat(attended.attendedAt()).isEqualTo(now.plusSeconds(1)); assertThat(attended.cancelledAt()).isNull();
            assertThatThrownBy(() -> attended.cancel(now.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> attended.attend(status,now.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
        }
        for(var status:new CampusEvent.Status[]{CampusEvent.Status.DRAFT,CampusEvent.Status.CANCELLED})
            assertThatThrownBy(() -> old.attend(status,now)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsInconsistentLifecycleDatesAndNegativeVersion() {
        var old=registered();
        assertThatThrownBy(() -> old.cancel(now.minusNanos(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> old.attend(CampusEvent.Status.OPEN,now.minusNanos(1))).isInstanceOf(IllegalArgumentException.class);
        for(var status:EventRegistration.Status.values()) {
            assertThatThrownBy(() -> new EventRegistration(old.id(),old.eventId(),old.studentId(),status,-1,now,null,null,now,now)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EventRegistration(old.id(),old.eventId(),old.studentId(),status,0,now,now,now,now,now)).isInstanceOf(IllegalArgumentException.class);
        }
        for(var status:new EventRegistration.Status[]{EventRegistration.Status.CANCELLED,EventRegistration.Status.ATTENDED})
            assertThatThrownBy(() -> new EventRegistration(old.id(),old.eventId(),old.studentId(),status,0,now,null,null,now,now)).isInstanceOf(IllegalArgumentException.class);
        assertThat(old.cancel(now).cancelledAt()).isEqualTo(now);
        assertThat(old.attend(CampusEvent.Status.OPEN,now).attendedAt()).isEqualTo(now);
    }
    @Test void boundsMembershipQueriesWithoutAcceptingUnapprovedSorts() {
        new RegistrationSearch(Integer.MAX_VALUE,1,null,null,null,"registeredAt",true);
        for(String field:new String[]{"registeredAt","status","createdAt","updatedAt"})
            new RegistrationSearch(0,100,UUID.randomUUID(),UUID.randomUUID(),EventRegistration.Status.REGISTERED,field,false);
        assertThatThrownBy(() -> new RegistrationSearch(-1,20,null,null,null,"registeredAt",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistrationSearch(Integer.MAX_VALUE,100,null,null,null,"registeredAt",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistrationSearch(0,101,null,null,null,"registeredAt",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegistrationSearch(0,20,null,null,null,"studentName",true)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void restorationReusesIdRetainsCreationHistoryAndRecordsLatestAdmissionTime() {
        var old=registered(); var cancelled=old.cancel(now.plusSeconds(1)); var restored=cancelled.restore(now.plusSeconds(2));
        assertThat(restored.id()).isEqualTo(old.id()); assertThat(restored.createdAt()).isEqualTo(old.createdAt());
        assertThat(restored.eventId()).isEqualTo(old.eventId()); assertThat(restored.studentId()).isEqualTo(old.studentId());
        assertThat(restored.status()).isEqualTo(EventRegistration.Status.REGISTERED); assertThat(restored.consumesSeat()).isTrue();
        assertThat(restored.registeredAt()).isEqualTo(now.plusSeconds(2)); assertThat(restored.cancelledAt()).isNull(); assertThat(restored.attendedAt()).isNull();
        assertThatThrownBy(() -> restored.restore(now.plusSeconds(3))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cancelled.restore(now)).isInstanceOf(IllegalArgumentException.class);
    }
}
