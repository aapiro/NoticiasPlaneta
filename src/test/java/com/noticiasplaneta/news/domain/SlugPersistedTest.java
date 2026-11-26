package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Slug: creación estricta, lectura tolerante")
class SlugPersistedTest {

    @Test
    @DisplayName("Al crear, el formato es obligatorio")
    void creacionEstricta() {
        assertThatThrownBy(() -> Slug.of("foo--bar")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Slug.of("foo-")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Slug.of("")).isInstanceOf(DomainException.class);
        assertThat(Slug.of("foo-bar-2").value()).isEqualTo("foo-bar-2");
    }

    @Test
    @DisplayName("Un slug heredado de la base se puede leer sin romper la pieza")
    void lecturaTolerante() {
        assertThat(Slug.persisted("lamine-yamal-mueve-ficha--a070").value())
                .isEqualTo("lamine-yamal-mueve-ficha--a070");
        assertThatThrownBy(() -> Slug.persisted(" ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Slug.persisted(null)).isInstanceOf(DomainException.class);
    }
}
