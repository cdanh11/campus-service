package com.campus.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ManualPaymentTest {
    @Test void fullReversalPreservesIdentityAmountAndAdmissionHistoryAndIsTerminal() {
        var now=Instant.parse("2026-01-01T00:00:00Z");
        var value=new ManualPayment(UUID.randomUUID(),"rc",UUID.randomUUID(),BigDecimal.TEN,"VND",PaymentStatus.RECORDED,0,now,null,null,now,now);
        var reversed=value.reverse(" \tCorrection\f ",now.plusSeconds(1));
        assertThat(reversed).usingRecursiveComparison().ignoringFields("status","reversedAt","reversalReason","updatedAt").isEqualTo(value);
        assertThat(reversed.reversalReason()).isEqualTo("Correction");
        assertThatThrownBy(() -> reversed.reverse("Again",now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> value.reverse("x",now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> value.reverse("😀".repeat(501),now)).isInstanceOf(IllegalArgumentException.class);
        assertThat(value.reverse("😀".repeat(500),now).reversalReason()).hasSize(1000);
        assertThatThrownBy(() -> value.reverse("Reason",now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validatesAmountReceiptAndReversalShape() {
        var now=Instant.now();
        for(String amount:new String[]{"0","-1","0.001","10000000000000000000"})
            assertThatThrownBy(() -> new ManualPayment(UUID.randomUUID(),"RC",UUID.randomUUID(),new BigDecimal(amount),"VND",PaymentStatus.RECORDED,0,now,null,null,now,now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManualPayment(UUID.randomUUID(),"RC",UUID.randomUUID(),BigDecimal.ONE,"VND",PaymentStatus.RECORDED,0,now,now,"Reason",now,now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManualPayment(UUID.randomUUID(),"RC",UUID.randomUUID(),BigDecimal.ONE,"VND",PaymentStatus.REVERSED,0,now,null,null,now,now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentSearch(Integer.MAX_VALUE,2,null,null,null,"recordedAt",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentSearch(0,20,null,null,null,"chargeId",true)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void balanceRetainsExactMaximumAndRejectsOverpaymentOrTornArithmetic() {
        var balance=new ChargeBalance(UUID.randomUUID(),FinanceValues.MAX_AMOUNT,"VND",ChargeStatus.OPEN,0,BigDecimal.ONE);
        assertThat(balance.outstandingAmount()).isEqualTo(new BigDecimal("9999999999999999998"));
        assertThat(new ChargeBalance(UUID.randomUUID(),BigDecimal.TEN,"VND",ChargeStatus.CANCELLED,1,BigDecimal.ZERO).outstandingAmount()).isZero();
        assertThatThrownBy(() -> new ChargeBalance(UUID.randomUUID(),BigDecimal.TEN,"VND",ChargeStatus.CANCELLED,1,BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChargeBalance(UUID.randomUUID(),BigDecimal.ONE,"VND",ChargeStatus.OPEN,0,BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChargeBalance(UUID.randomUUID(),BigDecimal.TEN,"VND",ChargeStatus.OPEN,0,BigDecimal.ONE,BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
    }
}
