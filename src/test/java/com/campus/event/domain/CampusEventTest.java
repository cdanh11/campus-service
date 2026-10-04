package com.campus.event.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CampusEventTest {
    private final Instant now = Instant.parse("2026-10-04T00:00:00Z");
    private CampusEvent draft(int capacity) {
        return CampusEvent.draft(UUID.randomUUID(), " \t\n\r\u000b\f vß \t", "Event title", "Event description",
                now.plusSeconds(3600), now.plusSeconds(7200), capacity, now);
    }
    @Test void normalizesTextAndChecksUnicodeLimitsAfterCaseExpansion() {
        assertThat(draft(1).code()).isEqualTo("VSS");
        assertThat(EventValues.text("😀".repeat(160),160)).hasSize(320);
        assertThat(EventValues.text("😀".repeat(4000),4000)).hasSize(8000);
        for (String invalid : new String[]{null,"","x"," \t\n\r\u000b\f","😀".repeat(161)})
            assertThatThrownBy(() -> EventValues.text(invalid,160)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventValues.code("ß".repeat(17))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventValues.text("x".repeat(4001),4000)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsInvalidSchedulesCapacityAndVersion() {
        for (Instant end : new Instant[]{now, now.minusNanos(1)})
            assertThatThrownBy(() -> CampusEvent.draft(UUID.randomUUID(),"EV","Title","Description",now,end,1,now))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> draft(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> draft(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventValues.version(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(draft(Integer.MAX_VALUE).capacity()).isEqualTo(Integer.MAX_VALUE);
    }
    @Test void enforcesLifecycleAndOccupiedCapacityWithoutRewritingIdentityOrHistory() {
        var original = draft(3);
        assertThatThrownBy(() -> update(original,CampusEvent.Status.CLOSED,3,0)).isInstanceOf(IllegalStateException.class);
        var open = update(original,CampusEvent.Status.OPEN,3,0);
        assertThat(open.id()).isEqualTo(original.id()); assertThat(open.createdAt()).isEqualTo(original.createdAt());
        assertThat(open.updatedAt()).isEqualTo(now.plusSeconds(1)); assertThat(open.rowVersion()).isZero();
        assertThatThrownBy(() -> update(open,CampusEvent.Status.DRAFT,3,0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> update(open,CampusEvent.Status.OPEN,1,2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> update(open,CampusEvent.Status.OPEN,3,-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(update(open,CampusEvent.Status.OPEN,2,2).capacity()).isEqualTo(2);
        for (var terminal : new CampusEvent[]{update(open,CampusEvent.Status.CLOSED,3,2),update(open,CampusEvent.Status.CANCELLED,3,2),update(original,CampusEvent.Status.CANCELLED,3,0)})
            for (var next : CampusEvent.Status.values())
                assertThatThrownBy(() -> update(terminal,next,3,2)).isInstanceOf(IllegalStateException.class);
    }
    private CampusEvent update(CampusEvent event, CampusEvent.Status status, int capacity, long occupied) {
        return event.update(event.code(),event.title(),event.description(),event.startsAt(),event.endsAt(),capacity,status,occupied,now.plusSeconds(1));
    }
    @Test void boundsSearchOffsetsAndAllowsOnlyApprovedSorts() {
        assertThat(new EventSearch(0,100," \t\n",null,"code",true).query()).isNull();
        new EventSearch(Integer.MAX_VALUE,1,"😀".repeat(100),null,"startsAt",false);
        for (String field : new String[]{"code","title","startsAt","endsAt","capacity","status","createdAt","updatedAt"})
            new EventSearch(0,20,null,CampusEvent.Status.OPEN,field,true);
        assertThatThrownBy(() -> new EventSearch(Integer.MAX_VALUE,100,null,null,"code",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventSearch(-1,20,null,null,"code",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventSearch(0,101,null,null,"code",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventSearch(0,20,"😀".repeat(101),null,"code",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventSearch(0,20,null,null,"description",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventSearch(0,20,null,null,null,true)).isInstanceOf(IllegalArgumentException.class);
    }
}
