package com.noticiasplaneta.ai.application.port.in;

/**
 * Presupuesto de IA visto desde otros módulos (F8).
 *
 * <p>El generador tiene que pasar por {@code BudgetPort} antes de gastar dinero real,
 * pero un módulo no puede depender de los puertos de salida de otro. Este puerto de
 * entrada es la puerta legítima: quien lo use no sabe si el contador es en memoria,
 * en base de datos o compartido.
 */
public interface PresupuestoIaUseCase {

    /**
     * Reserva coste en microdólares.
     *
     * @return {@code true} si se ha reservado; {@code false} si agotaría el presupuesto del día
     */
    boolean reservar(long costeMicros);
}
