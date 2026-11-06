package com.noticiasplaneta.ingest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F01 · Huella de contenido")
class ContentHashTest {

    @Test
    @DisplayName("El mismo texto con distinta forma produce la misma huella")
    void normalizaAntesDeHashear() {
        ContentHash a = ContentHash.of("Sube   la Luz", "El precio subió un 12%.");
        ContentHash b = ContentHash.of("  sube la luz ", "el precio subio un 12%.");

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("Textos distintos producen huellas distintas")
    void textosDistintos() {
        assertThat(ContentHash.of("A", "uno")).isNotEqualTo(ContentHash.of("A", "dos"));
    }

    @Test
    @DisplayName("La huella es un sha256 hexadecimal de 64 caracteres")
    void formatoHex() {
        assertThat(ContentHash.of("t", "b").value()).hasSize(64).matches("[0-9a-f]{64}");
    }
}
