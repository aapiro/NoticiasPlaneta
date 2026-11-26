package com.noticiasplaneta.generation.config;

import com.noticiasplaneta.ai.application.port.in.CapacidadesIaUseCase;
import com.noticiasplaneta.ai.application.port.in.PresupuestoIaUseCase;
import com.noticiasplaneta.generation.adapter.out.llm.RedaccionNoConfigurada;
import com.noticiasplaneta.generation.application.port.in.GenerarBorradorUseCase;
import com.noticiasplaneta.generation.application.port.out.GeneracionRepository;
import com.noticiasplaneta.generation.application.port.out.RedaccionPort;
import com.noticiasplaneta.generation.application.service.GenerarBorradorService;
import com.noticiasplaneta.ingest.application.port.in.BuscarMaterialUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.PublicarSinRevisionUseCase;
import com.noticiasplaneta.news.application.port.in.VincularFuentesGeneradasUseCase;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.shared.domain.AuditPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** F8 — cableado del generador. */
@Configuration
class GeneracionConfiguration {

    /** Sin proveedor configurado: falla con AI_NOT_CONFIGURED en vez de devolver vacío. */
    @Bean
    @ConditionalOnMissingBean(RedaccionPort.class)
    RedaccionPort redaccionNoConfigurada() {
        return new RedaccionNoConfigurada();
    }

    @Bean
    GenerarBorradorUseCase generarBorradorUseCase(BuscarMaterialUseCase material,
                                                  SectionAdminUseCase secciones,
                                                  RedaccionPort redaccion,
                                                  PresupuestoIaUseCase presupuesto,
                                                  CapacidadesIaUseCase capacidades,
                                                  CreateDraftFromItemUseCase crearBorrador,
                                                  VincularFuentesGeneradasUseCase vincularFuentes,
                                                  PublicarSinRevisionUseCase publicar,
                                                  GeneracionRepository generaciones,
                                                  AuditPort auditoria,
                                                  Clock reloj) {
        return new GenerarBorradorService(material, secciones, redaccion, presupuesto, capacidades,
                crearBorrador, vincularFuentes, publicar, generaciones, auditoria, reloj);
    }
}
