package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F04 · Dominio raíz")
class RootDomainTest {

    @Test
    @DisplayName("Dos URLs del mismo medio comparten dominio")
    void mismoMedio() {
        assertThat(RootDomain.of("https://www.elpais.com/a")).isEqualTo("elpais.com");
        assertThat(RootDomain.of("https://blog.elpais.com/b")).isEqualTo("elpais.com");
    }

    @Test
    @DisplayName("Reconoce sufijos de dos niveles")
    void dosNiveles() {
        assertThat(RootDomain.of("https://news.bbc.co.uk/x")).isEqualTo("bbc.co.uk");
        assertThat(RootDomain.of("https://www.boe.es/x")).isEqualTo("boe.es");
    }

    @Test
    @DisplayName("Una IP se conserva tal cual")
    void ip() {
        assertThat(RootDomain.of("http://192.168.1.10/x")).isEqualTo("192.168.1.10");
    }

    @Test
    @DisplayName("URL inválida se rechaza con código estable")
    void invalida() {
        assertThatThrownBy(() -> RootDomain.of("ftp://ejemplo.es/x"))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo("SOURCE_URL_INVALID"));
        assertThatThrownBy(() -> RootDomain.of("sin-esquema"))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo("SOURCE_URL_INVALID"));
    }
}
