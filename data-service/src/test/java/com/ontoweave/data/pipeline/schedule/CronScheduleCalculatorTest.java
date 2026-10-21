package com.ontoweave.data.pipeline.schedule;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronScheduleCalculatorTest {

    private final CronScheduleCalculator calculator = new CronScheduleCalculator();

    @Test
    void computesNextExecutionUsingTheConfiguredTimeZoneAndStoresUtc() {
        Instant from = Instant.parse("2026-03-07T02:00:00Z");

        Instant next = calculator.next("0 9 * * *", "Asia/Shanghai", from);

        assertThat(next).isEqualTo(Instant.parse("2026-03-08T01:00:00Z"));
    }

    @Test
    void keepsLocalWallClockAcrossDaylightSavingTransitions() {
        Instant from = Instant.parse("2026-03-07T15:00:00Z");

        Instant next = calculator.next("0 9 * * *", "America/New_York", from);

        assertThat(next).isEqualTo(Instant.parse("2026-03-08T13:00:00Z"));
    }

    @Test
    void resolvesTheRepeatedLocalHourAfterTheFallBackTransition() {
        Instant from = Instant.parse("2026-10-31T14:00:00Z");

        Instant next = calculator.next("30 1 * * *", "America/New_York", from);

        assertThat(next).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    void resumesAfterDowntimeAtTheMostRecentMissedTriggerOnly() {
        Instant firstDue = Instant.parse("2026-03-07T10:00:00Z");
        Instant now = Instant.parse("2026-03-07T10:05:30Z");

        Instant due = calculator.latestDue("* * * * *", "UTC", firstDue, now);

        assertThat(due).isEqualTo(Instant.parse("2026-03-07T10:05:00Z"));
    }

    @Test
    void rejectsSecondsQuartzExtensionsAndInvalidTimeZones() {
        Instant now = Instant.parse("2026-03-07T10:00:00Z");

        assertThatThrownBy(() -> calculator.next("0 0 9 * * *", "UTC", now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 段");
        assertThatThrownBy(() -> calculator.next("0 9 ? * *", "UTC", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.next("0 9 L * *", "UTC", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.next("0 9 15W * *", "UTC", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.next("0 9 * * 1#2", "UTC", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.next("0 9 * * *", "Mars/Olympus", now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("时区");
    }
}
