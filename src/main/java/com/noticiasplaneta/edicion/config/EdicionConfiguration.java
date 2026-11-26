package com.noticiasplaneta.edicion.config;

import com.noticiasplaneta.edicion.application.port.in.EdicionAdminUseCase;
import com.noticiasplaneta.edicion.application.port.out.EdicionStore;
import com.noticiasplaneta.edicion.application.service.EdicionAdminService;
import com.noticiasplaneta.ingest.application.port.in.IngestAllSourcesUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.shared.domain.AuditPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class EdicionConfiguration {

    @Bean
    EdicionAdminUseCase edicionAdminUseCase(EdicionStore store,
                                            IngestAllSourcesUseCase ingesta,
                                            TransitionArticleUseCase transiciones,
                                            SectionAdminUseCase secciones,
                                            AuditPort audit,
                                            Clock clock,
                                            @Value("${edicion.ventana-horas:48}") int ventanaHoras,
                                            @Value("${edicion.limite:20}") int limite,
                                            @Value("${edicion.max-por-fuente:3}") int maxPorFuente) {
        return new EdicionAdminService(store, ingesta, transiciones, secciones, audit, clock,
                ventanaHoras, limite, maxPorFuente);
    }
}
