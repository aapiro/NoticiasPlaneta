package com.noticiasplaneta.ai.application.port.out;

/** Presupuesto de IA. Sin esto, un bucle mal escrito cuesta dinero real. */
public interface BudgetPort {

    boolean tryConsume(long costMicros);
}
