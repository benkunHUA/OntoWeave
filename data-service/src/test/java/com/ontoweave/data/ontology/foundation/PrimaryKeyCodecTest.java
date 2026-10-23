package com.ontoweave.data.ontology.foundation;

import com.ontoweave.data.ontology.common.PrimaryKeyCodec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrimaryKeyCodecTest {

    @Test
    void canonicalizesTextWithoutChangingItsValue() {
        UUID uuid = UUID.fromString("6f7c4d1d-31d5-4f4d-8546-93f995a67f09");

        assertThat(PrimaryKeyCodec.canonicalize("  Ada  ", "text", null)).isEqualTo("  Ada  ");
        assertThat(PrimaryKeyCodec.canonicalize(uuid, "text", null)).isEqualTo(uuid.toString());
    }

    @Test
    void canonicalizesIntegersWithoutLeadingZerosAndChecksInt32Range() {
        assertThat(PrimaryKeyCodec.canonicalize("00042", "integer", null)).isEqualTo("42");
        assertThat(PrimaryKeyCodec.canonicalize(-7L, "integer", null)).isEqualTo("-7");

        assertThatThrownBy(() -> PrimaryKeyCodec.canonicalize("2147483648", "integer", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalizesDecimalsAtTheConfiguredScale() {
        assertThat(PrimaryKeyCodec.canonicalize(new BigDecimal("12.5"), "decimal", 2))
                .isEqualTo("12.50");
        assertThat(PrimaryKeyCodec.canonicalize("-0.10", "decimal", 3))
                .isEqualTo("-0.100");
        assertThatThrownBy(() -> PrimaryKeyCodec.canonicalize("12.345", "decimal", 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalizesDatesAndTimestampsInUtc() {
        assertThat(PrimaryKeyCodec.canonicalize(LocalDate.of(2026, 10, 23), "date", null))
                .isEqualTo("2026-10-23");

        Instant instant = Instant.parse("2026-10-23T00:30:00Z");
        assertThat(PrimaryKeyCodec.canonicalize(instant, "timestamp", null))
                .isEqualTo("2026-10-23T00:30:00Z");
        assertThat(PrimaryKeyCodec.canonicalize(
                OffsetDateTime.of(2026, 10, 23, 8, 30, 0, 0, ZoneOffset.ofHours(8)),
                "timestamp",
                null
        )).isEqualTo("2026-10-23T00:30:00Z");
    }

    @Test
    void canonicalizationIsIdempotentForSupportedTypes() {
        assertThat(PrimaryKeyCodec.canonicalize("42", "integer", null)).isEqualTo("42");
        assertThat(PrimaryKeyCodec.canonicalize("12.50", "decimal", 2)).isEqualTo("12.50");
        assertThat(PrimaryKeyCodec.canonicalize("2026-10-23", "date", null))
                .isEqualTo("2026-10-23");
        assertThat(PrimaryKeyCodec.canonicalize("2026-10-23T00:30:00Z", "timestamp", null))
                .isEqualTo("2026-10-23T00:30:00Z");
    }
}
