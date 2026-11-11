package com.uni.attendance.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilTest {

    @Test
    void cosineOfIdenticalAndOrthogonalVectors() {
        float[] a = {1, 2, 3};
        assertThat(Embeddings.cosine(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(Embeddings.cosine(new float[]{1, 0}, new float[]{0, 1})).isZero();
        assertThat(Embeddings.cosine(new float[]{1, 0}, new float[]{-1, 0}))
                .isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void normalizedHasUnitLengthAndRejectsBadInput() {
        float[] n = Embeddings.normalized(new float[]{3, 4});
        assertThat(n[0]).isCloseTo(0.6f, org.assertj.core.data.Offset.offset(1e-6f));
        assertThat(n[1]).isCloseTo(0.8f, org.assertj.core.data.Offset.offset(1e-6f));
        assertThatThrownBy(() -> Embeddings.normalized(new float[]{0, 0})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Embeddings.normalized(new float[]{Float.NaN, 1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Embeddings.normalized(new float[]{Float.POSITIVE_INFINITY, 1})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializeRoundTrips() {
        float[] v = {0.25f, -1.5f, 3.0e-5f};
        assertThat(Embeddings.parse(Embeddings.serialize(v))).containsExactly(v);
    }

    @Test
    void haversineMatchesKnownDistances() {
        assertThat(Geo.distanceMeters(21.0285, 105.8542, 21.0285, 105.8542)).isZero();
        // 1 độ vĩ độ ≈ 111.19 km
        assertThat(Geo.distanceMeters(0, 0, 1, 0)).isBetween(111_000.0, 111_400.0);
    }

    @Test
    void csvEscapesQuotesAndFormulas() {
        assertThat(Csv.cell("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(Csv.cell("=HYPERLINK(\"x\")")).startsWith("\"'=");
        assertThat(Csv.cell(null)).isEqualTo("\"\"");
        assertThat(Csv.row("x", "y,z")).isEqualTo("\"x\",\"y,z\"\r\n");
    }
}
