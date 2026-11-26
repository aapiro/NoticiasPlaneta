package com.noticiasplaneta.ai.adapter.out.budget;

import com.noticiasplaneta.ai.application.port.out.BudgetPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Presupuesto diario en memoria, con el día natural de Madrid.
 *
 * <p>El gasto se guarda junto al día al que pertenece: al cambiar el día el contador
 * vuelve a cero sin reiniciar el proceso. "Diario" es el día civil de
 * {@code ai.budget.zone} (Europe/Madrid por defecto), no 24 h desde el arranque ni el
 * día UTC (ADR-017).
 *
 * <p>Si algún día hay varias instancias, este adaptador pasa a contar en PostgreSQL; el
 * puerto no cambia.
 */
@Component
class InMemoryBudgetAdapter implements BudgetPort {

    private final long dailyLimitMicros;
    private final ZoneId zone;
    private final Clock clock;
    /** Día del gasto y microdólares consumidos ese día; inmutable, se sustituye por CAS. */
    private final AtomicReference<DaySpend> spend = new AtomicReference<>(new DaySpend(null, 0));

    @Autowired
    InMemoryBudgetAdapter(@Value("${ai.budget.daily-micros:5000000}") long dailyLimitMicros,
                          @Value("${ai.budget.zone:Europe/Madrid}") String zone,
                          Clock clock) {
        this(dailyLimitMicros, ZoneId.of(zone), clock);
    }

    /** Constructor para tests: permite fijar reloj y zona sin Spring. */
    InMemoryBudgetAdapter(long dailyLimitMicros, ZoneId zone, Clock clock) {
        this.dailyLimitMicros = dailyLimitMicros;
        this.zone = zone;
        this.clock = clock;
    }

    @Override
    public boolean tryConsume(long costMicros) {
        if (costMicros < 0) {
            throw new IllegalArgumentException("Un coste negativo no consume presupuesto: " + costMicros);
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        while (true) {
            DaySpend current = spend.get();
            long spentToday = today.equals(current.day()) ? current.micros() : 0L;
            // remaining >= 0 y la suma no desborda: spentToday nunca supera dailyLimitMicros
            long remaining = dailyLimitMicros - spentToday;
            if (costMicros > remaining) {
                return false;
            }
            if (spend.compareAndSet(current, new DaySpend(today, spentToday + costMicros))) {
                return true;
            }
        }
    }

    /** Gasto del día en curso, en microdólares; para diagnóstico. */
    long spentTodayMicros() {
        DaySpend current = spend.get();
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        return today.equals(current.day()) ? current.micros() : 0L;
    }

    private record DaySpend(LocalDate day, long micros) {
    }
}
