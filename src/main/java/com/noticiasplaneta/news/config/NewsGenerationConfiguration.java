package com.noticiasplaneta.news.config;

import com.noticiasplaneta.news.application.port.in.LinkSourceUseCase;
import com.noticiasplaneta.news.application.port.in.PublicarSinRevisionUseCase;
import com.noticiasplaneta.news.application.port.in.RecalculateContrastUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.VincularFuentesGeneradasUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.application.service.PublicarSinRevisionService;
import com.noticiasplaneta.news.application.service.VincularFuentesGeneradasService;
import com.noticiasplaneta.shared.domain.AuditPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Puertos que F8 necesita del módulo de noticias. */
@Configuration
class NewsGenerationConfiguration {

    @Bean
    VincularFuentesGeneradasUseCase vincularFuentesGeneradasUseCase(
            LinkSourceUseCase enlazar, RecalculateContrastUseCase contraste) {
        return new VincularFuentesGeneradasService(enlazar, contraste);
    }

    @Bean
    PublicarSinRevisionUseCase publicarSinRevisionUseCase(ArticleRepository articulos,
                                                          TransitionArticleUseCase transiciones,
                                                          AuditPort auditoria,
                                                          Clock reloj) {
        return new PublicarSinRevisionService(articulos, transiciones, auditoria, reloj);
    }
}
