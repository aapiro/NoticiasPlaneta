package com.noticiasplaneta.generation.adapter.out.llm;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Activa el redactor de DeepSeek solo si hay una clave real.
 *
 * <p>Misma razón que en el enriquecido (ADR-011): {@code @ConditionalOnProperty} da por
 * presente una variable vacía y el adaptador acabaría llamando a la API sin credencial.
 * Sin clave se usa el adaptador que falla con {@code AI_NOT_CONFIGURED}.
 */
public class RedaccionConfigurada implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String key = context.getEnvironment().getProperty("ai.deepseek.api-key");
        return key != null && !key.isBlank();
    }
}
