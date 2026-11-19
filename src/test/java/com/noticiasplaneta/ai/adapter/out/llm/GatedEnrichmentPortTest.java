package com.noticiasplaneta.ai.adapter.out.llm;

import com.noticiasplaneta.ai.application.port.out.AiCapabilities;
import com.noticiasplaneta.ai.application.port.out.EnrichmentPort;
import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.ai.domain.AiMode;
import com.noticiasplaneta.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ADR-011 · La puerta de capacidades no llama al proveedor si está apagada")
class GatedEnrichmentPortTest {

    private static final String TITLE = "Titular";
    private static final String BODY = "Cuerpo de la noticia";

    /** Delegado que cuenta cuántas veces le llaman. */
    private static final class CountingPort implements EnrichmentPort {
        int calls = 0;

        @Override
        public EnrichmentResult enrich(String title, String body) {
            calls++;
            return new EnrichmentResult(List.of("etiqueta"), List.of(), "resumen", "modelo", "v1", "proveedor", 42L);
        }
    }

    private static final class StubCapabilities implements AiCapabilities {
        private final Map<AiCapability, AiMode> modes = new EnumMap<>(AiCapability.class);

        void set(AiCapability capability, AiMode mode) {
            modes.put(capability, mode);
        }

        @Override
        public AiMode modeOf(AiCapability capability) {
            return modes.getOrDefault(capability, AiMode.OFF);
        }
    }

    private final StubCapabilities capabilities = new StubCapabilities();

    private GatedEnrichmentPort gateFor(CountingPort delegate) {
        return new GatedEnrichmentPort(capabilities, delegate);
    }

    @Test
    @DisplayName("Con la capacidad apagada no se llama al proveedor ni una vez")
    void apagadaNoLlama() {
        CountingPort delegate = new CountingPort();
        GatedEnrichmentPort gate = gateFor(delegate);

        assertThatThrownBy(() -> gate.enrich(TITLE, BODY))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo("AI_CAPABILITY_DISABLED"));

        assertThat(delegate.calls)
                .as("el proveedor no debe recibir ni una llamada")
                .isZero();
    }

    @Test
    @DisplayName("Con la capacidad encendida delega y devuelve su resultado")
    void encendidaDelega() {
        CountingPort delegate = new CountingPort();
        capabilities.set(AiCapability.ENRICH_TAGS, AiMode.EXTERNAL);
        GatedEnrichmentPort gate = gateFor(delegate);

        EnrichmentPort.EnrichmentResult result = gate.enrich(TITLE, BODY);

        assertThat(delegate.calls).isEqualTo(1);
        assertThat(result.tags()).containsExactly("etiqueta");
        assertThat(result.summary()).isEqualTo("resumen");
    }

    @Test
    @DisplayName("El modo HYBRID también enciende la capacidad")
    void hybridEnciende() {
        CountingPort delegate = new CountingPort();
        capabilities.set(AiCapability.ENRICH_TAGS, AiMode.HYBRID);
        GatedEnrichmentPort gate = gateFor(delegate);

        gate.enrich(TITLE, BODY);

        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("Un modo desconocido en configuración se trata como apagado")
    void modoDesconocidoEsApagado() {
        assertThat(AiMode.parse("vaya-usted-a-saber")).isEqualTo(AiMode.OFF);
        assertThat(AiMode.parse(null)).isEqualTo(AiMode.OFF);
        assertThat(AiMode.parse("")).isEqualTo(AiMode.OFF);
        assertThat(AiMode.parse(" external ")).isEqualTo(AiMode.EXTERNAL);
    }
}
