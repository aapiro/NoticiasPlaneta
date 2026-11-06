package com.noticiasplaneta.ingest.domain;

import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F01 · Fuente: salud y degradación")
class SourceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");

    @Test
    @DisplayName("Una fuente nueva nace ACTIVE y sin fallos")
    void naceActiva() {
        Source source = Source.register("BOE", "https://www.boe.es/rss.xml");

        assertThat(source.state()).isEqualTo(SourceState.ACTIVE);
        assertThat(source.failureCount()).isZero();
        assertThat(source.lastCheckedAt()).isNull();
    }

    @Test
    @DisplayName("Una URL que no es http/https se rechaza")
    void urlInvalida() {
        assertThatThrownBy(() -> Source.register("Mala", "ftp://ejemplo.com/feed"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("URL");
    }

    @Test
    @DisplayName("Cuatro fallos no degradan la fuente")
    void cuatroFallosSiguenActivos() {
        Source source = Source.register("BOE", "https://www.boe.es/rss.xml");

        for (int i = 0; i < 4; i++) {
            assertThat(source.recordFailure(NOW)).isFalse();
        }

        assertThat(source.state()).isEqualTo(SourceState.ACTIVE);
        assertThat(source.failureCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("El quinto fallo degrada la fuente y pide aviso")
    void quintoFalloDegrada() {
        Source source = Source.register("BOE", "https://www.boe.es/rss.xml");

        for (int i = 0; i < 4; i++) {
            source.recordFailure(NOW);
        }

        boolean degraded = source.recordFailure(NOW);

        assertThat(degraded).isTrue();
        assertThat(source.state()).isEqualTo(SourceState.DEGRADED);
    }

    @Test
    @DisplayName("Una fuente degradada no se borra ni se desactiva sola")
    void degradadaSigueExistiendo() {
        Source source = Source.register("BOE", "https://www.boe.es/rss.xml");
        for (int i = 0; i < Source.DEGRADE_THRESHOLD; i++) {
            source.recordFailure(NOW);
        }

        assertThat(source.state()).isEqualTo(SourceState.DEGRADED);
        assertThat(source.isFetchable()).isTrue();
    }

    @Test
    @DisplayName("Un éxito tras degradar la recupera y resetea el contador")
    void exitoRecupera() {
        Source source = Source.register("BOE", "https://www.boe.es/rss.xml");
        for (int i = 0; i < Source.DEGRADE_THRESHOLD; i++) {
            source.recordFailure(NOW);
        }

        boolean recovered = source.recordSuccess(NOW, "etag-1", "Thu, 06 Nov 2026 08:00:00 GMT");

        assertThat(recovered).isTrue();
        assertThat(source.state()).isEqualTo(SourceState.ACTIVE);
        assertThat(source.failureCount()).isZero();
        assertThat(source.etag()).isEqualTo("etag-1");
    }

    @Test
    @DisplayName("Una fuente desactivada no se consulta")
    void desactivadaNoSeConsulta() {
        Source source = Source.register("BOE", "https://www.boe.es/rss.xml");
        source.disable();

        assertThat(source.isFetchable()).isFalse();
    }
}
