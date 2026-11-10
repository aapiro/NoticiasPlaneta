package com.noticiasplaneta.news.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F04 · Cálculo de contraste")
class ContrastCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");

    @Test
    @DisplayName("Cuenta dominios distintos, no enlaces")
    void cuentaDominios() {
        List<ArticleSource> sources = List.of(
                source("elpais.com", false, 1),
                source("elpais.com", false, 1),
                source("boe.es", false, 1),
                source("caido.es", true, 1),
                source("peso-cero.es", false, 0));

        assertThat(ContrastCalculator.independentCount(sources)).isEqualTo(2);
    }

    @Test
    @DisplayName("Umbrales: low <2, medium 2-3, high >=4")
    void umbrales() {
        assertThat(ContrastLevel.from(0)).isEqualTo(ContrastLevel.LOW);
        assertThat(ContrastLevel.from(1)).isEqualTo(ContrastLevel.LOW);
        assertThat(ContrastLevel.from(2)).isEqualTo(ContrastLevel.MEDIUM);
        assertThat(ContrastLevel.from(3)).isEqualTo(ContrastLevel.MEDIUM);
        assertThat(ContrastLevel.from(4)).isEqualTo(ContrastLevel.HIGH);
    }

    private ArticleSource source(String rootDomain, boolean unreachable, int weight) {
        return new ArticleSource(UUID.randomUUID(), UUID.randomUUID(),
                "https://" + rootDomain + "/noticia", rootDomain, SourceKind.PRENSA,
                unreachable ? 404 : 200, NOW, weight, unreachable);
    }
}
