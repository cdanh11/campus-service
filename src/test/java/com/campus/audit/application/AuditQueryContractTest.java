package com.campus.audit.application;

import java.time.Instant;
import java.util.*;
import com.campus.shared.application.audit.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuditQueryContractTest {
    private AuditSearch search(int page,int size,String resource,String action,Instant from,Instant until) {
        return new AuditSearch(page,size,null,null,resource,action,from,until,false);
    }
    @Test void rejectsUnsafeBoundsRangesAndNonTokenFilters() {
        var now=Instant.parse("2026-10-04T00:00:00Z");
        for(var bounds:List.of(new int[]{-1,20},new int[]{0,0},new int[]{0,101},new int[]{Integer.MAX_VALUE,100}))
            assertThatThrownBy(()->search(bounds[0],bounds[1],null,null,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->search(0,20,null,null,now,now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->search(0,20,null,null,now,now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        for(String invalid:List.of(""," ","created","A' OR 1=1","x".repeat(65)))
            assertThatThrownBy(()->search(0,20,null,invalid,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(search(0,100,"TITLE","CREATED",now,now.plusSeconds(1)).size()).isEqualTo(100);
    }
    @Test void ownerPolicyRejectsUnsupportedResourceAndLengthsWithoutInventingActionEnum() {
        var policy=new AuditPolicy(16,Map.of("TITLE",Set.of("ACTIVE","INACTIVE")));
        assertThatThrownBy(()->policy.validate(search(0,20,"USER",null,null,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->policy.validate(search(0,20,"TITLE","X".repeat(17),null,null))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->policy.validate(search(0,20,"TITLE","FUTURE_ACTION",null,null))).doesNotThrowAnyException();
    }
    @Test void safeMetadataIsPerResourceAndDeeplyImmutable() {
        var statuses=new HashSet<>(Set.of("ACTIVE")); Map<String,Set<String>> original=new HashMap<>(Map.of("TITLE",statuses));
        var policy=new AuditPolicy(16,original); statuses.add("SENSITIVE"); original.put("OTHER",Set.of("SENSITIVE"));
        assertThat(policy.metadata("TITLE","ACTIVE")).containsExactlyEntriesOf(Map.of("status","ACTIVE"));
        for(String unsafe:List.of("SENSITIVE","OPEN","", "private payload")) assertThat(policy.metadata("TITLE",unsafe)).isEmpty();
        assertThat(policy.metadata("OTHER","SENSITIVE")).isEmpty(); assertThat(policy.metadata("TITLE",null)).isEmpty();
        assertThatThrownBy(()->policy.statuses().get("TITLE").add("NEW")).isInstanceOf(UnsupportedOperationException.class);
    }
}
