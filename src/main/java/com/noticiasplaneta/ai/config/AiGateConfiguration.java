package com.noticiasplaneta.ai.config;

import com.noticiasplaneta.ai.application.port.in.CapacidadesIaUseCase;
import com.noticiasplaneta.ai.application.port.in.PresupuestoIaUseCase;
import com.noticiasplaneta.ai.application.port.out.AiCapabilities;
import com.noticiasplaneta.ai.application.port.out.BudgetPort;
import com.noticiasplaneta.ai.application.service.CapacidadesIaService;
import com.noticiasplaneta.ai.application.service.PresupuestoIaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Puertos de entrada del módulo de IA para el resto de módulos (F8 los usa). */
@Configuration
class AiGateConfiguration {

    @Bean
    PresupuestoIaUseCase presupuestoIaUseCase(BudgetPort budget) {
        return new PresupuestoIaService(budget);
    }

    @Bean
    CapacidadesIaUseCase capacidadesIaUseCase(AiCapabilities capabilities) {
        return new CapacidadesIaService(capabilities);
    }
}
