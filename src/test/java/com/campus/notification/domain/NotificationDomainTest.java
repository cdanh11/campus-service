package com.campus.notification.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotificationDomainTest {
    @Test void trimsExactlySixWhitespaceCharactersAndCountsUnicodeAfterExpansion() {
        assertThat(NotificationValues.code(" \t\n\r\u000b\f vß \t")).isEqualTo("VSS");
        assertThat(NotificationValues.text("😀".repeat(160),160)).hasSize(320);
        for (String value : new String[]{null,"","x"," \t\n\r\u000b\f","😀".repeat(161)})
            assertThatThrownBy(() -> NotificationValues.text(value,160)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationValues.code("ß".repeat(17))).isInstanceOf(IllegalArgumentException.class);
        assertThat(NotificationValues.text("a".repeat(4000),4000)).hasSize(4000);
        assertThatThrownBy(() -> NotificationValues.text("a".repeat(4001),4000)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void publishedNoticeRetainsTemplateAndIsImmutable() {
        var now=Instant.parse("2026-10-04T00:00:00Z");
        var original=new Notice(UUID.randomUUID(),UUID.randomUUID(),"Title","Plain text",Notice.Status.DRAFT,0,null,now,now);
        var edited=original.edit("New title","New text",now.plusSeconds(1));
        var published=edited.publish(now.plusSeconds(2));
        assertThat(published.templateId()).isEqualTo(original.templateId());
        assertThat(published.createdAt()).isEqualTo(original.createdAt());
        assertThat(published.publishedAt()).isEqualTo(now.plusSeconds(2));
        assertThatThrownBy(() -> published.edit("Changed","Changed",now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> published.publish(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new Notice(original.id(),original.templateId(),"Title","Body",Notice.Status.DRAFT,0,now,now,now)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void readAcknowledgementIsIdempotentAndRetainsDeliveryHistory() {
        var now=Instant.parse("2026-10-04T00:00:00Z");
        var delivery=new NotificationDelivery(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),NotificationDelivery.Status.UNREAD,0,now,null,now,now);
        assertThatThrownBy(() -> delivery.read(now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        var read=delivery.read(now.plusSeconds(1));
        assertThat(read.read(now.plusSeconds(2))).isSameAs(read);
        assertThat(read.deliveredAt()).isEqualTo(delivery.deliveredAt());
        assertThat(read.recipientId()).isEqualTo(delivery.recipientId());
    }
    @Test void boundsOffsetStatusAndResourceSpecificSorts() {
        new NotificationSearch(Integer.MAX_VALUE,1,null,"UNREAD","deliveredAt",false).validate(NotificationSearch.Kind.INBOX);
        assertThatThrownBy(() -> new NotificationSearch(Integer.MAX_VALUE,100,null,null,"createdAt",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationSearch(0,101,null,null,"createdAt",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationSearch(0,20,null,"READ","code",true).validate(NotificationSearch.Kind.TEMPLATE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationSearch(0,20,"secret",null,"deliveredAt",true).validate(NotificationSearch.Kind.INBOX)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationSearch(0,20,null,null,"recipientId",true).validate(NotificationSearch.Kind.INBOX)).isInstanceOf(IllegalArgumentException.class);
    }
}
