package com.noticiasplaneta.autopublish.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F6 · reglas duras de la publicación automática")
class AutoPublishRulesTest {

    private static final Instant AHORA = Instant.parse("2026-11-26T10:00:00Z");

    @Test
    @DisplayName("La ventana es de 48 h y se mide hacia atrás desde ahora")
    void ventana() {
        assertThat(AutoPublishRules.withinWindow(AHORA.minusSeconds(3600), AHORA)).isTrue();
        assertThat(AutoPublishRules.withinWindow(AHORA.minusSeconds(47 * 3600L), AHORA)).isTrue();
        assertThat(AutoPublishRules.withinWindow(AHORA.minusSeconds(49 * 3600L), AHORA)).isFalse();
        assertThat(AutoPublishRules.withinWindow(AHORA.plusSeconds(60), AHORA)).isFalse();
        assertThat(AutoPublishRules.withinWindow(null, AHORA)).isFalse();
    }

    @Test
    @DisplayName("Una fuente deshabilitada no publica sola aunque esté marcada")
    void fuenteDeConfianza() {
        assertThat(AutoPublishRules.trustedSource(true, "ACTIVE")).isTrue();
        assertThat(AutoPublishRules.trustedSource(true, "DEGRADED")).isTrue();
        assertThat(AutoPublishRules.trustedSource(true, "DISABLED")).isFalse();
        assertThat(AutoPublishRules.trustedSource(false, "ACTIVE")).isFalse();
        assertThat(AutoPublishRules.trustedSource(true, null)).isFalse();
    }

    @Test
    @DisplayName("Cupo 0 apaga la sección; cupo alcanzado no publica")
    void cupo() {
        assertThat(AutoPublishRules.quotaAvailable(0, 0)).isFalse();
        assertThat(AutoPublishRules.quotaAvailable(0, 3)).isTrue();
        assertThat(AutoPublishRules.quotaAvailable(2, 3)).isTrue();
        assertThat(AutoPublishRules.quotaAvailable(3, 3)).isFalse();
    }

    @Test
    @DisplayName("Titulares con distinto formato comparan igual")
    void normalizaTitulares() {
        assertThat(AutoPublishRules.normalizeTitle("El  Congreso, ¿aprueba?"))
                .isEqualTo(AutoPublishRules.normalizeTitle("el congreso aprueba"));
        assertThat(AutoPublishRules.normalizeTitle(null)).isEmpty();
    }
}
