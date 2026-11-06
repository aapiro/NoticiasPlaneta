package com.noticiasplaneta.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Diff por líneas")
class TextDiffTest {

    @Test
    @DisplayName("Textos iguales no producen cambios")
    void iguales() {
        var diff = TextDiff.between("una\ndos", "una\ndos");
        assertThat(diff).allMatch(l -> l.kind() == TextDiff.Kind.IGUAL);
        assertThat(TextDiff.cambia("una\ndos", "una\ndos")).isFalse();
    }

    @Test
    @DisplayName("Una línea cambiada sale como una quitada y una añadida")
    void unaLinea() {
        var diff = TextDiff.between("uno\ndos\ntres", "uno\nDOS\ntres");
        assertThat(diff.stream().filter(l -> l.kind() == TextDiff.Kind.QUITADA).map(TextDiff.Line::text))
                .containsExactly("dos");
        assertThat(diff.stream().filter(l -> l.kind() == TextDiff.Kind.AÑADIDA).map(TextDiff.Line::text))
                .containsExactly("DOS");
    }

    @Test
    @DisplayName("Añadir al final se ve como añadidura, no como reemplazo")
    void anadirAlFinal() {
        var diff = TextDiff.between("uno", "uno\ndos");
        assertThat(diff.stream().filter(l -> l.kind() == TextDiff.Kind.QUITADA)).isEmpty();
        assertThat(diff.stream().filter(l -> l.kind() == TextDiff.Kind.AÑADIDA).map(TextDiff.Line::text))
                .containsExactly("dos");
    }

    @Test
    @DisplayName("Un texto vacío no rompe el diff")
    void vacio() {
        assertThat(TextDiff.between(null, "")).isNotNull();
        assertThat(TextDiff.cambia(null, "   ")).isFalse();
    }

    @Test
    @DisplayName("Los espacios sobrantes no cuentan como cambio")
    void espacios() {
        assertThat(TextDiff.cambia("texto", "  texto  ")).isFalse();
    }
}
