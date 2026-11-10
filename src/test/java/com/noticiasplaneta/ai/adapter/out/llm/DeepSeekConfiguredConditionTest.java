package com.noticiasplaneta.ai.adapter.out.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DeepSeek · condición de clave configurada")
class DeepSeekConfiguredConditionTest {

    private final DeepSeekConfiguredCondition condition = new DeepSeekConfiguredCondition();

    @Test
    @DisplayName("Sin propiedad no activa el adaptador")
    void sinPropiedadNoActiva() {
        assertThat(matches(null)).isFalse();
    }

    @Test
    @DisplayName("Clave vacía o en blanco no activa el adaptador")
    void claveVaciaNoActiva() {
        assertThat(matches("")).isFalse();
        assertThat(matches("   ")).isFalse();
    }

    @Test
    @DisplayName("Clave con texto sí activa el adaptador")
    void claveRealActiva() {
        assertThat(matches("sk-test")).isTrue();
    }

    private boolean matches(String key) {
        ConditionContext context = mock(ConditionContext.class);
        MockEnvironment environment = new MockEnvironment();
        if (key != null) {
            environment.setProperty("ai.deepseek.api-key", key);
        }
        when(context.getEnvironment()).thenReturn(environment);
        return condition.matches(context, mock(AnnotatedTypeMetadata.class));
    }
}
