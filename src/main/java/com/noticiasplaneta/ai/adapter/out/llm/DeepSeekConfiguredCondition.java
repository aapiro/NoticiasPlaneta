package com.noticiasplaneta.ai.adapter.out.llm;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Activa DeepSeek solo si hay una clave real.
 *
 * <p>{@code @ConditionalOnProperty} considera presente una variable vacía y el adaptador
 * acababa llamando a la API sin credencial. Aquí exigimos texto no blanco para que, sin
 * clave, se use {@link UnconfiguredEnrichmentAdapter} y el error sea {@code AI_NOT_CONFIGURED}.
 */
public class DeepSeekConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String key = context.getEnvironment().getProperty("ai.deepseek.api-key");
        return key != null && !key.isBlank();
    }
}
