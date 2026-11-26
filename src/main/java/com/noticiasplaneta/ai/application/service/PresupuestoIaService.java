package com.noticiasplaneta.ai.application.service;

import com.noticiasplaneta.ai.application.port.in.PresupuestoIaUseCase;
import com.noticiasplaneta.ai.application.port.out.BudgetPort;

/** Presupuesto de IA expuesto a otros módulos sin sacar {@code BudgetPort} de su módulo. */
public class PresupuestoIaService implements PresupuestoIaUseCase {

    private final BudgetPort budget;

    public PresupuestoIaService(BudgetPort budget) {
        this.budget = budget;
    }

    @Override
    public boolean reservar(long costeMicros) {
        if (costeMicros < 0) {
            throw new IllegalArgumentException("El coste a reservar no puede ser negativo");
        }
        return budget.tryConsume(costeMicros);
    }
}
