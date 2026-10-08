package com.campus.shared.api;

import java.math.BigDecimal;

import com.campus.academic.api.AdminAcademicCourseController;
import com.campus.finance.api.AdminFinanceController;
import com.campus.identity.api.AdminUserController;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JsonTest
@Import(IntegerJsonConfiguration.class)
class IntegerJsonContractTest {
    @Autowired ObjectMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = {"1.9", "1.0", "1e0", "\"1\"", "\"\"", "true", "[]", "{}", "9223372036854775808"})
    void rejectsNonIntegerTokensAndOverflowForVersionsAndCredits(String value) {
        assertThatThrownBy(() -> mapper.readValue("{\"expectedVersion\":" + value + "}", AdminUserController.StatusRequest.class))
                .isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"credits\":" + value + "}", AdminAcademicCourseController.Request.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void retainsExactInt64VersionsAndDecimalMoneyWithoutChangingSerialization() throws Exception {
        var zero = mapper.readValue("{\"expectedVersion\":0}", AdminUserController.StatusRequest.class);
        var maximum = mapper.readValue("{\"expectedVersion\":9223372036854775807}", AdminUserController.StatusRequest.class);
        assertThat(zero.expectedVersion()).isZero();
        assertThat(maximum.expectedVersion()).isEqualTo(Long.MAX_VALUE);
        assertThat(mapper.writeValueAsString(maximum)).contains("\"expectedVersion\":9223372036854775807");
        assertThat(mapper.readValue("{\"credits\":30}", AdminAcademicCourseController.Request.class).credits()).isEqualTo(30);
        // Finance domain validation, rather than the JSON parser, owns integer-VND rules.
        assertThat(mapper.readValue("{\"amount\":1.25}", AdminFinanceController.FeeCreate.class).amount())
                .isEqualByComparingTo(new BigDecimal("1.25"));
    }
}
