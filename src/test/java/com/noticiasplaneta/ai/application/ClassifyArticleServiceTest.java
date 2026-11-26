package com.noticiasplaneta.ai.application.service;

import com.noticiasplaneta.ai.application.port.in.ClassifyArticleUseCase;
import com.noticiasplaneta.ai.application.port.out.BudgetPort;
import com.noticiasplaneta.ai.application.port.out.ClassificationPort;
import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("F6 · clasificación con red de seguridad")
class ClassifyArticleServiceTest {

    private static final List<String> CATALOGO = List.of("tecnologia", "politica", "general");

    private final BudgetPort sinPresupuesto = cost -> false;
    private final BudgetPort presupuesto = cost -> true;

    @Test
    @DisplayName("Una sección del catálogo se respeta")
    void seccionDelCatalogo() {
        var service = new ClassifyArticleService(
                (title, allowed, fallback) -> new ClassificationPort.Classification("tecnologia", 0.9, "m", "v1"),
                presupuesto);

        ClassifyArticleUseCase.Classification result = service.classify("Nuevo modelo de IA", CATALOGO, "general");

        assertThat(result.section()).isEqualTo("tecnologia");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("Una sección inventada cae a la de reserva")
    void seccionInventada() {
        var service = new ClassifyArticleService(
                (title, allowed, fallback) -> new ClassificationPort.Classification("sucesos", 0.99, "m", "v1"),
                presupuesto);

        assertThat(service.classify("Suceso", CATALOGO, "general").section()).isEqualTo("general");
    }

    @Test
    @DisplayName("Poca confianza cae a la de reserva: no se publica adivinando")
    void pocaConfianza() {
        var service = new ClassifyArticleService(
                (title, allowed, fallback) -> new ClassificationPort.Classification("tecnologia", 0.1, "m", "v1"),
                presupuesto);

        assertThat(service.classify("Dudoso", CATALOGO, "general").section()).isEqualTo("general");
    }

    @Test
    @DisplayName("Sin presupuesto no se llama al modelo")
    void sinPresupuesto() {
        var service = new ClassifyArticleService(
                (title, allowed, fallback) -> {
                    throw new AssertionError("no se debe llamar al modelo sin presupuesto");
                },
                sinPresupuesto);

        assertThatThrownBy(() -> service.classify("IA", CATALOGO, "general"))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", "AI_BUDGET_EXHAUSTED");
    }

    @Test
    @DisplayName("Sin titular no hay clasificación")
    void sinTitular() {
        var service = new ClassifyArticleService(
                (title, allowed, fallback) -> new ClassificationPort.Classification("tecnologia", 0.9, "m", "v1"),
                presupuesto);

        assertThatThrownBy(() -> service.classify(" ", CATALOGO, "general"))
                .isInstanceOf(DomainException.class);
    }
}
