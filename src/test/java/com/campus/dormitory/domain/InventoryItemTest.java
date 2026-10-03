package com.campus.dormitory.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class InventoryItemTest {
    @Test void normalizesExactlySixWhitespaceCharactersAndUnicodeBoundaries() {
        assertThat(item(" \t\n\r\u000b\fcs01 ", "😀".repeat(160)).code()).isEqualTo("CS01");
        assertThatThrownBy(() -> item("ß".repeat(32), "Valid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item("CC", "😀".repeat(161))).isInstanceOf(IllegalArgumentException.class);
        assertThat(item("CC", "\u00a0Label\u00a0").name()).isEqualTo("\u00a0Label\u00a0");
    }
    @ParameterizedTest @ValueSource(strings = {"", "x", " \t\n\r\u000b\f"})
    void rejectsBlankAndShortText(String text) {
        assertThatThrownBy(() -> item(text, "Valid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item("CC", text)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsWrongParentShapeAndNegativeVersion() {
        Instant now = Instant.now(); UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new InventoryItem(id, InventoryKind.BUILDING, id, "CC", "Valid", InventoryStatus.ACTIVE, 0, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InventoryItem(id, InventoryKind.ROOM, null, "CC", "Valid", InventoryStatus.ACTIVE, 0, now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InventoryItem(id, InventoryKind.BED, id, "CC", "Valid", InventoryStatus.ACTIVE, -1, now, now)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void boundsQueriesAndAllowsExactIntegerOffset() {
        assertThatCode(() -> new InventorySearch(Integer.MAX_VALUE, 1, null, null, null, "code", true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new InventorySearch(Integer.MAX_VALUE, 2, null, null, null, "code", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InventorySearch(0, 1, "😀".repeat(101), null, null, "code", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InventorySearch(0, 1, null, null, null, "arbitrary", true)).isInstanceOf(IllegalArgumentException.class);
    }
    private InventoryItem item(String code, String name) {
        var now = Instant.now();
        return new InventoryItem(UUID.randomUUID(), InventoryKind.BUILDING, null, code, name, InventoryStatus.ACTIVE, 0, now, now);
    }
}
