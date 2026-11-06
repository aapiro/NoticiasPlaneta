package com.noticiasplaneta.ai.adapter.out.budget;

import com.noticiasplaneta.ai.application.port.out.BudgetPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Presupuesto diario en memoria. Si algún día hay varias instancias, este adaptador pasa a
 * contar en PostgreSQL; el puerto no cambia.
 */
@Component
class InMemoryBudgetAdapter implements BudgetPort {

    private final long dailyLimitMicros;
    private final AtomicLong spent = new AtomicLong();

    InMemoryBudgetAdapter(@Value("${ai.budget.daily-micros:5000000}") long dailyLimitMicros) {
        this.dailyLimitMicros = dailyLimitMicros;
    }

    @Override
    public boolean tryConsume(long costMicros) {
        long current = spent.addAndGet(costMicros);
        if (current > dailyLimitMicros) {
            spent.addAndGet(-costMicros);
            return false;
        }
        return true;
    }
}
