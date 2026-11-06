package com.noticiasplaneta.ingest.domain;

import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F01 · Elemento de ingesta")
class IngestItemTest {

    private final SourceId sourceId = SourceId.newId();

    private FeedEntry entry(String title, String url) {
        return new FeedEntry("ext-1", title, "cuerpo", url, Instant.parse("2026-11-05T10:00:00Z"), "Redacción");
    }

    @Test
    @DisplayName("Un item sin título se rechaza")
    void sinTitulo() {
        assertThatThrownBy(() -> IngestItem.receive(sourceId, entry("  ", "https://ejemplo.com/a")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("título");
    }

    @Test
    @DisplayName("Un item sin enlace canónico se rechaza")
    void sinUrl() {
        assertThatThrownBy(() -> IngestItem.receive(sourceId, entry("Titular", "")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("enlace");
    }

    @Test
    @DisplayName("El cuerpo se trunca a 100.000 caracteres")
    void truncado() {
        String gigante = "x".repeat(IngestItem.MAX_BODY_LENGTH + 500);
        FeedEntry largo = new FeedEntry("e", "Titular", gigante, "https://ejemplo.com/a", null, null);

        IngestItem item = IngestItem.receive(sourceId, largo);

        assertThat(item.body()).hasSize(IngestItem.MAX_BODY_LENGTH);
    }

    @Test
    @DisplayName("Sin externalId se usa la URL canónica como identificador")
    void externalIdFallback() {
        FeedEntry sinId = new FeedEntry(null, "Titular", "cuerpo", "https://ejemplo.com/a", null, null);

        assertThat(IngestItem.receive(sourceId, sinId).externalId()).isEqualTo("https://ejemplo.com/a");
    }
}
