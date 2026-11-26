package com.noticiasplaneta.ingest.config;

import com.noticiasplaneta.ingest.application.port.in.BuscarMaterialUseCase;
import com.noticiasplaneta.ingest.application.port.out.MaterialQueryRepository;
import com.noticiasplaneta.ingest.application.service.BuscarMaterialService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Búsqueda de material para el generador (F8). */
@Configuration
class BuscarMaterialConfiguration {

    @Bean
    BuscarMaterialUseCase buscarMaterialUseCase(MaterialQueryRepository repositorio) {
        return new BuscarMaterialService(repositorio);
    }
}
