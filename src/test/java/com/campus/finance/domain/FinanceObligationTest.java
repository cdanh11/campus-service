package com.campus.finance.domain;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FinanceObligationTest {
    @Test void acceptsExactIntegerBoundariesAndRejectsFractionalOverflowOrNonpositiveAmounts() {
        assertThat(FinanceValues.amount(new BigDecimal("1.000"))).isEqualTo(BigDecimal.ONE);
        assertThat(FinanceValues.amount(FinanceValues.MAX_AMOUNT)).isEqualTo(FinanceValues.MAX_AMOUNT);
        for (String value : new String[]{"0","-1","0.001","1.001","10000000000000000000"})
            assertThatThrownBy(() -> FinanceValues.amount(new BigDecimal(value))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinanceValues.amount(null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void normalizesSixWhitespaceCharactersAndValidatesUnicodeAfterUppercaseExpansion() {
        assertThat(FinanceValues.code(" \t\n\r\u000b\fßa\f\u000b\r\n\t ")).isEqualTo("SSA");
        assertThat(FinanceValues.text("😀".repeat(160),160)).hasSize(320);
        assertThat(FinanceValues.code("vV")).isEqualTo("VV");
        for (String value : new String[]{"x"," \t\n\r\u000b\f ","ß".repeat(17),"x".repeat(33)})
            assertThatThrownBy(() -> FinanceValues.code(value)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinanceValues.text("😀".repeat(161),160)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void chargeCancellationRetainsAllSnapshotFieldsAndIsTerminal() {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var value = new StudentCharge(UUID.randomUUID(),"CH",UUID.randomUUID(),UUID.randomUUID(),"FE","Fee",BigDecimal.TEN,"VND",LocalDate.now(),ChargeStatus.OPEN,3,now,now);
        var cancelled = value.cancel(now.plusSeconds(1));
        assertThat(cancelled).usingRecursiveComparison().ignoringFields("status","updatedAt").isEqualTo(value);
        assertThat(cancelled.status()).isEqualTo(ChargeStatus.CANCELLED);
        assertThatThrownBy(() -> cancelled.cancel(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FeeDefinition(UUID.randomUUID(),"FE","Fee",BigDecimal.ONE,"USD",FeeStatus.ACTIVE,0,now,now)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void boundsQueriesAndResourceSpecificSorts() {
        for (int size : new int[]{0,101})
            assertThatThrownBy(() -> new FinanceSearch(0,size,null,null,null,null,"code",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FinanceSearch(Integer.MAX_VALUE,2,null,null,null,null,"code",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FinanceSearch(0,20,"x".repeat(101),null,null,null,"code",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FinanceSearch(0,20,null,"OPEN",null,null,"code",true).fees()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FinanceSearch(0,20,null,null,null,null,"name",true).charges()).isInstanceOf(IllegalArgumentException.class);
    }
}
