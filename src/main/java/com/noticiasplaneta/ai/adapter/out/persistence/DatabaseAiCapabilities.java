package com.noticiasplaneta.ai.adapter.out.persistence;

import com.noticiasplaneta.ai.adapter.out.config.ConfigAiCapabilities;
import com.noticiasplaneta.ai.application.port.out.AiCapabilities;
import com.noticiasplaneta.ai.application.port.out.AiCapabilitiesAdminPort;
import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.ai.domain.AiMode;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Fuente de verdad en base de datos, con caché en memoria (ADR-011, F1).
 *
 * <p>Manda sobre {@link ConfigAiCapabilities}; si una capacidad no está en la tabla,
 * se usa el valor del {@code application.yml}. Cambiar un modo por el panel surte
 * efecto en el siguiente job, sin reiniciar.
 */
@Component
@Primary
public class DatabaseAiCapabilities implements AiCapabilities, AiCapabilitiesAdminPort {

    private final SpringDataFeatureFlagRepository flags;
    private final ConfigAiCapabilities defaults;
    private final Clock clock;

    /** Volátil: se sustituye entero en cada recarga, nunca se muta en sitio. */
    private volatile Map<AiCapability, AiMode> cache = Map.of();

    DatabaseAiCapabilities(SpringDataFeatureFlagRepository flags, ConfigAiCapabilities defaults, Clock clock) {
        this.flags = flags;
        this.defaults = defaults;
        this.clock = clock;
    }

    @PostConstruct
    void load() {
        reload();
    }

    @Override
    public AiMode modeOf(AiCapability capability) {
        if (capability == null) return AiMode.OFF;
        AiMode stored = cache.get(capability);
        return stored != null ? stored : defaults.modeOf(capability);
    }

    /** Estado de todas las capacidades, para el panel. */
    @Override
    public Map<AiCapability, AiMode> snapshot() {
        Map<AiCapability, AiMode> all = new EnumMap<>(AiCapability.class);
        for (AiCapability capability : AiCapability.values()) {
            all.put(capability, modeOf(capability));
        }
        return all;
    }

    @Override
    @Transactional
    public void setMode(AiCapability capability, AiMode mode, String updatedBy) {
        if (capability == null || mode == null) return;

        FeatureFlagJpaEntity entity = flags.findById(capability.name())
                .orElseGet(() -> new FeatureFlagJpaEntity(capability.name(), mode.name(), updatedBy, Instant.now(clock)));
        entity.update(mode.name(), updatedBy, Instant.now(clock));
        flags.save(entity);

        reload();
    }

    private void reload() {
        Map<AiCapability, AiMode> next = new EnumMap<>(AiCapability.class);
        for (FeatureFlagJpaEntity entity : flags.findAll()) {
            try {
                next.put(AiCapability.valueOf(entity.capability()), AiMode.parse(entity.mode()));
            } catch (IllegalArgumentException ignored) {
                // una fila con una capacidad desconocida no debe tumbar la app
            }
        }
        cache = next;
    }
}
