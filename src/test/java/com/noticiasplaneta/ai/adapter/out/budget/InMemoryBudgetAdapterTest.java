package com.noticiasplaneta.ai.adapter.out.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-017: el presupuesto es diario de verdad. El día es el civil de Europe/Madrid y
 * cambia sin reiniciar el proceso; dentro del mismo día el techo no se pasa.
 */
@DisplayName("Presupuesto de IA · día natural de Madrid (ADR-017)")
class InMemoryBudgetAdapterTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** 26/11/2026 11:00 en Madrid (CET, UTC+1). */
    private static final Instant MANANA_DEL_26 = Instant.parse("2026-11-26T10:00:00Z");
    /** 27/11/2026 00:00:01 en Madrid: un segundo después del cambio de día. */
    private static final Instant PRIMER_SEGUNDO_DEL_27 = Instant.parse("2026-11-26T23:00:01Z");

    private final RelojDePrueba reloj = new RelojDePrueba(MANANA_DEL_26, MADRID);

    @Test
    @DisplayName("dentro del mismo día no se pasa del techo")
    void respetaElTechoDentroDelMismoDia() {
        InMemoryBudgetAdapter budget = new InMemoryBudgetAdapter(1_000, MADRID, reloj);

        assertThat(budget.tryConsume(600)).isTrue();
        assertThat(budget.tryConsume(400)).isTrue();
        assertThat(budget.tryConsume(1)).isFalse();
        assertThat(budget.spentTodayMicros()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("un intento rechazado no descuenta presupuesto")
    void unIntentoRechazadoNoGasta() {
        InMemoryBudgetAdapter budget = new InMemoryBudgetAdapter(100, MADRID, reloj);

        assertThat(budget.tryConsume(60)).isTrue();
        assertThat(budget.tryConsume(60)).isFalse();
        assertThat(budget.spentTodayMicros()).isEqualTo(60);
        assertThat(budget.tryConsume(40)).isTrue();
    }

    @Test
    @DisplayName("dos días con el mismo presupuesto no se suman: el contador vuelve a cero")
    void alCambiarElDiaElContadorVuelveACero() {
        long diario = 5_000_000;
        InMemoryBudgetAdapter budget = new InMemoryBudgetAdapter(diario, MADRID, reloj);

        // Día 1: se agota el presupuesto entero.
        assertThat(budget.tryConsume(2_500_000)).isTrue();
        assertThat(budget.tryConsume(2_500_000)).isTrue();
        assertThat(budget.tryConsume(1)).isFalse();

        // Día 2, sin reiniciar nada: el techo está otra vez entero.
        reloj.moverA(PRIMER_SEGUNDO_DEL_27);
        assertThat(budget.spentTodayMicros()).isZero();
        assertThat(budget.tryConsume(2_500_000)).isTrue();
        assertThat(budget.tryConsume(2_500_000)).isTrue();
        assertThat(budget.tryConsume(1)).isFalse();
        assertThat(budget.spentTodayMicros()).isEqualTo(diario);
    }

    @Test
    @DisplayName("el día es el de Madrid, no el de UTC")
    void elDiaEsElDeMadridNoElDeUtc() {
        // 23:30 UTC del 26 = 00:30 del 27 en Madrid: ya es otro día en Madrid.
        reloj.moverA(Instant.parse("2026-11-26T23:30:00Z"));
        InMemoryBudgetAdapter budget = new InMemoryBudgetAdapter(1_000, MADRID, reloj);
        assertThat(budget.tryConsume(1_000)).isTrue();
        assertThat(budget.tryConsume(1)).isFalse();

        // 22:00 UTC del 27 = 23:00 del 27 en Madrid: mismo día, sigue agotado.
        reloj.moverA(Instant.parse("2026-11-27T22:00:00Z"));
        assertThat(budget.tryConsume(1)).isFalse();

        // 23:30 UTC del 27 = 00:30 del 28 en Madrid: día nuevo.
        reloj.moverA(Instant.parse("2026-11-27T23:30:00Z"));
        assertThat(budget.tryConsume(1_000)).isTrue();
    }

    @Test
    @DisplayName("un coste negativo es un error, no un ingreso")
    void costeNegativoEsUnError() {
        InMemoryBudgetAdapter budget = new InMemoryBudgetAdapter(1_000, MADRID, reloj);

        assertThatThrownBy(() -> budget.tryConsume(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(budget.spentTodayMicros()).isZero();
    }

    @Test
    @DisplayName("en concurrencia el techo se respeta exacto")
    void enConcurrenteNoSePasaDelTecho() throws InterruptedException {
        int hilos = 8;
        int intentosPorHilo = 200;
        long techo = 500;
        InMemoryBudgetAdapter budget = new InMemoryBudgetAdapter(techo, MADRID, reloj);
        AtomicInteger aceptados = new AtomicInteger();
        CountDownLatch salida = new CountDownLatch(hilos);
        CountDownLatch empieza = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(hilos)) {
            for (int i = 0; i < hilos; i++) {
                pool.execute(() -> {
                    try {
                        empieza.await();
                        for (int j = 0; j < intentosPorHilo; j++) {
                            if (budget.tryConsume(1)) {
                                aceptados.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        salida.countDown();
                    }
                });
            }
            empieza.countDown();
            assertThat(salida.await(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(aceptados.get()).isEqualTo((int) techo);
        assertThat(budget.spentTodayMicros()).isEqualTo(techo);
    }

    /** Reloj movible: todos los {@code withZone} comparten el instante. */
    private static final class RelojDePrueba extends Clock {

        private final AtomicReference<Instant> ahora;
        private final ZoneId zone;

        RelojDePrueba(Instant inicio, ZoneId zone) {
            this(new AtomicReference<>(inicio), zone);
        }

        private RelojDePrueba(AtomicReference<Instant> ahora, ZoneId zone) {
            this.ahora = ahora;
            this.zone = zone;
        }

        void moverA(Instant instante) {
            ahora.set(instante);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId otra) {
            return new RelojDePrueba(ahora, otra);
        }

        @Override
        public Instant instant() {
            return ahora.get();
        }
    }
}
