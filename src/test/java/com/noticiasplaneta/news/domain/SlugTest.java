package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ADR-001 · Slug canónico")
class SlugTest {

    @Test
    @DisplayName("Quita acentos, mayúsculas y puntuación")
    void normaliza() {
        assertThat(Slug.fromTitle("El Congreso aprueba la reforma fiscal").value())
                .isEqualTo("el-congreso-aprueba-la-reforma-fiscal");
        assertThat(Slug.fromTitle("¡Sube la luz un 12%!").value()).isEqualTo("sube-la-luz-un-12");
        assertThat(Slug.fromTitle("España, año 2026").value()).isEqualTo("espana-ano-2026");
    }

    @Test
    @DisplayName("Un título vacío no genera slug")
    void tituloVacio() {
        assertThatThrownBy(() -> Slug.fromTitle("   ")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("El sufijo de colisión es determinista")
    void sufijo() {
        Slug base = Slug.fromTitle("Reforma fiscal");

        assertThat(base.withSuffix(2).value()).isEqualTo("reforma-fiscal-2");
        assertThat(base.withSuffix(3).value()).isEqualTo("reforma-fiscal-3");
    }

    @Test
    @DisplayName("El slug nunca supera la longitud máxima")
    void longitudMaxima() {
        Slug largo = Slug.fromTitle("a".repeat(200) + " " + "b".repeat(200));

        assertThat(largo.value().length()).isLessThanOrEqualTo(Slug.MAX_LENGTH);
    }
}
