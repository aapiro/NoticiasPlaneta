package com.noticiasplaneta.ai.adapter.out.persistence;

import com.noticiasplaneta.ai.adapter.out.config.ConfigAiCapabilities;
import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.ai.domain.AiMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ADR-011 F1 · La tabla manda y el cambio se ve sin reiniciar")
class DatabaseAiCapabilitiesTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC);
    private final ConfigAiCapabilities defaults = new ConfigAiCapabilities();

    /** Repositorio simulado con estado real en un mapa. */
    private SpringDataFeatureFlagRepository repository(Map<String, FeatureFlagJpaEntity> store) {
        SpringDataFeatureFlagRepository repo = mock(SpringDataFeatureFlagRepository.class);

        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
        when(repo.findAll()).thenAnswer(inv -> List.copyOf(store.values()));
        when(repo.save(any(FeatureFlagJpaEntity.class))).thenAnswer(inv -> {
            FeatureFlagJpaEntity entity = inv.getArgument(0);
            store.put(entity.capability(), entity);
            return entity;
        });

        return repo;
    }

    @Test
    @DisplayName("Sin fila en la tabla se usa el valor de application.yml (OFF por defecto)")
    void sinFilaUsaConfig() {
        DatabaseAiCapabilities capabilities = new DatabaseAiCapabilities(repository(new HashMap<>()), defaults, clock);
        capabilities.load();

        assertThat(capabilities.modeOf(AiCapability.ENRICH_TAGS)).isEqualTo(AiMode.OFF);
        assertThat(capabilities.isOn(AiCapability.ENRICH_TAGS)).isFalse();
    }

    @Test
    @DisplayName("Cambiar el modo se refleja al momento, sin recrear el bean")
    void cambioEnCaliente() {
        Map<String, FeatureFlagJpaEntity> store = new HashMap<>();
        store.put("ENRICH_TAGS", new FeatureFlagJpaEntity("ENRICH_TAGS", "OFF", null, Instant.now(clock)));

        DatabaseAiCapabilities capabilities = new DatabaseAiCapabilities(repository(store), defaults, clock);
        capabilities.load();
        assertThat(capabilities.modeOf(AiCapability.ENRICH_TAGS)).isEqualTo(AiMode.OFF);

        capabilities.setMode(AiCapability.ENRICH_TAGS, AiMode.EXTERNAL, "admin@noticiasplaneta");

        assertThat(capabilities.modeOf(AiCapability.ENRICH_TAGS)).isEqualTo(AiMode.EXTERNAL);
        assertThat(capabilities.isOn(AiCapability.ENRICH_TAGS)).isTrue();
        assertThat(store.get("ENRICH_TAGS").mode()).isEqualTo("EXTERNAL");
        assertThat(store.get("ENRICH_TAGS").updatedBy()).isEqualTo("admin@noticiasplaneta");
    }

    @Test
    @DisplayName("Una capacidad nueva se inserta si no existía la fila")
    void insertaSiNoExiste() {
        Map<String, FeatureFlagJpaEntity> store = new HashMap<>();
        DatabaseAiCapabilities capabilities = new DatabaseAiCapabilities(repository(store), defaults, clock);
        capabilities.load();

        capabilities.setMode(AiCapability.ENRICH_SUMMARY, AiMode.HYBRID, "test");

        assertThat(capabilities.modeOf(AiCapability.ENRICH_SUMMARY)).isEqualTo(AiMode.HYBRID);
        assertThat(store).containsKey("ENRICH_SUMMARY");
    }

    @Test
    @DisplayName("El snapshot devuelve las seis capacidades para el panel")
    void snapshotCompleto() {
        DatabaseAiCapabilities capabilities = new DatabaseAiCapabilities(repository(new HashMap<>()), defaults, clock);
        capabilities.load();

        assertThat(capabilities.snapshot())
                .hasSize(AiCapability.values().length)
                .containsKeys(AiCapability.values());
    }
}
