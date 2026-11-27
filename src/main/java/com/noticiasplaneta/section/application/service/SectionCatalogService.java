package com.noticiasplaneta.section.application.service;

import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import com.noticiasplaneta.section.application.port.in.SectionCatalogUseCase;
import com.noticiasplaneta.section.application.port.out.SectionConfigRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
class SectionCatalogService implements SectionCatalogUseCase {

    private final SectionConfigRepository sections;

    SectionCatalogService(SectionConfigRepository sections) {
        this.sections = sections;
    }

    @Override
    public List<SectionAdminUseCase.SectionView> activas() {
        return sections.findActivas().stream()
                .map(SectionCatalogService::view)
                .toList();
    }

    @Override
    public Optional<SectionAdminUseCase.SectionView> porSlug(String slug) {
        return sections.findBySlug(slug)
                .filter(com.noticiasplaneta.section.domain.SectionConfig::activa)
                .map(SectionCatalogService::view);
    }

    private static SectionAdminUseCase.SectionView view(
            com.noticiasplaneta.section.domain.SectionConfig s) {
        return new SectionAdminUseCase.SectionView(
                s.slug(), s.nombre(), s.descripcion(), s.activa(), s.orden(),
                s.maxPiezas(), s.enfoque().name(), s.autoPublica(), s.autoLimiteDiario());
    }
}
