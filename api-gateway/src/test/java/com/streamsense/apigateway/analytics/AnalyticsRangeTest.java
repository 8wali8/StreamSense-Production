package com.streamsense.apigateway.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalyticsRangeTest {

    @Test
    void parsesASessionIdOrAnAbsoluteRange() {
        assertThat(AnalyticsRange.of(" 12 ", null, null)).isEqualTo(new AnalyticsRange(12L, null, null));
        assertThat(AnalyticsRange.of(null, 1.0, 2.0)).isEqualTo(new AnalyticsRange(null, 1L, 2L));
        assertThat(AnalyticsRange.of("", null, null)).isEqualTo(AnalyticsRange.NONE);
    }

    @Test
    void aMalformedSessionIdIsAnInputErrorNotADifferentQuestion() {
        assertThatThrownBy(() -> AnalyticsRange.of("12x", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId must be a number");
    }
}
