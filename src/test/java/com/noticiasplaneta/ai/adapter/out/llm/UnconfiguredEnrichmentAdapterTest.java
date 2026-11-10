package com.noticiasplaneta.ai.adapter.out.llm;

import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Enriquecido sin proveedor configurado")
class UnconfiguredEnrichmentAdapterTest {

    @Test
    @DisplayName("Falla con AI_NOT_CONFIGURED, no con un resultado vacío")
    void fallaConCodigoClaro() {
        assertThatThrownBy(() -> new UnconfiguredEnrichmentAdapter().enrich("Titular", "Cuerpo"))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo("AI_NOT_CONFIGURED"));
    }
}
